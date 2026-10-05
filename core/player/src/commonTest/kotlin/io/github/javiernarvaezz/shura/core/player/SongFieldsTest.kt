package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SongFieldsTest {
    @Test
    fun fullSongRoundTrips() {
        val song =
            Song(
                videoId = VideoId("7fwUH0oRmkQ"),
                title = "La Camisa Negra",
                artists = listOf(Artist("Juanes", "UC123"), Artist("Otro")),
                album = AlbumRef("Mi Sangre", "MPREb_1"),
                duration = 217.seconds,
                thumbnailUrl = "https://lh3.googleusercontent.com/x=w120-h120",
                isExplicit = true,
            )

        assertEquals(song, song.toFields().toSong())
    }

    @Test
    fun minimalSongRoundTrips() {
        val song = Song(VideoId("7fwUH0oRmkQ"), "La Camisa Negra", emptyList())

        assertEquals(song, song.toFields().toSong())
    }

    @Test
    fun invalidFieldsGiveNoSong() {
        val fields = Song(VideoId("7fwUH0oRmkQ"), "T", listOf(Artist("A"))).toFields()

        assertNull(fields.copy(videoId = "not-an-id").toSong())
        assertNull(fields.copy(title = " ").toSong())
        assertNull(fields.copy(artistNames = listOf(" ")).toSong())
        assertNull(fields.copy(artistIds = emptyList()).toSong())
    }
}
