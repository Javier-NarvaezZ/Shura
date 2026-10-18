package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.javiernarvaezz.shura.core.data.db.Followed_artist
import io.github.javiernarvaezz.shura.core.data.db.Liked_song
import io.github.javiernarvaezz.shura.core.data.db.Saved_album
import io.github.javiernarvaezz.shura.core.data.db.Saved_playlist
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.Library
import io.github.javiernarvaezz.shura.core.player.SongFields
import io.github.javiernarvaezz.shura.core.player.toFields
import io.github.javiernarvaezz.shura.core.player.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * [Library] in the app database, read and written on [ioContext]. The lists follow the tables, so a change shows
 * once it is stored (milliseconds). A row that cannot be read back is left out of its list.
 */
class SqlLibrary(
    private val database: ShuraDatabase,
    private val scope: CoroutineScope,
    private val ioContext: CoroutineContext,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : Library {
    private val queries get() = database.libraryQueries

    override val likedSongs: StateFlow<List<Song>> = queries.likedSongs().list { it.toSong() }

    override val albums: StateFlow<List<AlbumSummary>> = queries.albums().list { it.toAlbum() }

    override val playlists: StateFlow<List<PlaylistSummary>> = queries.playlists().list { it.toPlaylist() }

    override val artists: StateFlow<List<ArtistSummary>> = queries.artists().list { it.toArtist() }

    override fun setLiked(
        song: Song,
        liked: Boolean,
    ) = write {
        if (liked) {
            val fields = song.toFields()
            queries.like(
                video_id = fields.videoId,
                title = fields.title,
                artist_names = encodeStrings(fields.artistNames),
                artist_ids = encodeStrings(fields.artistIds),
                album_title = fields.albumTitle,
                album_id = fields.albumId,
                duration_ms = fields.durationMs,
                thumbnail_url = fields.thumbnailUrl,
                explicit = fields.explicit.toLong(),
                liked_at = now(),
                video = fields.video.toLong(),
            )
        } else {
            queries.unlike(song.videoId.value)
        }
    }

    override fun setSaved(
        album: AlbumSummary,
        saved: Boolean,
    ) = write {
        if (saved) {
            queries.saveAlbum(
                id = album.id,
                title = album.title,
                artist_names = encodeStrings(album.artists.map { it.name }),
                artist_ids = encodeStrings(album.artists.map { it.id.orEmpty() }),
                year = album.year?.toLong(),
                type_label = album.typeLabel,
                thumbnail_url = album.thumbnailUrl,
                explicit = album.isExplicit.toLong(),
                saved_at = now(),
            )
        } else {
            queries.removeAlbum(album.id)
        }
    }

    override fun setSaved(
        playlist: PlaylistSummary,
        saved: Boolean,
    ) = write {
        if (saved) {
            queries.savePlaylist(playlist.id, playlist.title, playlist.author, playlist.thumbnailUrl, now())
        } else {
            queries.removePlaylist(playlist.id)
        }
    }

    override fun setFollowed(
        artist: ArtistSummary,
        followed: Boolean,
    ) = write {
        if (followed) {
            queries.follow(artist.id, artist.name, artist.thumbnailUrl, now())
        } else {
            queries.unfollow(artist.id)
        }
    }

    private fun write(change: () -> Unit) {
        scope.launch(ioContext) { change() }
    }

    /** The query's rows as a list kept up to date, leaving out rows that cannot be read. */
    private fun <R : Any, T> Query<R>.list(read: (R) -> T?): StateFlow<List<T>> =
        asFlow()
            .mapToList(ioContext)
            .map { rows -> rows.mapNotNull { row -> runCatching { read(row) }.getOrNull() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private companion object {
        fun Liked_song.toSong(): Song? =
            SongFields(
                videoId = video_id,
                title = title,
                artistNames = decodeStrings(artist_names),
                artistIds = decodeStrings(artist_ids),
                albumTitle = album_title,
                albumId = album_id,
                durationMs = duration_ms,
                thumbnailUrl = thumbnail_url,
                explicit = explicit != 0L,
                video = video != 0L,
            ).toSong()

        fun Saved_album.toAlbum(): AlbumSummary {
            val names = decodeStrings(artist_names)
            val ids = decodeStrings(artist_ids)
            require(names.size == ids.size) { "Artist fields mismatch" }
            return AlbumSummary(
                id = id,
                title = title,
                artists = names.zip(ids) { name, artistId -> Artist(name, artistId.ifEmpty { null }) },
                year = year?.toInt(),
                typeLabel = type_label,
                thumbnailUrl = thumbnail_url,
                isExplicit = explicit != 0L,
            )
        }

        fun Saved_playlist.toPlaylist() = PlaylistSummary(id, title, author, thumbnail_url)

        fun Followed_artist.toArtist() = ArtistSummary(id, name, thumbnail_url)
    }
}
