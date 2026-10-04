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
