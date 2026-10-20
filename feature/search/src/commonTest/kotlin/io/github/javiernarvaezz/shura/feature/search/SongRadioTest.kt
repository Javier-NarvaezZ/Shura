package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.SongRadio
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The radio of a song tapped on its own (lives in `:core:player`; tested here, beside the fake player). */
class SongRadioTest {
    private val player = FakeAudioPlayer()
    private val song = testSong(1, "Tapped")
    private val asked = mutableListOf<VideoId>()
    private var answer = CompletableDeferred<List<Song>>()

    private fun TestScope.radio(trackOf: (VideoId) -> VideoId? = { null }) =
        SongRadio(
            player,
            related = {
                asked += it
                answer.await()
            },
            scope = backgroundScope,
            trackOf = trackOf,
        )

    /** What the player holds once it plays [song] alone. */
    private fun playing(song: Song) {
        player.queue.value = QueueState(items = listOf(song), currentIndex = 0)
    }

    @Test
    fun theSongPlaysAloneThenItsRadioIsAdded() =
        runTest {
            radio().start(song)
            playing(song)
            answer.complete(listOf(song, testSong(2, "R2"), testSong(3, "R3"), testSong(2, "R2")))
            runCurrent()

            assertEquals(listOf("shuffle:false", "play:Tapped", "enqueue:R2,R3"), player.calls)
            assertEquals(listOf(song.videoId), asked)
        }

    @Test
    fun withoutANetworkTheSongPlaysAlone() =
        runTest {
            radio().start(song)
            playing(song)
            answer.completeExceptionally(IllegalStateException("offline"))
            runCurrent()

            assertEquals(listOf("shuffle:false", "play:Tapped"), player.calls)
        }

    @Test
    fun anotherSongTappedMeanwhileDropsTheFirstRadio() =
        runTest {
            val radio = radio()
            radio.start(song)
            val other = testSong(9, "Other")
            radio.start(other)
            playing(other)
            answer.complete(listOf(testSong(2, "R2")))
            runCurrent()

            // One answer for both asks: only the second radio (the other song's) is added.
            assertEquals(
                listOf("shuffle:false", "play:Tapped", "shuffle:false", "play:Other", "enqueue:R2"),
                player.calls,
            )
        }

    @Test
    fun aQueueChangedMeanwhileGetsNoRadio() =
        runTest {
            radio().start(song)
            // An album started before the radio arrived.
            player.queue.value = QueueState(items = listOf(testSong(5, "Album 1"), song), currentIndex = 0)
            answer.complete(listOf(testSong(2, "R2")))
            runCurrent()

            assertEquals(listOf("shuffle:false", "play:Tapped"), player.calls)
        }

    @Test
    fun aVideoWithAKnownTrackStartsTheRadioFromTheTrack() =
        runTest {
            val video = song.copy(isVideo = true)
            val track = VideoId("Fb3j_yuFh1s")
            radio(trackOf = { if (it == video.videoId) track else null }).start(video)
            runCurrent()

            assertEquals(listOf(track), asked)
        }

    @Test
    fun aVideoWithoutATrackStartsTheRadioFromItself() =
        runTest {
            val video = song.copy(isVideo = true)
            radio().start(video)
            runCurrent()

            assertEquals(listOf(video.videoId), asked)
        }
}
