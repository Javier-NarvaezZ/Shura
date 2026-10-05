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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality

/** Seam over InnerTubeX extraction, so mapping can be tested without network. */
internal fun interface Extraction {
    suspend fun extract(
        videoId: String,
        quality: InnerTubeXAudioQuality,
    ): ExtractedStream?
}

/**
 * [StreamResolver] backed by InnerTubeX (ADR 0001): direct transport only and never any remote solver
 * configuration (R3). With a [PoTokenMinter], clients that need a PoToken become eligible (R2).
 */
class InnerTubeXStreamResolver internal constructor(
    private val extraction: Extraction,
) : StreamResolver {
    /**
     * The caller owns [httpClient]'s engine; requests are restricted to the app host policy.
     *
     * @param excludedProfiles InnerTubeX profile ids never to use. Diagnostics only (e.g. forcing a non-legacy
     *   client in a device test); production passes none.
     */
    constructor(
        httpClient: HttpClient,
        poTokenMinter: PoTokenMinter? = null,
        excludedProfiles: Set<String> = emptySet(),
    ) : this(innerTubeXExtraction(httpClient, poTokenMinter, excludedProfiles))

    override suspend fun resolve(
        videoId: VideoId,
        quality: AudioQuality,
    ): ResolvedStream {
        val tokenFailures = TokenFailures()
        return try {
            withContext(tokenFailures) { resolveDirect(videoId, quality) }
        } catch (e: StreamResolutionException) {
            throw e.withTokenFailure(tokenFailures.last)
        }
    }

    private suspend fun resolveDirect(
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

/** A failure that may have been caused by a missing token is reported as [StreamFailure.TokenUnavailable] (R4). */
private fun StreamResolutionException.withTokenFailure(token: PoTokenUnavailableException?): StreamResolutionException =
    if (token != null && failure in TOKEN_MASKABLE_FAILURES) {
        StreamResolutionException(StreamFailure.TokenUnavailable, attempts, "PoToken:${token.stage}", this)
    } else {
        this
    }

private val TOKEN_MASKABLE_FAILURES = setOf(StreamFailure.NoPlayableStream, StreamFailure.Unknown)

private fun innerTubeXExtraction(
    httpClient: HttpClient,
    poTokenMinter: PoTokenMinter?,
    excludedProfiles: Set<String>,
): Extraction {
    // InnerTubeX sends request bodies as @Serializable objects and expects the caller's client to serialize them.
    // Only this derived client gets that: same engine and OkHttpClient, no extra headers or logging (ADR 0001 R7).
    val client =
        httpClient.config {
            install(serializedRequestBodies(InnerTubeXJson))
            install(HostAllowlist)
        }
    val innerTube = InnerTube(client)
    // No remote solver configuration store: cipher solving uses only the solvers bundled in the library.
    val cipher = YouTubeCipherService(client)
    val extractor =
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(client, innerTube, cipherService = cipher),
            cipherService = cipher,
            innerTube = innerTube,
            tokenProvider = poTokenMinter?.let(::InnerTubeXTokenProvider),
        )
    val hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false)
    return Extraction { videoId, quality ->
        extractor.extract(videoId, hints, excludedClients = excludedProfiles, audioQuality = quality)
    }
}

/**
 * The configuration InnerTubeX v0.7.4 uses itself (its live harness and tests): unknown response keys are
 * ignored, and with `encodeDefaults = false` optional body fields that default to null are omitted.
 */
private val InnerTubeXJson = Json { ignoreUnknownKeys = true }

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
