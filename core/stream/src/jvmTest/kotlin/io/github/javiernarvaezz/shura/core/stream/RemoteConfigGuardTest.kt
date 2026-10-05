package io.github.javiernarvaezz.shura.core.stream

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * ADR 0001 R3: remote cipher configs are permanently disabled. Production code in this module must never
 * construct or reference InnerTubeX's remote config store.
 */
class RemoteConfigGuardTest {
    private val forbidden = listOf("RemotePlayerConfigStore", "PlayerConfigRepository", "faraday", "zemer")

    @Test
    fun productionSourcesNeverWireRemoteSolverConfigs() {
        val sources =
            File("src")
                .listFiles { file -> file.isDirectory && file.name.endsWith("Main") }
                .orEmpty()
                .flatMap { it.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
        assertTrue(sources.isNotEmpty(), "No production sources found; is the working directory the module dir?")

        val violations =
            sources.flatMap { file ->
                val text = file.readText()
                forbidden.filter { text.contains(it, ignoreCase = true) }.map { "${file.path}: $it" }
            }
        if (violations.isNotEmpty()) fail("Remote solver config wiring found:\n${violations.joinToString("\n")}")
    }
}
