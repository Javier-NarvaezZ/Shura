# Dependencias y licencias

Anota aquí cada dependencia nueva con su licencia y por qué se agregó.

| Dependencia | Versión | Licencia | Motivo |
|---|---|---|---|
| Gradle (wrapper) | 9.7.0 | Apache-2.0 | Build tool. Highest Gradle supported by Kotlin 2.4.20 |
| Kotlin (Multiplatform, JVM, Compose compiler plugins, kotlin-test) | 2.4.20 | Apache-2.0 | Language, KMP targets (android + jvm), Compose compiler |
| Android Gradle Plugin (`com.android.application`, `com.android.kotlin.multiplatform.library`) | 9.3.3 | Apache-2.0 | Android app and KMP Android target. 9.4.x is outside Kotlin 2.4.20's supported range |
| Compose Multiplatform (runtime, foundation, ui, desktop, Gradle plugin) | 1.12.1 | Apache-2.0 | Shared UI for Android and desktop |
| Compose Multiplatform Material3 | 1.12.0-alpha03 | Apache-2.0 | Material 3 components. Alpha: official pairing for CMP 1.12.1 (no stable release for 1.12) |
| androidx.activity:activity-compose | 1.13.0 | Apache-2.0 | Hosts Compose in the Android `MainActivity` (`setContent`, `enableEdgeToEdge`) |
| detekt (`dev.detekt` Gradle plugin) | 2.0.0-alpha.6 | Apache-2.0 | Static analysis. Alpha: 1.23.x does not support Kotlin 2.4 |
| ktlint | 1.8.0 | MIT | Kotlin formatting checks |
| ktlint-gradle (`org.jlleitschuh.gradle.ktlint`) | 14.2.0 | MIT | Runs ktlint from Gradle (supports AGP 9 built-in Kotlin) |
| Ktor client core (`io.ktor:ktor-client-core`) | 3.6.0 | Apache-2.0 | `:core:innertube` HTTP client for the YouTube Music catalog; `api` of `:core:stream`. Built with Kotlin 2.3.21; works from Kotlin 2.4.20. InnerTubeX is built against 3.5.2 and resolves to 3.6.0; live gate re-run on 3.6.0 passed on 2026-10-04 (ADR 0001 R6) |
| kotlinx-serialization-json | 1.11.0 | Apache-2.0 | `:core:innertube` reads responses as `JsonElement` and builds request bodies without the serialization compiler plugin. 1.12.0 is still RC |
| Ktor client mock (`io.ktor:ktor-client-mock`) | 3.6.0 | Apache-2.0 | Test only: `MockEngine` with recorded fixtures |
| kotlinx-coroutines-test | 1.11.0 | Apache-2.0 | Test only: `runTest` |
| InnerTubeX (`com.github.MetrolistGroup.innertubex:innertubex`, `-android`, `-desktop`) | 0.7.4 (exact pin) | GPL-3.0 | `:core:stream` stream extraction behind `StreamResolver` (ADR 0001). **JitPack only**, declared with `exclusiveContent` so that group can come from nowhere else and JitPack serves nothing else. Built without `RemotePlayerConfigStore` (R3) |
| quickjs-kt (`io.github.dokar3:quickjs-kt`, `-jvm`, `-android`) | 1.0.14 | Apache-2.0 | Transitive via InnerTubeX: JS engine for cipher solving. Maven Central. Bundles prebuilt QuickJS (MIT) natives: the JVM jar has linux x64/aarch64, macOS x64/aarch64 and **windows x64**; the Android AAR has arm64-v8a, armeabi-v7a, x86 and x86_64. Both are covered by the SHA-256 checksums |
| OkHttp (`com.squareup.okhttp3:okhttp`, resolves to `okhttp-android` / `okhttp-jvm`) | 5.5.0 | Apache-2.0 | `:core:network`: the app's single HTTP client with the host allowlist interceptor (ADR 0001 R7). Maven Central. Media3's OkHttp module asks for 4.12.0 and is upgraded to 5.5.0; OkHttp states it does not break binary compatibility with non-alpha APIs. Pulls Okio 3.18.2 (Apache-2.0) and, on Android, `androidx.startup:startup-runtime` 1.2.0 and `androidx.tracing:tracing` 1.0.0 (Apache-2.0, Google Maven) |
| Ktor OkHttp engine (`io.ktor:ktor-client-okhttp`) | 3.6.0 | Apache-2.0 | `:core:network`: Ktor (catalog and InnerTubeX) runs on the single `OkHttpClient` |
| Media3 ExoPlayer (`androidx.media3:media3-exoplayer`) | 1.11.1 | Apache-2.0 | `:core:player` (Android): playback. Google Maven. Spike version; final version decided in Phase 2 with `MediaSession` (Android 17 controls issue, ADR 0001 R7). Pulls media3-common/-datasource/-extractor/-container/-decoder/-database, `androidx.annotation`, `androidx.exifinterface` and Guava 33.3.1-android with failureaccess (Apache-2.0) |
| Media3 OkHttp data source (`androidx.media3:media3-datasource-okhttp`) | 1.11.1 | Apache-2.0 | `:core:player` (Android): ExoPlayer fetches media through the single `OkHttpClient` |
| Media3 session (`androidx.media3:media3-session`) | 1.11.1 | Apache-2.0 | `:core:player` (Android): `MediaSessionService`, media notification and system controls (Phase 2). Google Maven. Media3 stays on 1.11.1 after the Phase 2 check on Android 17; Metrolist pinned 1.10.1 for missing Android 17 controls (MetrolistGroup/Metrolist#4404, no root cause given, no matching androidx/media issue). Pulls `androidx.media` 1.7.0, `androidx.lifecycle:lifecycle-service`, `androidx.concurrent:concurrent-futures` and `androidx.core` (Apache-2.0, Google Maven) |
| SQLDelight (`app.cash.sqldelight`: `runtime`, `coroutines-extensions`, `android-driver`, Gradle plugin; `sqlite-driver` for JVM tests only) | 2.4.0 | Apache-2.0 | `:core:data`: persistence of the play queue and play history (Phase 2, E3). Chosen over Room because it needs no annotation processor (no KSP) with Kotlin 2.4.20 and AGP 9.3.3; compatibility checked with a probe (E0) on 2026-10-05. Maven Central. At runtime pulls `androidx.sqlite:sqlite`/`sqlite-framework` 2.7.1 (Apache-2.0, Google Maven); JVM tests use `org.xerial:sqlite-jdbc` 3.53.4.0 (Apache-2.0). Build-time only (Gradle plugin classpath, not shipped): kotlinpoet 2.4.0, sql-psi 0.8.0 and jheaps 0.14 (Apache-2.0), apfloat 1.14.0 (MIT), jgrapht 1.5.3 (LGPL-2.1 or EPL-2.0) |
| bgutils-js (reference only, not a build dependency) | v4.0.3 | MIT | The BotGuard bootstrap JavaScript in `:core:stream` (`BotGuardScripts.kt`) was written from bgutils-js's implementation of the BotGuard protocol and is treated as derived from it. The copyright and MIT notice are in that file (also embedded in the script that ships in the app) and in `THIRD_PARTY_NOTICES.md`. Nothing is fetched or bundled from the project itself |

## Dependency verification

`gradle/verification-metadata.xml` holds SHA-256 checksums for every resolved artifact (plugins, `build-logic`, Android and desktop builds, including the Windows-only Compose Desktop natives used by CI). A dependency whose checksum does not match fails the build.

Regenerate it after adding or upgrading a dependency, review the diff, and commit it with the change:

```
./gradlew --write-verification-metadata sha256 --refresh-dependencies \
    -I gradle/verification-windows-natives.init.gradle \
    build detekt ktlintCheck :app-android:assembleDebug :app-desktop:assemble :app-desktop:resolveWindowsNatives
```

`--refresh-dependencies` is required: with a warm cache Gradle can skip already-cached parent POMs and BOMs, and CI (empty cache) then fails verification. To double-check, run the CI tasks with an empty `GRADLE_USER_HOME`.

The init script adds a temporary configuration that resolves the windows-x64 Compose Desktop variant, which a Linux run would otherwise skip.
