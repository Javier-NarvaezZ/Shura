plugins {
    id("shura.kmp.compose")
}

// Album, artist and playlist pages (Phase 2, E6).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.model)
            implementation(projects.core.player)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
