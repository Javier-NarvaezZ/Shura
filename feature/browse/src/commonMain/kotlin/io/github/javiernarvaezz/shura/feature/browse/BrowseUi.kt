package io.github.javiernarvaezz.shura.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import io.github.javiernarvaezz.shura.core.model.AlbumShelf
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistShelf
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.CatalogShelf
import io.github.javiernarvaezz.shura.core.model.PlaylistShelf
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.SongLikes
import io.github.javiernarvaezz.shura.core.ui.artwork.prefetchArtwork
import io.github.javiernarvaezz.shura.core.ui.components.DetailToggle
import io.github.javiernarvaezz.shura.core.ui.components.DetailTopBar
import io.github.javiernarvaezz.shura.core.ui.components.LikeAction
import io.github.javiernarvaezz.shura.core.ui.components.MediaRail
import io.github.javiernarvaezz.shura.core.ui.components.MediaTile
import io.github.javiernarvaezz.shura.core.ui.components.SongMenu
import io.github.javiernarvaezz.shura.core.ui.components.SongMenuLabels
import io.github.javiernarvaezz.shura.core.ui.components.TILE_ARTWORK_PX
import io.github.javiernarvaezz.shura.core.ui.components.TOP_BAR_SPACE
import io.github.javiernarvaezz.shura.core.ui.components.TrackRow
import io.github.javiernarvaezz.shura.core.ui.components.heroScrolled
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.text.toClock
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.browse.resources.Res
import io.github.javiernarvaezz.shura.feature.browse.resources.add_to_queue
import io.github.javiernarvaezz.shura.feature.browse.resources.back
import io.github.javiernarvaezz.shura.feature.browse.resources.follow
import io.github.javiernarvaezz.shura.feature.browse.resources.go_to_album
import io.github.javiernarvaezz.shura.feature.browse.resources.go_to_artist
import io.github.javiernarvaezz.shura.feature.browse.resources.like
import io.github.javiernarvaezz.shura.feature.browse.resources.load_failed
import io.github.javiernarvaezz.shura.feature.browse.resources.more
import io.github.javiernarvaezz.shura.feature.browse.resources.play_next
import io.github.javiernarvaezz.shura.feature.browse.resources.remove_from_library
import io.github.javiernarvaezz.shura.feature.browse.resources.retry
import io.github.javiernarvaezz.shura.feature.browse.resources.save_to_library
import io.github.javiernarvaezz.shura.feature.browse.resources.unfollow
import io.github.javiernarvaezz.shura.feature.browse.resources.unlike
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * The frame of every catalog page: its list (hero first), the top bar that appears as the hero scrolls away, and
 * the loading and failed states. [content] gets the modifier its hero must carry, so the bar knows its height.
 */
@Composable
internal fun <T> DetailPage(
    state: PageState<T>,
    title: (T) -> String,
    navigation: BrowseNavigation,
    onRetry: () -> Unit,
    skeleton: Skeleton,
    content: LazyListScope.(page: T, heroModifier: Modifier) -> Unit,
) {
    val list = rememberLazyListState()
    // Written in layout, read in draw: scrolling never recomposes the bar.
    var heroHeight by remember { mutableIntStateOf(0) }
    val heroModifier = Modifier.onSizeChanged { heroHeight = it.height }
    Box(Modifier.fillMaxSize().background(Shura.colors.background)) {
        when (state) {
            is PageState.Loading -> {
                LoadingPage(skeleton)
            }

            is PageState.Failed -> {
                FailedPage(onRetry)
            }

            is PageState.Loaded -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = list,
                    contentPadding = PaddingValues(bottom = Shura.spacing.xxl),
                ) { content(state.page, heroModifier) }
            }
        }
        DetailTopBar(
            title = (state as? PageState.Loaded)?.page?.let(title).orEmpty(),
            backLabel = stringResource(Res.string.back),
            onBack = navigation.onBack,
            revealed = { if (state is PageState.Loaded) list.heroScrolled { heroHeight } else 0f },
        )
    }
}

/** The shape of a page's hero while it loads. */
internal enum class Skeleton { Cover, Image }

/**
 * Plain blocks in the shape of the page, drawn once: the opening transition runs on them at once and the
 * content replaces them when it arrives. No shimmer, so nothing redraws during the transition.
 */
@Composable
private fun LoadingPage(skeleton: Skeleton) {
    val block = Shura.colors.surface2
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.m),
    ) {
        when (skeleton) {
            Skeleton.Cover -> {
                Box(
                    Modifier
                        .statusBarsPadding()
                        .padding(
                            top = TOP_BAR_SPACE,
                        ).size(LOADING_COVER)
                        .clip(Shura.shapes.artwork)
                        .background(block),
                )
                Box(Modifier.size(LOADING_LINE_WIDTH, LOADING_LINE).clip(Shura.shapes.thumbnail).background(block))
            }

            Skeleton.Image -> {
                Box(Modifier.fillMaxWidth().aspectRatio(IMAGE_RATIO).background(block))
            }
        }
        repeat(LOADING_ROWS) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Shura.spacing.gutter)
                    .height(LOADING_ROW)
                    .clip(Shura.shapes.thumbnail)
                    .background(block),
            )
        }
    }
}

