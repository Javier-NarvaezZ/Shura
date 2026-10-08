package io.github.javiernarvaezz.shura.feature.player

import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import io.github.javiernarvaezz.shura.feature.player.resources.Res
import io.github.javiernarvaezz.shura.feature.player.resources.error_age_restricted
import io.github.javiernarvaezz.shura.feature.player.resources.error_audio
import io.github.javiernarvaezz.shura.feature.player.resources.error_format
import io.github.javiernarvaezz.shura.feature.player.resources.error_generic
import io.github.javiernarvaezz.shura.feature.player.resources.error_offline
import io.github.javiernarvaezz.shura.feature.player.resources.error_unavailable
import io.github.javiernarvaezz.shura.feature.player.resources.error_unplayable
import org.jetbrains.compose.resources.StringResource

/**
 * User-facing text for a playback failure (ADR 0001 R4). Plain language only; technical details go to the
 * sanitized log, never to the screen.
 */
internal fun playbackErrorMessage(error: PlaybackError): StringResource =
    when (error) {
        is PlaybackError.Stream -> {
            when (error.failure) {
                StreamFailure.NoPlayableStream, StreamFailure.TokenUnavailable -> Res.string.error_unplayable

                StreamFailure.AgeRestricted -> Res.string.error_age_restricted

                StreamFailure.Unavailable -> Res.string.error_unavailable

                // The library may report non-network failures as network ones: never claim the device is offline.
                StreamFailure.Network -> Res.string.error_offline

                StreamFailure.ResolverCrashed, StreamFailure.Unknown -> Res.string.error_generic
            }
        }

        is PlaybackError.Http -> {
            Res.string.error_audio
        }

        PlaybackError.Network -> {
            Res.string.error_offline
        }

        PlaybackError.Decoding -> {
            Res.string.error_format
        }

        PlaybackError.Unknown -> {
            Res.string.error_generic
        }
    }
