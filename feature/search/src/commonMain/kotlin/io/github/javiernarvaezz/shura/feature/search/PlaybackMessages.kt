package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.stream.StreamFailure

/**
 * User-facing text for a playback failure (ADR 0001 R4). Plain language only; technical details go to the
 * sanitized log, never to the screen. Spike strings, hardcoded in Spanish until resources land.
 */
internal fun playbackErrorMessage(error: PlaybackError): String =
    when (error) {
        is PlaybackError.Stream -> {
            when (error.failure) {
                StreamFailure.NoPlayableStream, StreamFailure.TokenUnavailable -> {
                    "Esta canción no se puede reproducir por ahora."
                }

                StreamFailure.AgeRestricted -> {
                    "Esta canción tiene restricción de edad y necesita una cuenta."
                }

                StreamFailure.Unavailable -> {
                    "Esta canción ya no está disponible."
                }

                StreamFailure.Network -> {
                    OFFLINE
                }

                StreamFailure.ResolverCrashed, StreamFailure.Unknown -> {
                    GENERIC
                }
            }
        }

        is PlaybackError.Http -> {
            "No se pudo cargar el audio. Inténtalo de nuevo."
        }

        PlaybackError.Network -> {
            OFFLINE
        }

        PlaybackError.Decoding -> {
            "Este dispositivo no puede reproducir el formato de esta canción."
        }

        PlaybackError.Unknown -> {
            GENERIC
        }
    }

// The library may report non-network failures as network ones, so this must not claim the device is offline.
private const val OFFLINE = "No se pudo cargar la canción. Revisa tu conexión e inténtalo de nuevo."
private const val GENERIC = "Algo salió mal al cargar la canción."
