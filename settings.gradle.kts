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
    }
}

rootProject.name = "shura"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app-android",
    ":app-desktop",
    ":core:model",
    ":core:innertube",
    ":core:stream",
    ":core:data",
    ":core:player",
    ":core:auth",
    ":core:ui",
    ":feature:home",
    ":feature:search",
    ":feature:player",
    ":feature:library",
    ":feature:lyrics",
    ":feature:settings",
)
