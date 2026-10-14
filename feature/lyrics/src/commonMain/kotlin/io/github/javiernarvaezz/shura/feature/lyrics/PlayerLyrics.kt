package io.github.javiernarvaezz.shura.feature.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.lyrics.Lyrics
import io.github.javiernarvaezz.shura.core.ui.components.TintActionButton
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.lyrics.resources.Res
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_attribution
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_failed
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_instrumental
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_not_found
import io.github.javiernarvaezz.shura.feature.lyrics.resources.lyrics_not_synced
import io.github.javiernarvaezz.shura.feature.lyrics.resources.retry
import io.github.javiernarvaezz.shura.feature.lyrics.resources.search_again
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The lyrics as a mode of the player. Synced lyrics follow the music: the current line is bright and kept a third
 * of the way down; tap a line to jump there. A manual scroll pauses the following for a few seconds.
 */
@Composable
fun PlayerLyrics(
    model: LyricsModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        when (val shown = state) {
            LyricsUiState.Idle, LyricsUiState.Loading -> {
                LoadingLines()
            }

            is LyricsUiState.Synced -> {
                SyncedLyrics(shown.lyrics, model)
            }

            is LyricsUiState.Plain -> {
                PlainLyrics(shown.text)
            }

            LyricsUiState.Instrumental -> {
                Message(Res.string.lyrics_instrumental)
            }

            LyricsUiState.NotFound -> {
                MessageWithAction(Res.string.lyrics_not_found, Res.string.search_again, model::searchAgain)
            }

            is LyricsUiState.Failed -> {
                MessageWithAction(Res.string.lyrics_failed, Res.string.retry, model::retry)
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics.Synced,
    model: LyricsModel,
) {
    val playing by model.isPlaying.collectAsStateWithLifecycle()
    // Changes a few times per line at most; rows read it only in their layer, so a new line only redraws.
    val active = remember(lyrics) { mutableIntStateOf(lyrics.lineAt(model.position())) }
    LaunchedEffect(lyrics, playing) { followPlayback(lyrics, model, active, playing) }

    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(dragged) {
        if (dragged) {
            following = false
        } else {
            delay(RESUME_FOLLOWING_MS)
            following = true
        }
    }
    LaunchedEffect(lyrics, following) {
        if (!following) return@LaunchedEffect
        var first = true
        snapshotFlow { active.intValue.coerceAtLeast(0) }.collect { index ->
            if (first) list.scrollToItem(index) else list.animateScrollToItem(index)
            first = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The padding puts the line scrolled to the top a third of the way down, with room to scroll the last up.
        val above = maxHeight / ACTIVE_LINE_AT
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = above, bottom = maxHeight - above),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                if (line.text.isBlank()) {
                    Spacer(Modifier.height(PAUSE_HEIGHT))
                } else {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.titleLarge,
                        color = Shura.colors.text,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    model.seekTo(line)
                                    active.intValue = index
                                    following = true
                                }.padding(horizontal = Shura.spacing.xl, vertical = Shura.spacing.s)
                                .graphicsLayer { alpha = if (active.intValue == index) 1f else INACTIVE_ALPHA },
                    )
                }
            }
            item { Attribution(Modifier.padding(horizontal = Shura.spacing.xl)) }
        }
    }
}

/**
 * Keeps [active] on the line shown at the current position: while playing it wakes when the next line is due (and
 * at least every [MAX_WAIT_MS], to catch seeks); while paused it only checks for seeks.
 */
private suspend fun followPlayback(
    lyrics: Lyrics.Synced,
    model: LyricsModel,
    active: MutableIntState,
    playing: Boolean,
) {
    while (true) {
        val position = model.position()
        val index = lyrics.lineAt(position)
        active.intValue = index
        val untilNext = lyrics.lines.getOrNull(index + 1)?.let { (it.time - position).inWholeMilliseconds }
        delay(if (playing) (untilNext ?: MAX_WAIT_MS).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS) else PAUSED_WAIT_MS)
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Shura.spacing.xl, vertical = Shura.spacing.l),
    ) {
        Text(
            stringResource(Res.string.lyrics_not_synced),
            style = MaterialTheme.typography.bodySmall,
            color = Shura.colors.textMuted,
        )
        Spacer(Modifier.height(Shura.spacing.l))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Shura.colors.text)
        Attribution(Modifier.padding(top = Shura.spacing.l))
    }
}

@Composable
private fun Attribution(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.lyrics_attribution),
        style = MaterialTheme.typography.labelMedium,
        color = Shura.colors.textMuted,
        modifier = modifier.padding(vertical = Shura.spacing.l),
    )
}

@Composable
private fun Message(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = Shura.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Shura.spacing.xl, vertical = Shura.spacing.xxl),
    )
}

@Composable
private fun MessageWithAction(
    text: StringResource,
    action: StringResource,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Shura.spacing.xl, vertical = Shura.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.l),
    ) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = Shura.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        TintActionButton(stringResource(action), onAction)
    }
}

/** Plain blocks in the shape of lyrics, drawn once (no shimmer), as on the catalog pages. */
@Composable
private fun LoadingLines() {
    val block = Shura.colors.text.copy(alpha = SKELETON_ALPHA)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Shura.spacing.xl, vertical = Shura.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.l),
    ) {
        SKELETON_WIDTHS.forEach { width ->
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(SKELETON_LINE)
                    .clip(Shura.shapes.thumbnail)
                    .background(block),
            )
        }
    }
}

private const val INACTIVE_ALPHA = 0.45f
private const val ACTIVE_LINE_AT = 3
private const val RESUME_FOLLOWING_MS = 3_000L
private const val MIN_WAIT_MS = 16L
private const val MAX_WAIT_MS = 250L
private const val PAUSED_WAIT_MS = 500L
private const val SKELETON_ALPHA = 0.12f
private val SKELETON_WIDTHS = listOf(0.8f, 0.6f, 0.9f, 0.5f, 0.75f, 0.65f)
private val SKELETON_LINE = 20.dp
private val PAUSE_HEIGHT = 24.dp
