package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The user's own timing fix for a song's synced lyrics, kept per song (keys are opaque song ids). A positive offset
 * shows each line sooner; [Duration.ZERO] means none.
 */
interface LyricsOffsets {
    suspend fun get(key: String): Duration

    suspend fun set(
        key: String,
        offset: Duration,
    )

    companion object {
        /** One tap of the timing controls. */
        val STEP: Duration = 500.milliseconds

        /** As far as an offset goes either way; further off, the lyrics are not for this recording. */
        val LIMIT: Duration = 60.seconds
    }
}
