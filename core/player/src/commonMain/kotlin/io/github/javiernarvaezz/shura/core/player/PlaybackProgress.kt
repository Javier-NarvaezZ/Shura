package io.github.javiernarvaezz.shura.core.player

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Where the current item is. [duration] is null until the player knows it. */
data class PlaybackProgress(
    val position: Duration,
    val duration: Duration?,
    val buffered: Duration,
) {
    /** Played part of the track, 0..1; 0 while the duration is unknown. */
    val fraction: Float get() = fractionOf(position)

    /** Buffered part of the track, 0..1; 0 while the duration is unknown. */
    val bufferedFraction: Float get() = fractionOf(buffered)

    private fun fractionOf(value: Duration): Float {
        val total = duration?.takeIf { it.isPositive() } ?: return 0f
        return (value / total).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        val ZERO = PlaybackProgress(Duration.ZERO, null, Duration.ZERO)
    }
}

/** From platform milliseconds; a non-positive [durationMs] (e.g. Media3's unset sentinel) means unknown. */
fun playbackProgress(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
): PlaybackProgress {
    val duration = durationMs.takeIf { it > 0 }
    val limit = duration ?: Long.MAX_VALUE
    return PlaybackProgress(
        position = positionMs.coerceIn(0, limit).milliseconds,
        duration = duration?.milliseconds,
        buffered = bufferedMs.coerceIn(0, limit).milliseconds,
    )
}

/** A seek position inside the track; with an unknown duration only the start bounds it. */
fun seekTarget(
    targetMs: Long,
    durationMs: Long,
): Long = targetMs.coerceIn(0, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
