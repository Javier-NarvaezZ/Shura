package io.github.javiernarvaezz.shura.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SongTest {
    @Test
    fun videoIdAcceptsElevenUrlSafeCharacters() {
        assertEquals("juRFjpB5Ppg", VideoId("juRFjpB5Ppg").value)
        assertEquals("nOj6d-HOw2w", VideoId("nOj6d-HOw2w").value)
        assertEquals("a_b-C1d2E3f", VideoId("a_b-C1d2E3f").value)
    }

    @Test
    fun videoIdRejectsBlankWrongLengthOrInvalidCharacters() {
        listOf("", "   ", "short", "toolongvideoid", "has space1", "bad/chars!!").forEach { raw ->
            assertFailsWith<IllegalArgumentException>(raw) { VideoId(raw) }
        }
    }

    @Test
    fun songRequiresNonBlankTitle() {
        assertFailsWith<IllegalArgumentException> {
            Song(videoId = VideoId("7fwUH0oRmkQ"), title = " ", artists = listOf(Artist("Juanes")))
        }
    }

    @Test
    fun artistRequiresNonBlankName() {
        assertFailsWith<IllegalArgumentException> { Artist(name = "") }
    }

    @Test
    fun artistNamesJoinsInOrder() {
        val song =
            Song(
                videoId = VideoId("nOj6d-HOw2w"),
                title = "Hips Don't Lie",
                artists = listOf(Artist("Shakira"), Artist("Wyclef Jean")),
            )

        assertEquals("Shakira, Wyclef Jean", song.artistNames)
    }

    @Test
    fun optionalFieldsDefaultToUnknown() {
        val song = Song(videoId = VideoId("7fwUH0oRmkQ"), title = "La Camisa Negra", artists = listOf(Artist("Juanes")))

        assertNull(song.album)
        assertNull(song.duration)
        assertNull(song.thumbnailUrl)
        assertFalse(song.isExplicit)
    }

    @Test
    fun songKeepsAllProvidedFields() {
        val song =
            Song(
                videoId = VideoId("juRFjpB5Ppg"),
                title = "Tití Me Preguntó",
                artists = listOf(Artist("Bad Bunny", id = "test-artist-id")),
                album = AlbumRef("Un Verano Sin Ti", id = "test-album-id"),
                duration = 244.seconds,
                thumbnailUrl = "https://lh3.googleusercontent.com/example",
                isExplicit = true,
            )

        assertEquals("Un Verano Sin Ti", song.album?.title)
        assertEquals(244.seconds, song.duration)
        assertEquals("test-artist-id", song.artists.single().id)
    }

    @Test
    fun albumRefRequiresNonBlankTitle() {
        assertFailsWith<IllegalArgumentException> { AlbumRef(title = "") }
    }
}
