package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import io.github.javiernarvaezz.shura.core.stream.StreamResolutionException
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Platform-neutral audio player. The UI depends only on this interface, never on Media3 (CLAUDE.md).
 * Implementations: Android (Media3) now; desktop in Phase 5.
 */
interface AudioPlayer {
    val state: StateFlow<PlaybackState>

    fun play(song: Song)

    fun pause()

    fun resume()

    /** Moves the playback position by [offset] (negative goes back), clamped to the track. */
    fun seekBy(offset: Duration)

    /** Re-resolves the current song's stream and starts it again after a failure. */
    fun retry()

    fun stop()

    fun release()
}

sealed interface PlaybackState {
    data object Idle : PlaybackState

    data class Loading(
        val song: Song,
    ) : PlaybackState

    data class Playing(
        val song: Song,
    ) : PlaybackState

    data class Paused(
        val song: Song,
    ) : PlaybackState

    data class Ended(
        val song: Song,
    ) : PlaybackState

    /** Always shown to the user with a retry action (ADR 0001 R4). */
    data class Failed(
        val song: Song,
        val error: PlaybackError,
    ) : PlaybackState
}

sealed interface PlaybackError {
    data class Stream(
        val failure: StreamFailure,
    ) : PlaybackError

    data class Http(
        val status: Int,
    ) : PlaybackError

    data object Network : PlaybackError

    data object Decoding : PlaybackError

    data object Unknown : PlaybackError

    companion object {
        /**
         * A [StreamResolutionException] anywhere in the cause chain wins; otherwise [platform] classifies
         * platform exceptions (HTTP status, network, decoding).
         */
        fun from(
            error: Throwable,
            platform: (Throwable) -> PlaybackError?,
        ): PlaybackError {
            val chain = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
            chain.firstNotNullOfOrNull { (it as? StreamResolutionException)?.failure }?.let { return Stream(it) }
            return chain.firstNotNullOfOrNull(platform) ?: Unknown
        }

        private const val MAX_CAUSE_DEPTH = 16
    }
}

/** Stable media URI: the signed URL is resolved only when the player opens the stream. */
object StreamUri {
    private const val PREFIX = "shura://stream/"

    fun of(videoId: VideoId): String = PREFIX + videoId.value

    fun parse(uri: String): VideoId? =
        uri
            .takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.let { runCatching { VideoId(it) }.getOrNull() }
}

/** Bounded byte-range planning for googlevideo, which throttles open-ended ranges (ADR 0001). */
internal object RangeChunks {
    /** Exclusive end of the chunk starting at [position]. */
    fun chunkEnd(
        position: Long,
        chunkSize: Long,
        endExclusive: Long?,
    ): Long = (position + chunkSize).let { if (endExclusive != null) minOf(it, endExclusive) else it }

    /** Total size from a `Content-Range: bytes a-b/total` header, or null when unknown. */
    fun totalFromContentRange(header: String?): Long? = header?.substringAfter('/', "")?.toLongOrNull()
}
