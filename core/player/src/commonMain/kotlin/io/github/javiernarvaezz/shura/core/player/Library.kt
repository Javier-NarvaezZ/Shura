package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's own library on this device, without an account: liked songs, saved albums and playlists, followed
 * artists. Each list is newest first; liking or saving something again moves it to the top. Changes are saved in
 * the background and show in the lists as soon as they are stored.
 */
interface Library {
    val likedSongs: StateFlow<List<Song>>
    val albums: StateFlow<List<AlbumSummary>>
    val playlists: StateFlow<List<PlaylistSummary>>
    val artists: StateFlow<List<ArtistSummary>>

    fun setLiked(
        song: Song,
        liked: Boolean,
    )

    fun setSaved(
        album: AlbumSummary,
        saved: Boolean,
    )

    fun setSaved(
        playlist: PlaylistSummary,
        saved: Boolean,
    )

    fun setFollowed(
        artist: ArtistSummary,
        followed: Boolean,
    )
}
