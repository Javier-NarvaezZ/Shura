package io.github.javiernarvaezz.shura.core.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the queue across process deaths and records the play history, from the player thread (the main thread).
 * - Restores the saved queue when the player starts empty, without preparing it: nothing is resolved or downloaded
 *   until play.
 * - Saves queue changes after a short debounce, the position every few seconds while playing, and on pause.
 * - Records a play once it has been listened to for 30 s or played to its end ([PlayTracker]).
 *
 * Saves run one at a time in launch order, so an older state never overwrites a newer one. Storage errors never
 * affect playback: they are logged with the exception type only.
 */
@OptIn(UnstableApi::class)
internal class QueuePersistence(
    private val player: ShuraPlayer,
    private val store: QueueStore,
    private val trace: Trace,
) : Player.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val saves = Mutex()
    private val tracker = PlayTracker { SystemClock.elapsedRealtime() }
    private var trackedSong: Song? = null

    // A queue handed to Media3 for playback resumption: Media3 sets only its items, index and position, so its
    // shuffled order, shuffle and repeat are applied once those items arrive.
    private var pendingModes: QueueSnapshot? = null
    private val debouncedSave = Runnable { saveNow() }
    private val tick =
        object : Runnable {
            override fun run() {
                if (tracker.checkListened()) record(trackedSong)
                saveNow()
                main.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
            }
        }

    fun start() {
        player.addListener(this)
        trackedSong = player.currentMediaItem?.toSong()
        if (player.mediaItemCount == 0) restore()
    }

    /** For system playback resumption (e.g. a headset play button while the app is not running). */
    fun resumptionItems(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            val snapshot = guarded { store.load() }
            val items = snapshot?.sessionItems()
            if (snapshot == null || items == null) {
                result.setException(UnsupportedOperationException("Nothing to resume"))
            } else {
                main.post {
                    pendingModes = snapshot
                    result.set(
                        MediaSession.MediaItemsWithStartPosition(items, snapshot.currentIndex, snapshot.positionMs),
                    )
                }
            }
        }
        return result
    }

    fun saveNow() {
        val snapshot = snapshot() ?: return
        scope.launch { saves.withLock { guarded { store.save(snapshot) } } }
    }

    /** Saves and waits at most [timeoutMs]; for the service's last moments. */
    fun saveBlocking(timeoutMs: Long) {
        val snapshot = snapshot() ?: return
        runBlocking { withTimeoutOrNull(timeoutMs) { saves.withLock { guarded { store.save(snapshot) } } } }
    }

    fun stop() {
        main.removeCallbacks(tick)
        main.removeCallbacks(debouncedSave)
        player.removeListener(this)
        scope.cancel()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        applyPendingModes()
        scheduleSave()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = scheduleSave()

    override fun onRepeatModeChanged(repeatMode: Int) = scheduleSave()

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        val ended =
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (tracker.onItemChanged(ended)) record(trackedSong)
        trackedSong = mediaItem?.toSong()
        scheduleSave()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && tracker.onItemChanged(ended = true)) record(trackedSong)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        tracker.onPlayingChanged(isPlaying)
        main.removeCallbacks(tick)
        if (isPlaying) {
            main.postDelayed(tick, POSITION_SAVE_INTERVAL_MS)
        } else {
            saveNow()
        }
    }

    private fun restore() {
        scope.launch {
            val snapshot = guarded { store.load() } ?: return@launch
            val items = snapshot.sessionItems() ?: return@launch
            main.post {
                // Playback may have started meanwhile (e.g. the user tapped a song): never replace it.
                if (player.mediaItemCount > 0) return@post
                player.restore(items, snapshot)
                trackedSong = player.currentMediaItem?.toSong()
                trace.event("queue: restored", mapOf("items" to items.size.toString()))
            }
        }
    }

    private fun applyPendingModes() {
        val snapshot = pendingModes ?: return
        if (player.mediaItemCount != snapshot.items.size) return
        pendingModes = null
        player.restoreModes(snapshot)
        trace.event("queue: resumed", mapOf("items" to snapshot.items.size.toString()))
    }

    private fun scheduleSave() {
        main.removeCallbacks(debouncedSave)
        main.postDelayed(debouncedSave, SAVE_DEBOUNCE_MS)
    }

    /** The current queue, or null when an item cannot be read back as a song (never save a partial queue). */
    private fun snapshot(): QueueSnapshot? {
        val timeline = player.currentTimeline
        val window = Timeline.Window()
        val songs = (0 until timeline.windowCount).mapNotNull { timeline.getWindow(it, window).mediaItem.toSong() }
        if (songs.size != timeline.windowCount) return null
        val order = player.playOrder().toList().takeIf { it.size == songs.size } ?: songs.indices.toList()
        return QueueSnapshot(
            items = songs,
            currentIndex = if (songs.isEmpty()) 0 else player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeat =
                when (player.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.All
                    Player.REPEAT_MODE_ONE -> RepeatMode.One
                    else -> RepeatMode.Off
                },
            playOrder = order,
        )
    }

    private fun record(song: Song?) {
        song ?: return
        val playedAt = System.currentTimeMillis()
        scope.launch { guarded { store.recordPlay(song, playedAt) } }
        trace.event("queue: play recorded")
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
        const val POSITION_SAVE_INTERVAL_MS = 10_000L
    }
}

private const val TAG = "ShuraQueue"

private fun QueueSnapshot.sessionItems(): List<MediaItem>? =
    items.map {
        it.toMediaItem().toSessionItem() ?: return null
    }

private suspend fun <T> guarded(block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Storage must never break playback; only the exception type is logged (no queue contents).
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Log.w(TAG, "Queue store failed: ${e::class.simpleName}")
        null
    }
