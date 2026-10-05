plugins {
    id("shura.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.stream)
        }
        androidMain.dependencies {
            implementation(projects.core.network)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.media3.session)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
