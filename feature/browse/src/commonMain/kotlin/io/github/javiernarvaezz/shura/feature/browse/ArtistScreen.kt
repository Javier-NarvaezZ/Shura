package io.github.javiernarvaezz.shura.feature.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.ui.components.DetailActions
import io.github.javiernarvaezz.shura.core.ui.components.ExpandableText
import io.github.javiernarvaezz.shura.core.ui.components.ImageHero
import io.github.javiernarvaezz.shura.core.ui.components.SectionHeader
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.browse.resources.Res
import io.github.javiernarvaezz.shura.feature.browse.resources.about
import io.github.javiernarvaezz.shura.feature.browse.resources.play
import io.github.javiernarvaezz.shura.feature.browse.resources.popular_songs
import io.github.javiernarvaezz.shura.feature.browse.resources.see_all
import io.github.javiernarvaezz.shura.feature.browse.resources.shuffle
import io.github.javiernarvaezz.shura.feature.browse.resources.subscribers
import org.jetbrains.compose.resources.stringResource

/** An artist: image hero, play and shuffle (top songs), the top songs, the artist's shelves and "about". */
@Composable
fun ArtistScreen(
    model: ArtistModel,
    navigation: BrowseNavigation,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val queue by model.queue.collectAsStateWithLifecycle()
    val labels = songMenuLabels()
    val playLabel = stringResource(Res.string.play)
    val shuffleLabel = stringResource(Res.string.shuffle)
    val popular = stringResource(Res.string.popular_songs)
    val seeAll = stringResource(Res.string.see_all)
    val about = stringResource(Res.string.about)
    val loaded = (state as? PageState.Loaded)?.page
    val details = loaded?.monthlyListeners ?: loaded?.subscribers?.let { stringResource(Res.string.subscribers, it) }
    loaded?.let { PrefetchShelfCovers(it.shelves) }
    DetailPage(state, { it.artist.name }, navigation, model::retry, Skeleton.Image) { page, heroModifier ->
        item(key = "hero") { ImageHero(page.artist.name, page.artist.thumbnailUrl, details, heroModifier) }
        if (page.topSongs.isNotEmpty()) {
            item(key = "actions") {
                DetailActions(
                    playLabel,
                    shuffleLabel,
                    onPlay = model::play,
                    onShuffle = model::shuffle,
                    modifier = Modifier.padding(vertical = Shura.spacing.l),
                )
            }
            item(key = "popular") {
                val all = page.allSongs
                SectionHeader(popular, action = all?.let { seeAll }, onAction = { all?.let(navigation.onPlaylist) })
            }
        }
        itemsIndexed(page.topSongs, key = { index, song -> "top:$index:${song.videoId.value}" }) { index, song ->
            PageTrack(
                song = song,
                subtitle = song.album?.title ?: song.artistNames,
                isCurrent = queue.current?.videoId == song.videoId,
                onClick = { model.playTrack(index) },
                onPlayNext = { model.playNext(song) },
                onEnqueue = { model.enqueue(song) },
                navigation = navigation,
                labels = labels,
                pageArtistId = page.artist.id,
            )
        }
        shelves(page.shelves, navigation)
        page.description?.let { text ->
            item(
                key = "about",
            ) { SectionHeader(about, Modifier.padding(top = Shura.spacing.xl, bottom = Shura.spacing.s)) }
            item(key = "description") { ExpandableText(text, collapsedLines = ABOUT_LINES) }
        }
    }
}

private const val ABOUT_LINES = 4
