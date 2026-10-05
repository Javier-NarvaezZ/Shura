package io.github.javiernarvaezz.shura.core.player

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event

/** Debug timing of playback start-up: ready state and the moment audio actually starts playing out. */
@OptIn(UnstableApi::class)
internal class TraceAnalytics(
    private val trace: Trace,
) : AnalyticsListener {
    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int,
    ) {
        if (state == Player.STATE_READY) trace.event("player: ready")
    }

    override fun onAudioPositionAdvancing(
        eventTime: AnalyticsListener.EventTime,
        playoutStartSystemTimeMs: Long,
    ) {
        trace.event("player: first audio", mapOf("playoutStartMs" to playoutStartSystemTimeMs.toString()))
    }
}
