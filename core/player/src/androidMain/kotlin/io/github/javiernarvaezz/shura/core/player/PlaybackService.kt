package io.github.javiernarvaezz.shura.core.player

import android.app.PendingIntent
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.javiernarvaezz.shura.core.stream.StreamResolutionException
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event

/**
 * Hosts the player in a [MediaSessionService], so playback continues in the background with the media
 * notification, lock screen, headset and Bluetooth controls. The UI talks to it only through [AndroidAudioPlayer].
 *
 * The service is exported (system UI, Bluetooth and other controllers connect to it), so it trusts nothing a
 * controller sends: media items are rebuilt from a validated video id, and untrusted controllers keep Media3's
 * read-only default.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var specResolver: StreamSpecResolver
    private var trace: Trace = Trace.NONE

    override fun onCreate() {
        super.onCreate()
        val dependencies = (application as PlaybackDependenciesProvider).playbackDependencies
        trace = dependencies.trace
        trace.event("player: service created")
        specResolver = StreamSpecResolver(dependencies.resolver, dependencies.quality, dependencies.trace)
        val exo = buildShuraExoPlayer(this, dependencies, specResolver)
        val player = ShuraPlayer(exo)
        // Artwork through the single OkHttpClient and its host allowlist, never Media3's default HTTP stack (R7).
        val artworkLoader =
            DataSourceBitmapLoader
                .Builder(this)
                .setDataSourceFactory(OkHttpDataSource.Factory(dependencies.callFactory))
                .build()
        session =
            MediaSession
                .Builder(this, player)
                .setCallback(SessionCallback())
                .setBitmapLoader(artworkLoader)
                .apply { launchIntent()?.let(::setSessionActivity) }
                .build()
                .also { exo.addListener(ErrorListener(it, exo)) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Opens the app when the notification is tapped. */
    private fun launchIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /** Re-resolves the current item and prepares again from where it failed, keeping the whole queue. */
    private fun retry(player: Player) {
        player.currentMediaItem
            ?.mediaId
            ?.let(::videoIdOf)
            ?.let(specResolver::invalidate) ?: return
        session?.setSessionExtras(Bundle.EMPTY)
        player.prepare()
        player.play()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<ConnectionResult> {
            val result =
                when {
                    // Our own UI: every command, plus retry.
                    controller.uid == Process.myUid() -> {
                        AcceptedResultBuilder(session)
                            .setAvailablePlayerCommands(ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                            .setAvailableSessionCommands(
                                ConnectionResult.DEFAULT_SESSION_COMMANDS
                                    .buildUpon()
                                    .add(SessionContract.RETRY)
                                    .build(),
                            ).build()
                    }

                    // System UI, lock screen and Bluetooth reach the session through these controllers. Granted
                    // explicitly since Media3 1.11 made untrusted controllers read-only by default.
                    session.isMediaNotificationController(controller) || controller.isTrusted -> {
                        AcceptedResultBuilder(session)
                            .setAvailablePlayerCommands(ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                            .setAvailableSessionCommands(ConnectionResult.DEFAULT_SESSION_COMMANDS)
                            .build()
                    }

                    else -> {
                        AcceptedResultBuilder(session, controller).build()
                    }
                }
            return Futures.immediateFuture(result)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> =
            rebuilt(mediaItems)?.let { Futures.immediateFuture(it) }
                ?: Futures.immediateFailedFuture(UnsupportedOperationException("Invalid media id"))

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val items =
                rebuilt(mediaItems)
                    ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Invalid media id"))
            mediaSession.setSessionExtras(Bundle.EMPTY)
            trace.event("player: session set items")
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, startIndex, startPositionMs))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SessionContract.RETRY.customAction) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            retry(session.player)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        private fun rebuilt(items: List<MediaItem>): List<MediaItem>? = items.map { it.toSessionItem() ?: return null }
    }

    /** Classifies errors where the cause still exists and publishes the code to controllers (R4). */
    private inner class ErrorListener(
        private val session: MediaSession,
        private val player: ExoPlayer,
    ) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val classified = PlaybackError.from(error, ::classifyPlatformError)
            if (classified is PlaybackError.Http) {
                player.currentMediaItem
                    ?.mediaId
                    ?.let(::videoIdOf)
                    ?.let(specResolver::invalidate)
            }
            session.setSessionExtras(
                Bundle().apply {
                    putString(SessionContract.EXTRA_ERROR, PlaybackErrorCodec.encode(classified))
                },
            )
            // Sanitized: error code and typed failure only, never URLs, headers or the exception message.
            val streamFailure =
                generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<StreamResolutionException>()
                    .firstOrNull()
            Log.w(TAG, "Playback failed: ${error.errorCodeName} -> $classified ${streamFailure ?: ""}".trim())
        }
    }

    private companion object {
        const val TAG = "ShuraPlayer"
    }
}
