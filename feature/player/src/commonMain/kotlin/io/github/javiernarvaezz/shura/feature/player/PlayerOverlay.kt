package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.artwork.rememberArtworkTint
import io.github.javiernarvaezz.shura.core.ui.components.LikeAction
import io.github.javiernarvaezz.shura.core.ui.components.PlayPauseButton
import io.github.javiernarvaezz.shura.core.ui.components.Scrubber
import io.github.javiernarvaezz.shura.core.ui.components.TintActionButton
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.text.toClock
import io.github.javiernarvaezz.shura.core.ui.theme.OnTintTheme
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.player.resources.Res
import io.github.javiernarvaezz.shura.feature.player.resources.close_player
import io.github.javiernarvaezz.shura.feature.player.resources.end_of_queue
import io.github.javiernarvaezz.shura.feature.player.resources.hide_lyrics
import io.github.javiernarvaezz.shura.feature.player.resources.hide_queue
import io.github.javiernarvaezz.shura.feature.player.resources.like
import io.github.javiernarvaezz.shura.feature.player.resources.next
import io.github.javiernarvaezz.shura.feature.player.resources.pause
import io.github.javiernarvaezz.shura.feature.player.resources.play
import io.github.javiernarvaezz.shura.feature.player.resources.previous
import io.github.javiernarvaezz.shura.feature.player.resources.removed_from_queue
import io.github.javiernarvaezz.shura.feature.player.resources.repeat_all
import io.github.javiernarvaezz.shura.feature.player.resources.repeat_off
import io.github.javiernarvaezz.shura.feature.player.resources.repeat_one
import io.github.javiernarvaezz.shura.feature.player.resources.retry
import io.github.javiernarvaezz.shura.feature.player.resources.show_lyrics
import io.github.javiernarvaezz.shura.feature.player.resources.show_queue
import io.github.javiernarvaezz.shura.feature.player.resources.shuffle_off
import io.github.javiernarvaezz.shura.feature.player.resources.shuffle_on
import io.github.javiernarvaezz.shura.feature.player.resources.undo
import io.github.javiernarvaezz.shura.feature.player.resources.unlike
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The full player, above everything. Composed while there is a song, but drawn only while
 * [PlayerSheetState.isShown]. A layer, not a destination: the page underneath stays alive, so dragging the player
 * down reveals the real content.
 *
 * Motion (driven by [PlayerSheetState.fraction], read only in layer lambdas): the cover flies between the
 * mini-player and its slot here; the background fades in, and the rest of the body arrives late and leaves early.
 */
@Composable
fun PlayerOverlay(
    model: PlayerModel,
    sheet: PlayerSheetState,
    modifier: Modifier = Modifier,
    links: PlayerLinks = PlayerLinks.None,
    lyrics: (@Composable (Modifier) -> Unit)? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val scope = rememberCoroutineScope()
    val tint = rememberArtworkTint(song.thumbnailUrl)
    val background = remember { TintBackground() }
    Box(
        modifier
            .fillMaxSize()
            .placedOnlyWhenShown(sheet)
            .draggable(
                state = rememberDraggableState { delta -> sheet.dragBy(-delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> sheet.settle(-velocity, scope) },
            ),
    ) {
        // Background: the cover's colors continued into the screen (top edge to bottom edge) with a soft glow of its
        // main color. One full-screen fade without an offscreen buffer; the tint is read at draw time.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = sheet.fraction
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.drawBehind { with(background) { drawTint(tint) } },
        )
        OnTintTheme {
            PlayerBody(
                song = song,
                model = model,
                sheet = sheet,
                lyrics = lyrics,
                onClose = { sheet.close(scope) },
                links =
                    PlayerLinks(
                        onArtist = {
                            sheet.close(scope)
                            links.onArtist(it)
                        },
                        onAlbum = {
                            sheet.close(scope)
                            links.onAlbum(it)
                        },
                    ),
                modifier =
                    Modifier.graphicsLayer {
                        val f = sheet.fraction
                        alpha = ((f - BODY_FADE_START) / (1f - BODY_FADE_START)).coerceIn(0f, 1f)
                        translationY = (1f - f) * size.height * BODY_DRIFT
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            )
        }
        FlyingCover(song, sheet)
    }
}

/**
 * Keeps the player composed (so opening it composes nothing) but unplaced while it is closed: unplaced content is
 * neither drawn nor hit by touches, so the app underneath works normally. Only placement changes on open/close.
 */
private fun Modifier.placedOnlyWhenShown(sheet: PlayerSheetState): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (sheet.isShown) placeable.place(0, 0)
        }
    }

