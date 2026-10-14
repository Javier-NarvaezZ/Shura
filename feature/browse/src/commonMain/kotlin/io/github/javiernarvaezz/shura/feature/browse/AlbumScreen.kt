package io.github.javiernarvaezz.shura.feature.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.AlbumPage
import io.github.javiernarvaezz.shura.core.ui.components.CoverHero
import io.github.javiernarvaezz.shura.core.ui.components.DetailActions
import io.github.javiernarvaezz.shura.core.ui.components.ExpandableText
import io.github.javiernarvaezz.shura.core.ui.components.HeroLink
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.browse.resources.Res
import io.github.javiernarvaezz.shura.feature.browse.resources.album
import io.github.javiernarvaezz.shura.feature.browse.resources.hours_minutes
import io.github.javiernarvaezz.shura.feature.browse.resources.minutes
import io.github.javiernarvaezz.shura.feature.browse.resources.one_song
import io.github.javiernarvaezz.shura.feature.browse.resources.play
import io.github.javiernarvaezz.shura.feature.browse.resources.shuffle
import io.github.javiernarvaezz.shura.feature.browse.resources.song_count
import org.jetbrains.compose.resources.stringResource

/** An album: cover hero, play and shuffle, description, numbered tracks and the shelves below. */
@Composable
fun AlbumScreen(
    model: AlbumModel,
    navigation: BrowseNavigation,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val queue by model.queue.collectAsStateWithLifecycle()
    val labels = songMenuLabels()
    val likeOf = rememberLikeActions(model.likes)
    val save = rememberSaveToggle(model.canSave, model.saved, model::toggleSaved)
    val playLabel = stringResource(Res.string.play)
    val shuffleLabel = stringResource(Res.string.shuffle)
    val details = (state as? PageState.Loaded)?.page?.let { albumDetails(it) }
    (state as? PageState.Loaded)?.page?.let { PrefetchShelfCovers(it.shelves) }
    DetailPage(state, { it.album.title }, navigation, model::retry, Skeleton.Cover) { page, heroModifier ->
        val album = page.album
        val artistId = album.artists.firstNotNullOfOrNull { it.id }
        item(key = "hero") {
            CoverHero(
                title = album.title,
                artworkUrl = album.thumbnailUrl,
                link =
                    album.artistNames.takeIf { it.isNotEmpty() }?.let { names ->
                        HeroLink(names, artistId?.let { id -> { navigation.onArtist(id) } })
                    },
                details = details,
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
                toggle = save,
            )
        }
        page.description?.let {
            item(
                key = "description",
            ) { ExpandableText(it, Modifier.padding(bottom = Shura.spacing.l)) }
        }
        itemsIndexed(page.tracks, key = { index, song -> "track:$index:${song.videoId.value}" }) { index, song ->
            PageTrack(
                song = song,
                // Only songs by someone else than the album's artist say who sings them.
                subtitle = song.artistNames.takeIf { song.artists != album.artists },
                isCurrent = queue.current?.videoId == song.videoId,
                onClick = { model.playTrack(index) },
                onPlayNext = { model.playNext(song) },
                onEnqueue = { model.enqueue(song) },
                navigation = navigation,
                labels = labels,
                like = likeOf?.invoke(song),
                number = index + 1,
                pageAlbumId = album.id,
            )
        }
        shelves(page.shelves, navigation)
    }
}

/** "Álbum · 2004 · 12 canciones · 45 min". */
@Composable
private fun albumDetails(page: AlbumPage): String {
    val count = page.tracks.size
    val songs = if (count == 1) stringResource(Res.string.one_song) else stringResource(Res.string.song_count, count)
    val length =
        page.knownDuration?.let { duration ->
            val minutes = duration.inWholeMinutes.toInt()
            if (minutes < MINUTES_PER_HOUR) {
                stringResource(Res.string.minutes, minutes)
            } else {
                stringResource(Res.string.hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
            }
        }
    val kind = page.album.typeLabel ?: stringResource(Res.string.album)
    return listOfNotNull(kind, page.album.year?.toString(), songs, length).joinToString(" · ")
}

private const val MINUTES_PER_HOUR = 60
