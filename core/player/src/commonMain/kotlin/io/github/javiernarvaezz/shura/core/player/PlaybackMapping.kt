package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.StreamFailure

/**
 * Carries a [PlaybackError] across process boundaries (e.g. from a media session to its controllers), where
 * exception causes are lost. Codes are short and fixed: never URLs, headers or exception messages.
 */
object PlaybackErrorCodec {
    private const val STREAM = "stream:"
    private const val HTTP = "http:"
    private const val NETWORK = "network"
    private const val DECODING = "decoding"
    private const val UNKNOWN = "unknown"

    fun encode(error: PlaybackError): String =
        when (error) {
            is PlaybackError.Stream -> STREAM + error.failure.name
            is PlaybackError.Http -> HTTP + error.status
            PlaybackError.Network -> NETWORK
            PlaybackError.Decoding -> DECODING
            PlaybackError.Unknown -> UNKNOWN
        }

    fun decode(code: String?): PlaybackError =
        when {
            code == null -> PlaybackError.Unknown
            code.startsWith(STREAM) -> streamFailure(code.removePrefix(STREAM))?.let(PlaybackError::Stream)
            code.startsWith(HTTP) -> code.removePrefix(HTTP).toIntOrNull()?.let(PlaybackError::Http)
            code == NETWORK -> PlaybackError.Network
            code == DECODING -> PlaybackError.Decoding
            else -> null
        } ?: PlaybackError.Unknown

    private fun streamFailure(name: String): StreamFailure? = StreamFailure.entries.firstOrNull { it.name == name }
}

/** Platform-neutral player phase, so state mapping does not depend on Media3 constants. */
enum class PlayerPhase { Idle, Buffering, Ready, Ended }

/** Maps a player snapshot to [PlaybackState]; an error wins over every other signal. */
fun playbackStateOf(
    song: Song?,
    isPlaying: Boolean,
    phase: PlayerPhase,
    error: PlaybackError?,
): PlaybackState =
    when {
        song == null -> PlaybackState.Idle
        error != null -> PlaybackState.Failed(song, error)
        phase == PlayerPhase.Ended -> PlaybackState.Ended(song)
        isPlaying -> PlaybackState.Playing(song)
        phase == PlayerPhase.Ready -> PlaybackState.Paused(song)
        else -> PlaybackState.Loading(song)
    }

/** A media id is accepted only if it is a valid [VideoId]; anything else (URLs included) is rejected. */
fun videoIdOf(mediaId: String): VideoId? = runCatching { VideoId(mediaId) }.getOrNull()

/**
 * Queues commands until a target (e.g. a media controller) is connected, then runs them in order. After
 * [release], queued and later commands are dropped. Not thread-safe: use from one thread (the main thread).
 */
class CommandGate<T : Any> {
    private val pending = ArrayDeque<(T) -> Unit>()
    private var target: T? = null
    private var released = false

    val isConnected: Boolean get() = target != null

    fun run(command: (T) -> Unit) {
        if (released) return
        target?.let(command) ?: pending.addLast(command)
    }

    fun connect(target: T) {
        if (released) return
        this.target = target
        while (pending.isNotEmpty()) pending.removeFirst()(target)
    }

    fun release() {
        released = true
        target = null
        pending.clear()
    }
}
