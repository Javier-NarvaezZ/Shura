package io.github.javiernarvaezz.shura

import android.app.Application
import android.content.Context
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeClient
import io.github.javiernarvaezz.shura.core.network.ShuraNetwork
import io.github.javiernarvaezz.shura.core.player.AndroidAudioPlayer
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.stream.BotGuardPoTokenMinter
import io.github.javiernarvaezz.shura.core.stream.FilePreprocessedPlayerStore
import io.github.javiernarvaezz.shura.core.stream.InnerTubeXStreamResolver
import io.github.javiernarvaezz.shura.core.stream.WebViewJsRuntime
import java.io.File

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
    private val trace = debugTrace()

    private val network = ShuraNetwork(debugNetworkInterceptors(), debugEventListenerFactory())

    private val poTokenMinter =
        debugPoTokenMinter(BotGuardPoTokenMinter(network.ktor) { WebViewJsRuntime.create(context) })

    private val streamResolver =
        InnerTubeXStreamResolver(
            network.ktor,
            poTokenMinter,
            debugExcludedStreamProfiles(),
            trace,
            // App-private cache: generated on device from YouTube's player script, rebuilt if the system clears it.
            debugPreprocessedPlayerStore(FilePreprocessedPlayerStore(File(context.cacheDir, "ejs-players")), trace),
        )

    val catalog = InnerTubeClient(network.ktor)

    val player: AudioPlayer =
        AndroidAudioPlayer(
            context = context,
            callFactory = network.okHttp,
            resolver = debugStreamResolver(streamResolver),
            trace = trace,
        )

    init {
        debugMaybePrewarm(streamResolver, trace)
    }
}
