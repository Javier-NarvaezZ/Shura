package io.github.javiernarvaezz.shura

import android.app.Application
import android.content.Context
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeClient
import io.github.javiernarvaezz.shura.core.network.ShuraNetwork
import io.github.javiernarvaezz.shura.core.player.AndroidAudioPlayer
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.stream.InnerTubeXStreamResolver

class ShuraApp : Application() {
    /** Created on first use from the main thread (ExoPlayer binds to the creating thread's looper). */
    val graph: AppGraph by lazy { AppGraph(this) }
}

/**
 * Manual dependency wiring for the spike (Koin is evaluated in Phase 2). One [ShuraNetwork] per process:
 * catalog, stream resolver and ExoPlayer all use its single OkHttpClient (ADR 0001 R7).
 */
class AppGraph(
    context: Context,
) {
    private val network = ShuraNetwork(debugNetworkInterceptors())

    val catalog = InnerTubeClient(network.ktor)

    val player: AudioPlayer =
        AndroidAudioPlayer(
            context = context,
            callFactory = network.okHttp,
            resolver = InnerTubeXStreamResolver(network.ktor),
        )
}
