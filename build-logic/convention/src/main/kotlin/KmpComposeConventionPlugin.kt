import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Kotlin Multiplatform library with Compose Multiplatform UI and Compose resources (UI strings). */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("shura.kmp.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(libs.findLibrary("compose-runtime").get())
                    implementation(libs.findLibrary("compose-foundation").get())
                    implementation(libs.findLibrary("compose-ui").get())
                    implementation(libs.findLibrary("compose-material3").get())
                    implementation(libs.findLibrary("compose-components-resources").get())
                }
                // Compose resources are packaged as Android resources by the Android-KMP library plugin.
                (this as ExtensionAware)
                    .extensions
                    .getByType<KotlinMultiplatformAndroidLibraryTarget>()
                    .androidResources
                    .enable = true
            }

            // One Res class per module, in the module's own package (e.g. ...feature.home.resources.Res).
            (extensions.getByType<ComposeExtension>() as ExtensionAware).extensions.configure<ResourcesExtension> {
                packageOfResClass = "$shuraNamespace.resources"
            }
        }
    }
}
