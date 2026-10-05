package io.github.javiernarvaezz.shura.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionCommand
import io.github.javiernarvaezz.shura.core.model.Song

/** Keys and commands shared by [PlaybackService] and [AndroidAudioPlayer]. */
internal object SessionContract {
    /** Session extra holding a [PlaybackErrorCodec] code for the current player error. */
    const val EXTRA_ERROR = "io.github.javiernarvaezz.shura.player.ERROR"

    /** Re-resolves the current item's stream and prepares it again after a failure. */
    val RETRY = SessionCommand("io.github.javiernarvaezz.shura.player.RETRY", Bundle.EMPTY)
}

/** Item sent by the controller: the video id and display metadata only. The session builds the media URI. */
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
                .build(),
        ).build()

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
