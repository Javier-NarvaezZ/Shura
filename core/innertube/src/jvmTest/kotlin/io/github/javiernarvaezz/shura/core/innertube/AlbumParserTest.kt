package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.AlbumShelf
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
        assertEquals(VideoId("VAFGFL7PhRs"), first.videoId)
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
    fun tracksPlayAsTheAlbumsSongsInAlbumOrder() {
        // The rows link the artist's video uploads; their song-credits entry names the album's own track.
        val songs =
            listOf(
                "VAFGFL7PhRs",
                "GLfn_Y3wI20",
                "pQ0fhVkEiqQ",
                "7fwUH0oRmkQ",
                "CM2nA0zZQ54",
                "kgt0jyB5KXs",
                "_xuHhwq8V44",
                "SuD58uVvAqM",
                "69nXLxJe56I",
                "ZtWPh78WzJI",
                "NhFXgemJofg",
                "1aWKz9rH4uk",
            ).map(::VideoId)

        assertEquals(songs, page.tracks.map { it.videoId })
        assertTrue(page.tracks.none { it.isVideo })
    }

    @Test
    fun aTrackWithoutSongCreditsKeepsTheRowsVideo() {
        val withoutCredits = Json.parseToJsonElement(Fixtures.read("album/album_juanes.json")).withoutSongCredits()

        val tracks = AlbumParser.parse(withoutCredits, id).tracks
        assertEquals(VideoId("Bpd-yqMBp_A"), tracks.first().videoId)
        // Without its track, the row stays the artist's video.
        assertTrue(tracks.first().isVideo)
        assertEquals(12, tracks.size)
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

    /** The same response with every song-credits link (`MPTC…`) removed. */
    private fun JsonElement.withoutSongCredits(): JsonElement =
        when (this) {
            is JsonObject -> {
                JsonObject(
                    filterValues { (it as? JsonPrimitive)?.contentOrNull?.startsWith("MPTC") != true }
                        .mapValues { (_, value) -> value.withoutSongCredits() },
                )
            }

            is JsonArray -> {
                JsonArray(map { it.withoutSongCredits() })
            }

            else -> {
                this
            }
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

    @Test
    fun headerArtistsLeaveOutJoiningWords() {
        fun runs(json: String) = runsOf(Json.parseToJsonElement(json))
        val linked = """{"browseEndpoint":{"browseId":"UC"""

        assertEquals(
            listOf(Artist("Dani Martin", "UC1"), Artist("Juanes", "UC2")),
            headerArtists(
                runs(
                    """{"runs":[{"text":"Dani Martin","navigationEndpoint":${linked}1"}}},""" +
                        """{"text":" y "},{"text":"Juanes","navigationEndpoint":${linked}2"}}}]}""",
                ),
            ),
        )
        assertEquals(
            listOf(Artist("Varios artistas")),
            headerArtists(runs("""{"runs":[{"text":"Varios artistas"}]}""")),
        )
        assertEquals(emptyList(), headerArtists(runs("""{"runs":[]}""")))
    }
}
