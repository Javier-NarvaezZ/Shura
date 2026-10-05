package io.github.javiernarvaezz.shura

import android.app.Application
import android.content.Context
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeClient
import io.github.javiernarvaezz.shura.core.network.ShuraNetwork
import io.github.javiernarvaezz.shura.core.player.AndroidAudioPlayer
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.stream.BotGuardPoTokenMinter
import io.github.javiernarvaezz.shura.core.stream.InnerTubeXStreamResolver
import io.github.javiernarvaezz.shura.core.stream.WebViewJsRuntime

class ShuraApp : Application() {
    /** Created on first use from the main thread (ExoPlayer binds to the creating thread's looper). */
    val graph: AppGraph by lazy { AppGraph(this) }
}

/**
 * Manual dependency wiring (Koin is evaluated in Phase 2). One [ShuraNetwork] per process: catalog, stream
 * resolver, PoToken minter and ExoPlayer all use its single OkHttpClient (ADR 0001 R7). The minter's WebView only
 * computes and has no network (R2).
 */
class AppGraph(
    context: Context,
) {
    private val network = ShuraNetwork(debugNetworkInterceptors())

    private val poTokenMinter =
        debugPoTokenMinter(BotGuardPoTokenMinter(network.ktor) { WebViewJsRuntime.create(context) })

    val catalog = InnerTubeClient(network.ktor)

    val player: AudioPlayer =
        AndroidAudioPlayer(
            context = context,
            callFactory = network.okHttp,
            resolver =
                debugStreamResolver(
                    InnerTubeXStreamResolver(network.ktor, poTokenMinter, debugExcludedStreamProfiles()),
                ),
        )
}
