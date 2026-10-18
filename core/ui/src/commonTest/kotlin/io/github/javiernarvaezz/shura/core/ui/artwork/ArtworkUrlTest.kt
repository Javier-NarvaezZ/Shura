package io.github.javiernarvaezz.shura.core.ui.artwork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkUrlTest {
    private val base = "https://yt3.googleusercontent.com/AbC_123-xyz"

    @Test
    fun googleArtworkIsRequestedAtTheGivenSize() {
        assertEquals("$base=w544-h544-l90-rj", artworkUrl("$base=w60-h60-l90-rj", 544))
        assertEquals("$base=w226-h226-l90-rj", artworkUrl("$base=w120-h120-s-l90-rj", 226))
        assertEquals(
            "https://lh3.googleusercontent.com/AbC=w96-h96-l90-rj",
            artworkUrl("https://lh3.googleusercontent.com/AbC=w60-c-h60-k-c0x00ffffff-no-l90-rj", 96),
        )
    }

    @Test
    fun otherUrlsAreLeftAlone() {
        val other = "https://example.com/cover.jpg?size=60"
        assertEquals(other, artworkUrl(other, 544))
        assertEquals(listOf(other), artworkUrls(other, 544))
        // No size suffix: nothing to replace.
        assertEquals(base, artworkUrl(base, 544))
    }

    @Test
    fun aVideoFrameIsAskedLargestFirstWithSmallerOnesToFallBackOn() {
        val ytimg = "https://i.ytimg.com/vi/w2T_wDvJNu4/hq720.jpg?sqp=abc&rs=def"
        val frames = "https://i.ytimg.com/vi/w2T_wDvJNu4"

        assertEquals(
            listOf("maxresdefault", "hq720", "sddefault", "hqdefault").map { "$frames/$it.jpg" },
            artworkUrls(ytimg, 1080),
        )
        assertEquals("$frames/maxresdefault.jpg", artworkUrl(ytimg, 544))
        // Small: the 16:9 frame without black bars, as small as it comes.
        assertEquals(listOf("$frames/mqdefault.jpg", "$frames/hqdefault.jpg"), artworkUrls(ytimg, 120))
        assertEquals(listOf("$frames/mqdefault.jpg", "$frames/hqdefault.jpg"), artworkUrls("$frames/default.jpg", 64))
    }

    @Test
    fun aGoogleCoverHasOneCandidate() {
        assertEquals(listOf("$base=w544-h544-l90-rj"), artworkUrls("$base=w60-h60-l90-rj", 544))
    }

    @Test
    fun noUrlGivesNoUrl() {
        assertNull(artworkUrl(null, 544))
    }

    @Test
    fun aShownSizeIsRoundedUpToTheNextStep() {
        assertEquals(120, artworkStep(1))
        assertEquals(226, artworkStep(126))
        assertEquals(544, artworkStep(544))
        assertEquals(1080, artworkStep(950))
        assertEquals(1440, artworkStep(1081))
        // Beyond the largest step, the largest: Google's covers are not sharper than that.
        assertEquals(1440, artworkStep(3000))
    }
}
