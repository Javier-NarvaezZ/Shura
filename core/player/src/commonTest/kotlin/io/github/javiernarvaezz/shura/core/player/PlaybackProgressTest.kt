package io.github.javiernarvaezz.shura.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlaybackProgressTest {
    @Test
    fun aKnownDurationGivesTheFraction() {
        val progress = playbackProgress(positionMs = 30_000, durationMs = 120_000, bufferedMs = 60_000)

        assertEquals(30.seconds, progress.position)
        assertEquals(120.seconds, progress.duration)
        assertEquals(60.seconds, progress.buffered)
        assertEquals(0.25f, progress.fraction)
        assertEquals(0.5f, progress.bufferedFraction)
    }

    @Test
    fun anUnknownDurationIsNullAndTheFractionsAreZero() {
        // Media3 reports an unknown duration as a negative sentinel.
        val progress = playbackProgress(positionMs = 5_000, durationMs = Long.MIN_VALUE + 1, bufferedMs = 9_000)

        assertNull(progress.duration)
        assertEquals(0f, progress.fraction)
        assertEquals(0f, progress.bufferedFraction)
    }

    @Test
    fun positionsAreClampedToTheTrack() {
        val progress = playbackProgress(positionMs = -40, durationMs = 1_000, bufferedMs = 5_000)

        assertEquals(0.milliseconds, progress.position)
        assertEquals(1_000.milliseconds, progress.buffered)
        assertEquals(1f, progress.bufferedFraction)
    }

    @Test
    fun seekTargetsStayInsideTheTrack() {
        assertEquals(0L, seekTarget(targetMs = -3_000, durationMs = 200_000))
        assertEquals(200_000L, seekTarget(targetMs = 250_000, durationMs = 200_000))
        assertEquals(42_000L, seekTarget(targetMs = 42_000, durationMs = 200_000))
    }

    @Test
    fun seekTargetsWithAnUnknownDurationOnlyStopAtZero() {
        assertEquals(0L, seekTarget(targetMs = -1, durationMs = -1))
        assertEquals(900_000L, seekTarget(targetMs = 900_000, durationMs = -1))
    }
}
