package io.github.javiernarvaezz.shura.feature.home

import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.FakePlayHistory
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeModelTest {
    private val player = FakeAudioPlayer()
    private val history = FakePlayHistory()
    private val a = testSong(1, "A")
    private val b = testSong(2, "B")
    private val c = testSong(3, "C")

    private fun TestScope.model(hour: Int = 10) = HomeModel(player, history, { hour }, backgroundScope)

    @Test
    fun thePartOfTheDayFollowsTheHour() {
        assertEquals(DayPart.Night, dayPartOf(4))
        assertEquals(DayPart.Morning, dayPartOf(5))
        assertEquals(DayPart.Morning, dayPartOf(11))
        assertEquals(DayPart.Afternoon, dayPartOf(12))
        assertEquals(DayPart.Afternoon, dayPartOf(18))
        assertEquals(DayPart.Night, dayPartOf(19))
    }

    @Test
    fun itIsLoadingUntilTheHistoryAnswers() =
        runTest {
            val home = model()
            runCurrent()
            assertTrue(home.state.value.loading)

            history.songs.value = listOf(a)
            runCurrent()

            assertFalse(home.state.value.loading)
            assertEquals(listOf(a), home.state.value.recent)
        }

    @Test
    fun continueListeningIsTheCurrentQueueItemAndIsLeftOutOfRecent() =
        runTest {
            val home = model()
            player.queue.value = QueueState(items = listOf(b, c), currentIndex = 0)
            player.state.value = PlaybackState.Paused(b)
            history.songs.value = listOf(b, a)
            runCurrent()

            assertEquals(b, home.state.value.current)
            assertFalse(home.state.value.isPlaying)
            assertEquals(listOf(a), home.state.value.recent)
        }

    @Test
    fun anEmptyQueueAndHistoryIsTheEmptyState() =
        runTest {
            val home = model()
            history.songs.value = emptyList()
            runCurrent()

            assertNull(home.state.value.current)
            assertTrue(home.state.value.isEmpty)
        }

    @Test
    fun aRecentSongPlaysWithTheRestOfTheRowQueued() =
        runTest {
            val home = model()
            history.songs.value = listOf(a, b, c)
            runCurrent()

            home.playRecent(1)

            assertEquals(listOf("playQueue:A,B,C@1"), player.calls)
        }

    @Test
    fun theContinueButtonResumesOrPauses() =
        runTest {
            val home = model()
            player.queue.value = QueueState(items = listOf(a), currentIndex = 0)
            player.state.value = PlaybackState.Paused(a)
            history.songs.value = emptyList()
            runCurrent()
            home.togglePlay()

            player.state.value = PlaybackState.Playing(a)
            runCurrent()
            home.togglePlay()

            assertEquals(listOf("resume", "pause"), player.calls)
        }
}
