plugins {
    id("shura.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.ui)
    }
}
