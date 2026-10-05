package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InnerTubeXTokenProviderTest {
    private class FakeMinter(
        var failure: PoTokenUnavailableException? = null,
    ) : PoTokenMinter {
        val minted = mutableListOf<Pair<VideoId, String>>()
        var invalidated = 0
        var closed = false

        override suspend fun mint(
            videoId: VideoId,
            visitorData: String,
        ): PoTokens {
            failure?.let { throw it }
            minted += videoId to visitorData
            return PoTokens("player-token", "streaming-token", visitorData)
        }

        override suspend fun invalidate() {
            invalidated++
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun declaresWebPageAttestationThroughAWebView() {
        val capabilities = InnerTubeXTokenProvider(FakeMinter()).capabilities

        assertEquals(setOf(PoTokenProviderKind.WEBPAGE_ATTESTATION), capabilities.providers)
        assertTrue(capabilities.usesWebView)
    }

    @Test
    fun mapsMintedTokensToInnerTubeXResult() =
        runTest {
            val minter = FakeMinter()

            val result = InnerTubeXTokenProvider(minter).getPoToken("7fwUH0oRmkQ", "visitor", cookie = "ignored")

            assertEquals("player-token", result?.playerRequestToken)
            assertEquals("streaming-token", result?.streamingDataToken)
            assertEquals("visitor", result?.visitorData)
            assertEquals(listOf(VideoId("7fwUH0oRmkQ") to "visitor"), minter.minted)
        }

    @Test
    fun failureReturnsNoTokenAndIsRecordedForTheCurrentResolution() =
        runTest {
            val failure = PoTokenUnavailableException("botguard", "timeout")
            val tracker = TokenFailures()

            val result =
                withContext(
                    tracker,
                ) { InnerTubeXTokenProvider(FakeMinter(failure)).getPoToken("7fwUH0oRmkQ", "v", null) }

            assertNull(result)
            assertSame(failure, tracker.last)
        }

    @Test
    fun invalidVideoIdNeverReachesTheMinter() =
        runTest {
            val minter = FakeMinter()

            assertNull(InnerTubeXTokenProvider(minter).getPoToken("not a video id", "v", null))
            assertTrue(minter.minted.isEmpty())
        }

    @Test
    fun invalidationAndCloseAreForwarded() =
        runTest {
            val minter = FakeMinter()
            val provider = InnerTubeXTokenProvider(minter)

            provider.invalidateAttestation()
            provider.close()

            assertEquals(1, minter.invalidated)
            assertTrue(minter.closed)
        }
}
