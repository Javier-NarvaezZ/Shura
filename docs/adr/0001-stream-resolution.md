# ADR 0001: Stream resolution via InnerTubeX behind `StreamResolver`

- **Status:** Accepted (pending the live gate in "Validation gate")
- **Date:** 2026-10-04
- **Scope:** `:core:stream`, `:core:innertube`, build configuration

## Context

Resolving a playable audio URL for a YouTube Music track is the main technical risk of Shura (PLAN.md §3). It needs client selection, signature (`sig`) and throttling (`n`) deobfuscation of YouTube's `player.js`, and in some cases a proof-of-origin token (PoToken). All of this changes frequently and without notice.

The architecture rule is fixed: everything that resolves streams lives in `:core:stream` behind a `StreamResolver` interface and must be replaceable without touching the rest of the code.

Shura targets Android and JVM desktop (Windows; Linux for development) with Kotlin Multiplatform, and is licensed GPL-3.0.

## Options considered

All facts were checked on 2026-10-04 against the GitHub API, JitPack and Maven Central.

### A. InnerTubeX (`MetrolistGroup/innertubex`)

- **Activity:** repository created 2026-08-21. Latest release v0.7.4 on 2026-09-30, after 12 releases in about a month. One effective maintainer (48 of 52 commits). 20 stars.
- **Distribution:** JitPack only (`com.github.MetrolistGroup.innertubex:innertubex:<tag>`). The v0.7.4 build is green. Not on Maven Central.
- **Targets:** Kotlin Multiplatform, built with Kotlin 2.4.10, Ktor 3.5.2 and kotlinx 1.11.0:
  - `android` (minSdk 23);
  - `jvm("desktop")` (JVM 17);
  - `iosArm64`, `iosSimulatorArm64`.
- **Coverage:**
  - Client selection with scoring and health tracking.
  - Layered cipher solving, run in QuickJS through `quickjs-kt` (Apache-2.0):
    1. remote "Faraday" configs;
    2. yt-dlp EJS solver, bundled as a resource;
    3. regex parser.

    The quickjs-kt JVM jar ships `libquickjs` for windows_x64, linux_x64/aarch64 and macOS.
  - Direct, HLS and SABR transports, format selection, and media URL probing.
- **PoToken:** only a `TokenProvider` contract. The host app has to mint the token. Without a token, the library falls back to clients that do not require one.
- **License:** GPL-3.0.
- **Risks:**
  - Bus factor of one.
  - `0.x` API that changes between minor versions.
  - Open issue #23: a native SIGSEGV in QuickJS during challenge solving kills the host process.
  - Optional runtime download of third-party solver configs (see "Remote code").

### B. NewPipeExtractor (`TeamNewPipe/NewPipeExtractor`)

- **Activity:** active since 2017. Latest release v0.26.5 on 2026-08-15. Many maintainers, about 2000 stars.
- **Distribution:** releases on JitPack only. Maven Central has snapshots only.
- **Targets:** plain Java/JVM, not KMP. It fits a shared JVM source set (Android and desktop) but not `commonMain`. Android with minSdk < 33 needs `desugar_jdk_libs_nio`. The API is blocking.
- **Coverage:** YouTube `sig`/`n` deobfuscation (own JS lexer plus Rhino 1.8.1, pure Java, no native code). Its YouTube Music support is limited to search and a trending kiosk: no home, album, radio or `next`.
- **PoToken:** only a `PoTokenProvider` interface. The host mints the token.
- **License:** GPL-3.0.

### C. Own implementation

- **Catalog** (search/browse/next over Ktor): small, stable and testable with fixtures.
- **Cipher and client selection:** still needs an embedded JS engine and continuous tracking of `player.js` changes. This is the fragile work that A and B already do.
- **PoToken:** the same gap as with A and B.

## Decision

1. **Use InnerTubeX, pinned to exactly v0.7.4, only inside `:core:stream`.** It is wrapped by an `InnerTubeXStreamResolver` that implements Shura's `StreamResolver`. No InnerTubeX type appears in the public API of `:core:stream`, which only exposes `StreamResolver`, `ResolvedStream`, `AudioQuality` and Shura error types.
2. **Write the catalog in `:core:innertube` with Ktor** (starting with search). InnerTubeX would only save the transport and a few DTOs. The parsers to domain models are Shura's own code either way. Owning the catalog keeps the stable part of the app independent of a fast-moving `0.x` library. The extra effort is roughly 200–300 lines plus fixture tests.
3. **Plan B:** a `NewPipeStreamResolver` in a shared JVM source set, behind the same interface. It is not implemented now.
4. **PoToken is out of scope** for the spike. The validation gate measures whether playback works without it.
5. **Shura stays GPL-3.0.** Both candidate libraries are GPL-3.0, so this is a constraint: Shura cannot move to a permissive license while it depends on either of them.

## Remote code

Requirement: the app must not download and execute third-party solver code at runtime.

From reading the InnerTubeX v0.7.4 source:

