package io.github.javiernarvaezz.shura.feature.player

import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.player.PlaybackProgress
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PlayerModelTest {
    private val player = FakeAudioPlayer()
    private val a = testSong(1, "A")
    private val b = testSong(2, "B")

    private fun TestScope.model() = PlayerModel(player, backgroundScope)

    private fun TestScope.playing(state: PlaybackState) {
        player.queue.value = QueueState(items = listOf(a, b), currentIndex = 0, playOrder = listOf(0, 1))
        player.state.value = state
        runCurrent()
    }

    @Test
    fun nothingQueuedShowsNoPlayer() =
        runTest {
            val model = model()
            runCurrent()

            assertNull(model.state.value.song)
            assertFalse(model.state.value.isVisible)
        }

    @Test
    fun theStateFollowsTheCurrentItemAndPlayback() =
        runTest {
            val model = model()
            playing(PlaybackState.Playing(a))

            assertEquals(a, model.state.value.song)
            assertTrue(model.state.value.isVisible)
            assertTrue(model.state.value.showsPause)
            assertNull(model.state.value.error)
        }

    @Test
    fun loadingAlsoShowsPauseBecauseItWillPlay() =
        runTest {
            val model = model()
            playing(PlaybackState.Loading(a))

            assertTrue(model.state.value.showsPause)
            assertTrue(model.state.value.isLoading)
        }

    @Test
    fun aFailureIsExposedForTheRetryAction() =
        runTest {
            val model = model()
            playing(PlaybackState.Failed(a, PlaybackError.Network))

            assertEquals(PlaybackError.Network, model.state.value.error)
            model.retry()

            assertEquals(listOf("retry"), player.calls)
        }

    @Test
    fun togglePlayPausesResumesAndRestartsAFinishedSong() =
        runTest {
            val model = model()
            playing(PlaybackState.Playing(a))
            model.togglePlay()
            playing(PlaybackState.Paused(a))
            model.togglePlay()
            playing(PlaybackState.Ended(a))
            model.togglePlay()

            assertEquals(listOf("pause", "resume", "seekTo:0", "resume"), player.calls)
        }

    @Test
    fun seekingTakesAFractionOfTheKnownDuration() =
        runTest {
            val model = model()
            player.progress = PlaybackProgress(10.seconds, 200.seconds, 30.seconds)

            model.seekToFraction(0.25f)
            player.progress = PlaybackProgress(10.seconds, null, Duration.ZERO)
            model.seekToFraction(0.5f) // unknown duration: ignored

            assertEquals(listOf("seekTo:50"), player.calls)
        }

    @Test
    fun theSongDurationStandsInUntilThePlayerKnowsIt() =
        runTest {
            val model = model()
            val long = a.copy(duration = 240.seconds)
            player.queue.value = QueueState(items = listOf(long), currentIndex = 0)
            player.state.value = PlaybackState.Paused(long)
            runCurrent()
            player.progress = PlaybackProgress(60.seconds, null, Duration.ZERO)

            assertEquals(240.seconds, model.progress().duration)
            assertEquals(0.25f, model.progress().fraction)
            model.seekToFraction(0.5f)

            assertEquals(listOf("seekTo:120"), player.calls)
        }

    @Test
    fun shuffleTogglesAndRepeatCyclesOffAllOne() =
        runTest {
            val model = model()
            playing(PlaybackState.Playing(a))
            model.toggleShuffle()
            player.queue.value = player.queue.value.copy(shuffle = true)
            runCurrent()
            model.toggleShuffle()
            model.cycleRepeat()
            player.queue.value = player.queue.value.copy(repeat = RepeatMode.All)
            runCurrent()
            model.cycleRepeat()
            player.queue.value = player.queue.value.copy(repeat = RepeatMode.One)
            runCurrent()
            model.cycleRepeat()

            assertEquals(
                listOf("shuffle:true", "shuffle:false", "repeat:All", "repeat:One", "repeat:Off"),
                player.calls,
            )
        }

    @Test
    fun skipsGoToThePlayer() =
        runTest {
            val model = model()
            model.next()
            model.previous()

            assertEquals(listOf("next", "previous"), player.calls)
        }

    private val c = testSong(3, "C")

    @Test
    fun queueRowsFollowThePlayOrderAndMarkTheCurrentSong() =
        runTest {
            val model = model()
            player.queue.value = QueueState(items = listOf(a, b, c), currentIndex = 2, playOrder = listOf(2, 0, 1))
            runCurrent()

            val rows = model.queue.queueRows.value
            assertEquals(listOf(c, a, b), rows.map { it.song })
            assertEquals(listOf(2, 0, 1), rows.map { it.index })
            assertEquals(listOf(true, false, false), rows.map { it.isCurrent })
        }

    @Test
    fun tappingARowPlaysThatSong() =
        runTest {
            val model = model()
            model.queue.playAt(2)

            assertEquals(listOf("skipTo:2"), player.calls)
        }

    @Test
    fun aRemovedSongCanBePutBackWhereItWas() =
        runTest {
            val model = model()
            player.queue.value = QueueState(items = listOf(a, b, c), currentIndex = 0, playOrder = listOf(0, 1, 2))
            runCurrent()

            val removed = model.queue.remove(1)
            model.queue.undoRemove(assertNotNull(removed))

            assertEquals(listOf("remove:1", "enqueue:B", "move:2>1"), player.calls)
        }

    @Test
    fun songsMoveUpAndDownOnlyWithoutShuffle() =
        runTest {
            val model = model()
            player.queue.value = QueueState(items = listOf(a, b, c), currentIndex = 0, playOrder = listOf(0, 1, 2))
            runCurrent()
            assertTrue(model.state.value.canReorder)
            model.queue.moveUp(1)
            model.queue.moveDown(1)
            model.queue.moveUp(0) // already first: nothing
            model.queue.moveDown(2) // already last: nothing

            player.queue.value = player.queue.value.copy(shuffle = true)
            runCurrent()
            assertFalse(model.state.value.canReorder)
            model.queue.moveUp(1)

            assertEquals(listOf("move:1>0", "move:1>2"), player.calls)
        }
}
