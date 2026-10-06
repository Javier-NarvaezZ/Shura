plugins {
    id("shura.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.coil.compose)
        }
    }
}
