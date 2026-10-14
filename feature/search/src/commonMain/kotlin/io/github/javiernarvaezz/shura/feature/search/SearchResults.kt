package io.github.javiernarvaezz.shura.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.ui.components.LikeAction
import io.github.javiernarvaezz.shura.core.ui.components.SongMenu
import io.github.javiernarvaezz.shura.core.ui.components.SongMenuLabels
import io.github.javiernarvaezz.shura.core.ui.components.SongRow
import io.github.javiernarvaezz.shura.core.ui.text.toClock
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.search.resources.Res
import io.github.javiernarvaezz.shura.feature.search.resources.add_to_queue
import io.github.javiernarvaezz.shura.feature.search.resources.go_to_album
import io.github.javiernarvaezz.shura.feature.search.resources.go_to_artist
import io.github.javiernarvaezz.shura.feature.search.resources.kind_albums
import io.github.javiernarvaezz.shura.feature.search.resources.kind_artists
import io.github.javiernarvaezz.shura.feature.search.resources.kind_playlists
import io.github.javiernarvaezz.shura.feature.search.resources.kind_songs
import io.github.javiernarvaezz.shura.feature.search.resources.like
import io.github.javiernarvaezz.shura.feature.search.resources.more_actions
import io.github.javiernarvaezz.shura.feature.search.resources.play_next
import io.github.javiernarvaezz.shura.feature.search.resources.unlike
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The search filters as chips under the field. */
@Composable
internal fun KindChips(
    selected: SearchKind,
    onSelect: (SearchKind) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Shura.spacing.gutter, vertical = Shura.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.s),
    ) {
        SearchKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                label = { Text(stringResource(kind.label)) },
                shape = Shura.shapes.pill,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Shura.colors.accent,
                        selectedLabelColor = Shura.colors.onAccent,
                    ),
            )
        }
    }
}

private val SearchKind.label: StringResource
    get() =
        when (this) {
            SearchKind.Songs -> Res.string.kind_songs
            SearchKind.Albums -> Res.string.kind_albums
            SearchKind.Artists -> Res.string.kind_artists
            SearchKind.Playlists -> Res.string.kind_playlists
        }

/** Song results; each row's menu queues the song or leads to its album or artist. */
@Composable
internal fun SongResults(
    songs: List<Song>,
    currentId: String?,
    onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onEnqueue: (Song) -> Unit,
    links: SearchLinks,
    likeOf: ((Song) -> LikeAction)? = null,
) {
    val labels =
        SongMenuLabels(
            more = stringResource(Res.string.more_actions),
            playNext = stringResource(Res.string.play_next),
            addToQueue = stringResource(Res.string.add_to_queue),
            goToAlbum = stringResource(Res.string.go_to_album),
            goToArtist = stringResource(Res.string.go_to_artist),
            like = stringResource(Res.string.like),
            unlike = stringResource(Res.string.unlike),
        )
    ResultList(songs, key = { it.videoId.value }) { song ->
        val albumId = song.album?.id
        val artistId = song.artists.firstNotNullOfOrNull { it.id }
        SongRow(
            title = song.title,
            subtitle = song.subtitle(),
            artworkUrl = song.thumbnailUrl,
            explicit = song.isExplicit,
            isCurrent = song.videoId.value == currentId,
            onClick = { onPlay(song) },
        ) {
            SongMenu(
                labels,
                onPlayNext = { onPlayNext(song) },
                onEnqueue = { onEnqueue(song) },
                onGoToAlbum = albumId?.let { id -> { links.onAlbum(id) } },
                onGoToArtist = artistId?.let { id -> { links.onArtist(id) } },
                like = likeOf?.invoke(song),
            )
        }
    }
}

@Composable
internal fun AlbumResults(
    albums: List<AlbumSummary>,
    links: SearchLinks,
) = ResultList(albums, key = { it.id }) { album ->
    SongRow(
        title = album.title,
        subtitle =
            listOfNotNull(
                album.typeLabel,
                album.artistNames.ifEmpty {
                    null
                },
                album.year?.toString(),
            ).joinToString(" • "),
        artworkUrl = album.thumbnailUrl,
        explicit = album.isExplicit,
        onClick = { links.onAlbum(album.id) },
    )
}

@Composable
internal fun ArtistResults(
    artists: List<ArtistSummary>,
    links: SearchLinks,
) = ResultList(artists, key = { it.id }) { artist ->
    SongRow(
        title = artist.name,
        subtitle = artist.subtitle.orEmpty(),
        artworkUrl = artist.thumbnailUrl,
        explicit = false,
        round = true,
        onClick = { links.onArtist(artist.id) },
    )
}

@Composable
internal fun PlaylistResults(
    playlists: List<PlaylistSummary>,
    links: SearchLinks,
) = ResultList(playlists, key = { it.id }) { playlist ->
    SongRow(
        title = playlist.title,
        subtitle = listOfNotNull(playlist.author, playlist.subtitle).joinToString(" • "),
        artworkUrl = playlist.thumbnailUrl,
        explicit = false,
        onClick = { links.onPlaylist(BrowseLink(playlist.id)) },
    )
}

@Composable
private fun <T : Any> ResultList(
    items: List<T>,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(top = Shura.spacing.s)) {
        items(items, key = key) { row(it) }
    }
}

private fun Song.subtitle(): String =
    listOfNotNull(artistNames.ifBlank { null }, album?.title, duration?.toClock()).joinToString(" • ")
