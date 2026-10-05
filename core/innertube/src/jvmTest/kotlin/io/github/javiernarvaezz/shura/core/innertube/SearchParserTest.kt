package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SearchParserTest {
    private fun parse(fixture: String) = SearchParser.parseSongs(Json.parseToJsonElement(Fixtures.read(fixture)))

    @Test
    fun parsesEverySongInSongsFilterResults() {
        val songs = parse("search/search_songs_bad_bunny.json")

        assertEquals(20, songs.size)
        assertEquals(13, songs.count { it.isExplicit })
    }

    @Test
    fun parsesAllFieldsOfASong() {
        val song = parse("search/search_songs_bad_bunny.json").first()

        assertEquals(VideoId("ddDYuehbO74"), song.videoId)
        assertEquals("Amorfoda", song.title)
        assertEquals(listOf(Artist("Bad Bunny", id = "UCiY3z8HAGD6BlSNKVn2kSvQ")), song.artists)
        assertEquals(AlbumRef("Amorfoda", id = "MPREb_I8ptawcF9wt"), song.album)
        assertEquals(2.minutes + 34.seconds, song.duration)
        assertTrue(song.isExplicit)
        assertTrue(song.thumbnailUrl.orEmpty().startsWith("https://"))
    }

    @Test
    fun keepsEveryLinkedArtistInOrder() {
        val song = parse("search/search_songs_bad_bunny.json").single { it.videoId == VideoId("yIQ2VtGPt_E") }

        assertEquals(listOf("Bad Bunny", "Omar Courtz", "Dei V"), song.artists.map { it.name })
        assertEquals("DeBÍ TiRAR MáS FOToS", song.album?.title)
        assertEquals(3.minutes + 56.seconds, song.duration)
    }

    @Test
    fun nonExplicitSongIsNotFlagged() {
        val song = parse("search/search_songs_bad_bunny.json").last()

        assertEquals("No Te Hagas", song.title)
        assertEquals("Jory Boy", song.artistNames)
        assertFalse(song.isExplicit)
    }

    @Test
    fun picksLargestThumbnail() {
        val song = parse("search/search_songs_bad_bunny.json").first()

        assertTrue(song.thumbnailUrl.orEmpty().contains("w120-h120"), "expected the 120px thumbnail")
    }

    @Test
    fun unfilteredResultsKeepOnlyAlbumAudioTracks() {
        val songs = parse("search/search_unfiltered_juanes.json")

        assertEquals(
            listOf("7fwUH0oRmkQ", "t_M5BS6-H6s", "H5xd8ubqC00", "5aq0NdXYrMY", "clruhkkPQCY"),
            songs.map { it.videoId.value },
        )
        val first = songs.first()
        assertEquals("La Camisa Negra", first.title)
        assertEquals(listOf(Artist("Juanes", id = "UCeWBcJf4eNsQmGQO-N98eTg")), first.artists)
        assertNull(first.album)
        assertNull(first.duration)
    }

    @Test
    fun noResultsYieldsEmptyList() {
        assertTrue(parse("search/search_songs_no_results.json").isEmpty())
    }

    @Test
    fun unexpectedShapesYieldEmptyList() {
        assertTrue(SearchParser.parseSongs(Json.parseToJsonElement("{}")).isEmpty())
        assertTrue(SearchParser.parseSongs(Json.parseToJsonElement("[]")).isEmpty())
        assertTrue(SearchParser.parseSongs(Json.parseToJsonElement("\"text\"")).isEmpty())
    }

    @Test
    fun skipsItemsWithInvalidVideoIdOrTitle() {
        val json =
            """
            {"contents":{"musicShelfRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"bad id"},
                "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Broken"}]}}}],
                "overlay":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}},
              {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"7fwUH0oRmkQ"},
                "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":" "}]}}}],
                "overlay":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}
            ]}}}
            """.trimIndent()

        assertTrue(SearchParser.parseSongs(Json.parseToJsonElement(json)).isEmpty())
    }

    @Test
    fun parsesHourLongDurations() {
        assertEquals(1.minutes * 62 + 3.seconds, SearchParser.parseDuration("1:02:03"))
        assertEquals(154.seconds, SearchParser.parseDuration("2:34"))
        assertNull(SearchParser.parseDuration("Amorfoda"))
        assertNull(SearchParser.parseDuration("2:3"))
    }
}