- **Remote configs come from two places.** Both are reached only through `RemotePlayerConfigStore`:
  - `RemotePlayerConfigStore`, the config table. It allows `raw.githubusercontent.com` and `github.com` under `MetrolistGroup/faraday`.
  - `GitHubPlayerConfigClient`, preprocessed players per player hash. It uses `cdn.jsdelivr.net` and a `raw.githubusercontent.com` fallback.
- **`PlayerConfigRepository.disabled()` blocks those downloads.** It returns a repository with `enabled = false` and empty source URLs. Every network entry point checks `repository.enabled` first and returns before building a URL:
  - in the store: `refreshIfStale`, `forceRefresh`, `refreshAfterStreamRejection`, `getConfig` and the legacy signature-timestamp lookup;
  - in `GitHubPlayerConfigClient`: `solve`, which is the only caller of its fetch path, and it reuses the store's repository.
- **A stronger option is to pass no store at all.** `YouTubeCipherService(httpClient, remotePlayerConfigStore = null)` and `YtConfigParserImpl(..., remotePlayerConfigStore = null)` are both supported. With no store, `GitHubPlayerConfigClient` is never constructed, so the remote config path does not exist at runtime.

**Decision:** construct InnerTubeX without a `RemotePlayerConfigStore`. Cipher solving then uses only the bundled yt-dlp EJS solver and the regex parser.

As defense in depth, the Ktor `HttpClient` given to InnerTubeX gets a **host allowlist**:
- YouTube hosts (`*.youtube.com`) and media hosts (`*.googlevideo.com`), finalized from the hosts observed in the live gate;
- anything else fails before a connection is opened, and only the host is logged, never the URL.

**Remaining remote code, and it is unavoidable:** YouTube's own `player.js` is downloaded from `www.youtube.com` and evaluated in QuickJS to solve the `sig`/`n` challenges. Every working extractor does this (NewPipeExtractor evaluates it in Rhino). It runs in an embedded JS engine with no file or network bindings.

The live gate below verifies empirically that no request reaches GitHub or jsDelivr.

## Crash isolation (mitigation for issue #23, not implemented yet)

A native crash in QuickJS runs in-process and kills the whole app, including playback.

**Android:** run the whole resolver in a separate process.
- A bound `Service` declared with `android:process=":resolver"` hosts `InnerTubeXStreamResolver`.
- In the main process, an IPC-backed `StreamResolver` (Messenger or AIDL) sends `videoId` and quality, and receives a `ResolvedStream`: URL, MIME type, bitrate, content length, expiry and request headers. These are small, Parcelable- or JSON-friendly values, far below the 1 MB Binder limit.
- We isolate the **whole resolver**, not just the cipher. That needs no InnerTubeX internals: issue #23 asks to make `ExtractionCipherService` public because isolating only the cipher currently needs reflection.
- If the resolver process dies, the client sees `binderDied` / `DeadObjectException`, rebinds, retries once and then reports a typed error. Media3 playback stays in the main process and survives.
- **Costs** (estimated, not measured): extra process memory (QuickJS alone may use up to its configured heap limit); bind latency on cold start; `Application.onCreate` runs in both processes, so DI setup must be per-process; and the HTTP session (visitor data) lives in the resolver process.
- **When:** after the spike, before the public beta (Phase 4). The `StreamResolver` interface stays the same, so only the Android wiring changes.

**Desktop:** the equivalent would be a child JVM process. It is not evaluated yet; it will be decided in Phase 5.

## Supply chain

- InnerTubeX is consumed from JitPack, which builds artifacts from Git tags. A rebuilt tag can change bytes.
- When InnerTubeX is added, **Gradle dependency verification** is enabled (`gradle/verification-metadata.xml` with SHA-256 for every resolved artifact, including plugins). A changed artifact then fails the build instead of being used silently.
- The JitPack repository is declared with a content filter so it can only serve `com.github.MetrolistGroup.innertubex`.
- **Not verified:** whether F-Droid's main repository accepts a JitPack dependency that bundles prebuilt QuickJS native libraries. IzzyOnDroid is expected to be fine.

## Validation gate (before writing the domain model or search)

A disposable JVM program, kept outside the repository, runs on Linux. For a handful of music tracks (including an explicit one), it must:

1. resolve an audio stream with InnerTubeX v0.7.4, **without a PoToken** and **without a `RemotePlayerConfigStore`**;
2. download the **whole** file with bounded `Range` requests and the returned request headers, checking that the byte count equals the reported content length, which detects the "prefix only, then 403" failure mode both references observed;
3. decode it end to end with a local tool (e.g. `ffprobe` or `ffmpeg -f null`);
4. record which client was used and the list of hosts contacted, with no GitHub or jsDelivr host in that list.

If this fails, the spike stops and the decision is revisited (PoToken minting, plan B, or both) before any more code is written.

## Consequences

- Shura depends on a young, single-maintainer GPL-3.0 library for its riskiest part. This is mitigated by:
  - the narrow `StreamResolver` boundary;
  - an exact version pin with checksum verification;
  - plan B already identified.
- Upgrading InnerTubeX means reading its CHANGELOG and running the live gate again.
- Without remote solver configs, Shura gets new cipher support only when the bundled EJS solver in InnerTubeX is updated, which means a Shura release.
