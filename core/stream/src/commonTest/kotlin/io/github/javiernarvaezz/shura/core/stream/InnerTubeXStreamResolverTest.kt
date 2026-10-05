package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.StreamAttemptDiagnostic
import com.metrolist.innertubex.extraction.StreamDiagnostics
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.sabr.SabrBootstrap
import com.metrolist.innertubex.sabr.SabrFormatId
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality

class InnerTubeXStreamResolverTest {
    private val videoId = VideoId("7fwUH0oRmkQ")
    private val signedUrl = "https://rr3---sn-test.googlevideo.com/videoplayback?expire=1&sig=SECRET"

    private fun extracted(sabr: SabrBootstrap? = null) =
        ExtractedStream(
            videoId = videoId.value,
            audioUrl = signedUrl,
            headers = mapOf("User-Agent" to "test-agent", "Origin" to "https://music.youtube.com"),
            loudnessDb = -7.5,
            expiresAt = Instant.fromEpochSeconds(1_800_000_000),
            contentLengthBytes = 3_565_616,
            itag = 251,
            mimeType = "audio/webm",
            codecs = "opus",
            bitrate = 143_444,
            sampleRate = 48_000,
            clientName = "VISIONOS",
            profileId = "VISIONOS_0_1__nopo",
            requireBoundedRange = true,
            rangeChunkSizeBytes = 1_048_576,
            sabrBootstrap = sabr,
        )

    private fun sabrBootstrap() =
        SabrBootstrap(
            videoId = videoId.value,
            serverAbrStreamingUrl = "https://rr3---sn-test.googlevideo.com/videoplayback",
            videoPlaybackUstreamerConfig = byteArrayOf(1),
            clientName = 1,
            clientVersion = "1",
            audioFormat = SabrFormatId(itag = 251),
            discardVideoFormat = SabrFormatId(itag = 0),
            discardVideoHeight = 0,
            durationMs = 1,
            contentLengthBytes = 1,
            mimeType = "audio/webm",
        )

    private fun resolver(block: suspend (String, InnerTubeXAudioQuality) -> ExtractedStream?) =
        InnerTubeXStreamResolver(Extraction { id, quality -> block(id, quality) })

    @Test
    fun resolvesDirectStreamWithAllFields() =
        runTest {
            val stream = resolver { _, _ -> extracted() }.resolve(videoId, AudioQuality.High)

            assertEquals(videoId, stream.videoId)
            assertEquals(signedUrl, stream.url)
            assertEquals("audio/webm", stream.mimeType)
            assertEquals("opus", stream.codecs)
            assertEquals(143_444, stream.bitrate)
            assertEquals(3_565_616, stream.contentLength)
            assertEquals(Instant.fromEpochSeconds(1_800_000_000), stream.expiresAt)
            assertEquals("test-agent", stream.requestHeaders["User-Agent"])
            assertEquals("VISIONOS_0_1__nopo", stream.clientProfile)
            assertTrue(stream.requiresBoundedRange)
            assertEquals(1_048_576, stream.rangeChunkSizeBytes)
        }

    @Test
    fun passesVideoIdAndMapsQuality() =
        runTest {
            val seen = mutableListOf<Pair<String, InnerTubeXAudioQuality>>()
            val resolver =
                resolver { id, quality ->
                    seen += id to quality
                    extracted()
                }

            AudioQuality.entries.forEach { resolver.resolve(videoId, it) }

            assertEquals(
                listOf(
                    "7fwUH0oRmkQ" to InnerTubeXAudioQuality.LOW,
                    "7fwUH0oRmkQ" to InnerTubeXAudioQuality.AUTO,
                    "7fwUH0oRmkQ" to InnerTubeXAudioQuality.HIGH,
                ),
                seen,
            )
        }

    @Test
    fun noStreamIsNoPlayableStream() =
        runTest {
            val error = assertFailsWith<StreamResolutionException> { resolver { _, _ -> null }.resolve(videoId) }

            assertEquals(StreamFailure.NoPlayableStream, error.failure)
        }

    @Test
    fun sabrResultIsRejectedBecausePlaybackIsDirectOnly() =
        runTest {
            val error =
                assertFailsWith<StreamResolutionException> {
                    resolver { _, _ -> extracted(sabr = sabrBootstrap()) }.resolve(videoId)
                }

            assertEquals(StreamFailure.NoPlayableStream, error.failure)
        }

