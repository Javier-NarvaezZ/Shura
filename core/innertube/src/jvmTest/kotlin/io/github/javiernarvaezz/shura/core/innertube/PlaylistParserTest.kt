package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlaylistParserTest {
    private fun page(
        fixture: String,
        id: String,
    ) = PlaylistParser.parse(Json.parseToJsonElement(Fixtures.read(fixture)), id)

    @Test
    fun readsAPlaylistWithAFullHeader() {
        val page = page("playlist/playlist_short.json", "VLPLB426C801F16013D3")

        assertEquals("VLPLB426C801F16013D3", page.playlist.id)
        assertEquals("Juanes - La Camisa Negra", page.playlist.title)
        assertEquals("Izolda Gloveli", page.playlist.author)
        assertTrue(
            page.playlist.thumbnailUrl
                .orEmpty()
                .startsWith("https://"),
        )
        assertEquals("271 vistas • 52 pistas • Más de 3 horas", page.details)
        assertEquals("Juanes - La Camisa Negra", page.description)
    }

    @Test
    fun keepsEveryTrackInOrderIncludingRepeats() {
        val page = page("playlist/playlist_short.json", "VLPLB426C801F16013D3")

        assertEquals(25, page.tracks.size)
        assertEquals(
            24,
            page.tracks
                .map { it.videoId }
                .distinct()
                .size,
        )
        val first = page.tracks.first()
        assertEquals(VideoId("voxgN3Dhjuo"), first.videoId)
        assertEquals("Me enamora", first.title)
        assertEquals(listOf(Artist("Juanes", "UCeWBcJf4eNsQmGQO-N98eTg")), first.artists)
        assertEquals(3.minutes + 13.seconds, first.duration)
    }

    @Test
    fun aPlaylistWithoutMoreTracksHasNoContinuation() {
        // Its other continuation belongs to the suggestions below the tracks, not to them.
        assertNull(page("playlist/playlist_short.json", "VLPLB426C801F16013D3").continuation)
    }

    @Test
    fun aLongPlaylistEndsItsFirstPageWithAContinuation() {
        val page = page("playlist/playlist_artist_songs.json", "VLOLAK5uy_nhiZ58OtcuzRST9aW5qI1xAxW9XwtG0RE")

        assertEquals("Canciones más populares", page.playlist.title)
        assertEquals(100, page.tracks.size)
        assertEquals("Es Por Ti", page.tracks.first().title)
        assertEquals(
            "Un Día Normal",
            page.tracks
                .first()
                .album
                ?.title,
        )
        assertTrue(page.continuation.orEmpty().startsWith("4qmFsg"))
    }

    @Test
    fun theLastContinuationPageHasNoFurtherToken() {
        val more =
            PlaylistParser.parseContinuation(
                Json.parseToJsonElement(Fixtures.read("playlist/playlist_artist_songs_continuation.json")),
            )

        assertEquals(50, more.tracks.size)
        assertFalse(more.hasMore)
    }

    @Test
    fun aResponseWithoutTracksOrTitleIsAParseFailure() {
        assertFailsWith<InnerTubeException.Parse> { PlaylistParser.parse(JsonObject(emptyMap()), "VLPL1") }
    }

    @Test
    fun aPlaylistsVideosAreMarkedAsVideosAndItsSongsAreNot() {
        assertTrue(page("playlist/playlist_short.json", "VLPL1").tracks.all { it.isVideo })
        assertTrue(page("playlist/playlist_artist_songs.json", "VLOLAK1").tracks.none { it.isVideo })
    }
}
