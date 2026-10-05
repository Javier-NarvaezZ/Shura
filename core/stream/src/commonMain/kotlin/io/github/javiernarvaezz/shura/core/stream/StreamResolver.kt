package io.github.javiernarvaezz.shura.core.stream

import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlin.time.Instant

/**
 * Resolves a playable audio URL for a track. Implementations are swappable (ADR 0001); callers never see
 * the underlying extraction library.
 */
interface StreamResolver {
    /** @throws StreamResolutionException with a typed [StreamFailure]; cancellation is never wrapped. */
    suspend fun resolve(
        videoId: VideoId,
        quality: AudioQuality = AudioQuality.Auto,
    ): ResolvedStream
}

enum class AudioQuality { Low, Auto, High }

/**
 * A resolved, direct (progressive) audio stream. [url] is signed and short-lived and must be fetched with
 * [requestHeaders]. [toString] redacts both, so the object is safe to log.
 */
class ResolvedStream(
    val videoId: VideoId,
    val url: String,
    val requestHeaders: Map<String, String>,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val contentLength: Long?,
    val expiresAt: Instant?,
    val clientProfile: String,
    val requiresBoundedRange: Boolean,
    val rangeChunkSizeBytes: Long,
) {
    override fun toString(): String =
        "ResolvedStream(url=redacted, headers=${requestHeaders.size}, mimeType=$mimeType, codecs=$codecs, " +
            "bitrate=$bitrate, contentLength=$contentLength, expiresAt=$expiresAt, clientProfile=$clientProfile)"
}

/** Why a stream could not be resolved. The player maps every case to a visible "retry" state (ADR 0001 R4). */
enum class StreamFailure {
    TokenUnavailable,
    NoPlayableStream,
    AgeRestricted,
    Unavailable,
    Network,
    ResolverCrashed,
    Unknown,
}

/** One client profile the resolver tried, with the library's outcome label (no URLs or tokens). */
data class StreamAttempt(
    val profile: String,
    val outcome: String,
)

/**
 * Typed resolution failure. The message, [attempts] and [causeType] are safe to log: they never contain the
 * video id, URLs, headers, cookies or tokens.
 */
class StreamResolutionException(
    val failure: StreamFailure,
    val attempts: List<StreamAttempt> = emptyList(),
    val causeType: String? = null,
    cause: Throwable? = null,
) : Exception("Stream resolution failed: $failure", cause) {
    override fun toString(): String = "StreamResolutionException($failure, cause=$causeType, attempts=$attempts)"
}
