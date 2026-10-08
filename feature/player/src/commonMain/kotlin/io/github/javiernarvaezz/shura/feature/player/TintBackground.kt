package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.javiernarvaezz.shura.core.ui.artwork.ArtworkTint
import kotlin.math.roundToInt

/**
 * The player's background (the cover's colors continued down the screen, with a soft glow of its main color),
 * rendered once per set of colors into a quarter-resolution bitmap and drawn scaled with bilinear filtering. Two
 * full-screen gradients per frame were costly on the test phone's GPU while the player opened or closed; a small
 * cached bitmap is one scaled texture. The bitmap is redrawn only when the colors change (a new cover, and while
 * its colors fade in). Use from draw lambdas only.
 */
internal class TintBackground {
    private var bitmap: ImageBitmap? = null
    private var key: List<Any>? = null

    fun DrawScope.drawTint(tint: ArtworkTint) {
        val w = (size.width / SCALE).roundToInt().coerceAtLeast(1)
        val h = (size.height / SCALE).roundToInt().coerceAtLeast(1)
        val newKey = listOf(w, h, tint.top, tint.deep, tint.glow)
        val image =
            bitmap?.takeIf { key == newKey } ?: render(w, h, tint).also {
                bitmap = it
                key = newKey
            }
        drawImage(
            image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            dstOffset = IntOffset.Zero,
            filterQuality = FilterQuality.Low,
        )
    }

    private fun DrawScope.render(
        w: Int,
        h: Int,
        tint: ArtworkTint,
    ): ImageBitmap {
        val image = bitmap?.takeIf { it.width == w && it.height == h } ?: ImageBitmap(w, h)
        val small = Size(w.toFloat(), h.toFloat())
        CanvasDrawScope().draw(this, layoutDirection, Canvas(image), small) {
            drawRect(Brush.verticalGradient(listOf(tint.top, tint.deep, lerp(tint.deep, Color.Black, DEPTH))))
            drawRect(
                Brush.radialGradient(
                    listOf(tint.glow.copy(alpha = GLOW_ALPHA), Color.Transparent),
                    center = Offset(small.width / 2, small.height * GLOW_Y),
                    radius = small.width,
                ),
            )
        }
        return image
    }

    private companion object {
        const val SCALE = 4f
        const val DEPTH = 0.35f
        const val GLOW_ALPHA = 0.35f
        const val GLOW_Y = 0.3f
    }
}