    @Test
    fun mapsInnerTubeXReasonsToTypedFailures() =
        runTest {
            val expected =
                mapOf(
                    StreamResolveException.Reason.NO_PLAYABLE_STREAM to StreamFailure.NoPlayableStream,
                    StreamResolveException.Reason.EXPLICIT_UNSUPPORTED to StreamFailure.NoPlayableStream,
                    StreamResolveException.Reason.NO_MUSIC_VIDEO to StreamFailure.NoPlayableStream,
                    StreamResolveException.Reason.UNAVAILABLE to StreamFailure.Unavailable,
                    StreamResolveException.Reason.AGE_RESTRICTED to StreamFailure.AgeRestricted,
                    StreamResolveException.Reason.NETWORK to StreamFailure.Network,
                    StreamResolveException.Reason.UNKNOWN to StreamFailure.Unknown,
                )
            assertEquals(StreamResolveException.Reason.entries.toSet(), expected.keys, "every reason must be mapped")

            expected.forEach { (reason, failure) ->
                val error =
                    assertFailsWith<StreamResolutionException> {
                        resolver { _, _ -> throw StreamResolveException(reason, "library message") }.resolve(videoId)
                    }
                assertEquals(failure, error.failure, "reason $reason")
            }
        }

    @Test
    fun keepsSanitizedAttemptDiagnostics() =
        runTest {
            val diagnostics =
                StreamDiagnostics(
                    attempts =
                        listOf(
                            StreamAttemptDiagnostic("WEB_REMIX", "WEB_REMIX", "ua", TOKEN_OUTCOME),
                            StreamAttemptDiagnostic("WEB_EMBEDDED_PLAYER", null, "ua", "playability:ERROR"),
                        ),
                    usedAuthenticatedWatchPage = false,
                )
            val error =
                assertFailsWith<StreamResolutionException> {
                    resolver { _, _ ->
                        throw StreamResolveException(NO_PLAYABLE, "x", null, diagnostics)
                    }.resolve(videoId)
                }

            assertEquals(
                listOf(
                    StreamAttempt("WEB_REMIX", TOKEN_OUTCOME),
                    StreamAttempt("WEB_EMBEDDED_PLAYER", "playability:ERROR"),
                ),
                error.attempts,
            )
        }

    @Test
    fun unexpectedErrorIsUnknownAndKeepsOnlyTheType() =
        runTest {
            val error =
                assertFailsWith<StreamResolutionException> {
                    resolver { _, _ -> throw IllegalStateException("token=SECRET url=$signedUrl") }.resolve(videoId)
                }

            assertEquals(StreamFailure.Unknown, error.failure)
            assertEquals("IllegalStateException", error.causeType)
            assertFalse(error.toString().contains("SECRET"))
        }

    @Test
    fun cancellationIsNotWrapped() =
        runTest {
            assertFailsWith<CancellationException> {
                resolver { _, _ -> throw CancellationException("cancelled") }.resolve(videoId)
            }
        }

    @Test
    fun resolvedStreamToStringRedactsUrlAndHeaders() =
        runTest {
            val text = resolver { _, _ -> extracted() }.resolve(videoId).toString()

            assertFalse(text.contains("googlevideo"))
            assertFalse(text.contains("SECRET"))
            assertFalse(text.contains("test-agent"))
            assertTrue(text.contains("VISIONOS_0_1__nopo"))
        }

    @Test
    fun failureMessageDoesNotLeakVideoIdOrUrl() =
        runTest {
            val error = assertFailsWith<StreamResolutionException> { resolver { _, _ -> null }.resolve(videoId) }

            val message = error.message.orEmpty()
            assertFalse(message.contains(videoId.value))
            assertFalse(message.contains("http"))
        }

    @Test
    fun failedResolutionAfterATokenFailureIsTokenUnavailable() =
        runTest {
            val resolver =
                resolver { _, _ ->
                    recordTokenFailure(PoTokenUnavailableException("botguard", "timeout"))
                    null
                }

            val error = assertFailsWith<StreamResolutionException> { resolver.resolve(videoId) }

            assertEquals(StreamFailure.TokenUnavailable, error.failure)
            assertEquals("PoToken:botguard", error.causeType)
        }

    @Test
    fun tokenFailureDoesNotHideNetworkOrAgeErrors() =
        runTest {
            val resolver =
                resolver { _, _ ->
                    recordTokenFailure(PoTokenUnavailableException("watch", "HTTP 503"))
                    throw StreamResolveException(StreamResolveException.Reason.AGE_RESTRICTED, "x")
                }

            assertEquals(
                StreamFailure.AgeRestricted,
                assertFailsWith<StreamResolutionException> {
                    resolver.resolve(videoId)
                }.failure,
            )
        }

    @Test
    fun tokenFailureFromAnotherResolutionDoesNotLeak() =
        runTest {
            var first = true
            val resolver =
                resolver { _, _ ->
                    if (first) {
                        first = false
                        recordTokenFailure(PoTokenUnavailableException("watch", "timeout"))
                        extracted()
                    } else {
                        null
                    }
                }

            resolver.resolve(videoId)
            val error = assertFailsWith<StreamResolutionException> { resolver.resolve(videoId) }

            assertEquals(StreamFailure.NoPlayableStream, error.failure)
        }

    private companion object {
        const val TOKEN_OUTCOME = "selection:GVS PO-token provider unavailable"
        val NO_PLAYABLE = StreamResolveException.Reason.NO_PLAYABLE_STREAM
    }
}
