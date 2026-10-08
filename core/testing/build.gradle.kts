plugins {
    id("shura.kmp.library")
}

// Test doubles shared by the feature modules' tests. Only ever a test dependency, never shipped.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.player)
        }
    }
}