/** Starts loading the first covers of each shelf as soon as the page is there, before they scroll into view. */
@Composable
internal fun PrefetchShelfCovers(shelves: List<CatalogShelf>) {
    val context = LocalPlatformContext.current
    LaunchedEffect(shelves) {
        val urls =
            shelves.flatMap { shelf ->
                when (shelf) {
                    is AlbumShelf -> shelf.albums.map { it.thumbnailUrl }
                    is ArtistShelf -> shelf.artists.map { it.thumbnailUrl }
                    is PlaylistShelf -> shelf.playlists.map { it.thumbnailUrl }
                }.filterNotNull().take(PREFETCH_PER_SHELF)
            }
        prefetchArtwork(context, urls, TILE_ARTWORK_PX)
    }
}

@Composable
private fun FailedPage(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = TOP_BAR_SPACE + Shura.spacing.xxl)
            .padding(horizontal = Shura.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.l),
    ) {
        Text(
            stringResource(Res.string.load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = Shura.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
internal fun songMenuLabels() =
    SongMenuLabels(
        more = stringResource(Res.string.more),
        playNext = stringResource(Res.string.play_next),
        addToQueue = stringResource(Res.string.add_to_queue),
        goToAlbum = stringResource(Res.string.go_to_album),
        goToArtist = stringResource(Res.string.go_to_artist),
        like = stringResource(Res.string.like),
        unlike = stringResource(Res.string.unlike),
    )

/**
 * A track of a page with its menu. "Go to" entries leave out the page the user is on ([pageAlbumId],
 * [pageArtistId]) and links the song does not have.
 */
@Composable
internal fun PageTrack(
    song: Song,
    subtitle: String?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    navigation: BrowseNavigation,
    labels: SongMenuLabels,
    number: Int? = null,
    pageAlbumId: String? = null,
    pageArtistId: String? = null,
    like: LikeAction? = null,
) {
    val albumId = song.album?.id?.takeIf { it != pageAlbumId }
    val artistId = song.artists.firstNotNullOfOrNull { it.id }?.takeIf { it != pageArtistId }
    TrackRow(
        title = song.title,
        subtitle = subtitle,
        duration = song.duration?.toClock(),
        explicit = song.isExplicit,
        onClick = onClick,
        number = number,
        artworkUrl = song.thumbnailUrl,
        isCurrent = isCurrent,
    ) {
        SongMenu(
            labels,
            onPlayNext = onPlayNext,
            onEnqueue = onEnqueue,
            onGoToAlbum = albumId?.let { id -> { navigation.onAlbum(id) } },
            onGoToArtist = artistId?.let { id -> { navigation.onArtist(id) } },
            like = like,
        )
    }
}

/** The page's shelves as rails, in YouTube Music's order. */
internal fun LazyListScope.shelves(
    shelves: List<CatalogShelf>,
    navigation: BrowseNavigation,
) {
    items(shelves, key = { "shelf:${it.title}" }) { shelf ->
        ShelfRail(shelf, navigation, Modifier.padding(top = Shura.spacing.xl))
    }
}

@Composable
private fun ShelfRail(
    shelf: CatalogShelf,
    navigation: BrowseNavigation,
    modifier: Modifier,
) {
    when (shelf) {
        is AlbumShelf -> {
            MediaRail(shelf.title, shelf.albums, key = { it.id }, modifier = modifier) {
                MediaTile(it.title, it.caption(), it.thumbnailUrl, onClick = { navigation.onAlbum(it.id) })
            }
        }

        is ArtistShelf -> {
            MediaRail(shelf.title, shelf.artists, key = { it.id }, modifier = modifier) {
                MediaTile(it.name, it.subtitle, it.thumbnailUrl, onClick = { navigation.onArtist(it.id) }, round = true)
            }
        }

        is PlaylistShelf -> {
            MediaRail(shelf.title, shelf.playlists, key = { it.id }, modifier = modifier) {
                MediaTile(it.title, it.author, it.thumbnailUrl, onClick = { navigation.onPlaylist(BrowseLink(it.id)) })
            }
        }
    }
}

/** "Sencillo · 2007", or the artists when neither is known. */
internal fun AlbumSummary.caption(): String? =
    listOfNotNull(typeLabel, year?.toString()).joinToString(" · ").ifEmpty { artistNames.ifEmpty { null } }

private val LOADING_COVER = 220.dp
private val LOADING_LINE_WIDTH = 180.dp
private val LOADING_LINE = 24.dp
private val LOADING_ROW = 48.dp
private const val LOADING_ROWS = 5
private const val IMAGE_RATIO = 1.1f

// About what a phone shows of a rail before scrolling sideways.
private const val PREFETCH_PER_SHELF = 4
