package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import io.github.javiernarvaezz.shura.core.stream.Trace
import okhttp3.Call

/** What [PlaybackService] needs from the app. All media and artwork traffic uses [callFactory] (ADR 0001 R7). */
class PlaybackDependencies(
    val callFactory: Call.Factory,
    val resolver: StreamResolver,
    val quality: AudioQuality = AudioQuality.Auto,
    val trace: Trace = Trace.NONE,
)

/** Implemented by the `Application`, so the service gets its dependencies without a DI framework. */
interface PlaybackDependenciesProvider {
    val playbackDependencies: PlaybackDependencies
}
