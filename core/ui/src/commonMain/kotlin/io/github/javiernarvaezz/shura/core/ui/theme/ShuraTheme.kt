package io.github.javiernarvaezz.shura.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.javiernarvaezz.shura.core.ui.resources.Res
import io.github.javiernarvaezz.shura.core.ui.resources.outfit_bold
import io.github.javiernarvaezz.shura.core.ui.resources.outfit_semibold
import org.jetbrains.compose.resources.Font

/**
 * App-wide theme: the Sunloop tokens of [ShuraTokens.kt] (dark only), also mapped onto Material 3 so stock
 * components match. Large titles use Outfit, the brand's typeface.
 */
@Composable
fun ShuraTheme(content: @Composable () -> Unit) {
    val typography = rememberShuraTypography()
    CompositionLocalProvider(LocalShuraColors provides ShuraDarkColors) {
        MaterialTheme(colorScheme = ShuraDarkColors.toMaterial(), typography = typography, content = content)
    }
}

/**
 * Content on cover-tinted chrome (the mini-player and the player): tints are always dark, so text and controls are
 * white. Stock Material components inside follow it too.
 */
@Composable
fun OnTintTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShuraColors provides ShuraOnTintColors) {
        MaterialTheme(
            colorScheme = ShuraOnTintColors.toMaterial(),
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

/** The type scale with Outfit (the official static 600 and 700 cuts, bundled) for the large titles. */
@Composable
private fun rememberShuraTypography(): Typography {
    val semiBold = Font(Res.font.outfit_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.outfit_bold, FontWeight.Bold)
    return remember(semiBold, bold) { shuraTypography(display = FontFamily(semiBold, bold)) }
}

/** Token access for screens: `Shura.colors.textMuted`, `Shura.spacing.gutter`, `Shura.shapes.artwork`. */
object Shura {
    val colors: ShuraColors
        @Composable @ReadOnlyComposable
        get() = LocalShuraColors.current

    val shapes: ShuraShapes
        @Composable @ReadOnlyComposable
        get() = LocalShuraShapes.current

    val spacing: ShuraSpacing
        @Composable @ReadOnlyComposable
        get() = LocalShuraSpacing.current
}

private fun ShuraColors.toMaterial() =
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = textMuted,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface1,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface3,
        outline = textMuted,
        outlineVariant = line,
        error = error,
    )
