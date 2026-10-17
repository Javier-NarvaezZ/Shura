import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("shura.kmp.library")
}

// The YouTube Music session (Phase 3, E4): model, signing, cookies, sign-in hosts and the encrypted store.
kotlin {
    // SHA-1 and AES-GCM come from the JVM, shared by Android and desktop.
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
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
