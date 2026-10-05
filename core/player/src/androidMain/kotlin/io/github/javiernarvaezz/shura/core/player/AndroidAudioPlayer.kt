package io.github.javiernarvaezz.shura.core.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
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
 * [AudioPlayer] backed by a Media3 `MediaController` connected to [PlaybackService], where the player lives.
 * Commands issued before the connection completes are queued. Create and call from the main thread; one instance
 * per process.
 */
class AndroidAudioPlayer(
    context: Context,
    private val trace: Trace = Trace.NONE,
) : AudioPlayer {
    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val gate = CommandGate<MediaController>()
    private var current: Song? = null
    private var connectionFailed = false
    private val createdAt = SystemClock.elapsedRealtime()

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

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

    override fun play(song: Song) {
        trace.event("player: play", mapOf("queued" to (!gate.isConnected).toString()))
        current = song
        mutableState.value =
            if (connectionFailed) PlaybackState.Failed(song, PlaybackError.Unknown) else PlaybackState.Loading(song)
        gate.run {
            it.setMediaItem(song.toMediaItem())
            it.prepare()
            it.play()
        }
    }

    override fun pause() = gate.run { it.pause() }

    override fun resume() = gate.run { it.play() }

    override fun seekBy(offset: Duration) =
        gate.run {
            val target = it.currentPosition + offset.inWholeMilliseconds
            val duration = it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE
            it.seekTo(target.coerceIn(0, duration))
        }

    override fun retry() {
        val song = current ?: return
        mutableState.value = PlaybackState.Loading(song)
        gate.run { it.sendCustomCommand(SessionContract.RETRY, Bundle.EMPTY) }
    }

    override fun stop() {
        gate.run {
            it.stop()
            it.clearMediaItems()
        }
        current = null
        mutableState.value = PlaybackState.Idle
    }

    override fun release() {
        gate.release()
        MediaController.releaseFuture(controllerFuture)
        current = null
        mutableState.value = PlaybackState.Idle
    }

    private fun onConnected(controller: MediaController?) {
        if (controller == null) {
            connectionFailed = true
            current?.let { mutableState.value = PlaybackState.Failed(it, PlaybackError.Unknown) }
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
        val error =
            controller.playerError?.let {
                PlaybackErrorCodec.decode(controller.sessionExtras.getString(SessionContract.EXTRA_ERROR))
            }
        mutableState.value = playbackStateOf(current, controller.isPlaying, controller.playbackState.toPhase(), error)
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
