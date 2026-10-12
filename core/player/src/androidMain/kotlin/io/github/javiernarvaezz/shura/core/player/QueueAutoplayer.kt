package io.github.javiernarvaezz.shura.core.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Autoplay: when the song playing becomes the last in play order, appends its related songs (see
 * [QueueAutoplay]), so the queue goes on instead of ending. Asks once per seed song; if that fails (e.g. no
 * network) the queue ends as usual. Appended songs are ordinary queue items: shown, saved and pre-resolved like
 * the rest.
 *
 * Only while audio plays, so a restored or paused queue costs no network. Runs on the player thread (the main
 * thread); [scope] must run there too.
 */
internal class QueueAutoplayer(
    private val player: Player,
    private val relatedSongs: suspend (VideoId) -> List<Song>,
    private val isEnabled: () -> Boolean,
    private val scope: CoroutineScope,
    private val trace: Trace,
) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val update = Runnable { check() }
    private var asked: VideoId? = null
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
    ) = schedule()

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) = schedule()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = schedule()

    override fun onRepeatModeChanged(repeatMode: Int) = schedule()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) schedule()
    }

    // Debounced, so a burst of queue edits checks once.
    private fun schedule() {
        main.removeCallbacks(update)
        main.postDelayed(update, DEBOUNCE_MS)
    }

    private fun check() {
        val seed = if (player.isPlaying) QueueAutoplay.seedFor(player.queueState(), isEnabled()) else null
        if (seed == null || seed.videoId == asked) return
        asked = seed.videoId
        job?.cancel()
        job = scope.launch { append(seed) }
    }

    @Suppress("TooGenericExceptionCaught") // Any failure just lets the queue end as it would without autoplay.
    private suspend fun append(seed: Song) {
        val related =
            try {
                relatedSongs(seed.videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trace.event("autoplay: failed", mapOf("type" to e::class.simpleName.orEmpty()))
                return
            }
        val queue = player.queueState()
        // The queue may have changed while waiting: add only if the seed is still where it was.
        if (QueueAutoplay.seedFor(queue, isEnabled())?.videoId != seed.videoId) {
            trace.event("autoplay: skipped", mapOf("reason" to "queue changed"))
            return
        }
        val songs = QueueAutoplay.toAppend(related, queue)
        player.addMediaItems(songs.mapNotNull { it.toMediaItem().toSessionItem() })
        trace.event("autoplay: appended", mapOf("count" to songs.size.toString()))
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
