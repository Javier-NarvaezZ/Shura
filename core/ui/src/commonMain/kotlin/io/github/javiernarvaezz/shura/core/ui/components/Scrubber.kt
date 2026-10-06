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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion
import kotlinx.coroutines.delay

/**
 * How often a progress bar [widthPx] wide must redraw to move by one pixel over a track of [durationMs]: no
 * faster than a frame, and at least once per second (also when the duration or width is unknown).
 */
fun progressStepMs(
    durationMs: Long?,
    widthPx: Float,
): Long {
    if (durationMs == null || durationMs <= 0 || widthPx <= 0f) return MAX_STEP_MS
    return (durationMs / widthPx).toLong().coerceIn(MIN_STEP_MS, MAX_STEP_MS)
}

/**
 * A state that changes as often as a progress bar actually moves (see [progressStepMs]) while [running]. Read it
 * only in a draw lambda: a bar then redraws a few times per second instead of every frame, so the screen is not
 * repainted 60 times per second for a line that moves one pixel at a time.
 */
@Composable
fun rememberProgressTick(
    running: Boolean,
    stepMs: () -> Long,
): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    val step by rememberUpdatedState(stepMs)
    LaunchedEffect(running) {
        while (running) {
            tick.longValue++
            delay(step())
        }
    }
    return tick
}

/**
 * A quiet progress bar: thin and without a thumb at rest, it grows and shows a thumb under the finger. Tap or drag
 * to choose a position; [onSeek] gets the fraction (0..1) on release. [fraction] and [bufferedFraction] are read
 * at draw time and redrawn while [playing] as often as the bar moves by a pixel (see [rememberProgressTick]).
 */
@Composable
fun Scrubber(
    fraction: () -> Float,
    bufferedFraction: () -> Float,
    playing: Boolean,
    durationMs: () -> Long?,
    onSeek: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val width = remember { floatArrayOf(0f) }
    val tick = rememberProgressTick(playing) { progressStepMs(durationMs(), width[0]) }
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
                tick.value // Redraw as the played part moves.
                width[0] = size.width
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

private const val MIN_STEP_MS = 16L
private const val MAX_STEP_MS = 1_000L
