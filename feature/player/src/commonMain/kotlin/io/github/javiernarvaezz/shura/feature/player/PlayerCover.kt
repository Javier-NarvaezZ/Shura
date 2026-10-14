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

/** The current song's heart, or null where there is no library. */
@Composable
internal fun rememberLike(
    model: PlayerModel,
    song: Song,
): LikeAction? {
    if (!model.likes.isAvailable) return null
    val liked by model.likes.likedIds.collectAsStateWithLifecycle()
    return LikeAction(song.videoId in liked, model::toggleLike)
}

@Composable
internal fun CoverAndTitle(
    song: Song,
    sheet: PlayerSheetState,
    links: PlayerLinks,
    like: LikeAction?,
    modifier: Modifier = Modifier,
) {
    val artistId = song.artists.firstNotNullOfOrNull { it.id }
    val album = song.album
    Column(modifier, verticalArrangement = Arrangement.Center) {
        // The cover slot: the flying cover lands here.
        Spacer(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { sheet.playerCover = it.boundsInRoot() },
        )
        Spacer(Modifier.height(Shura.spacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Shura.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // The artist and the album open their pages (the first artist when there are several).
                Text(
                    song.artistNames,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Shura.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = artistId?.let { id -> Modifier.clickable { links.onArtist(id) } } ?: Modifier,
                )
                if (album != null) {
                    Text(
                        album.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Shura.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = album.id?.let { id -> Modifier.clickable { links.onAlbum(id) } } ?: Modifier,
                    )
                }
            }
            if (like != null) {
                IconButton(onClick = like.onToggle) {
                    Icon(
                        if (like.liked) ShuraIcons.FavoriteFilled else ShuraIcons.Favorite,
                        stringResource(if (like.liked) Res.string.unlike else Res.string.like),
                        tint = Shura.colors.text,
                    )
                }
            }
        }
    }
}
