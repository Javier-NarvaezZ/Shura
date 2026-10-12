package io.github.javiernarvaezz.shura.core.testing

import io.github.javiernarvaezz.shura.core.model.AlbumPage
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistPage
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.MusicCatalog
import io.github.javiernarvaezz.shura.core.model.PlaylistPage
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.TrackPage
import io.github.javiernarvaezz.shura.core.model.VideoId

/**
 * A catalog that answers from the maps a test fills, records each call in [calls] and fails with [failure] while
 * it is set. A missing entry fails like a network error would.
 */
class FakeMusicCatalog : MusicCatalog {
    val calls = mutableListOf<String>()
    var failure: Exception? = null

    val songs = mutableMapOf<String, List<Song>>()
    val albumResults = mutableMapOf<String, List<AlbumSummary>>()
    val artistResults = mutableMapOf<String, List<ArtistSummary>>()
    val playlistResults = mutableMapOf<String, List<PlaylistSummary>>()
    val albums = mutableMapOf<String, AlbumPage>()
    val artists = mutableMapOf<String, ArtistPage>()
    val playlists = mutableMapOf<String, PlaylistPage>()
    val continuations = mutableMapOf<String, TrackPage>()
    val related = mutableMapOf<VideoId, List<Song>>()

    override suspend fun searchSongs(query: String) = answer("searchSongs:$query", songs[query])

    override suspend fun searchAlbums(query: String) = answer("searchAlbums:$query", albumResults[query])

    override suspend fun searchArtists(query: String) = answer("searchArtists:$query", artistResults[query])

    override suspend fun searchPlaylists(query: String) = answer("searchPlaylists:$query", playlistResults[query])

    override suspend fun album(id: String) = answer("album:$id", albums[id])

    override suspend fun artist(id: String) = answer("artist:$id", artists[id])

    override suspend fun playlist(link: BrowseLink) = answer("playlist:${link.id}", playlists[link.id])

    override suspend fun playlistContinuation(token: String) = answer("continuation:$token", continuations[token])

    override suspend fun relatedSongs(videoId: VideoId) = answer("related:${videoId.value}", related[videoId])

    private fun <T> answer(
        call: String,
        value: T?,
    ): T {
        calls += call
        failure?.let { throw it }
        return value ?: throw IllegalStateException("No fake answer for $call")
    }
}
