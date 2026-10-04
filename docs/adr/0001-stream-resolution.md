# ADR 0001: Stream resolution via InnerTubeX behind `StreamResolver`

- **Status:** Proposed (revised). The first live gate passed only partially. The follow-up investigation and the revised recommendation are below, pending approval.
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
- `*.youtube.com` and `*.googlevideo.com` for resolution and media. The InnerTubeX runs only contacted `music.youtube.com`, `www.youtube.com` (`/watch`, `/embed`, `/youtubei/v1/player`, `/s/player/…`) and `rr*---sn-*.googlevideo.com` (`/videoplayback`);
- `youtubei.googleapis.com` (`/youtubei/v1/visitor_id`, `/reel`, `/player`). NewPipeExtractor needs it, but InnerTubeX does not;
- anything else fails before a connection is opened, and only the host is logged, never the URL.

Artwork is loaded by a different HTTP client (Coil), so it does not go through this allowlist. If Shura later adds an app-wide allowlist, it must also include the artwork hosts seen in search and player responses on 2026-10-04: `i.ytimg.com` (video thumbnails), `lh3.googleusercontent.com` (album and track art), and `yt3.ggpht.com` and `yt3.googleusercontent.com` (artist and channel images).

**Remaining remote code, and it is unavoidable:** YouTube's own `player.js` is downloaded from `www.youtube.com` and evaluated in QuickJS to solve the `sig`/`n` challenges. Every working extractor does this (NewPipeExtractor evaluates it in Rhino).

**Live check:** across 8 resolutions with every request routed through a host guard, no request was attempted to GitHub, jsDelivr or any host outside the allowlist.

### QuickJS sandbox

The context that evaluates `player.js` has no network, file or host API access. This was verified in three ways:

1. **Native build.** quickjs-kt 1.0.14 compiles only the QuickJS core (`quickjs.c`, `libregexp`, `libunicode`, `cutils`, `dtoa`) plus its JNI bridge. It does **not** compile `quickjs-libc`, which is what provides the `std`/`os` modules (files, processes, timers). The shipped `libquickjs.so` (linux_x64) exports no `js_std*`/`js_os*`/`js_init_module*` symbols and imports none of `fopen`, `open`, `popen`, `system`, `socket`, `connect`, `exec*` or `dlopen`.
2. **InnerTubeX setup.** `QuickJsEngine` creates the runtime with an evaluation timeout and a memory limit, and registers **no** host functions or bindings: there are no `define`/`function`/`asyncFunction` calls anywhere in the library. Its YouTube globals setup only adds inert stub objects (`XMLHttpRequest`, `location`, `document`, `navigator`, `self`, `window`, and a minimal `Intl` polyfill) that cannot call back into the host.
3. **Runtime probe.** In a default quickjs-kt 1.0.14 context, `std`, `os`, `fetch`, `require`, `XMLHttpRequest`, `print`, `console`, `scriptArgs`, `setTimeout` and `WebAssembly` are all `undefined`.

