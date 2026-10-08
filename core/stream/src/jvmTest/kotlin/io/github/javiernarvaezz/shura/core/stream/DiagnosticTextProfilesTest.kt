package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.models.YouTubeClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reads the real client profiles from InnerTubeX, so a library update cannot add one that gets masked. */
class DiagnosticTextProfilesTest {
    private val clients: Map<String, YouTubeClient> =
        YouTubeClient.Companion::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == YouTubeClient::class.java }
            .filter { it.name.startsWith("get") }
            .associate { it.name.removePrefix("get") to it.invoke(YouTubeClient.Companion) as YouTubeClient }

    @Test
    fun theLibraryExposesItsProfiles() {
        assertTrue("WEB_REMIX" in clients && "VISIONOS" in clients, "profiles found: ${clients.keys}")
    }

    @Test
    fun noRealProfileOrClientNameIsMasked() {
        val names = clients.keys + clients.values.map(YouTubeClient::clientName)
        for (name in names) {
            for (label in listOf(name, "${name}__nopo")) {
                assertEquals(label, DiagnosticText.sanitize(label), label)
                assertEquals("$label request:x", DiagnosticText.sanitize("$label request:x"), label)
            }
        }
    }
}
