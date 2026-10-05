package io.github.javiernarvaezz.shura.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionCommand
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId

/** Keys and commands shared by [PlaybackService] and [AndroidAudioPlayer]. */
internal object SessionContract {
    /** Session extra holding a [PlaybackErrorCodec] code for the current player error. */
    const val EXTRA_ERROR = "io.github.javiernarvaezz.shura.player.ERROR"

    /** Re-resolves the current item's stream and prepares it again after a failure. */
    val RETRY = SessionCommand("io.github.javiernarvaezz.shura.player.RETRY", Bundle.EMPTY)
}

private object SongExtras {
    const val VIDEO_ID = "shura.song.videoId"
    const val TITLE = "shura.song.title"
    const val ARTIST_NAMES = "shura.song.artistNames"
    const val ARTIST_IDS = "shura.song.artistIds"
    const val ALBUM_TITLE = "shura.song.albumTitle"
    const val ALBUM_ID = "shura.song.albumId"
    const val DURATION_MS = "shura.song.durationMs"
    const val THUMBNAIL_URL = "shura.song.thumbnailUrl"
    const val EXPLICIT = "shura.song.explicit"
}

private fun SongFields.toBundle(): Bundle =
    Bundle().apply {
        putString(SongExtras.VIDEO_ID, videoId)
        putString(SongExtras.TITLE, title)
        putStringArrayList(SongExtras.ARTIST_NAMES, ArrayList(artistNames))
        putStringArrayList(SongExtras.ARTIST_IDS, ArrayList(artistIds))
        putString(SongExtras.ALBUM_TITLE, albumTitle)
        putString(SongExtras.ALBUM_ID, albumId)
        durationMs?.let { putLong(SongExtras.DURATION_MS, it) }
        putString(SongExtras.THUMBNAIL_URL, thumbnailUrl)
        putBoolean(SongExtras.EXPLICIT, explicit)
    }

private fun Bundle.toSongFields(): SongFields? {
    val videoId = getString(SongExtras.VIDEO_ID) ?: return null
    return SongFields(
        videoId = videoId,
        title = getString(SongExtras.TITLE).orEmpty(),
        artistNames = getStringArrayList(SongExtras.ARTIST_NAMES).orEmpty(),
        artistIds = getStringArrayList(SongExtras.ARTIST_IDS).orEmpty(),
        albumTitle = getString(SongExtras.ALBUM_TITLE),
        albumId = getString(SongExtras.ALBUM_ID),
        durationMs = if (containsKey(SongExtras.DURATION_MS)) getLong(SongExtras.DURATION_MS) else null,
        thumbnailUrl = getString(SongExtras.THUMBNAIL_URL),
        explicit = getBoolean(SongExtras.EXPLICIT),
    )
}

/**
 * Item sent by the controller: the video id, display metadata and the full song in the extras. The session builds
 * the media URI.
 */
internal fun Song.toMediaItem(): MediaItem =
    MediaItem
        .Builder()
        .setMediaId(videoId.value)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setArtist(artistNames)
                .setAlbumTitle(album?.title)
                .setArtworkUri(thumbnailUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(toFields().toBundle())
                .build(),
        ).build()

/**
 * The song of an item: from the extras when present and consistent with the media id, otherwise rebuilt from the
 * display metadata. Null when the media id is not a valid video id.
 */
internal fun MediaItem.toSong(): Song? {
    val videoId = videoIdOf(mediaId) ?: return null
    val fromExtras =
        mediaMetadata.extras
            ?.toSongFields()
            ?.takeIf { it.videoId == videoId.value }
            ?.toSong()
    return fromExtras ?: songFromDisplayMetadata(videoId)
}

private fun MediaItem.songFromDisplayMetadata(videoId: VideoId): Song? {
    val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
    return Song(videoId, title, listOfNotNull(artist?.let { Artist(it) }))
}

/**
 * Rebuilds an item received from any controller: only a valid video id is accepted, and the URI is always the
 * app's own stream URI, so a controller can never make the player fetch an arbitrary URL.
 */
internal fun MediaItem.toSessionItem(): MediaItem? =
    videoIdOf(mediaId)?.let { videoId ->
        MediaItem
            .Builder()
            .setMediaId(videoId.value)
            .setUri(StreamUri.of(videoId))
            .setMediaMetadata(mediaMetadata)
            .build()
    }
