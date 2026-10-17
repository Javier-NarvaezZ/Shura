package io.github.javiernarvaezz.shura.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.Library
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.SongLikes
import io.github.javiernarvaezz.shura.core.player.TrackPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

/** What the Library tab shows, newest first. */
data class LibraryUiState(
    val likedCount: Int = 0,
    val playlists: List<PlaylistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = likedCount == 0 && playlists.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/** The Library tab; plain so it can be tested without Android. */
class LibraryModel(
    library: Library,
    scope: CoroutineScope,
    /** The signed-in account's liked songs, joined with the local ones in the count; null without an account. */
    accountLiked: StateFlow<List<Song>>? = null,
) {
    private val liked = likedSongsOf(library, accountLiked, scope)

    val state: StateFlow<LibraryUiState> =
        combine(liked, library.playlists, library.albums, library.artists) { liked, playlists, albums, artists ->
            LibraryUiState(liked.size, playlists, albums, artists)
        }.stateIn(scope, SharingStarted.Eagerly, LibraryUiState())
}

/** Local liked songs, joined after the account's when there is an account ([mergeLiked]). */
private fun likedSongsOf(
    library: Library,
    accountLiked: StateFlow<List<Song>>?,
    scope: CoroutineScope,
): StateFlow<List<Song>> =
    accountLiked
        ?.let { account ->
            combine(account, library.likedSongs, ::mergeLiked).stateIn(scope, SharingStarted.Eagerly, emptyList())
        } ?: library.likedSongs

/** The liked songs as a playlist: play, shuffle, play from one, queue, and unlike from the menu. */
class LikedSongsModel(
    library: Library,
    player: AudioPlayer,
    scope: CoroutineScope,
    random: Random = Random.Default,
    accountLiked: StateFlow<List<Song>>? = null,
) {
    private val playback = TrackPlayback(player, random)

    /** The account's liked songs first (when signed in), then local ones it does not have. */
    val songs: StateFlow<List<Song>> = likedSongsOf(library, accountLiked, scope)

    val likes = SongLikes(library, scope)

    /** The queue, to mark the song that is playing. */
    val queue: StateFlow<QueueState> = player.queue

    fun play() = playback.playAll(songs.value)

    fun shuffle() = playback.shuffle(songs.value)

    fun playTrack(index: Int) = playback.playFrom(songs.value, index)

    fun playNext(song: Song) = playback.playNext(song)

    fun enqueue(song: Song) = playback.enqueue(song)
}

class LibraryViewModel(
    library: Library,
    accountLiked: StateFlow<List<Song>>? = null,
) : ViewModel() {
    val model = LibraryModel(library, viewModelScope, accountLiked)
}

class LikedSongsViewModel(
    library: Library,
    player: AudioPlayer,
    accountLiked: StateFlow<List<Song>>? = null,
) : ViewModel() {
    val model = LikedSongsModel(library, player, viewModelScope, accountLiked = accountLiked)
}
