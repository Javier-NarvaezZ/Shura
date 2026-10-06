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

/** Round play/pause control; the icon cross-fades between states. */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    playLabel: String,
    pauseLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = Shura.colors.accent,
    content: Color = Shura.colors.onAccent,
) {
    Box(
        modifier
            .size(size)
            .clip(Shura.shapes.pill)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(isPlaying, animationSpec = ShuraMotion.quick()) { playing ->
            Icon(
                if (playing) ShuraIcons.Pause else ShuraIcons.Play,
                contentDescription = if (playing) pauseLabel else playLabel,
                tint = content,
                modifier = Modifier.size(size * ICON_FRACTION),
            )
        }
    }
}

/** Loading placeholder: a light sweep drawn only in the draw phase, so it never recomposes. */
@Composable
fun Modifier.shimmer(base: Color = Shura.colors.surface2): Modifier {
    val sweep by rememberInfiniteTransition()
        .animateFloat(-1f, 2f, infiniteRepeatable(tween(SHIMMER_MS)))
    return drawWithContent {
        drawRect(base)
        val x = size.width * sweep
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = SHIMMER_ALPHA), Color.Transparent),
                start = Offset(x - size.width / 2, 0f),
                end = Offset(x, size.height),
            ),
        )
    }
}

private const val ICON_FRACTION = 0.55f
private const val SHIMMER_MS = 1_300
private const val SHIMMER_ALPHA = 0.08f
