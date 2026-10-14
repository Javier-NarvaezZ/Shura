plugins {
    id("shura.kmp.compose")
}

// Synced lyrics as a mode of the player (Phase 3, E1).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.model)
            implementation(projects.core.player)
            api(projects.core.lyrics)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
