package io.github.javiernarvaezz.shura.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

/** Implemented by the `Application`, so the download service gets the offline storage without a DI framework. */
interface OfflineStorageProvider {
    val offlineStorage: OfflineStorage

    /** The app's texts for the download notification and its channel. */
    val offlineNotificationTexts: OfflineNotificationTexts
}

class OfflineNotificationTexts(
    val channelName: String,
    val channelDescription: String,
    val downloading: String,
)

/**
 * Keeps songs to listen without a connection while the app is in the background: a foreground "data sync" service
 * with a progress notification. No scheduler: downloads left waiting go on the next time the app opens. When the
 * system stops it at its time limit for data sync services (Android 15), downloads are paused first, so they go on
 * later from the bytes already kept.
 */
@OptIn(UnstableApi::class)
class OfflineDownloadService :
    // The channel is created here with the app's texts: this module has no resources of its own.
    DownloadService(NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL) {
    private val provider get() = application as OfflineStorageProvider
    private val storage get() = provider.offlineStorage

    override fun onCreate() {
        val texts = provider.offlineNotificationTexts
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, texts.channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = texts.channelDescription
            },
        )
        super.onCreate()
    }

    override fun getDownloadManager(): DownloadManager = storage.downloads

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
            this,
            androidx.media3.session.R.drawable.media3_notification_small_icon,
            null,
            provider.offlineNotificationTexts.downloading,
            downloads,
            notMetRequirements,
        )

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        storage.downloads.pauseDownloads()
        super.onTimeout(startId, fgsType)
    }

    private companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "offline"
    }
}
