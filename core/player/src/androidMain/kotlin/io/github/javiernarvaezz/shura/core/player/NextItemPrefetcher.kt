package io.github.javiernarvaezz.shura.core.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event
import kotlinx.coroutines.Job

/**
 * Resolves the stream URL of the item that plays next, so a skip or an automatic transition does not wait for it.
 * - Follows the player's own play order ([Player.getNextMediaItemIndex]), so shuffle and repeat are respected.
 *   With repeat one it is the next item in the queue, which is what "next" plays.
 * - Only while audio is playing: a paused or restored queue costs no network, and a skip that is still starting
 *   never competes with the next item's resolution.
 * - Resolves the URL only (one `player` request); no audio is downloaded.
 *
 * Runs on the player thread (the main thread).
 */
internal class NextItemPrefetcher(
    private val player: Player,
    private val prefetch: (VideoId) -> Job,
    private val isEnabled: () -> Boolean,
    private val trace: Trace,
) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val update = Runnable { prefetchNext() }
    private var target: VideoId? = null
    private var job: Job? = null

    fun start() {
        player.addListener(this)
        schedule()
    }

    fun stop() {
        main.removeCallbacks(update)
        player.removeListener(this)
        job?.cancel()
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // Whether the item now playing is the one pre-resolved, without logging its id.
        val prefetched = mediaItem?.mediaId?.let(::videoIdOf)?.let { it == target } ?: false
        trace.event(
            "player: item transition",
            mapOf("reason" to transitionReasonName(reason), "prefetched" to prefetched.toString()),
        )
        schedule()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) = schedule()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = schedule()

    override fun onRepeatModeChanged(repeatMode: Int) = schedule()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) schedule()
    }

    // Debounced, so toggling shuffle or editing the queue in a burst sends one request at most.
    private fun schedule() {
        main.removeCallbacks(update)
        main.postDelayed(update, DEBOUNCE_MS)
    }

    private fun prefetchNext() {
        if (!player.isPlaying || !isEnabled()) return
        val index = player.nextMediaItemIndex
        val next = if (index == C.INDEX_UNSET) null else videoIdOf(player.getMediaItemAt(index).mediaId)
        if (next == target) return
        job?.takeIf { it.isActive }?.let {
            it.cancel()
            trace.event("player: prefetch cancelled")
        }
        target = next
        job = next?.let(prefetch)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}

private fun transitionReasonName(reason: Int): String =
    when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "auto"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "seek"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "repeat"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "playlist"
        else -> "other"
    }
