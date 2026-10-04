import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

/** Derives an Android namespace from the module path, e.g. `:core:model` -> `io.github.javiernarvaezz.shura.core.model`. */
internal val Project.shuraNamespace: String
    get() = "io.github.javiernarvaezz.shura" + path.replace(':', '.').replace("-", "")
