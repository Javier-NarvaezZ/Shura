import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Manual live check (ADR 0001 R7): never a dependency of any module, never part of an app artifact, never run by
// CI. Run by hand only: ./gradlew :tools:livecheck:run
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    id("shura.quality")
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
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.stream)
}

application {
    mainClass.set("io.github.javiernarvaezz.shura.tools.livecheck.MainKt")
}
