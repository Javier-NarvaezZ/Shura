package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.artwork.rememberArtworkTint
import io.github.javiernarvaezz.shura.core.ui.components.ChromeSurface
import io.github.javiernarvaezz.shura.core.ui.components.PlayPauseButton
import io.github.javiernarvaezz.shura.core.ui.components.progressStepMs
import io.github.javiernarvaezz.shura.core.ui.components.rememberProgressTick
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.OnTintTheme
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion
import io.github.javiernarvaezz.shura.feature.player.resources.Res
import io.github.javiernarvaezz.shura.feature.player.resources.open_player
import io.github.javiernarvaezz.shura.feature.player.resources.pause
import io.github.javiernarvaezz.shura.feature.player.resources.play
import io.github.javiernarvaezz.shura.feature.player.resources.retry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * The mini-player above the navigation bar: cover, title and artist (or the error), play/pause (or retry) and a
 * thin progress line. Tap or drag it up to open the player. Its cover hides while the flying cover is out.
 */
@Composable
fun MiniPlayer(
    model: PlayerModel,
    sheet: PlayerSheetState,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val scope = rememberCoroutineScope()
    // Tinted by the cover; the fade between covers only redraws (the tint is read at draw time).
    val tint = rememberArtworkTint(song.thumbnailUrl)
    // Redraws only as the line moves by a pixel, and not at all while the player covers the mini-player.
    val lineWidth = remember { floatArrayOf(0f) }
    val tick =
        rememberProgressTick(running = state.showsPause && !sheet.isShown) {
            progressStepMs(model.progress().duration?.inWholeMilliseconds, lineWidth[0])
        }
    val openLabel = stringResource(Res.string.open_player)
    val swipe = remember { SkipSwipe() }
    ChromeSurface(
        tint = { tint.deep },
        shape = Shura.shapes.miniPlayer,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Shura.spacing.s)
                .height(MINI_HEIGHT)
                .miniGestures(openLabel, sheet, swipe, model, scope),
    ) {
        OnTintTheme {
            Row(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { swipe.widthPx = it.width.toFloat() }
                    .graphicsLayer {
                        translationX = swipe.offset
                        alpha = 1f - (abs(swipe.offset) / swipe.widthPx).coerceIn(0f, 1f) * SWIPE_FADE
                    }.padding(horizontal = Shura.spacing.s)
                    .drawBehind {
                        tick.value
                        lineWidth[0] = size.width
                        val y = size.height - LINE_HEIGHT.toPx() / 2
                        val end = size.width * model.progress().fraction
                        drawLine(Color.White, Offset(0f, y), Offset(end, y), LINE_HEIGHT.toPx())
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
            ) {
                Artwork(
                    song.thumbnailUrl,
                    sizePx = PLAYER_COVER_PX,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(MINI_COVER)
                            .onGloballyPositioned { sheet.miniCover = it.boundsInRoot() }
                            .graphicsLayer { alpha = if (sheet.fraction > 0f) 0f else 1f },
                )
                MiniText(song, state.error, Modifier.weight(1f))
                MiniAction(model, state)
            }
        }
    }
}

@Composable
private fun MiniText(
    song: Song,
    error: PlaybackError?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            color = Shura.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (error != null) stringResource(playbackErrorMessage(error)) else song.artistNames,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) Shura.colors.error else Shura.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Play/pause, or retry after a failure. */
@Composable
private fun MiniAction(
    model: PlayerModel,
    state: PlayerUiState,
) {
    if (state.error != null) {
        IconButton(onClick = model::retry) {
            Icon(ShuraIcons.Retry, contentDescription = stringResource(Res.string.retry), tint = Shura.colors.text)
        }
    } else {
        PlayPauseButton(
            isPlaying = state.showsPause,
            onClick = model::togglePlay,
            playLabel = stringResource(Res.string.play),
            pauseLabel = stringResource(Res.string.pause),
            size = MINI_BUTTON,
        )
    }
}

/** Tap or drag up to open the player; swipe sideways to skip. */
@Composable
private fun Modifier.miniGestures(
    openLabel: String,
    sheet: PlayerSheetState,
    swipe: SkipSwipe,
    model: PlayerModel,
    scope: CoroutineScope,
): Modifier =
    this
        .clickable(onClickLabel = openLabel, role = Role.Button) { sheet.open(scope) }
        .draggable(
            state = rememberDraggableState { delta -> sheet.dragBy(-delta) },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity -> sheet.settle(-velocity, scope) },
        ).draggable(
            state = rememberDraggableState { delta -> swipe.drag(delta) },
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity -> swipe.release(velocity, scope, model::next, model::previous) },
        )

/**
 * Swipe the mini-player sideways to skip: left plays the next song, right the previous one. The content follows the
 * finger; past a third of the width (or on a fling) it leaves on that side and the new song comes in from the
 * other, otherwise it springs back. Read [offset] only in layer lambdas.
 */
@Stable
private class SkipSwipe {
    var offset by mutableFloatStateOf(0f)
        private set

    var widthPx = 1f

    private var job: Job? = null

    fun drag(delta: Float) {
        job?.cancel()
        offset += delta
    }

    fun release(
        velocity: Float,
        scope: CoroutineScope,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
    ) {
        val threshold = widthPx * SKIP_SHARE
        val direction =
            when {
                offset < -threshold || velocity < -SKIP_FLING -> -1
                offset > threshold || velocity > SKIP_FLING -> 1
                else -> 0
            }
        job?.cancel()
        job =
            scope.launch {
                if (direction == 0) {
                    animate(offset, 0f, animationSpec = ShuraMotion.settle()) { v, _ -> offset = v }
                    return@launch
                }
                animate(offset, direction * widthPx, animationSpec = ShuraMotion.quick()) { v, _ -> offset = v }
                if (direction < 0) onNext() else onPrevious()
                offset = -direction * widthPx * ENTER_SHARE
                animate(offset, 0f, animationSpec = ShuraMotion.quick()) { v, _ -> offset = v }
            }
    }
}

private const val SKIP_SHARE = 0.33f
private const val SKIP_FLING = 1_000f
private const val ENTER_SHARE = 0.3f
private const val SWIPE_FADE = 0.6f

private val MINI_HEIGHT = 64.dp
private val MINI_COVER = 44.dp
private val MINI_BUTTON = 40.dp
private val LINE_HEIGHT = 2.dp

/** One size for the mini and the player cover, so both share Coil's cache and the flight never reloads. */
internal const val PLAYER_COVER_PX = 544
