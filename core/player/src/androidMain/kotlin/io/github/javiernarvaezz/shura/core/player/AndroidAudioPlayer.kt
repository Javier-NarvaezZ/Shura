package io.github.javiernarvaezz.shura.core.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.time.Duration

/**
 * [AudioPlayer] backed by a Media3 `MediaController` connected to [PlaybackService], where the player and the queue
 * live. Commands issued before the connection completes are queued. Create and call from the main thread; one
 * instance per process.
 */
class AndroidAudioPlayer(
    context: Context,
    private val trace: Trace = Trace.NONE,
) : AudioPlayer {
    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val mutableQueue = MutableStateFlow(QueueState())
    private val gate = CommandGate<MediaController>()
    private val createdAt = SystemClock.elapsedRealtime()

    // Shown as loading until the controller reports the new queue.
    private var pending: Song? = null
    private var connectionFailed = false

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    override val queue: StateFlow<QueueState> = mutableQueue.asStateFlow()

    private val controllerFuture =
        context.applicationContext.let { appContext ->
            MediaController
                .Builder(appContext, SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)))
                .setListener(ControllerListener())
                .buildAsync()
                .also { future ->
                    val mainThread = Handler(Looper.getMainLooper())
                    future.addListener({ onConnected(future.getOrNull()) }, mainThread::post)
                }
        }

    override fun play(song: Song) = playQueue(listOf(song), 0)

    override fun playQueue(
        songs: List<Song>,
        startIndex: Int,
    ) {
        val start = songs.getOrNull(startIndex) ?: return
        trace.event("player: play", mapOf("queued" to (!gate.isConnected).toString()))
        pending = start
        mutableState.value =
            if (connectionFailed) PlaybackState.Failed(start, PlaybackError.Unknown) else PlaybackState.Loading(start)
        gate.run {
            it.setMediaItems(songs.map(Song::toMediaItem), startIndex, 0)
            it.prepare()
            it.play()
        }
    }

    override fun pause() = gate.run { it.pause() }

    override fun resume() = gate.run { it.playFromHere() }

    override fun next() = gate.run { it.seekToNext() }

    override fun previous() = gate.run { it.seekToPrevious() }

    override fun skipTo(index: Int) =
        gate.run {
            if (index !in 0 until it.mediaItemCount) return@run
            it.seekToDefaultPosition(index)
            it.playFromHere()
        }

    override fun setShuffle(enabled: Boolean) = gate.run { it.shuffleModeEnabled = enabled }

    override fun setRepeat(mode: RepeatMode) =
        gate.run {
            it.repeatMode =
                when (mode) {
                    RepeatMode.Off -> Player.REPEAT_MODE_OFF
                    RepeatMode.All -> Player.REPEAT_MODE_ALL
                    RepeatMode.One -> Player.REPEAT_MODE_ONE
                }
        }

    override fun playNext(songs: List<Song>) =
        gate.run {
            if (it.mediaItemCount == 0) {
                playQueue(songs, 0)
            } else {
                it.addMediaItems(it.currentMediaItemIndex + 1, songs.map(Song::toMediaItem))
            }
        }

    override fun enqueue(songs: List<Song>) = gate.run { it.addMediaItems(songs.map(Song::toMediaItem)) }

    override fun remove(index: Int) = gate.run { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }

    override fun move(
        from: Int,
        to: Int,
    ) = gate.run {
        val range = 0 until it.mediaItemCount
        if (from in range && to in range) it.moveMediaItem(from, to)
    }

    override fun seekBy(offset: Duration) =
        gate.run {
            val target = it.currentPosition + offset.inWholeMilliseconds
            val duration = it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE
            it.seekTo(target.coerceIn(0, duration))
        }

    override fun retry() {
        val song = mutableQueue.value.current ?: pending ?: return
        mutableState.value = PlaybackState.Loading(song)
        gate.run { it.sendCustomCommand(SessionContract.RETRY, Bundle.EMPTY) }
    }

    override fun stop() {
        gate.run {
            it.stop()
            it.clearMediaItems()
        }
        pending = null
        mutableState.value = PlaybackState.Idle
    }

    override fun release() {
        gate.release()
        MediaController.releaseFuture(controllerFuture)
        pending = null
        mutableState.value = PlaybackState.Idle
        mutableQueue.value = QueueState()
    }

    private fun onConnected(controller: MediaController?) {
        if (controller == null) {
            connectionFailed = true
            pending?.let { mutableState.value = PlaybackState.Failed(it, PlaybackError.Unknown) }
            Log.w(TAG, "Could not connect to the playback service")
            return
        }
        trace.event(
            "player: controller connected",
            mapOf("afterCreateMs" to (SystemClock.elapsedRealtime() - createdAt).toString()),
        )
        controller.addListener(PlayerListener(controller))
        gate.connect(controller)
        publish(controller)
    }

    private fun publish(controller: MediaController) {
        val queueState = controller.queueState()
        mutableQueue.value = queueState
        if (queueState.current != null) pending = null
        val error =
            controller.playerError?.let {
                PlaybackErrorCodec.decode(controller.sessionExtras.getString(SessionContract.EXTRA_ERROR))
            }
        mutableState.value =
            playbackStateOf(
                queueState.current ?: pending,
                controller.isPlaying,
                controller.playbackState.toPhase(),
                error,
                controller.playWhenReady,
            )
    }

    private inner class PlayerListener(
        private val controller: MediaController,
    ) : Player.Listener {
        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) = publish(controller)
    }

    /** The error code arrives as a session extra, possibly after the player error itself. */
    private inner class ControllerListener : MediaController.Listener {
        override fun onExtrasChanged(
            controller: MediaController,
            extras: Bundle,
        ) = publish(controller)

        override fun onDisconnected(controller: MediaController) {
            Log.w(TAG, "Playback service disconnected")
        }
    }

    private companion object {
        const val TAG = "ShuraPlayer"

        fun Int.toPhase(): PlayerPhase =
            when (this) {
                Player.STATE_BUFFERING -> PlayerPhase.Buffering
                Player.STATE_READY -> PlayerPhase.Ready
                Player.STATE_ENDED -> PlayerPhase.Ended
                else -> PlayerPhase.Idle
            }

        /** Plays from the current position, preparing first when the player is idle (e.g. after a stop). */
        fun Player.playFromHere() {
            if (playbackState == Player.STATE_IDLE) prepare()
            play()
        }

        /** Queue as the UI sees it: items in queue order and their play order (shuffled when shuffle is on). */
        fun Player.queueState(): QueueState {
            val timeline = currentTimeline
            val window = Timeline.Window()
            val items = (0 until timeline.windowCount).mapNotNull { timeline.getWindow(it, window).mediaItem.toSong() }
            if (items.size != timeline.windowCount) return QueueState()
            val playOrder = mutableListOf<Int>()
            var index = timeline.getFirstWindowIndex(shuffleModeEnabled)
            while (index != C.INDEX_UNSET && playOrder.size < timeline.windowCount) {
                playOrder += index
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
            }
            return QueueState(
                items = items,
                currentIndex = if (items.isEmpty()) -1 else currentMediaItemIndex,
                shuffle = shuffleModeEnabled,
                repeat =
                    when (repeatMode) {
                        Player.REPEAT_MODE_ALL -> RepeatMode.All
                        Player.REPEAT_MODE_ONE -> RepeatMode.One
                        else -> RepeatMode.Off
                    },
                playOrder = playOrder,
            )
        }

        fun <T> java.util.concurrent.Future<T>.getOrNull(): T? =
            try {
                get()
            } catch (
                // Connection failures surface as a visible failed state; the cause adds nothing for the user.
                @Suppress("SwallowedException") e: ExecutionException,
            ) {
                null
            } catch (
                @Suppress("SwallowedException") e: CancellationException,
            ) {
                null
            }
    }
}
