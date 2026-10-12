package io.github.javiernarvaezz.shura.core.player

import kotlinx.coroutines.flow.StateFlow

/** The user's playback settings, kept across launches. */
interface PlaybackSettings {
    /** Whether a queue that is about to end continues with related songs. On by default. */
    val autoplay: StateFlow<Boolean>

    fun setAutoplay(enabled: Boolean)
}
