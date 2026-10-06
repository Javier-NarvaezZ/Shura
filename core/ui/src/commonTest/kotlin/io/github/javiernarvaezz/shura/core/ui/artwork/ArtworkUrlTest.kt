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
        val ytimg = "https://i.ytimg.com/vi/w2T_wDvJNu4/hqdefault.jpg?sqp=abc&rs=def"
        assertEquals(ytimg, artworkUrl(ytimg, 544))
        // No size suffix: nothing to replace.
        assertEquals(base, artworkUrl(base, 544))
    }

    @Test
    fun noUrlGivesNoUrl() {
        assertNull(artworkUrl(null, 544))
    }
}
