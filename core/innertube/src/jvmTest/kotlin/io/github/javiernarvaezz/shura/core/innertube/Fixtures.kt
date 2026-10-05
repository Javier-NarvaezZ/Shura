package io.github.javiernarvaezz.shura.core.innertube

import java.io.File

/** Recorded, sanitized InnerTube responses under `src/jvmTest/resources/fixtures`. */
internal object Fixtures {
    private const val ROOT = "/fixtures"

    fun read(path: String): String {
        val url = requireNotNull(Fixtures::class.java.getResource("$ROOT/$path")) { "Missing fixture $path" }
        return url.readText()
    }

    fun all(): List<File> {
        val root = File(requireNotNull(Fixtures::class.java.getResource(ROOT)) { "Missing fixtures root" }.toURI())
        return root.walkTopDown().filter { it.isFile }.toList()
    }
}
