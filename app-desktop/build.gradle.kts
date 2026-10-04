import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    id("shura.quality")
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.feature.home)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
}

compose.desktop {
    application {
        mainClass = "io.github.javiernarvaezz.shura.desktop.MainKt"
    }
}
