package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamAttemptDiagnostic
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.network.HostAllowlist
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality

/** Seam over InnerTubeX extraction, so mapping can be tested without network. */
internal fun interface Extraction {
    suspend fun extract(
        videoId: String,
        quality: InnerTubeXAudioQuality,
    ): ExtractedStream?
}

/**
 * [StreamResolver] backed by InnerTubeX (ADR 0001): direct transport only, no PoToken yet, and never any
 * remote solver configuration (R3).
 */
class InnerTubeXStreamResolver internal constructor(
    private val extraction: Extraction,
) : StreamResolver {
    /** The caller owns [httpClient]'s engine; requests are restricted to the app host policy. */
    constructor(httpClient: HttpClient) : this(innerTubeXExtraction(httpClient))

    override suspend fun resolve(
        videoId: VideoId,
        quality: AudioQuality,
    ): ResolvedStream {
        val stream = extractSafely(videoId, quality)
        // Shura's players use direct transport only; a SABR result is not playable here.
        if (stream == null || stream.sabrBootstrap != null) {
            throw StreamResolutionException(StreamFailure.NoPlayableStream)
        }
        return stream.toResolvedStream(videoId)
    }

    @Suppress("TooGenericExceptionCaught") // Any library failure must surface as a typed StreamFailure.
    private suspend fun extractSafely(
        videoId: VideoId,
        quality: AudioQuality,
    ): ExtractedStream? =
        try {
            extraction.extract(videoId.value, quality.toInnerTubeX())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toResolutionException()
        }
}

private fun Exception.toResolutionException(): StreamResolutionException =
    when (this) {
        is StreamResolveException -> {
            StreamResolutionException(
                failure = reason.toFailure(),
                attempts = diagnostics?.attempts.orEmpty().map { it.toAttempt() },
                causeType = "StreamResolveException.$reason",
                cause = this,
            )
        }

        else -> {
            StreamResolutionException(StreamFailure.Unknown, causeType = this::class.simpleName, cause = this)
        }
    }

private fun innerTubeXExtraction(httpClient: HttpClient): Extraction {
    val client = httpClient.config { install(HostAllowlist) }
    val innerTube = InnerTube(client)
    // No remote solver configuration store: cipher solving uses only the solvers bundled in the library.
    val cipher = YouTubeCipherService(client)
    val extractor =
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(client, innerTube, cipherService = cipher),
            cipherService = cipher,
            innerTube = innerTube,
        )
    val hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false)
    return Extraction { videoId, quality -> extractor.extract(videoId, hints, audioQuality = quality) }
}

private fun AudioQuality.toInnerTubeX(): InnerTubeXAudioQuality =
    when (this) {
        AudioQuality.Low -> InnerTubeXAudioQuality.LOW
        AudioQuality.Auto -> InnerTubeXAudioQuality.AUTO
        AudioQuality.High -> InnerTubeXAudioQuality.HIGH
    }

private fun StreamAttemptDiagnostic.toAttempt() = StreamAttempt(profileId ?: clientName, outcome)

private fun StreamResolveException.Reason.toFailure(): StreamFailure =
    when (this) {
        StreamResolveException.Reason.NO_PLAYABLE_STREAM,
        StreamResolveException.Reason.EXPLICIT_UNSUPPORTED,
        StreamResolveException.Reason.NO_MUSIC_VIDEO,
        -> StreamFailure.NoPlayableStream

        StreamResolveException.Reason.UNAVAILABLE -> StreamFailure.Unavailable

        StreamResolveException.Reason.AGE_RESTRICTED -> StreamFailure.AgeRestricted

        StreamResolveException.Reason.NETWORK -> StreamFailure.Network

        StreamResolveException.Reason.UNKNOWN -> StreamFailure.Unknown
    }

private fun ExtractedStream.toResolvedStream(videoId: VideoId) =
    ResolvedStream(
        videoId = videoId,
        url = audioUrl,
        requestHeaders = headers,
        mimeType = mimeType,
        codecs = codecs,
        bitrate = bitrate,
        contentLength = contentLengthBytes,
        expiresAt = expiresAt,
        clientProfile = profileId,
        requiresBoundedRange = requireBoundedRange,
        rangeChunkSizeBytes = rangeChunkSizeBytes,
    )
