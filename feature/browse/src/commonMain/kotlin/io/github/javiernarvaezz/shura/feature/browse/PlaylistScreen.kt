package io.github.javiernarvaezz.shura.feature.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.ui.components.CoverHero
import io.github.javiernarvaezz.shura.core.ui.components.DetailActions
import io.github.javiernarvaezz.shura.core.ui.components.ExpandableText
import io.github.javiernarvaezz.shura.core.ui.components.HeroLink
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.browse.resources.Res
import io.github.javiernarvaezz.shura.feature.browse.resources.play
import io.github.javiernarvaezz.shura.feature.browse.resources.retry
import io.github.javiernarvaezz.shura.feature.browse.resources.shuffle
import org.jetbrains.compose.resources.stringResource

/** A playlist: cover hero, play and shuffle, description and its tracks, loaded a page at a time. */
@Composable
fun PlaylistScreen(
    model: PlaylistModel,
    navigation: BrowseNavigation,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val queue by model.queue.collectAsStateWithLifecycle()
    val preparing by model.preparing.collectAsStateWithLifecycle()
    val moreFailed by model.moreFailed.collectAsStateWithLifecycle()
    val labels = songMenuLabels()
    val playLabel = stringResource(Res.string.play)
    val shuffleLabel = stringResource(Res.string.shuffle)
    val retryLabel = stringResource(Res.string.retry)
    DetailPage(state, { it.playlist.title }, navigation, model::retry) { page, heroModifier ->
        val playlist = page.playlist
        item(key = "hero") {
            CoverHero(
                title = playlist.title,
                // Some playlists (an artist's songs) have no cover of their own: the first song's stands in.
                artworkUrl = playlist.thumbnailUrl ?: page.tracks.firstOrNull()?.thumbnailUrl,
                link = playlist.author?.let { HeroLink(it) },
                details = page.details,
                modifier = heroModifier,
            )
        }
        item(key = "actions") {
            DetailActions(
                playLabel,
                shuffleLabel,
                onPlay = model::play,
                onShuffle = model::shuffle,
                modifier = Modifier.padding(vertical = Shura.spacing.l),
                loading = preparing,
            )
        }
        page.description?.takeIf { it != playlist.title }?.let {
            item(key = "description") { ExpandableText(it, Modifier.padding(bottom = Shura.spacing.l)) }
        }
        itemsIndexed(page.tracks, key = { index, song -> "track:$index:${song.videoId.value}" }) { index, song ->
            PageTrack(
                song = song,
                subtitle = song.artistNames.ifEmpty { null },
                isCurrent = queue.current?.videoId == song.videoId,
                onClick = { model.playTrack(index) },
                onPlayNext = { model.playNext(song) },
                onEnqueue = { model.enqueue(song) },
                navigation = navigation,
                labels = labels,
            )
        }
        if (page.hasMore) {
            // Composed only when the list nears its end: that is when the next tracks are needed.
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(Shura.spacing.l), contentAlignment = Alignment.Center) {
                    if (moreFailed) {
                        TextButton(onClick = model::loadMore) { Text(retryLabel) }
                    } else {
                        LaunchedEffect(page.tracks.size) { model.loadMore() }
                        CircularProgressIndicator(Modifier.size(SPINNER), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private val SPINNER = 24.dp
