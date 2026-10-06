plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "shura.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "shura.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("quality") {
            id = "shura.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
