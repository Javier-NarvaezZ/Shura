package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion

/**
 * How the bottom chrome (mini-player and navigation bar) paints its background. Solid tint today; a frosted glass
 * style can be added here after measuring it with real covers (PLAN.md), without touching the screens.
 */
sealed interface ChromeStyle {
    data object Solid : ChromeStyle
}

/** The single place where bottom-chrome backgrounds are drawn. [tint] is read at draw time. */
@Composable
fun ChromeSurface(
    tint: () -> Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    style: ChromeStyle = ChromeStyle.Solid,
    content: @Composable () -> Unit,
) {
    val surface =
        when (style) {
            ChromeStyle.Solid -> {
                Modifier.clip(shape).drawWithContent {
                    drawRect(tint())
                    drawContent()
                }
            }
        }
    Box(modifier.then(surface)) { content() }
}