/** The one visible cover while the player is out: moves and scales from the mini cover to the player slot. */
@Composable
private fun FlyingCover(
    song: Song,
    sheet: PlayerSheetState,
) {
    val density = LocalDensity.current
    val thumbRadius = with(density) { Shura.shapes.thumbnailRadius.toPx() }
    val artworkRadius = with(density) { Shura.shapes.artworkRadius.toPx() }
    val elevation = with(density) { COVER_ELEVATION.toPx() }
    // The slot moves with the body while it animates: read it only in layout and layer lambdas, so the flight
    // never recomposes.
    Box(
        Modifier
            .layout { measurable, _ ->
                val target = sheet.playerCover
                val w = target.width.roundToInt()
                val h = target.height.roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { if (w > 0) placeable.place(0, 0) }
            }.graphicsLayer {
                val f = sheet.fraction
                val start = sheet.miniCover
                val target = sheet.playerCover
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
                alpha = if (sheet.mode == PlayerMode.Cover) 1f else 0f
                // Only at rest: a shadow that changes every frame of the flight is costly to render.
                shadowElevation = if (f >= 1f) elevation else 0f
            },
    ) {
        Artwork(
            song.thumbnailUrl,
            sizePx = playerCoverPx(),
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
    lyrics: (@Composable (Modifier) -> Unit)?,
    onClose: () -> Unit,
    links: PlayerLinks,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val onRemoved = rememberUndoRemove(snackbars, model)
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Side padding per section: the queue's rows bring their own.
            val side = Modifier.padding(horizontal = Shura.spacing.xl)
            TopRow(sheet, hasLyrics = lyrics != null, onClose, side)
            // Lyrics mode is only reachable through its button, which shows only when there are lyrics.
            when (sheet.mode) {
                PlayerMode.Queue -> {
                    PlayerQueue(model, state.canReorder, onRemoved, Modifier.weight(1f))
                }

                PlayerMode.Lyrics -> {
                    lyrics?.invoke(Modifier.weight(1f))
                }

                PlayerMode.Cover -> {
                    CoverAndTitle(song, sheet, links, rememberLike(model, song), side.weight(1f))
                }
            }
            Spacer(Modifier.height(Shura.spacing.l))
            val error = state.error
            Column(side) {
                if (error != null) {
                    ErrorRow(stringResource(playbackErrorMessage(error)), model::retry)
                } else {
                    if (state.queueEnded) {
                        Text(
                            stringResource(Res.string.end_of_queue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Shura.colors.textMuted,
                            modifier = Modifier.padding(bottom = Shura.spacing.s),
                        )
                    }
                    // Hidden: no progress redraws and no time updates.
                    Progress(model, playing = state.showsPause && sheet.isShown)
                }
                Spacer(Modifier.height(Shura.spacing.m))
                Controls(model, state)
            }
            Spacer(Modifier.height(Shura.spacing.l))
        }
        // At the top, under the close, lyrics and queue buttons: at the bottom it would cover the playback controls.
        SnackbarHost(snackbars, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = SNACKBAR_TOP))
    }
}

/** Removing a song from the queue shows "Deshacer" for a moment; one message at a time. */
@Composable
private fun rememberUndoRemove(
    snackbars: SnackbarHostState,
    model: PlayerModel,
): (RemovedSong) -> Unit {
    val scope = rememberCoroutineScope()
    val removedLabel = stringResource(Res.string.removed_from_queue)
    val undoLabel = stringResource(Res.string.undo)
    return { removed ->
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(removedLabel, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) model.queue.undoRemove(removed)
        }
    }
}

@Composable
private fun TopRow(
    sheet: PlayerSheetState,
    hasLyrics: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(top = Shura.spacing.s), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(ShuraIcons.Collapse, stringResource(Res.string.close_player), tint = Shura.colors.text)
        }
        Spacer(Modifier.weight(1f))
        if (hasLyrics) {
            ModeButton(sheet, PlayerMode.Lyrics, ShuraIcons.Lyrics, Res.string.show_lyrics, Res.string.hide_lyrics)
        }
        ModeButton(sheet, PlayerMode.Queue, ShuraIcons.Queue, Res.string.show_queue, Res.string.hide_queue)
    }
}

/** Shows [target] in place of the cover, or the cover again; bright while [target] is shown. */
@Composable
private fun ModeButton(
    sheet: PlayerSheetState,
    target: PlayerMode,
    icon: ImageVector,
    showLabel: StringResource,
    hideLabel: StringResource,
) {
    val shown = sheet.mode == target
    IconButton(onClick = { sheet.toggle(target) }) {
        Icon(
            icon,
            stringResource(if (shown) hideLabel else showLabel),
            tint = if (shown) Shura.colors.text else Shura.colors.textMuted,
        )
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
        Spacer(Modifier.height(Shura.spacing.s))
        TintActionButton(stringResource(Res.string.retry), onRetry)
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
private val SNACKBAR_TOP = 56.dp
