import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("shura.kmp.library")
}

kotlin {
    // OkHttp and Ktor's OkHttp engine are JVM libraries: shared by Android and desktop only.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
        }
        getByName("jvmCommonMain").dependencies {
            api(libs.okhttp)
            api(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// SingleHttpClientGuardTest scans sources and build files of every module (ADR 0001 R7). Declare them as
// inputs so a change anywhere re-runs it instead of reporting a stale UP-TO-DATE result.
tasks.named<Test>("jvmTest") {
    inputs
        .files(
            fileTree(rootDir) {
                include("**/src/**/*.kt", "**/*.gradle.kts", "gradle/libs.versions.toml")
                exclude("**/build/**", ".gradle/**", "build-logic/**/build/**")
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("repositorySources")
}
