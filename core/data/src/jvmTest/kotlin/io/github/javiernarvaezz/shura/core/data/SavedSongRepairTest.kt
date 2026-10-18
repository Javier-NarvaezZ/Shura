package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.QueueSnapshot
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SavedSongRepairTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) }
    private val database = ShuraDatabase(driver)
    private var clock = 1L

    private val video = VideoId("NLZRYQMLDW4")
    private val track = VideoId("Fb3j_yuFh1s")
    private val album = AlbumRef("DAMN. COLLECTORS EDITION.", "MPREb_FQT8RhNisCl")

    /** "DNA." as it was saved before album rows were mapped to their tracks: the video, from the album. */
    private val savedVideo = Song(video, "DNA.", listOf(Artist("Kendrick Lamar", "UC1")), album, 186.seconds)
    private val other = Song(VideoId("kJQP7kiw5Fk"), "Other", listOf(Artist("Y")))

    private var answer: suspend (String) -> Map<VideoId, VideoId>? = { mapOf(video to track) }
    private val asked = mutableListOf<String>()

    private fun TestScope.repair() =
        SavedSongRepair(database, StandardTestDispatcher(testScheduler)) { id ->
            asked += id
            answer(id)
        }

    private fun TestScope.queueStore() = SqlQueueStore(database, StandardTestDispatcher(testScheduler))

    private fun TestScope.library(): SqlLibrary {
        val library = SqlLibrary(database, backgroundScope, StandardTestDispatcher(testScheduler), now = { clock++ })
        runCurrent()
        return library
    }

    private fun TestScope.offsets() = SqlLyricsOffsets(database, StandardTestDispatcher(testScheduler))

    @Test
    fun savedSongsFromAnAlbumMoveToItsTrack() =
        runTest {
            queueStore().recordPlay(savedVideo, atMillis = 1)
            library().setLiked(savedVideo, true)
            offsets().set(video.value, 2.seconds)
            database.lyricsQueries.put(video.value, "none", null, 1)
            runCurrent()

            assertTrue(repair().repairOnce())

            val repaired = savedVideo.copy(videoId = track)
            assertEquals(listOf(repaired), queueStore().recent(limit = 10).first())
            assertEquals(listOf(repaired), library().likedSongs.value)
            assertEquals(2.seconds, offsets().get(track.value))
            assertEquals(0L, database.lyricsQueries.count().executeAsOne())
            assertEquals(listOf(album.id), asked)
        }

    @Test
    fun aRestoredQueueGetsTheTrackEvenIfSavedWithTheVideoAgain() =
        runTest {
            queueStore().recordPlay(savedVideo, atMillis = 1)
            assertTrue(repair().repairOnce())
            // The player may save its queue with the video id after the repair (it was already loaded).
            val snapshot =
                QueueSnapshot(
                    items = listOf(savedVideo, other),
                    currentIndex = 0,
                    positionMs = 0,
                    shuffle = false,
                    repeat = RepeatMode.Off,
                    playOrder = listOf(0, 1),
                )
            queueStore().save(snapshot)

            val restored = queueStore().load()!!.items
            assertEquals(listOf(savedVideo.copy(videoId = track), other), restored)
        }

    @Test
    fun anExistingLikeOfTheTrackWins() =
        runTest {
            val library = library()
            library.setLiked(savedVideo, true)
            library.setLiked(savedVideo.copy(videoId = track, title = "DNA. (track)"), true)
            runCurrent()

            assertTrue(repair().repairOnce())

            assertEquals(listOf("DNA. (track)"), library().likedSongs.value.map { it.title })
        }

    @Test
    fun itRunsOnce() =
        runTest {
            queueStore().recordPlay(savedVideo, atMillis = 1)
            assertTrue(repair().repairOnce())
            assertTrue(repair().repairOnce())
            assertEquals(1, asked.size)
        }

    @Test
    fun anAlbumThatCannotBeReachedNowIsTriedAgainNextTime() =
        runTest {
            queueStore().recordPlay(savedVideo, atMillis = 1)
            answer = { null }
            assertFalse(repair().repairOnce())

            answer = { mapOf(video to track) }
            assertTrue(repair().repairOnce())
            assertEquals(
                track,
                queueStore()
                    .recent(limit = 10)
                    .first()
                    .single()
                    .videoId,
            )
        }

    @Test
    fun theKnownTracksAreShared() =
        runTest {
            queueStore().recordPlay(savedVideo, atMillis = 1)
            val repair = repair()
            repair.repairOnce()

            assertEquals(mapOf(video to track), repair.tracks.first())
        }
}
