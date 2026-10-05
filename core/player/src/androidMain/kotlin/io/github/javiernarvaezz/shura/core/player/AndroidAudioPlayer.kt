package io.github.javiernarvaezz.shura.core.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.StreamResolutionException
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call

/**
 * Foreground-only Media3 player for the spike (no MediaSession yet; Phase 2).
 *
 * All media traffic goes through [callFactory], the app's single OkHttpClient (ADR 0001 R7): ExoPlayer is built
 * with an explicit media source factory so it never falls back to its default HTTP stack. Call from the main thread.
 */
@OptIn(UnstableApi::class)
class AndroidAudioPlayer(
    context: Context,
    callFactory: Call.Factory,
    resolver: StreamResolver,
    quality: AudioQuality = AudioQuality.Auto,
) : AudioPlayer {
    private val specResolver = StreamSpecResolver(resolver, quality)
    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private var current: Song? = null

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val player: ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(
                ProgressiveMediaSource.Factory(
                    ResolvingDataSource.Factory(
                        BoundedRangeDataSource.Factory(OkHttpDataSource.Factory(callFactory)),
                        specResolver,
                    ),
                ),
            ).build()
            .apply { addListener(Listener()) }

    override fun play(song: Song) {
        current = song
        mutableState.value = PlaybackState.Loading(song)
        player.setMediaItem(
            MediaItem
                .Builder()
                .setMediaId(song.videoId.value)
                .setUri(StreamUri.of(song.videoId))
                .build(),
        )
        player.prepare()
        player.play()
    }

    override fun pause() = player.pause()

    override fun resume() = player.play()

    override fun retry() {
        val song = current ?: return
        specResolver.invalidate(song.videoId)
        play(song)
    }

    override fun stop() {
        player.stop()
        current = null
        mutableState.value = PlaybackState.Idle
    }

    override fun release() {
        player.release()
        current = null
        mutableState.value = PlaybackState.Idle
    }

    private inner class Listener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val song = current ?: return
            if (isPlaying) {
                mutableState.value = PlaybackState.Playing(song)
            } else if (player.playbackState == Player.STATE_READY) {
                mutableState.value = PlaybackState.Paused(song)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val song = current ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> mutableState.value = PlaybackState.Loading(song)
                Player.STATE_ENDED -> mutableState.value = PlaybackState.Ended(song)
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val song = current ?: return
            val classified = PlaybackError.from(error, ::classifyPlatformError)
            if (classified is PlaybackError.Http) specResolver.invalidate(song.videoId)
            mutableState.value = PlaybackState.Failed(song, classified)
            // Sanitized: error code and typed failure only, never URLs, headers or the exception message.
            val streamFailure =
                generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<StreamResolutionException>()
                    .firstOrNull()
            Log.w(TAG, "Playback failed: ${error.errorCodeName} -> $classified ${streamFailure ?: ""}".trim())
        }
    }

    private fun classifyPlatformError(error: Throwable): PlaybackError? =
        when (error) {
            is HttpDataSource.InvalidResponseCodeException -> {
                PlaybackError.Http(error.responseCode)
            }

            is HttpDataSource.HttpDataSourceException -> {
                PlaybackError.Network
            }

            is PlaybackException -> {
                when (error.errorCode) {
                    in DECODING_ERRORS -> PlaybackError.Decoding

                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    -> PlaybackError.Network

                    else -> null
                }
            }

            else -> {
                null
            }
        }

    private companion object {
        const val TAG = "ShuraPlayer"
        val DECODING_ERRORS =
            setOf(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            )
    }
}
