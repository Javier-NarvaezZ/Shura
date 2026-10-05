package io.github.javiernarvaezz.shura

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeClient
import io.github.javiernarvaezz.shura.core.network.ShuraNetwork
import io.github.javiernarvaezz.shura.core.player.AndroidAudioPlayer
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlaybackDependencies
import io.github.javiernarvaezz.shura.core.player.PlaybackDependenciesProvider
import io.github.javiernarvaezz.shura.core.player.PlaybackWarmup
import io.github.javiernarvaezz.shura.core.stream.BotGuardPoTokenMinter
import io.github.javiernarvaezz.shura.core.stream.FilePreprocessedPlayerStore
import io.github.javiernarvaezz.shura.core.stream.InnerTubeXStreamResolver
import io.github.javiernarvaezz.shura.core.stream.WebViewJsRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class ShuraApp :
    Application(),
    PlaybackDependenciesProvider {
    /** Created on first use from the main thread (the media controller binds to the creating thread's looper). */
    val graph: AppGraph by lazy { AppGraph(this) }

    override val playbackDependencies: PlaybackDependencies get() = graph.playbackDependencies
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

    /** Used by `PlaybackService`, where the player lives; media and artwork go through the single client. */
    val playbackDependencies =
        PlaybackDependencies(
            callFactory = network.okHttp,
            resolver = debugStreamResolver(streamResolver),
            trace = trace,
        )

    val player: AudioPlayer = AndroidAudioPlayer(context, trace)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /**
     * One per process: at most one warm-up, never in battery saver, nor on a metered network while Data Saver
     * restricts this app.
     */
    val playbackWarmup =
        PlaybackWarmup(
            warmUp = debugWarmUp(streamResolver::warmUp, trace),
            scope = scope,
            isEnabled = ::playbackWarmupEnabled,
            isPowerSaveMode = { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true },
            isActiveNetworkMetered = { connectivity?.isActiveNetworkMetered != false },
            isDataSaverRestricting = {
                connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            },
            trace = trace,
        )

    init {
        debugMaybePrewarm(streamResolver, trace)
    }
}
