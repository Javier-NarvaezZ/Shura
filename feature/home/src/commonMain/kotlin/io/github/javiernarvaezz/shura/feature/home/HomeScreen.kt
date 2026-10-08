package io.github.javiernarvaezz.shura.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.components.PlayPauseButton
import io.github.javiernarvaezz.shura.core.ui.components.shimmer
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.home.resources.Res
import io.github.javiernarvaezz.shura.feature.home.resources.continue_listening
import io.github.javiernarvaezz.shura.feature.home.resources.empty_action
import io.github.javiernarvaezz.shura.feature.home.resources.empty_title
import io.github.javiernarvaezz.shura.feature.home.resources.greeting_afternoon
import io.github.javiernarvaezz.shura.feature.home.resources.greeting_morning
import io.github.javiernarvaezz.shura.feature.home.resources.greeting_night
import io.github.javiernarvaezz.shura.feature.home.resources.now_playing
import io.github.javiernarvaezz.shura.feature.home.resources.pause
import io.github.javiernarvaezz.shura.feature.home.resources.paused
import io.github.javiernarvaezz.shura.feature.home.resources.play
import io.github.javiernarvaezz.shura.feature.home.resources.recently_played
import org.jetbrains.compose.resources.stringResource

/** Home: a greeting, the song to continue, and recently played songs. [onSearch] opens the Search tab. */
@Composable
fun HomeScreen(
    model: HomeModel,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Shura.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.xl),
    ) {
        item(key = "greeting") { Greeting(state.dayPart) }
        when {
            state.loading -> {
                item(key = "loading") { LoadingRow() }
            }

            state.isEmpty -> {
                item(key = "empty") { EmptyHome(onSearch) }
            }

            else -> {
                state.current?.let { song ->
                    item(key = "continue") { ContinueListening(song, state.isPlaying, model::togglePlay) }
                }
                if (state.recent.isNotEmpty()) {
                    item(key = "recent") { RecentlyPlayed(state.recent, model::playRecent) }
                }
            }
        }
    }
}

@Composable
private fun Greeting(dayPart: DayPart) {
    val text =
        when (dayPart) {
            DayPart.Morning -> Res.string.greeting_morning
            DayPart.Afternoon -> Res.string.greeting_afternoon
            DayPart.Night -> Res.string.greeting_night
        }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.headlineMedium,
        color = Shura.colors.text,
        modifier = Modifier.padding(horizontal = Shura.spacing.gutter).padding(top = Shura.spacing.xl),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Shura.colors.text,
        modifier = Modifier.padding(horizontal = Shura.spacing.gutter).padding(bottom = Shura.spacing.m),
    )
}

@Composable
private fun ContinueListening(
    song: Song,
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        SectionTitle(stringResource(Res.string.continue_listening))
        Row(
            Modifier
                .padding(horizontal = Shura.spacing.gutter)
                .fillMaxWidth()
                .clip(Shura.shapes.card)
                .background(Shura.colors.surface1)
                .clickable(onClick = onToggle)
                .padding(Shura.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
        ) {
            Artwork(song.thumbnailUrl, CONTINUE_PX, contentDescription = null, modifier = Modifier.size(CONTINUE_SIZE))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Shura.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artistNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Shura.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(if (isPlaying) Res.string.now_playing else Res.string.paused),
                    style = MaterialTheme.typography.labelMedium,
                    color = Shura.colors.textMuted,
                    modifier = Modifier.padding(top = Shura.spacing.xs),
                )
            }
            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = onToggle,
                playLabel = stringResource(Res.string.play),
                pauseLabel = stringResource(Res.string.pause),
            )
        }
    }
}

@Composable
private fun RecentlyPlayed(
    songs: List<Song>,
    onPlay: (Int) -> Unit,
) {
    Column {
        SectionTitle(stringResource(Res.string.recently_played))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Shura.spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
        ) {
            itemsIndexed(songs, key = { _, song -> song.videoId.value }) { index, song ->
                // Only the cover is rounded: clipping the whole tile would cut the text in the bottom corner.
                Column(Modifier.width(TILE_SIZE).clickable { onPlay(index) }) {
                    Artwork(
                        song.thumbnailUrl,
                        TILE_PX,
                        contentDescription = null,
                        modifier = Modifier.size(TILE_SIZE),
                        shape = Shura.shapes.card,
                    )
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Shura.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Shura.spacing.s),
                    )
                    Text(
                        song.artistNames,
                        style = MaterialTheme.typography.bodySmall,
                        color = Shura.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        Modifier.padding(horizontal = Shura.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
    ) {
        repeat(LOADING_TILES) { Box(Modifier.size(TILE_SIZE).clip(Shura.shapes.card).shimmer()) }
    }
}

@Composable
private fun EmptyHome(onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Shura.spacing.gutter).padding(top = Shura.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = Shura.colors.textMuted,
        )
        Spacer(Modifier.height(Shura.spacing.l))
        FilledTonalButton(onClick = onSearch) { Text(stringResource(Res.string.empty_action)) }
    }
}

private val CONTINUE_SIZE = 88.dp
private const val CONTINUE_PX = 226
private val TILE_SIZE = 140.dp
private const val TILE_PX = 300
private const val LOADING_TILES = 3
