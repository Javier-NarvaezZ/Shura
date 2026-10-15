package io.github.javiernarvaezz.shura.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.ui.components.MediaRail
import io.github.javiernarvaezz.shura.core.ui.components.MediaTile
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.library.resources.Res
import io.github.javiernarvaezz.shura.feature.library.resources.albums
import io.github.javiernarvaezz.shura.feature.library.resources.artists
import io.github.javiernarvaezz.shura.feature.library.resources.empty_library
import io.github.javiernarvaezz.shura.feature.library.resources.library_title
import io.github.javiernarvaezz.shura.feature.library.resources.liked_count
import io.github.javiernarvaezz.shura.feature.library.resources.liked_none
import io.github.javiernarvaezz.shura.feature.library.resources.liked_one
import io.github.javiernarvaezz.shura.feature.library.resources.liked_songs
import io.github.javiernarvaezz.shura.feature.library.resources.playlists
import org.jetbrains.compose.resources.stringResource

/** The Library tab: the liked songs, then saved playlists, albums and followed artists, newest first. */
@Composable
fun LibraryScreen(
    model: LibraryModel,
    links: LibraryLinks,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Shura.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.xl),
    ) {
        item(key = "title") {
            Text(
                stringResource(Res.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Shura.colors.text,
                modifier = Modifier.padding(horizontal = Shura.spacing.gutter).padding(top = Shura.spacing.xl),
            )
        }
        if (state.isEmpty) {
            item(key = "empty") {
                Text(
                    stringResource(Res.string.empty_library),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Shura.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Shura.spacing.gutter),
                )
            }
        }
        item(key = "liked") { LikedSongsCard(state.likedCount, links.onLikedSongs) }
        if (state.playlists.isNotEmpty()) {
            item(key = "playlists") {
                MediaRail(stringResource(Res.string.playlists), state.playlists, key = { it.id }) { playlist ->
                    MediaTile(playlist.title, playlist.author, playlist.thumbnailUrl, { links.onPlaylist(playlist.id) })
                }
            }
        }
        if (state.albums.isNotEmpty()) {
            item(key = "albums") {
                MediaRail(stringResource(Res.string.albums), state.albums, key = { it.id }) { album ->
                    MediaTile(album.title, album.artistNames, album.thumbnailUrl, { links.onAlbum(album.id) })
                }
            }
        }
        if (state.artists.isNotEmpty()) {
            item(key = "artists") {
                MediaRail(stringResource(Res.string.artists), state.artists, key = { it.id }) { artist ->
                    MediaTile(artist.name, null, artist.thumbnailUrl, { links.onArtist(artist.id) }, round = true)
                }
            }
        }
    }
}

/** The liked songs as one card: a heart on the brand gradient, the name and how many there are. */
@Composable
private fun LikedSongsCard(
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Shura.spacing.gutter, vertical = Shura.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(CARD_ART)
                .clip(Shura.shapes.thumbnail)
                .background(Shura.colors.brandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ShuraIcons.FavoriteFilled, contentDescription = null, tint = Shura.colors.onAccent)
        }
        Column(Modifier.padding(start = Shura.spacing.l)) {
            Text(
                stringResource(Res.string.liked_songs),
                style = MaterialTheme.typography.titleMedium,
                color = Shura.colors.text,
            )
            Text(
                when (count) {
                    0 -> stringResource(Res.string.liked_none)
                    1 -> stringResource(Res.string.liked_one)
                    else -> stringResource(Res.string.liked_count, count)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Shura.colors.textMuted,
            )
        }
    }
}

private val CARD_ART = 64.dp
