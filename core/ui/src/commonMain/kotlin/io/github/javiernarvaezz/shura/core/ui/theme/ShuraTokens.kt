package io.github.javiernarvaezz.shura.core.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Design tokens: the Sunloop brand (docs/brand/DESIGN.md). Dark only. Screens read tokens through [Shura] or
 * MaterialTheme, never hard-coded values.
 */

/** Color roles. The cover tints only the mini-player and the player; the rest of the app stays on these. */
@Immutable
data class ShuraColors(
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    /** The second color of the brand gradient (gold). */
    val accentGold: Color,
    val onAccent: Color,
    val error: Color,
) {
    /** The brand gradient, like the sun: coral at the bottom, gold at the top. Text on it uses [onAccent]. */
    val brandGradient: Brush get() = Brush.verticalGradient(listOf(accentGold, accent))
}

val ShuraDarkColors =
    ShuraColors(
        background = Color(0xFF120D0B),
        surface1 = Color(0xFF1E1714),
        surface2 = Color(0xFF2A201C),
        surface3 = Color(0xFF362A25),
        line = Color(0x1FFFF4EC),
        text = Color(0xFFFFF4EC),
        textMuted = Color(0xA8FFF4EC),
        accent = Color(0xFFFF5E3A),
        accentGold = Color(0xFFFFB547),
        onAccent = Color(0xFF120D0B),
        error = Color(0xFFFF8FA3),
    )

/** Content colors on cover-tinted chrome (see OnTintTheme): white on a dark tint. */
val ShuraOnTintColors =
    ShuraDarkColors.copy(
        background = Color.Transparent,
        surface1 = Color(0x14FFFFFF),
        surface2 = Color(0x1FFFFFFF),
        surface3 = Color(0x2EFFFFFF),
        line = Color(0x26FFFFFF),
        text = Color.White,
        textMuted = Color(0xB3FFFFFF),
        accent = Color.White,
        onAccent = Color(0xFF120D0B),
    )

/** Extra line height over the font size, in sp, when a style does not set its own. */
private const val DEFAULT_LEADING = 6

/** The body font: the system's. Large titles use the brand's display font (Outfit, see [shuraTypography]). */
val ShuraFont: FontFamily = FontFamily.Default

private fun style(
    size: Int,
    weight: FontWeight,
    tracking: Float = 0f,
    line: Int = size + DEFAULT_LEADING,
) = TextStyle(
    fontFamily = ShuraFont,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeight = line.sp,
)

/** The type scale, all in the body font; [shuraTypography] sets the large titles in the brand's display font. */
val ShuraTypography =
    Typography(
        displaySmall = style(32, FontWeight.Bold, tracking = -0.5f, line = 38),
        headlineMedium = style(26, FontWeight.Bold, tracking = -0.3f, line = 32),
        headlineSmall = style(22, FontWeight.Bold, tracking = -0.2f, line = 28),
        titleLarge = style(20, FontWeight.SemiBold, line = 26),
        titleMedium = style(16, FontWeight.SemiBold, line = 22),
        titleSmall = style(14, FontWeight.SemiBold, line = 20),
        bodyLarge = style(16, FontWeight.Normal, line = 22),
        bodyMedium = style(14, FontWeight.Normal, line = 20),
        bodySmall = style(12, FontWeight.Normal, line = 16),
        labelLarge = style(14, FontWeight.Medium, tracking = 0.1f, line = 20),
        labelMedium = style(12, FontWeight.Medium, tracking = 0.3f, line = 16),
        labelSmall = style(11, FontWeight.Medium, tracking = 0.5f, line = 14),
    )

/** [ShuraTypography] with its large titles (album and artist names, headings) set in [display]. */
fun shuraTypography(display: FontFamily): Typography =
    with(ShuraTypography) {
        copy(
            displaySmall = displaySmall.copy(fontFamily = display),
            headlineMedium = headlineMedium.copy(fontFamily = display),
            headlineSmall = headlineSmall.copy(fontFamily = display),
            titleLarge = titleLarge.copy(fontFamily = display),
        )
    }

@Immutable
data class ShuraShapes(
    val thumbnail: Shape = RoundedCornerShape(8.dp),
    val card: Shape = RoundedCornerShape(14.dp),
    val miniPlayer: Shape = RoundedCornerShape(16.dp),
    val artwork: Shape = RoundedCornerShape(22.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val pill: Shape = CircleShape,
    val thumbnailRadius: Dp = 8.dp,
    val artworkRadius: Dp = 22.dp,
)

@Immutable
data class ShuraSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Side padding of every screen. */
    val gutter: Dp = 16.dp,
    /** Minimum touch target. */
    val touch: Dp = 48.dp,
)

/** Springs for direct manipulation, short tweens for state changes, slow fades for ambient color. */
@Immutable
object ShuraMotion {
    const val QUICK_MS = 180
    const val OPEN_MS = 350
    const val CLOSE_MS = 280
    const val COLOR_MS = 600

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK_MS)

    fun <T> open(): FiniteAnimationSpec<T> = tween(OPEN_MS)

    fun <T> close(): FiniteAnimationSpec<T> = tween(CLOSE_MS)

    fun <T> color(): FiniteAnimationSpec<T> = tween(COLOR_MS)

    /** Release of a drag that did not commit: settles back without bouncing much. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Touch feedback (e.g. the scrubber growing under the finger). */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
}

val LocalShuraColors = staticCompositionLocalOf { ShuraDarkColors }
val LocalShuraShapes = staticCompositionLocalOf { ShuraShapes() }
val LocalShuraSpacing = staticCompositionLocalOf { ShuraSpacing() }
