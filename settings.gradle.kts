pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // InnerTubeX is only published on JitPack. Exclusive content: that group can only come from
        // JitPack, and JitPack can serve nothing else (ADR 0001, supply chain).
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.MetrolistGroup.innertubex") }
        }
    }
}

rootProject.name = "shura"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app-android",
    ":app-desktop",
    ":app-shell",
    ":core:model",
    ":core:innertube",
    ":core:stream",
    ":core:network",
    ":core:data",
    ":core:player",
    ":core:auth",
    ":core:ui",
    ":core:testing",
    ":feature:home",
    ":feature:search",
    ":feature:player",
    ":feature:library",
    ":feature:lyrics",
    ":feature:settings",
    ":tools:livecheck",
)