**Residual risk:** a memory-safety bug in QuickJS itself (as in issue #23). That is a crash risk, and it is covered by the process isolation below.

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

## Validation gate results (2026-10-04, Linux, one residential IP)

**Setup:**
- InnerTubeX v0.7.4 with no `TokenProvider` and no `RemotePlayerConfigStore`.
- `AudioQuality.HIGH`, `allowHls = false`, `allowSabr = false` (direct only, the transport Media3 plays natively).
- Requests were sequential: 8 s between tracks and 400 ms between 1 MiB range chunks.
- Two runs, 8 tracks in total. The first run's picker took the top search result, which was a live version or a video (OMV/UGC). The second run preferred album audio (ATV).

| Track (type) | Client / profile | Transport | Expected bytes | Received bytes | Decode | Resolve time |
|---|---|---|---|---|---|---|
| Hips Don't Lie, Anniversary (OMV) | VISIONOS_0_1 (no PoToken) | Direct, opus | 3,691,220 | 3,691,220 (206×4) | OK, 216.2 s | 1,208 ms |
| La Camisa Negra, live MTV (OMV) | VISIONOS_0_1 | Direct, opus | 3,710,791 | 3,710,791 (206×4) | OK, 230.5 s | 263 ms |
| Tití Me Preguntó, live Super Bowl (UGC) | VISIONOS_0_1 | Direct, opus | 1,371,892 | 1,371,892 (206×2) | OK, 79.6 s | 281 ms |
| Bohemian Rhapsody, Live Aid (UGC) | VISIONOS_0_1 | Direct, opus | 2,643,857 | 2,643,857 (206×3) | OK, 164.6 s | 274 ms |
| Provenza, Tiësto remix live (UGC) | VISIONOS_0_1 | Direct, opus | 5,421,902 | 5,421,902 (206×6) | OK, 350.0 s | 275 ms |
| **Tití Me Preguntó (ATV, explicit)** | WEB_EMBEDDED_PLAYER (no PoToken) | Direct, opus | 4,318,756 | **0 (403 on first chunk)** | — | 5,459 ms |
| La Camisa Negra (ATV) | VISIONOS_0_1 | Direct, opus | 3,565,616 | 3,565,616 (206×4) | OK, 216.7 s | 223 ms |
| Hips Don't Lie (ATV) | VISIONOS_0_1 | Direct, AAC | 3,568,011 | 3,568,011 (206×4) | OK, 220.4 s | 215 ms |

**Hosts contacted:** `music.youtube.com`, `www.youtube.com` and `rr*---sn-*.googlevideo.com` only.

**Findings:**

1. **Every successful track came from a single client, `VISIONOS_0_1`.** Every other automatic profile was skipped at selection with "GVS PO-token provider unavailable" (and `WEB_CREATOR` also with "login required"). Playback without a PoToken therefore depends on one legacy client. InnerTubeX v0.7.2 already removed it from automatic selection once ("rejected legacy visionOS"), and v0.7.3 allowed it back for ordinary audio only.
2. **Explicit tracks fail with direct transport and no PoToken.** InnerTubeX limits `VISIONOS_0_1` to "normal audio only".
   - For the explicit ATV track, it found playable responses only for **SABR** profiles (`VISIONOS_SABR`, `WEB_REMIX_SABR`), which this run disallowed.
   - It then fell back to `WEB_EMBEDDED_PLAYER`. That path downloaded `player.js`, so the cipher ran, but googlevideo answered **403** on the first range.
   - **Not determined:** whether that 403 comes from a wrong `n` solution (no remote configs, EJS only) or from the missing streaming PoToken.
   - This matters for the target audience: a large share of urban and Latin catalog is marked explicit.
3. **The cipher path was not exercised by any successful resolution.** `VISIONOS_0_1` returns plain URLs, so the "EJS-only, no remote configs" setup is still unvalidated for clients that need deciphering.
4. Resolution is fast once warm (about 215–280 ms per track; about 1.2 s cold; 5.5 s for the failing explicit track).
5. No requests to GitHub or jsDelivr, and no blocked hosts.

**Outcome:** the gate **passes for ordinary tracks and fails for explicit ones**, and the passing path depends on one fragile client. Per this ADR, the spike stops here and the approach is revisited before more code is written.

## Follow-up investigation (2026-10-04)

### How the reference apps resolve streams today

Both apps work today, which makes them the best available evidence. This is read from their source code (Metrolist `f758c86`, Orchard `e6cf95b`); no code was copied.

**Metrolist (Android only)**
- InnerTubeX v0.7.0, with **direct transport only**: it sets `allowHls = false` and `allowSabr = false` and rejects any SABR result.
- **PoToken:** a `TokenProvider` declared as `WEB_BOTGUARD` with `usesWebView = true`. A hidden Android `WebView` runs BotGuard and mints a player token per `videoId` and a streaming token bound to the visitor data. Android only.
- **Remote cipher configs enabled.** Metrolist points them at the zemer-cipher table. InnerTubeX v0.7.4 would no longer accept that URL: it only accepts `MetrolistGroup/faraday`.
- **Explicit tracks:** it passes the catalog's `isExplicit` to `ContentHints` and has no other special path. It sends login cookies when the user is signed in, but does not require a session.
- **Client order:** InnerTubeX's scored catalog, plus per-video client exclusion after a 403/410 for 5 min.

**Orchard, Android**
- **Primary resolver:** NewPipeExtractor v0.26.4, with **no PoToken provider configured**. When the user is signed in, the session cookie rides along on YouTube hosts.
- **Fallback:** a guest InnerTube client catalog (`ANDROID_VR`, `VISIONOS`, `WEB_REMIX` with PoToken, …) with per-track client bans.
- **PoToken:** minted with bgutils-js (MIT) in a hidden `WebView`.
- **Explicit tracks:** Orchard states that the catalog's "explicit" flag is only a lyrics advisory and must not change stream selection.
- **Real age gates:** they need a **signed-in session** (itag 18 direct, then HLS).

**Orchard, desktop (Electron)**
- youtubei.js for streams.
- **PoToken:** BotGuard in Node with bgutils-js plus a jsdom DOM shim.
- A local proxy serves the audio.

### Diagnosis of the explicit-track 403 (one resolution each)

| Variant | Client / profile | Transport | Result |
|---|---|---|---|
| Direct only, no remote configs (gate run) | WEB_EMBEDDED_PLAYER | Direct | 403 on the first range |
| **SABR allowed**, no remote configs | VISIONOS_SABR (no PoToken) | SABR | **OK**: 4,318,756 bytes in 694 ms, decodes to 243.7 s |
| **Remote configs enabled** (Faraday table fetched), direct only | WEB_EMBEDDED_PLAYER | Direct | **Still 403** |

**Conclusions:**
- **The cipher is not the cause.** The 403 is identical with the remote solver configs.
- **The session is not the cause either.** The track plays without a session over SABR, and also over NewPipe's direct URL (below). It is not a real age gate.
- **The cause is InnerTubeX's client policy combined with the missing PoToken.** Without a token, the only automatic direct client that is not blocked is `VISIONOS_0_1`, and InnerTubeX allows it for "normal audio only". For explicit tracks the remaining direct candidate (`WEB_EMBEDDED_PLAYER`) yields a URL that googlevideo rejects. **Most likely** it needs a streaming (GVS) PoToken, but this is not verified, because no token was minted.

**Side observations:**
- InnerTubeX keeps some failure state **process-wide**. A fresh extractor in the same JVM failed in 5 ms with no network request right after a previous run. A per-process resolver (see "Crash isolation") also contains this.
- The Faraday repository (`MetrolistGroup/faraday`) declares **no license**. That is one more reason never to fetch its configs in Shura.

### NewPipeExtractor gate (v0.26.5, same conditions, no PoToken provider)

| Track | Client (from the URL's `c=` parameter) | Transport | Expected bytes | Received bytes | Decode | Resolve time | Hosts |
|---|---|---|---|---|---|---|---|
| Tití Me Preguntó (ATV, explicit) | VISIONOS | Direct (progressive), itag 251 opus | 4,318,756 | 4,318,756 (206×5) | OK, 243.7 s | 2,545 ms | `youtubei.googleapis.com`, `www.youtube.com`, googlevideo |
| La Camisa Negra (ATV) | VISIONOS | Direct, itag 251 | 3,565,616 | 3,565,616 (206×4) | OK, 216.7 s | 1,787 ms | same |
| Hips Don't Lie (ATV) | VISIONOS | Direct, itag 251 | 3,219,692 | **0 (403)** | — | 2,067 ms | same |

**Findings:**
- **NewPipeExtractor also depends on `VISIONOS` without a PoToken.** It plays the explicit track over direct transport, so InnerTubeX's "normal audio only" rule is a library policy, not a YouTube limit observed here.
- **One of the three URLs was rejected**, and the cause was not determined. NewPipe does not expose the client's required request headers per stream, and the test used a desktop Firefox User-Agent. Orchard notes that the URL must be fetched with the identity of the client that produced it. **Not verified.**
- **Neither library downloaded `player.js`** in these runs, so the cipher was not exercised by either.
- Resolution takes about 1.8–2.5 s per track (more requests: `visitor_id`, `reel`, `player`, `next`), compared with about 0.2–0.3 s for InnerTubeX once warm.

### Comparison

| | InnerTubeX v0.7.4 | NewPipeExtractor v0.26.5 | Metrolist / Orchard approach |
|---|---|---|---|
| Ordinary tracks today, no PoToken | ✅ direct, via `VISIONOS_0_1` only | ✅ direct, via `VISIONOS` only (2/3, one 403 not explained) | — (both mint tokens) |
| Explicit tracks today, no PoToken | ❌ direct (403) · ✅ **SABR** (`VISIONOS_SABR`) | ✅ direct (1/1) | Metrolist: direct + PoToken · Orchard: NewPipe, then PoToken clients |
| Needs a PoToken for a robust path | Yes: every non-legacy client was skipped for lack of one | Not shown; it uses the same single legacy client | **Yes: both apps mint one** |
| Needs a session | No (only for real age gates and uploads) | No | Orchard: only for real age gates and uploads |
| Cipher exercised | No (resolved without deciphering) | No | Yes, in production (remote configs or EJS in a WebView) |
| Platforms | KMP: Android + JVM desktop (Windows QuickJS bundled) | JVM (Android + desktop), not `commonMain` | Metrolist: Android only · Orchard: separate Android and Electron code |
| Transports | Direct, HLS, SABR (experimental API) | Direct, DASH/HLS manifests | Direct (Metrolist rejects SABR) |
| PoToken minting | Host-provided (`TokenProvider`) | Host-provided (`PoTokenProvider`) | Android: BotGuard in a hidden `WebView` · desktop: bgutils-js + jsdom in Node |
| License | GPL-3.0 | GPL-3.0 | GPL-3.0 / AGPL-3.0 (reference only) |

### Revised recommendation

1. **Keep InnerTubeX as the primary `StreamResolver` and NewPipeExtractor as plan B.**
   - Without a PoToken, both libraries hit the same wall (one legacy client), so switching would not remove the risk.
   - InnerTubeX remains KMP, resolves faster, and is the only one of the two with a working no-token route for explicit tracks today (SABR).
2. **Make PoToken minting a first-class part of `:core:stream`.** Both production apps do it, and it is the only thing that makes the non-legacy direct clients eligible again. InnerTubeX already defines the `TokenProvider` contract, so Shura only supplies the minter per platform.
3. **SABR is a fallback, not the main path.**
   - It works today without a token, but its InnerTubeX API is marked `@ExperimentalSabrApi`, and Metrolist (by the same authors) does not use it in production.
   - Using it from Media3 needs a custom `DataSource` that streams from `SabrAudioStream` and restarts at the seek position.
   - It is deferred until the PoToken path is measured.

**PoToken on Android (proposed design, to be validated with the same live gate):**
- A small `PoTokenMinter` in `androidMain` of `:core:stream`, implementing InnerTubeX's `TokenProvider`.
- It hosts a hidden `WebView` loaded with a local HTML page with base URL `https://www.youtube.com`. Inside it, bgutils-js (MIT, bundled as a local asset, not fetched remotely) runs BotGuard:
  1. fetch the challenge (`jnn/v1/Create`);
  2. run the BotGuard program;
  3. get the integrity token (`jnn/v1/GenerateIT`);
  4. mint a **streaming token** bound to the visitor data, once per session;
  5. mint a **player token** per `videoId`.
- **Rules:**
  - tokens are cached in memory with their expiry;
  - a token is invalidated when a URL carrying it is rejected;
  - a hard timeout (about 8 s) applies, after which it proceeds without a token;
  - the `WebView` is recreated if it crashes;
  - tokens are never logged.
- It lives in the same `:resolver` process as the rest of the resolver (see "Crash isolation").
- **The JS that runs is BotGuard's own challenge program, downloaded from Google.** It is remote code, like `player.js`, and inherent to the mechanism. It runs inside the system `WebView` sandbox.
- **Acceptance:** the explicit ATV track and the ordinary tracks play over **direct transport with a non-legacy client** (e.g. `WEB_REMIX`), and the downloaded length matches.

**PoToken on desktop (Windows); none of these is evaluated yet, to be decided in Phase 5:**
1. **System WebView2** (Edge/Chromium runtime, preinstalled on Windows 10/11), driven through a native bridge. This is the closest analogue to the Android `WebView` and adds no browser to the download. It needs JNI/JNA interop and Windows-only code, and cannot be tested locally.
2. **Embedded Chromium** (JCEF, e.g. through a Compose Desktop wrapper). Cross-platform and proven, but it adds a large download (Chromium) to the installer.
3. **BotGuard in a JS engine with a DOM shim**, the way Orchard desktop uses Node + jsdom. On the JVM there is no jsdom equivalent, so this would mean bundling Node or writing a shim. BotGuard may detect a fake DOM. Highest uncertainty.
4. **No token on desktop:** legacy direct client plus SABR for the rest. Simplest, but it inherits the single-client fragility.
5. **External token service:** rejected. It sends user traffic to a third party and conflicts with the no-remote-code rule.

**Suggested order:**
1. Finish the spike with InnerTubeX direct without a token, which plays ordinary tracks, to get search → resolve → play working end to end on the phone.
2. **Immediately after**, implement the Android `PoTokenMinter` and re-run the live gate on the phone with the explicit track.
3. Decide SABR afterwards, depending on what still fails.
4. Desktop PoToken in Phase 5, starting with WebView2.

## Consequences

- Shura depends on a young, single-maintainer GPL-3.0 library for its riskiest part. This is mitigated by:
  - the narrow `StreamResolver` boundary;
  - an exact version pin with checksum verification;
  - plan B already identified.
- Upgrading InnerTubeX means reading its CHANGELOG and running the live gate again.
- Without remote solver configs, Shura gets new cipher support only when the bundled EJS solver in InnerTubeX is updated, which means a Shura release.
