package io.github.javiernarvaezz.shura

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import io.github.javiernarvaezz.shura.core.data.androidQueueStore
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
    PlaybackDependenciesProvider,
    SingletonImageLoader.Factory {
    /** Created on first use from the main thread (the media controller binds to the creating thread's looper). */
    val graph: AppGraph by lazy { AppGraph(this) }

    override val playbackDependencies: PlaybackDependencies get() = graph.playbackDependencies

    // Every Compose image request uses this loader, so artwork never reaches Coil's default HTTP stack.
    override fun newImageLoader(context: Context): ImageLoader = graph.imageLoader(context)
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
            // App-private database (excluded from backups); opened on first use, off the main thread.
            queueStore = androidQueueStore(context, Dispatchers.IO),
            prefetchNext = ::nextItemPrefetchEnabled,
        )

    val player: AudioPlayer = AndroidAudioPlayer(context, trace)

    /**
     * Artwork through the single OkHttpClient and its host allowlist (R7). The service loader stays off: it would
     * also register Coil's default network fetcher, which builds its own OkHttpClient.
     */
    fun imageLoader(context: Context): ImageLoader =
        ImageLoader
            .Builder(context)
            .serviceLoaderEnabled(false)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { network.okHttp })) }
            .build()

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
