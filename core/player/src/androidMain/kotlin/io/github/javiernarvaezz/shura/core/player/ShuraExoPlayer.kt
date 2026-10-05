package io.github.javiernarvaezz.shura.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event

/**
 * Builds the app's ExoPlayer. Media goes through the single OkHttpClient (ADR 0001 R7): the explicit media source
 * factory means ExoPlayer never falls back to its default HTTP stack. Handles audio focus (calls, other apps),
 * pauses when headphones are unplugged and keeps CPU and Wi-Fi awake while playing.
 */
@OptIn(UnstableApi::class)
internal fun buildShuraExoPlayer(
    context: Context,
    dependencies: PlaybackDependencies,
    specResolver: StreamSpecResolver,
): ExoPlayer {
    val trace = dependencies.trace
    val audioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    trace.event(
        "player: buffer config",
        mapOf(
            "bufferForPlaybackMs" to DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS.toString(),
            "minBufferMs" to DefaultLoadControl.DEFAULT_MIN_BUFFER_MS.toString(),
        ),
    )
    return ExoPlayer
        .Builder(context)
        .setMediaSourceFactory(
            ProgressiveMediaSource.Factory(
                ResolvingDataSource.Factory(
                    BoundedRangeDataSource.Factory(OkHttpDataSource.Factory(dependencies.callFactory), trace),
                    specResolver,
                ),
            ),
        ).setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply { if (trace !== Trace.NONE) addAnalyticsListener(TraceAnalytics(trace)) }
}

/** Classifies platform exceptions for [PlaybackError.from]: HTTP status, network or decoding. */
@OptIn(UnstableApi::class)
internal fun classifyPlatformError(error: Throwable): PlaybackError? =
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

private val DECODING_ERRORS =
    setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    )
