import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Static analysis (detekt) and formatting checks (ktlint) shared by every module. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig.set(true)
                // The default sources (src/main/kotlin, ...) miss KMP source sets such as commonMain.
                source.setFrom(layout.projectDirectory.dir("src"))
                parallel.set(true)
                config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))
            }

            extensions.configure<KtlintExtension> {
                version.set(libs.version("ktlint"))
            }
        }
    }
}
