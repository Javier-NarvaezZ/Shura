package io.github.javiernarvaezz.shura

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import io.github.javiernarvaezz.shura.core.auth.CookieJarText
import io.github.javiernarvaezz.shura.core.auth.KeystoreSessionStore
import io.github.javiernarvaezz.shura.core.auth.SapisidHash
import io.github.javiernarvaezz.shura.core.auth.Session
import io.github.javiernarvaezz.shura.core.data.AndroidAppStorage
import io.github.javiernarvaezz.shura.core.innertube.AccountCredentials
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeAccountClient
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeClient
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeConfig
import io.github.javiernarvaezz.shura.core.lyrics.LrcLibClient
import io.github.javiernarvaezz.shura.core.lyrics.LyricsRepository
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
import io.github.javiernarvaezz.shura.feature.settings.AccountCheck
import io.github.javiernarvaezz.shura.feature.settings.AccountModel
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.Locale

class ShuraApp :
    Application(),
    PlaybackDependenciesProvider,
    SingletonImageLoader.Factory {
    /** Created on first use from the main thread (the media controller binds to the creating thread's looper). */
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // Before any WebView exists: nothing of a previous sign-in may survive (ADR 0002).
        WebViewData.wipeAtStart(this)
    }

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

    /** Catalog language and region follow the device locale at startup. */
    private val catalogConfig = Locale.getDefault().let { InnerTubeConfig.forDevice(it.language, it.country) }

    val catalog = InnerTubeClient(network.ktor, catalogConfig)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Queue, play history, settings, lyrics and library: the app-private database, used off the main thread. It is the
     * only data in Android backups and device transfers (`res/xml/data_extraction_rules.xml`).
     */
    private val storage = AndroidAppStorage(context, Dispatchers.IO, scope)

    val store = storage.queue

    val settings = storage.settings

    /** Liked songs, saved albums and playlists, followed artists: the local library, in the app database. */
    val library = storage.library

    /** Account requests (as the signed-in user), apart from the anonymous catalog client. */
    private val accountClient = InnerTubeAccountClient(network.ktor, catalogConfig)

    /**
     * The YouTube Music account (ADR 0002): the session encrypted with a Keystore key, checked with Google at start,
     * and signed out at Google before it is deleted here.
     */
    val account =
        AccountModel(
            store = KeystoreSessionStore.create(context),
            check = ::checkSession,
            signOutRemotely = ::signOutAtGoogle,
            ioContext = Dispatchers.IO,
            scope = scope,
        )

    private suspend fun checkSession(session: Session): AccountCheck {
        val secret = SapisidHash.secret(session.cookies) ?: return AccountCheck(false, null, emptyList())
        val credentials =
            AccountCredentials(
                cookie = CookieJarText.header(session.cookies),
                authorization = SapisidHash.header(secret, YOUTUBE_MUSIC, System.currentTimeMillis() / MILLIS),
                onBehalfOfUser = session.onBehalfOfUser,
            )
        val menu = accountClient.accountMenu(credentials)
        return AccountCheck(menu.signedIn, menu.accountName, menu.setCookies)
    }

    /** Google's sign-out URL with Google's cookies ends the session on Google's side too. */
    private suspend fun signOutAtGoogle(session: Session) {
        network.ktor.get(GOOGLE_SIGN_OUT) { header(HttpHeaders.Cookie, CookieJarText.header(session.googleCookies)) }
    }

    /**
     * Lyrics from LRCLIB through the single client (`lrclib.net` is on the allowlist), cached in the app database.
     * LRCLIB asks clients to name themselves: app name, version and homepage.
     */
    val lyrics =
        LyricsRepository(
            fetch =
                LrcLibClient(
                    network.ktor,
                    LrcLibClient.userAgent(APP_NAME, versionName(context), HOMEPAGE),
                )::lyrics,
            store = storage.lyrics,
        )

    /** Used by `PlaybackService`, where the player lives; media and artwork go through the single client. */
    val playbackDependencies =
        PlaybackDependencies(
            callFactory = network.okHttp,
            resolver = debugStreamResolver(streamResolver),
            trace = trace,
            queueStore = store,
            prefetchNext = ::nextItemPrefetchEnabled,
            relatedSongs = catalog::relatedSongs,
            autoplay = settings.autoplay,
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

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** One registration per process, for the app's lifetime. */
    val networkRegained = networkRegained(connectivity)

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

private const val APP_NAME = "Sunloop"
private const val HOMEPAGE = "https://github.com/Javier-NarvaezZ/Shura"

/** The installed version name (from the manifest), so it never drifts from the build. */
private fun versionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

private const val YOUTUBE_MUSIC = "https://music.youtube.com"
private const val GOOGLE_SIGN_OUT = "https://accounts.google.com/Logout"
private const val MILLIS = 1000
