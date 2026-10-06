package io.github.javiernarvaezz.shura.feature.player

import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaybackMessagesTest {
    private val allErrors: List<PlaybackError> =
        StreamFailure.entries.map { PlaybackError.Stream(it) } +
            listOf(
                PlaybackError.Http(403),
                PlaybackError.Http(500),
                PlaybackError.Network,
                PlaybackError.Decoding,
                PlaybackError.Unknown,
            )

    private val technicalWords =
        listOf("http", "403", "500", "stream", "token", "playable", "resolver", "exception", "error", "null") +
            StreamFailure.entries.map { it.name.lowercase() }

    // The Spanish texts as shipped (JVM tests run from the module directory).
    private val strings: Map<String, String> =
        Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(File("src/commonMain/composeResources/values/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun everyErrorHasAPlainLanguageMessage() {
        allErrors.forEach { error ->
            val message = assertNotNull(strings[playbackErrorMessage(error).key], "no text for $error")
            assertTrue(message.endsWith("."), "full sentence for $error: $message")
            technicalWords.forEach { word ->
                assertFalse(message.lowercase().contains(word), "technical word '$word' for $error: $message")
            }
        }
    }

    @Test
    fun trackSpecificProblemsAreToldApartFromConnectionProblems() {
        val unavailableNow = playbackErrorMessage(PlaybackError.Stream(StreamFailure.NoPlayableStream))
        val offline = playbackErrorMessage(PlaybackError.Network)

        assertEquals(unavailableNow, playbackErrorMessage(PlaybackError.Stream(StreamFailure.TokenUnavailable)))
        assertEquals(offline, playbackErrorMessage(PlaybackError.Stream(StreamFailure.Network)))
        assertTrue(unavailableNow != offline)
    }
}
