plugins {
    id("shura.kmp.compose")
}

// The app's shared shell (navigation, bottom chrome, player overlay), used by Android now and desktop in Phase 5.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.model)
            implementation(projects.core.player)
            implementation(projects.feature.home)
            implementation(projects.feature.search)
            implementation(projects.feature.player)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
        }
    }
}
