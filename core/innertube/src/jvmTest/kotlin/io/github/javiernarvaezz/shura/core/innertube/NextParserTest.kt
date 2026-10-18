package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NextParserTest {
    private val seed = VideoId("7fwUH0oRmkQ")
    private val songs = NextParser.parse(Json.parseToJsonElement(Fixtures.read("next/next_la_camisa_negra.json")), seed)

    @Test
    fun leavesOutTheSeedAndKeepsTheRestInOrder() {
        assertEquals(49, songs.size)
        assertTrue(songs.none { it.videoId == seed })
        assertEquals("Me Enamora", songs.first().title)
        assertEquals("La Flaca (con Juanes)", songs.last().title)
    }

    @Test
    fun readsEverySongField() {
        val song = songs.first()

        assertEquals(VideoId("XHbQvTQPHNw"), song.videoId)
        assertEquals(listOf(Artist("Juanes", "UCeWBcJf4eNsQmGQO-N98eTg")), song.artists)
        assertEquals(AlbumRef("La Vida Es Un Ratico", "MPREb_zdmxFpmClH1"), song.album)
        assertEquals(3.minutes + 13.seconds, song.duration)
        assertTrue(song.thumbnailUrl.orEmpty().startsWith("https://"))
    }

    @Test
    fun aResponseWithoutAListGivesNothing() {
        assertEquals(emptyList(), NextParser.parse(JsonObject(emptyMap()), seed))
    }

    @Test
    fun songsAreNotVideos() {
        assertTrue(songs.none { it.isVideo })
    }

    @Test
    fun aRadioSeededByAVideoIsAllVideos() {
        val seed = VideoId("NLZRYQMLDW4")
        val videos = NextParser.parse(Json.parseToJsonElement(Fixtures.read("next/next_video_dna.json")), seed)

        assertTrue(videos.isNotEmpty())
        assertTrue(videos.all { it.isVideo })
    }
}
