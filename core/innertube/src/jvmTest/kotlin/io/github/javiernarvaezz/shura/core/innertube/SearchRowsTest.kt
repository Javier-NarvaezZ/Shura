package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.Artist
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchRowsTest {
    private fun json(fixture: String) = Json.parseToJsonElement(Fixtures.read(fixture))

    @Test
    fun readsAlbumResults() {
        val albums = SearchRows.albums(json("search/search_albums_juanes.json"))

        assertEquals(19, albums.size)
        val first = albums.first()
        assertEquals("MPREb_0Jkb6HEHtew", first.id)
        assertEquals("Me Enamora", first.title)
        assertEquals("Sencillo", first.typeLabel)
        assertEquals(listOf(Artist("Juanes", "UCeWBcJf4eNsQmGQO-N98eTg")), first.artists)
        assertEquals(2007, first.year)
        assertTrue(first.thumbnailUrl.orEmpty().startsWith("https://"))
    }

    @Test
    fun readsArtistResultsWithoutTheKindWord() {
        val artists = SearchRows.artists(json("search/search_artists_juanes.json"))

        assertEquals(8, artists.size)
        assertEquals("UCeWBcJf4eNsQmGQO-N98eTg", artists.first().id)
        assertEquals("Juanes", artists.first().name)
        assertEquals("Público mensual: 17.9 M usuarios", artists.first().subtitle)
    }

    @Test
    fun readsPlaylistResultsWithTheirAuthor() {
        val playlists = SearchRows.playlists(json("search/search_playlists_juanes.json"))

        assertEquals(16, playlists.size)
        val first = playlists.first()
        assertEquals("VLRDCLAK5uy_m7gMEUfTIFcNbv_BlDoKtfCCW7yANKcps", first.id)
        assertEquals("Despecho", first.title)
        assertEquals("YouTube Music", first.author)
        assertEquals("50 canciones", first.subtitle)
    }

    @Test
    fun eachFilterKeepsOnlyItsKind() {
        val unfiltered = json("search/search_unfiltered_juanes.json")
        val songs = json("search/search_songs_bad_bunny.json")

        assertTrue(SearchRows.albums(songs).isEmpty())
        assertTrue(SearchRows.artists(json("search/search_albums_juanes.json")).isEmpty())
        // An unfiltered search mixes kinds; each reader takes its own.
        assertTrue(SearchRows.albums(unfiltered).isNotEmpty())
        assertTrue(SearchRows.artists(unfiltered).all { it.id.startsWith("UC") })
    }
}
