package io.github.javiernarvaezz.shura.core.ui.artwork

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkPaletteTest {
    private val size = 40

    /** An image whose pixel at (x, y) is [color]. */
    private fun image(color: (x: Int, y: Int) -> Color) = IntArray(size * size) { color(it % size, it / size).toArgb() }

    private fun Color.hueDegrees(): Float {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val d = max - min
        if (d == 0f) return 0f
        val h =
            when (max) {
                red -> ((green - blue) / d).mod(6f)
                green -> (blue - red) / d + 2
                else -> (red - green) / d + 4
            }
        return h * 60
    }

    private fun assertClose(
        expected: Color,
        actual: Color,
        tolerance: Float = 0.03f,
    ) {
        val ok =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue(ok, "expected ~$expected, was $actual")
    }

    @Test
    fun edgesAreTheAverageOfTheTopAndBottomBands() {
        val yellow = Color(0xFFE8C547)
        val navy = Color(0xFF1B2A4A)
        val halves = image { _, y -> if (y < size / 2) yellow else navy }

        val colors = assertNotNull(ArtworkPalette.from(halves, size, size))

        assertClose(yellow, colors.top)
        assertClose(navy, colors.bottom)
    }

    @Test
    fun theAccentFollowsTheLargestColorfulAreaNotTheMostSaturatedPixel() {
        val blue = Color(0xFF2F6FB0)
        val orange = Color(0xFFFF6A00) // more saturated, but a small spot
        val pixels = image { x, y -> if (x < 6 && y < 6) orange else blue }

        val accent = assertNotNull(ArtworkPalette.from(pixels, size, size)?.accent)

        assertTrue(accent.hueDegrees() in 195f..225f, "accent hue ${accent.hueDegrees()} is not blue")
    }

    @Test
    fun aGreyscaleCoverHasNoAccent() {
        val pixels = image { x, _ -> if (x % 2 == 0) Color(0xFF202020) else Color(0xFFBDBDBD) }

        assertNull(ArtworkPalette.from(pixels, size, size)?.accent)
    }

    @Test
    fun theDeepColorAlwaysCarriesWhiteText() {
        val pastels = listOf(Color(0xFFFFF4C2), Color(0xFFE6F7FF), Color.White, Color(0xFFFFC1E3))
        for (pastel in pastels) {
            val deep = assertNotNull(ArtworkPalette.from(image { _, _ -> pastel }, size, size)).deep

            assertTrue(
                contrast(Color.White, deep) >= 4.5f,
                "white on $deep for $pastel: ${contrast(Color.White, deep)}",
            )
        }
    }

    @Test
    fun theDeepTopColorAlsoCarriesWhiteText() {
        val pale = Color(0xFFFFF4C2)
        val navy = Color(0xFF1B2A4A)
        val colors = assertNotNull(ArtworkPalette.from(image { _, y -> if (y < size / 2) pale else navy }, size, size))

        assertTrue(contrast(Color.White, colors.deepTop) >= 4.5f, "white on ${colors.deepTop}")
    }

    @Test
    fun aDarkOrGreyBorderGivesWayToTheCoversMainColor() {
        val red = Color(0xFFD62828)
        // A black frame around a red picture: the edges alone would give a near-black tint.
        val framed = image { x, y -> if (x in 6..33 && y in 6..33) red else Color(0xFF080808) }

        val colors = assertNotNull(ArtworkPalette.from(framed, size, size))

        for (tint in listOf(colors.deep, colors.deepTop)) {
            assertTrue(abs(tint.hueDegrees() - red.hueDegrees()) < 12f, "tint $tint is not red")
            assertTrue(contrast(Color.White, tint) >= 4.5f, "white on $tint")
            assertTrue(tint.red > tint.green + 0.08f, "tint $tint is too dull")
        }
    }

    @Test
    fun aColorfulBorderIsKept() {
        val teal = Color(0xFF2A9D8F)
        val orange = Color(0xFFE76F51)
        // Teal bands at the top and bottom (the edges the palette reads), orange in between.
        val banded = image { _, y -> if (y < 4 || y > 35) teal else orange }

        val colors = assertNotNull(ArtworkPalette.from(banded, size, size))

        assertTrue(
            abs(colors.deep.hueDegrees() - teal.hueDegrees()) < 12f,
            "deep ${colors.deep} should follow the edge",
        )
    }

    @Test
    fun theDeepColorKeepsTheHueOfTheBottomEdge() {
        val teal = Color(0xFF2A9D8F)
        val deep = assertNotNull(ArtworkPalette.from(image { _, _ -> teal }, size, size)).deep

        assertTrue(
            abs(deep.hueDegrees() - teal.hueDegrees()) < 12f,
            "deep ${deep.hueDegrees()} vs ${teal.hueDegrees()}",
        )
    }

    @Test
    fun anEmptyImageGivesNothing() {
        assertNull(ArtworkPalette.from(IntArray(0), 0, 0))
    }

    @Test
    fun contrastFollowsWcag() {
        assertEquals(21f, contrast(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrast(Color.Gray, Color.Gray), 0.01f)
    }

    @Test
    fun theCacheEvictsTheLeastRecentlyUsedEntry() {
        val cache = LruCache<String, Int>(capacity = 2)
        cache["a"] = 1
        cache["b"] = 2
        cache["a"] // a is now the most recent
        cache["c"] = 3

        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
    }
}
