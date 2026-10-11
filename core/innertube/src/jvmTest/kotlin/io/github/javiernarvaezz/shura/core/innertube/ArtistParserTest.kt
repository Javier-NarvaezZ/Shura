package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumShelf
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.ArtistShelf
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.PlaylistShelf
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArtistParserTest {
    private val id = "UCeWBcJf4eNsQmGQO-N98eTg"
    private val page = ArtistParser.parse(Json.parseToJsonElement(Fixtures.read("artist/artist_juanes.json")), id)

    @Test
    fun readsTheHeader() {
        assertEquals(id, page.artist.id)
        assertEquals("Juanes", page.artist.name)
        assertTrue(
            page.artist.thumbnailUrl
                .orEmpty()
                .startsWith("https://lh3.googleusercontent.com/"),
        )
        assertTrue(page.description.orEmpty().startsWith("Juan Esteban Aristizábal Vásquez"))
        // YouTube Music separates the number and its unit with a no-break space.
        assertEquals("3.29\u00a0M", page.subscribers)
        assertEquals("Público mensual: 17.9\u00a0M usuarios", page.monthlyListeners)
    }

    @Test
    fun readsTheTopSongsAndTheLinkToAll() {
        assertEquals(
            listOf("Es Por Ti", "Besos En Guerra", "La Camisa Negra", "A Dios Le Pido", "Antes que el tiempo se vaya"),
            page.topSongs.map { it.title },
        )
        val first = page.topSongs.first()
        assertEquals(VideoId("5aq0NdXYrMY"), first.videoId)
        assertEquals(listOf(Artist("Juanes", id)), first.artists)
        assertEquals("Un Día Normal", first.album?.title)
        assertEquals(BrowseLink("VLOLAK5uy_nhiZ58OtcuzRST9aW5qI1xAxW9XwtG0RE", "ggMCCAI%3D"), page.allSongs)
    }

    @Test
    fun keepsAlbumPlaylistAndArtistShelvesAndSkipsVideos() {
        assertEquals(
            listOf(
                "Álbumes",
                "Sencillos y EP",
                "Aparece en",
                "Juanes hizo estas playlists",
                "A los fans también podría gustarles",
            ),
            page.shelves.map { it.title },
        )
        val albums = page.shelves[0] as AlbumShelf
        assertEquals("MPREb_PyHyOQjb274", albums.albums.first().id)
        assertEquals("JuanesTeban", albums.albums.first().title)
        // An artist's own albums do not name the artist: the page's artist stands in.
        assertEquals(listOf(Artist("Juanes", id)), albums.albums.first().artists)
        assertEquals(
            "VLRDCLAK5uy_kP_oochBOnGUJCZtvOsCbRC3NkI8HLPek",
            (page.shelves[2] as PlaylistShelf).playlists.first().id,
        )
        val similar = page.shelves.last() as ArtistShelf
        assertEquals("Maná", similar.artists.first().name)
        assertEquals("UCtSeeqnGJM5ngqJWZ1vQygw", similar.artists.first().id)
    }

    @Test
    fun aResponseWithoutAHeaderIsAParseFailure() {
        assertFailsWith<InnerTubeException.Parse> { ArtistParser.parse(JsonObject(emptyMap()), id) }
    }
}
