package io.github.javiernarvaezz.shura.core.ui.artwork

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Colors taken from a cover, shared by everything tinted by what is playing.
 * - [top] and [bottom]: the cover's edges, so a backdrop can continue the image without a seam.
 * - [accent]: the cover's main color, or null for a greyscale cover (callers use the neutral accent).
 * - [deep]: the bottom edge darkened until white text on it reaches WCAG AA (4.5:1).
 * - [deepTop]: the same for the top edge, so a gradient from top to bottom carries white text everywhere.
 */
data class ArtworkColors(
    val top: Color,
    val bottom: Color,
    val accent: Color?,
    val deep: Color,
    val deepTop: Color,
)

object ArtworkPalette {
    /** From ARGB pixels (row by row) of a small copy of the cover; null for an empty image. */
    fun from(
        argb: IntArray,
        width: Int,
        height: Int,
    ): ArtworkColors? {
        if (width <= 0 || height <= 0 || argb.size < width * height) return null
        val band = max(1, (height * EDGE_BAND).roundToInt())
        val bottom = average(argb, width, height - band until height)
        val top = average(argb, width, 0 until band)
        return ArtworkColors(
            top = top,
            bottom = bottom,
            accent = accent(argb, width * height),
            deep = readableUnderWhite(lerp(bottom, Color.Black, DEEP_PULL)),
            deepTop = readableUnderWhite(lerp(top, Color.Black, DEEP_PULL)),
        )
    }

    private fun average(
        argb: IntArray,
        width: Int,
        rows: IntRange,
    ): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in rows) {
            for (x in 0 until width) {
                val p = argb[y * width + x]
                r += p.red()
                g += p.green()
                b += p.blue()
                n++
            }
        }
        return Color(red = (r / n).toInt(), green = (g / n).toInt(), blue = (b / n).toInt())
    }

    /**
     * A hue histogram where each pixel weighs by saturation and by how close it is to mid-lightness, so a large
     * colored area beats a small vivid spot and greys count for nothing.
     */
    private fun accent(
        argb: IntArray,
        pixels: Int,
    ): Color? {
        val weight = FloatArray(HUE_BINS)
        val sums = Array(HUE_BINS) { FloatArray(3) }
        for (i in 0 until pixels) {
            val p = argb[i]
            val r = p.red() / CHANNEL_MAX
            val g = p.green() / CHANNEL_MAX
            val b = p.blue() / CHANNEL_MAX
            val hsl = Hsl.of(r, g, b)
            if (hsl.s < MIN_SATURATION || hsl.l !in MIN_LIGHTNESS..MAX_LIGHTNESS) continue
            val w = hsl.s * (1 - abs(hsl.l - 0.5f) * 2)
            val bin = (hsl.h / 360f * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            weight[bin] += w
            sums[bin][0] += r * w
            sums[bin][1] += g * w
            sums[bin][2] += b * w
        }
        val best = weight.indices.maxBy { weight[it] }
        if (weight[best] < pixels * MIN_COLORFUL_SHARE) return null
        val w = weight[best]
        val mean = Hsl.of(sums[best][0] / w, sums[best][1] / w, sums[best][2] / w)
        return mean.copy(l = mean.l.coerceIn(ACCENT_LIGHTNESS)).toColor()
    }

    private fun readableUnderWhite(color: Color): Color {
        var c = color
        while (contrast(Color.White, c) < MIN_CONTRAST) c = lerp(c, Color.Black, DARKEN_STEP)
        return c
    }

    private const val EDGE_BAND = 0.08f
    private const val DEEP_PULL = 0.4f
    private const val DARKEN_STEP = 0.12f
    private const val MIN_CONTRAST = 4.5f
    private const val HUE_BINS = 36
    private const val MIN_SATURATION = 0.2f
    private const val MIN_LIGHTNESS = 0.12f
    private const val MAX_LIGHTNESS = 0.9f
    private const val MIN_COLORFUL_SHARE = 0.02f
    private val ACCENT_LIGHTNESS = 0.4f..0.65f
}

/** WCAG contrast ratio between two colors (1..21). */
fun contrast(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + WCAG_FLARE) / (minOf(la, lb) + WCAG_FLARE)
}

private const val WCAG_FLARE = 0.05f
private const val CHANNEL_MAX = 255f
private const val CHANNEL_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val DEGREES_PER_SECTOR = 60f
private const val HUE_SECTORS = 6f

private fun Int.red(): Int = this shr RED_SHIFT and CHANNEL_MASK

private fun Int.green(): Int = this shr GREEN_SHIFT and CHANNEL_MASK

private fun Int.blue(): Int = this and CHANNEL_MASK

private data class Hsl(
    val h: Float,
    val s: Float,
    val l: Float,
) {
    fun toColor(): Color = Color.hsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

    companion object {
        fun of(
            r: Float,
            g: Float,
            b: Float,
        ): Hsl {
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val d = max - min
            val l = (max + min) / 2
            if (d == 0f) return Hsl(0f, 0f, l)
            val s = d / (1 - abs(2 * l - 1))
            val h =
                when (max) {
                    r -> ((g - b) / d).mod(HUE_SECTORS)
                    g -> (b - r) / d + 2
                    else -> (r - g) / d + 4
                } * DEGREES_PER_SECTOR
            return Hsl(h, s, l)
        }
    }
}
