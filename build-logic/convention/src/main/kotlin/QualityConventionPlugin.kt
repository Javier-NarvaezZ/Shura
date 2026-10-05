import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

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

            // Generated code (e.g. SQLDelight under build/generated) is not ours to format. Path.startsWith compares
            // path segments, so this works with Windows and Unix separators alike; everything under src/ is checked.
            // Applied to the tasks: the extension's filter does not see sources that plugins add to KMP source sets.
            val buildDirectory = layout.buildDirectory.get().asFile.toPath()
            tasks.withType(BaseKtLintCheckTask::class.java).configureEach {
                exclude { it.file.toPath().startsWith(buildDirectory) }
            }
        }
    }
}
