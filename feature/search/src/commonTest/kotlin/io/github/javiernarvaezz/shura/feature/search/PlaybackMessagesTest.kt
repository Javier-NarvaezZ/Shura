package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun everyErrorHasAPlainLanguageMessage() {
        allErrors.forEach { error ->
            val message = playbackErrorMessage(error)
            assertTrue(message.isNotBlank(), "$error")
            assertTrue(message.endsWith("."), "full sentence for $error: $message")
            technicalWords.forEach { word ->
                assertFalse(
                    message.lowercase().contains(word),
                    "technical word '$word' in message for $error: $message",
                )
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
