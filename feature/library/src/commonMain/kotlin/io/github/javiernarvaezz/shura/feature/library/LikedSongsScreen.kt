package io.github.javiernarvaezz.shura.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.ui.components.DetailActions
import io.github.javiernarvaezz.shura.core.ui.components.LikeAction
import io.github.javiernarvaezz.shura.core.ui.components.SongMenu
import io.github.javiernarvaezz.shura.core.ui.components.SongMenuLabels
import io.github.javiernarvaezz.shura.core.ui.components.TrackRow
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.text.toClock
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.library.resources.Res
import io.github.javiernarvaezz.shura.feature.library.resources.add_to_queue
import io.github.javiernarvaezz.shura.feature.library.resources.back
import io.github.javiernarvaezz.shura.feature.library.resources.go_to_album
import io.github.javiernarvaezz.shura.feature.library.resources.go_to_artist
import io.github.javiernarvaezz.shura.feature.library.resources.like
import io.github.javiernarvaezz.shura.feature.library.resources.liked_count
import io.github.javiernarvaezz.shura.feature.library.resources.liked_empty
import io.github.javiernarvaezz.shura.feature.library.resources.liked_one
import io.github.javiernarvaezz.shura.feature.library.resources.liked_songs
import io.github.javiernarvaezz.shura.feature.library.resources.more
import io.github.javiernarvaezz.shura.feature.library.resources.play
import io.github.javiernarvaezz.shura.feature.library.resources.play_next
import io.github.javiernarvaezz.shura.feature.library.resources.shuffle
import io.github.javiernarvaezz.shura.feature.library.resources.unlike
import org.jetbrains.compose.resources.stringResource

/** The liked songs, newest first, as a playlist: play, shuffle, or tap a song to play from it. */
@Composable
fun LikedSongsScreen(
    model: LikedSongsModel,
    links: LibraryLinks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs by model.songs.collectAsStateWithLifecycle()
    val queue by model.queue.collectAsStateWithLifecycle()
    val liked by model.likes.likedIds.collectAsStateWithLifecycle()
    val labels = songMenuLabels()
    LazyColumn(modifier.fillMaxSize().statusBarsPadding()) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(ShuraIcons.Back, stringResource(Res.string.back), tint = Shura.colors.text)
                }
            }
        }
        item(key = "title") { Column { Title(songs.size) } }
        if (songs.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(Res.string.liked_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Shura.colors.textMuted,
                    modifier = Modifier.padding(Shura.spacing.gutter),
                )
            }
        } else {
            item(key = "actions") {
                DetailActions(
                    stringResource(Res.string.play),
                    stringResource(Res.string.shuffle),
                    onPlay = model::play,
                    onShuffle = model::shuffle,
                    modifier = Modifier.padding(vertical = Shura.spacing.l),
                )
            }
        }
        itemsIndexed(songs, key = { _, song -> song.videoId.value }) { index, song ->
            val albumId = song.album?.id
            val artistId = song.artists.firstNotNullOfOrNull { it.id }
            TrackRow(
                title = song.title,
                subtitle = song.artistNames,
                duration = song.duration?.toClock(),
                explicit = song.isExplicit,
                onClick = { model.playTrack(index) },
                artworkUrl = song.thumbnailUrl,
                isCurrent = queue.current?.videoId == song.videoId,
            ) {
                SongMenu(
                    labels,
                    onPlayNext = { model.playNext(song) },
                    onEnqueue = { model.enqueue(song) },
                    onGoToAlbum = albumId?.let { id -> { links.onAlbum(id) } },
                    onGoToArtist = artistId?.let { id -> { links.onArtist(id) } },
                    like = LikeAction(song.videoId in liked) { model.likes.toggle(song) },
                )
            }
        }
    }
}

@Composable
private fun Title(count: Int) {
    Text(
        stringResource(Res.string.liked_songs),
        style = MaterialTheme.typography.headlineMedium,
        color = Shura.colors.text,
        modifier = Modifier.padding(horizontal = Shura.spacing.gutter),
    )
    Text(
        if (count == 1) stringResource(Res.string.liked_one) else stringResource(Res.string.liked_count, count),
        style = MaterialTheme.typography.bodyMedium,
        color = Shura.colors.textMuted,
        modifier = Modifier.padding(horizontal = Shura.spacing.gutter),
    )
}

@Composable
private fun songMenuLabels() =
    SongMenuLabels(
        more = stringResource(Res.string.more),
        playNext = stringResource(Res.string.play_next),
        addToQueue = stringResource(Res.string.add_to_queue),
        goToAlbum = stringResource(Res.string.go_to_album),
        goToArtist = stringResource(Res.string.go_to_artist),
        like = stringResource(Res.string.like),
        unlike = stringResource(Res.string.unlike),
    )
