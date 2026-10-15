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

/** Each song's "Me gusta" action, from one read of the liked set; null where there is no library. */
@Composable
internal fun rememberLikeActions(likes: SongLikes): ((Song) -> LikeAction)? {
    if (!likes.isAvailable) return null
    val liked by likes.likedIds.collectAsStateWithLifecycle()
    return { song -> LikeAction(song.videoId in liked) { likes.toggle(song) } }
}

/** "Guardar en la biblioteca" for an album or playlist page; null where there is no library. */
@Composable
internal fun rememberSaveToggle(
    available: Boolean,
    saved: StateFlow<Boolean>,
    onToggle: () -> Unit,
): DetailToggle? {
    if (!available) return null
    val isSaved by saved.collectAsStateWithLifecycle()
    return DetailToggle(
        active = isSaved,
        label = stringResource(Res.string.save_to_library),
        activeLabel = stringResource(Res.string.remove_from_library),
        icon = ShuraIcons.LibraryAdd,
        activeIcon = ShuraIcons.LibraryAddCheck,
        onToggle = onToggle,
    )
}

/** "Seguir" for an artist page; null where there is no library. */
@Composable
internal fun rememberFollowToggle(
    available: Boolean,
    followed: StateFlow<Boolean>,
    onToggle: () -> Unit,
): DetailToggle? {
    if (!available) return null
    val isFollowed by followed.collectAsStateWithLifecycle()
    return DetailToggle(
        active = isFollowed,
        label = stringResource(Res.string.follow),
        activeLabel = stringResource(Res.string.unfollow),
        icon = ShuraIcons.PersonAdd,
        activeIcon = ShuraIcons.Following,
        onToggle = onToggle,
    )
}
