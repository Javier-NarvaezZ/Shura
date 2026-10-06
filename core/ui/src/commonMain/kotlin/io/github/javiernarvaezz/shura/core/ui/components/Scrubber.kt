package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion

/**
 * A state that changes on every frame while [running], to redraw something that moves continuously (a playback
 * position read on demand). Read it only in a draw lambda, so each frame redraws without recomposing.
 */
@Composable
fun rememberFrameTick(running: Boolean): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        while (running) withFrameNanos { tick.longValue = it }
    }
    return tick
}

/**
 * A quiet progress bar: thin and without a thumb at rest, it grows and shows a thumb under the finger. Tap or drag
 * to choose a position; [onSeek] gets the fraction (0..1) on release. [fraction] and [bufferedFraction] are read
 * at draw time; [tick] (see [rememberFrameTick]) redraws them while playing.
 */
@Composable
fun Scrubber(
    fraction: () -> Float,
    bufferedFraction: () -> Float,
    tick: State<Long>,
    onSeek: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val track by animateDpAsState(if (dragFraction != null) 8.dp else 4.dp, ShuraMotion.press())
    val thumb by animateDpAsState(if (dragFraction != null) 14.dp else 0.dp, ShuraMotion.press())
    Box(
        modifier
            .fillMaxWidth()
            .height(TOUCH_HEIGHT)
            .pointerInput(onSeek) {
                detectTapGestures(
                    onPress = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        if (tryAwaitRelease()) dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                )
            }.pointerInput(onSeek) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = {
                        change,
                        _,
                        ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            }.drawBehind {
                tick.value // Redraw every frame while playing.
                val played = dragFraction ?: fraction()
                val h = track.toPx()
                val top = (size.height - h) / 2
                val radius = CornerRadius(h / 2)
                drawRoundRect(color.copy(alpha = REST_ALPHA), Offset(0f, top), Size(size.width, h), radius)
                drawRoundRect(
                    color.copy(alpha = BUFFERED_ALPHA),
                    Offset(0f, top),
                    Size(size.width * bufferedFraction(), h),
                    radius,
                )
                drawRoundRect(color, Offset(0f, top), Size(size.width * played, h), radius)
                if (thumb > 0.dp) drawCircle(color, thumb.toPx() / 2, Offset(size.width * played, size.height / 2))
            },
    )
}

private val TOUCH_HEIGHT = 32.dp
private const val REST_ALPHA = 0.2f
private const val BUFFERED_ALPHA = 0.35f
