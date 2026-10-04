import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Kotlin Multiplatform library targeting Android and JVM (desktop). */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("shura.quality")

            val jvmTarget = JvmTarget.fromTarget(libs.version("jvmTarget"))

            extensions.configure<KotlinMultiplatformExtension> {
                jvm {
                    compilerOptions.jvmTarget.set(jvmTarget)
                }
                (this as ExtensionAware).extensions
                    .getByType<KotlinMultiplatformAndroidLibraryTarget>()
                    .apply {
                        namespace = shuraNamespace
                        compileSdk = libs.version("android-compileSdk").toInt()
                        minSdk = libs.version("android-minSdk").toInt()
                        compilerOptions.jvmTarget.set(jvmTarget)
                    }

                sourceSets.commonTest.dependencies {
                    implementation(libs.findLibrary("kotlin-test").get())
                }
            }
        }
    }
}
