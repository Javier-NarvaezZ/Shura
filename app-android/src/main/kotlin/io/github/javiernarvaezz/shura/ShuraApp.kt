package io.github.javiernarvaezz.shura

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import io.github.javiernarvaezz.shura.core.auth.AccountFingerprint
import io.github.javiernarvaezz.shura.core.auth.CookieJarText
import io.github.javiernarvaezz.shura.core.auth.KeystoreSessionStore
import io.github.javiernarvaezz.shura.core.auth.SapisidHash
import io.github.javiernarvaezz.shura.core.auth.Session
import io.github.javiernarvaezz.shura.core.auth.SessionHealthLog
import io.github.javiernarvaezz.shura.core.data.AndroidAppStorage
import io.github.javiernarvaezz.shura.core.innertube.AccountCredentials
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeAccountClient
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeClient
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeConfig
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeException
import io.github.javiernarvaezz.shura.core.lyrics.LrcLibClient
import io.github.javiernarvaezz.shura.core.lyrics.LyricsOffsets
import io.github.javiernarvaezz.shura.core.lyrics.LyricsRepository
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
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
import io.github.javiernarvaezz.shura.feature.library.AccountLibraryModel
import io.github.javiernarvaezz.shura.feature.library.AccountLibrarySnapshot
import io.github.javiernarvaezz.shura.feature.library.asTracks
import io.github.javiernarvaezz.shura.feature.settings.AccountCheck
import io.github.javiernarvaezz.shura.feature.settings.AccountLikes
import io.github.javiernarvaezz.shura.feature.settings.AccountModel
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
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
        // Main process only, before any WebView exists: nothing of a previous sign-in may survive (ADR 0002). The
        // sign-in process (`:signin`) must not touch the main process's WebView data.
        if (isMainProcess()) WebViewData.wipeAtStart(this)
    }

    private fun isMainProcess(): Boolean {
        val name =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getProcessName()
            } else {
                getSystemService(ActivityManager::class.java)
                    ?.runningAppProcesses
                    ?.firstOrNull { it.pid == Process.myPid() }
                    ?.processName
            }
        return name == null || name == packageName
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

    /**
     * Songs saved before album rows were mapped to their tracks still have the artist's video id: moved once to the
     * track, each album page asked once (a network failure leaves the rest for later; an album gone is skipped).
     */
    private val savedSongRepair = storage.savedSongRepair(::albumTracks)

    /** Videos known to stand for an album track, and that track. */
    private val videoTracks = savedSongRepair.tracks.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    @Suppress("SwallowedException") // A network failure only means "later"; anything else skips the album.
    private suspend fun albumTracks(albumId: String): Map<VideoId, VideoId>? =
        try {
            catalog.album(albumId).trackOfVideo
        } catch (e: InnerTubeException.Network) {
            null
        } catch (e: InnerTubeException) {
            emptyMap()
        }

    val store = storage.queue

    val settings = storage.settings

    /**
     * One line per day with whether Google still accepted the session ("alive" or "expired"), to measure how long a
     * session lasts. Read with `dumpsys activity <package>/.MainActivity`.
     */
    val sessionHealth = SessionHealthLog(File(context.noBackupFilesDir, "session-health.txt"))

    /** Account requests (as the signed-in user), apart from the anonymous catalog client. */
    internal val accountClient = InnerTubeAccountClient(network.ktor, catalogConfig)

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
            onChecked = { alive -> sessionHealth.record(alive) },
            onSignedOut = { storage.pendingLikes.clear() },
        )

    /**
     * The local library (liked songs, saved albums and playlists, followed artists), whose likes also reach the
     * signed-in account, queued while offline (the queue is flushed when the network comes back).
     */
    private val accountLikes =
        AccountLikes(
            local = storage.library,
            pending = storage.pendingLikes,
            account = account,
            send = { session, videoId, liked ->
                val credentials = checkNotNull(credentialsOf(session)) { "Session cannot sign" }
                accountClient.setLiked(credentials, videoId, liked)
            },
            fingerprint = AccountFingerprint::of,
            scope = scope,
            now = System::currentTimeMillis,
            videosOf = { track ->
                videoTracks.value
                    .filterValues { it == track }
                    .keys
                    .toList()
            },
        )

    val library = accountLikes.library

    /** The signed-in account's library for the Library tab, refreshed at most every 30 minutes. */
    val accountLibrary =
        AccountLibraryModel(account.state, ::loadAccountLibrary, System::currentTimeMillis, scope)

    private suspend fun loadAccountLibrary(): AccountLibrarySnapshot? {
        val credentials = account.session()?.let(::credentialsOf) ?: return null
        return coroutineScope {
            val playlists = async { accountClient.playlists(credentials) }
            val albums = async { accountClient.albums(credentials) }
            val artists = async { accountClient.artists(credentials) }
            val (liked, likedCookies) = likedSongs(credentials)
            val pages = listOf(playlists.await(), albums.await(), artists.await())
            account.keepCookies(pages.flatMap { it.setCookies } + likedCookies)
            AccountLibrarySnapshot(liked, playlists.await().value, albums.await().value, artists.await().value)
        }
    }

    /** The account's liked songs, page by page up to [MAX_LIKED]; with the cookies the responses rotated. */
    private suspend fun likedSongs(credentials: AccountCredentials): Pair<List<Song>, List<String>> {
        val songs = mutableListOf<Song>()
        val cookies = mutableListOf<String>()
        var continuation: String? = null
        do {
            val page = accountClient.likedSongs(credentials, continuation)
            songs += page.value.tracks
            cookies += page.setCookies
            continuation = page.value.continuation
        } while (continuation != null && songs.size < MAX_LIKED)
        return songs.take(MAX_LIKED).asTracks(videoTracks.value) to cookies
    }

    private suspend fun checkSession(session: Session): AccountCheck {
        val credentials = credentialsOf(session) ?: return AccountCheck(false, null, emptyList())
        val menu = accountClient.accountMenu(credentials)
        return AccountCheck(menu.signedIn, menu.accountName, menu.setCookies)
    }

    /** What signs a request as [session]'s user, freshly timed; null when its cookies cannot sign. */
    internal fun credentialsOf(session: Session): AccountCredentials? {
        val secret = SapisidHash.secret(session.cookies) ?: return null
        return AccountCredentials(
            cookie = CookieJarText.header(session.cookies),
            authorization = SapisidHash.header(secret, YOUTUBE_MUSIC, System.currentTimeMillis() / MILLIS),
            onBehalfOfUser = session.onBehalfOfUser,
        )
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

    /** The user's timing fix for each song's lyrics, in the app database. */
    val lyricsOffsets: LyricsOffsets = storage.lyricsOffsets

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
        // Likes queued while offline go to the account as soon as there is a network again.
        scope.launch { networkRegained.collect { accountLikes.flush() } }
        scope.launch {
            var repaired = savedSongRepair.repairOnce()
            networkRegained.takeWhile { !repaired }.collect { repaired = savedSongRepair.repairOnce() }
        }
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
private const val MAX_LIKED = 500
