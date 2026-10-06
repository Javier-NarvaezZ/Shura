package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.components.PlayPauseButton
import io.github.javiernarvaezz.shura.core.ui.components.Scrubber
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.text.toClock
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.player.resources.Res
import io.github.javiernarvaezz.shura.feature.player.resources.close_player
import io.github.javiernarvaezz.shura.feature.player.resources.next
import io.github.javiernarvaezz.shura.feature.player.resources.pause
import io.github.javiernarvaezz.shura.feature.player.resources.play
import io.github.javiernarvaezz.shura.feature.player.resources.previous
import io.github.javiernarvaezz.shura.feature.player.resources.repeat_all
import io.github.javiernarvaezz.shura.feature.player.resources.repeat_off
import io.github.javiernarvaezz.shura.feature.player.resources.repeat_one
import io.github.javiernarvaezz.shura.feature.player.resources.retry
import io.github.javiernarvaezz.shura.feature.player.resources.shuffle_off
import io.github.javiernarvaezz.shura.feature.player.resources.shuffle_on
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The full player, drawn above everything while [PlayerSheetState.isShown]. A layer, not a destination: the page
 * underneath stays alive, so dragging the player down reveals the real content.
 *
 * Motion (driven by [PlayerSheetState.fraction], read only in layer lambdas): the cover flies between the
 * mini-player and its slot here; the background fades in, and the rest of the body arrives late and leaves early.
 */
@Composable
fun PlayerOverlay(
    model: PlayerModel,
    sheet: PlayerSheetState,
    modifier: Modifier = Modifier,
) {
    if (!sheet.isShown) return
    val state by model.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val scope = rememberCoroutineScope()
    val background = Shura.colors.background
    Box(
        modifier
            .fillMaxSize()
            .draggable(
                state = rememberDraggableState { delta -> sheet.dragBy(-delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> sheet.settle(-velocity, scope) },
            ),
    ) {
        // Background: one full-screen fade without an offscreen buffer.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = sheet.fraction
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.background(background),
        )
        PlayerBody(
            song = song,
            model = model,
            sheet = sheet,
            onClose = { sheet.close(scope) },
            modifier =
                Modifier.graphicsLayer {
                    val f = sheet.fraction
                    alpha = ((f - BODY_FADE_START) / (1f - BODY_FADE_START)).coerceIn(0f, 1f)
                    translationY = (1f - f) * size.height * BODY_DRIFT
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
        FlyingCover(song, sheet)
    }
}

/** The one visible cover while the player is out: moves and scales from the mini cover to the player slot. */
@Composable
private fun FlyingCover(
    song: Song,
    sheet: PlayerSheetState,
) {
    val density = LocalDensity.current
    val target = sheet.playerCover
    if (target.width <= 0f) return
    val thumbRadius = with(density) { Shura.shapes.thumbnailRadius.toPx() }
    val artworkRadius = with(density) { Shura.shapes.artworkRadius.toPx() }
    val elevation = with(density) { COVER_ELEVATION.toPx() }
    Box(
        Modifier
            .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
            .graphicsLayer {
                val f = sheet.fraction
                val start = sheet.miniCover
                val scale = lerp(if (target.width > 0f) start.width / target.width else 1f, 1f, f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = lerp(start.left, target.left, f)
                translationY = lerp(start.top, target.top, f)
                scaleX = scale
                scaleY = scale
                // Corner radius in the cover's own (unscaled) pixels, so it looks right at every scale.
                shape =
                    RoundedCornerShape(
                        lerp(thumbRadius / scale.coerceAtLeast(MIN_SCALE), artworkRadius, f),
                    )
                clip = true
                shadowElevation = elevation * f
            },
    ) {
        Artwork(
            song.thumbnailUrl,
            sizePx = PLAYER_COVER_PX,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
        )
    }
}

@Composable
private fun PlayerBody(
    song: Song,
    model: PlayerModel,
    sheet: PlayerSheetState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Shura.spacing.xl),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Shura.spacing.s), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(ShuraIcons.Collapse, stringResource(Res.string.close_player), tint = Shura.colors.text)
            }
        }
        Spacer(Modifier.height(Shura.spacing.l))
        // The cover slot: the flying cover lands here.
        Spacer(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { sheet.playerCover = it.boundsInRoot() },
        )
        Spacer(Modifier.height(Shura.spacing.xl))
        Text(
            song.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Shura.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artistNames,
            style = MaterialTheme.typography.bodyLarge,
            color = Shura.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Shura.spacing.l))
        val error = state.error
        if (error != null) {
            ErrorRow(stringResource(playbackErrorMessage(error)), model::retry)
        } else {
            Progress(model, playing = state.showsPause)
        }
        Spacer(Modifier.height(Shura.spacing.m))
        Controls(model, state)
    }
}

