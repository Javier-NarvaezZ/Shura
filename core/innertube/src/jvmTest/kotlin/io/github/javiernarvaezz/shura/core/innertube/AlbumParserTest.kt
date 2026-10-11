package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.AlbumShelf
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AlbumParserTest {
    private val id = "MPREb_xDw1x6Hl5Jo"
    private val page = AlbumParser.parse(Json.parseToJsonElement(Fixtures.read("album/album_juanes.json")), id)
    private val juanes = Artist("Juanes", "UCeWBcJf4eNsQmGQO-N98eTg")

    @Test
    fun readsTheHeader() {
        val album = page.album

        assertEquals(id, album.id)
        assertEquals("Mi Sangre", album.title)
        assertEquals(listOf(juanes), album.artists)
        assertEquals("Álbum", album.typeLabel)
        assertEquals(2004, album.year)
        assertTrue(album.thumbnailUrl.orEmpty().startsWith("https://yt3.googleusercontent.com/"))
        assertTrue(page.description.orEmpty().startsWith("Mi sangre Tour Edition"))
    }

    @Test
    fun tracksTakeArtistAlbumAndCoverFromTheHeader() {
        assertEquals(12, page.tracks.size)
        val first = page.tracks.first()
        assertEquals(VideoId("Bpd-yqMBp_A"), first.videoId)
        assertEquals("Ámame", first.title)
        assertEquals(listOf(juanes), first.artists)
        assertEquals(AlbumRef("Mi Sangre", id), first.album)
        assertEquals(4.minutes + 20.seconds, first.duration)
        assertEquals(page.album.thumbnailUrl, first.thumbnailUrl)
        assertEquals("Tu Guardián", page.tracks.last().title)
        assertEquals(2727.seconds, page.knownDuration)
    }

    @Test
    fun keepsTheShelvesInOrderWithTheirTitles() {
        val shelves = page.shelves.filterIsInstance<AlbumShelf>()

        assertEquals(listOf("Otras versiones", "Lanzamientos para ti"), shelves.map { it.title })
        assertEquals(listOf(2, 10), shelves.map { it.albums.size })
        val other = shelves.first().albums.first()
        assertEquals("MPREb_XH2wPioHYnZ", other.id)
        assertEquals("Mi Sangre", other.title)
        assertEquals(listOf(juanes), other.artists)
        assertEquals("Álbum", other.typeLabel)
    }

    @Test
    fun aRepeatedTrackAppearsOnce() {
        val doubled = Json.parseToJsonElement(Fixtures.read("album/album_juanes.json")).repeatFirstShelfRow()

        val tracks = AlbumParser.parse(doubled, id).tracks
        assertEquals(12, tracks.size)
        assertEquals(tracks.map { it.videoId }.distinct(), tracks.map { it.videoId })
    }

    @Test
    fun aResponseWithoutAHeaderIsAParseFailure() {
        assertFailsWith<InnerTubeException.Parse> { AlbumParser.parse(JsonObject(emptyMap()), id) }
    }

    /** The same response with the first row of its track shelf added again at the end. */
    private fun JsonElement.repeatFirstShelfRow(): JsonElement =
        when (this) {
            is JsonObject -> {
                JsonObject(
                    mapValues { (key, value) ->
                        val shelf = value as? JsonObject
                        val rows = shelf?.get("contents") as? JsonArray
                        if (key == "musicShelfRenderer" && rows != null) {
                            JsonObject(shelf + ("contents" to JsonArray(rows + rows.first())))
                        } else {
                            value.repeatFirstShelfRow()
                        }
                    },
                )
            }

            is JsonArray -> {
                JsonArray(map { it.repeatFirstShelfRow() })
            }

            else -> {
                this
            }
        }
}
