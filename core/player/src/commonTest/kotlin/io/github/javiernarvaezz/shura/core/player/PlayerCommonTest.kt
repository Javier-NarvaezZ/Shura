package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import io.github.javiernarvaezz.shura.core.stream.StreamResolutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerCommonTest {
    @Test
    fun streamUriRoundTrips() {
        val uri = StreamUri.of(VideoId("7fwUH0oRmkQ"))

        assertEquals("shura://stream/7fwUH0oRmkQ", uri)
        assertEquals(VideoId("7fwUH0oRmkQ"), StreamUri.parse(uri))
    }

    @Test
    fun streamUriRejectsForeignOrInvalidUris() {
        assertNull(StreamUri.parse("https://rr1---sn-x.googlevideo.com/videoplayback"))
        assertNull(StreamUri.parse("shura://stream/not-an-id"))
        assertNull(StreamUri.parse("shura://other/7fwUH0oRmkQ"))
    }

    @Test
    fun chunkEndIsBoundedByChunkSizeAndTotal() {
        assertEquals(1_048_576, RangeChunks.chunkEnd(position = 0, chunkSize = 1_048_576, endExclusive = 3_565_616))
        assertEquals(3_565_616, RangeChunks.chunkEnd(position = 3_145_728, chunkSize = MIB, endExclusive = 3_565_616))
        assertEquals(2_048_576, RangeChunks.chunkEnd(position = 1_000_000, chunkSize = 1_048_576, endExclusive = null))
    }

    @Test
    fun parsesTotalFromContentRange() {
        assertEquals(3_565_616, RangeChunks.totalFromContentRange("bytes 0-1048575/3565616"))
        assertNull(RangeChunks.totalFromContentRange("bytes 0-1048575/*"))
        assertNull(RangeChunks.totalFromContentRange(null))
    }

    @Test
    fun streamFailureAnywhereInTheCauseChainWins() {
        val failure = StreamResolutionException(StreamFailure.TokenUnavailable)
        val wrapped = IllegalStateException("io", RuntimeException("loader", failure))

        val classified = PlaybackError.from(wrapped) { PlaybackError.Http(403) }

        assertEquals(PlaybackError.Stream(StreamFailure.TokenUnavailable), classified)
    }

    @Test
    fun platformClassifierIsUsedWhenThereIsNoStreamFailure() {
        val error = IllegalStateException("source", RuntimeException("http"))

        val classified = PlaybackError.from(error) { if (it.message == "http") PlaybackError.Http(403) else null }

        assertEquals(PlaybackError.Http(403), classified)
    }

    @Test
    fun unknownWhenNothingMatches() {
        assertEquals(PlaybackError.Unknown, PlaybackError.from(IllegalStateException("x")) { null })
    }

    private companion object {
        const val MIB = 1_048_576L
    }
}
