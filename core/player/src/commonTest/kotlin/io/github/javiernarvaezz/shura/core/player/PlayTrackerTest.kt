package io.github.javiernarvaezz.shura.core.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayTrackerTest {
    private var now = 0L
    private val tracker = PlayTracker { now }

    private fun play(ms: Long) {
        tracker.onPlayingChanged(true)
        now += ms
        tracker.onPlayingChanged(false)
    }

    @Test
    fun aPlayIsReadyToRecordAfterThirtySecondsOfListening() {
        play(20_000)
        assertFalse(tracker.checkListened())
        play(10_000)
        assertTrue(tracker.checkListened())
        // Only once per play.
        play(60_000)
        assertFalse(tracker.checkListened())
    }

    @Test
    fun timeWhilePausedDoesNotCount() {
        tracker.onPlayingChanged(true)
        now += 10_000
        tracker.onPlayingChanged(false)
        now += 60_000

        assertFalse(tracker.checkListened())
    }

    @Test
    fun listeningIsCountedWhileStillPlaying() {
        tracker.onPlayingChanged(true)
        now += 31_000

        assertTrue(tracker.checkListened())
    }

    @Test
    fun aShortSongPlayedToTheEndIsRecorded() {
        play(12_000)

        assertTrue(tracker.onItemChanged(ended = true))
    }

    @Test
    fun aSkipBeforeThirtySecondsIsNotRecordedAndTheNextItemStartsFresh() {
        play(10_000)
        assertFalse(tracker.onItemChanged(ended = false))

        play(25_000)
        assertFalse(tracker.checkListened())
        play(5_000)
        assertTrue(tracker.checkListened())
    }

    @Test
    fun aSkipAfterThirtySecondsIsRecordedIfTheTickDidNotRecordItYet() {
        play(35_000)

        assertTrue(tracker.onItemChanged(ended = false))
    }
}
