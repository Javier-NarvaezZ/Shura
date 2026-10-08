package io.github.javiernarvaezz.shura.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * App-wide theme: the neutral tokens of [ShuraTokens.kt], also mapped onto Material 3 so stock components match.
 * The brand (Phase 1B) changes the tokens, not this function.
 */
@Composable
fun ShuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) ShuraDarkColors else ShuraLightColors
    CompositionLocalProvider(LocalShuraColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = ShuraTypography,
            content = content,
        )
    }
}

/**
 * Content on cover-tinted chrome (the mini-player and the player): tints are always dark, so text and controls are
 * white in both light and dark mode. Stock Material components inside follow it too.
 */
@Composable
fun OnTintTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShuraColors provides ShuraOnTintColors) {
        MaterialTheme(colorScheme = ShuraOnTintColors.toMaterial(), typography = ShuraTypography, content = content)
    }
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
    if (isDark) {
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
    } else {
        lightColorScheme(
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
            surfaceContainerLowest = surface1,
            surfaceContainerLow = surface1,
            surfaceContainer = surface2,
            surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface3,
            outline = textMuted,
            outlineVariant = line,
            error = error,
        )
    }