@Composable
private fun Progress(
    model: PlayerModel,
    playing: Boolean,
) {
    val color = Shura.colors.text
    Scrubber(
        fraction = { model.progress().fraction },
        bufferedFraction = { model.progress().bufferedFraction },
        playing = playing,
        durationMs = { model.progress().duration?.inWholeMilliseconds },
        onSeek = model::seekToFraction,
        color = color,
    )
    // The times change once per second at most; they are the only part that recomposes while playing.
    var progress by remember { mutableStateOf(model.progress()) }
    LaunchedEffect(playing) {
        do {
            progress = model.progress()
            delay(TIME_REFRESH_MS)
        } while (playing)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(progress.position.toClock(), style = MaterialTheme.typography.labelMedium, color = Shura.colors.textMuted)
        Text(
            progress.duration?.toClock().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = Shura.colors.textMuted,
        )
    }
}

@Composable
private fun ErrorRow(
    message: String,
    onRetry: () -> Unit,
) {
    Column {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Shura.colors.error)
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
private fun Controls(
    model: PlayerModel,
    state: PlayerUiState,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = model::toggleShuffle) {
            Icon(
                ShuraIcons.Shuffle,
                stringResource(if (state.shuffle) Res.string.shuffle_on else Res.string.shuffle_off),
                tint = if (state.shuffle) Shura.colors.text else Shura.colors.textMuted.copy(alpha = OFF_ALPHA),
            )
        }
        IconButton(onClick = model::previous, modifier = Modifier.size(SKIP_TOUCH)) {
            Icon(
                ShuraIcons.SkipPrevious,
                stringResource(Res.string.previous),
                tint = Shura.colors.text,
                modifier = Modifier.size(SKIP_ICON),
            )
        }
        PlayPauseButton(
            isPlaying = state.showsPause,
            onClick = model::togglePlay,
            playLabel = stringResource(Res.string.play),
            pauseLabel = stringResource(Res.string.pause),
            size = PLAY_SIZE,
        )
        IconButton(onClick = model::next, modifier = Modifier.size(SKIP_TOUCH)) {
            Icon(
                ShuraIcons.SkipNext,
                stringResource(Res.string.next),
                tint = Shura.colors.text,
                modifier = Modifier.size(SKIP_ICON),
            )
        }
        IconButton(onClick = model::cycleRepeat) {
            val (icon, label) =
                when (state.repeat) {
                    RepeatMode.Off -> ShuraIcons.Repeat to Res.string.repeat_off
                    RepeatMode.All -> ShuraIcons.Repeat to Res.string.repeat_all
                    RepeatMode.One -> ShuraIcons.RepeatOne to Res.string.repeat_one
                }
            Icon(
                icon,
                stringResource(label),
                tint =
                    if (state.repeat !=
                        RepeatMode.Off
                    ) {
                        Shura.colors.text
                    } else {
                        Shura.colors.textMuted.copy(alpha = OFF_ALPHA)
                    },
            )
        }
    }
}

private const val BODY_FADE_START = 0.45f
private const val BODY_DRIFT = 0.08f
private const val MIN_SCALE = 0.01f
private const val OFF_ALPHA = 0.6f
private const val TIME_REFRESH_MS = 500L
private val COVER_ELEVATION = 16.dp
private val PLAY_SIZE = 72.dp
private val SKIP_TOUCH = 56.dp
private val SKIP_ICON = 36.dp
