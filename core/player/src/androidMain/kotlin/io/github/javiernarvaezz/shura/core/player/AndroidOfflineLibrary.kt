package io.github.javiernarvaezz.shura.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import io.github.javiernarvaezz.shura.core.stream.StreamResolutionException
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [OfflineLibrary] on Android: Media3's [DownloadManager] in [storage], driven through [OfflineDownloadService]
 * (a foreground "data sync" service), and the records in [store]. A song is resolved first, for its
 * format (itag) and size: it is downloaded only if it fits [limitBytes]. Downloads stopped by the system (its time
 * limit for data sync services) are paused and resumed the next time the app starts, from the bytes already kept.
 * Runs on the main thread, where the download manager reports.
 */
@OptIn(UnstableApi::class)
class AndroidOfflineLibrary(
    private val context: Context,
    private val storage: OfflineStorage,
    private val resolver: StreamResolver,
    private val store: OfflineSongStore,
    private val scope: CoroutineScope,
    private val trackOf: (VideoId) -> VideoId? = { null },
    private val limitBytes: () -> Long? = { null },
    private val quality: AudioQuality = AudioQuality.Auto,
    private val now: () -> Long = System::currentTimeMillis,
) : OfflineLibrary {
    private val kept = store.songs.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val active = MutableStateFlow<Map<VideoId, OfflineState>>(emptyMap())

    override val states: StateFlow<Map<VideoId, OfflineState>> =
        combine(kept, active) { songs, downloads ->
            songs.filter { it.complete }.associate { it.song.videoId to OfflineState.Downloaded } + downloads
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val usedBytes: StateFlow<Long> =
        kept.map { songs -> songs.filter { it.complete }.sumOf { it.bytes } }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** For playback: the cache key of each song kept whole. */
    val cacheKeys: StateFlow<Map<VideoId, String>> =
        kept
            .map { songs -> songs.filter { it.complete }.associate { it.song.videoId to it.cacheKey } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        storage.downloads.addListener(Listener())
        storage.downloads.currentDownloads.forEach(::show)
        // Paused by the system's time limit, or by the app closing: they go on from where they were.
        DownloadService.sendResumeDownloads(context, service, false)
    }

    override fun download(song: Song) {
        val target = OfflineRules.downloadable(song, trackOf)
        if (states.value[target.videoId].let { it != null && it !is OfflineState.Failed }) return
        set(target.videoId, OfflineState.Queued)
        scope.launch { request(target) }
    }

    override fun remove(videoId: VideoId) {
        DownloadService.sendRemoveDownload(context, service, videoId.value, false)
        active.update { it - videoId }
        scope.launch { store.remove(videoId) }
    }

    override fun removeAll() {
        DownloadService.sendRemoveAllDownloads(context, service, false)
        active.value = emptyMap()
        scope.launch { store.clear() }
    }

    // Any failure is shown on the song (only its kind); the app goes on.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun request(song: Song) {
        try {
            val stream = resolver.resolve(song.videoId, quality)
            val itag = stream.itag ?: throw StreamResolutionException(StreamFailure.NoPlayableStream)
            if (!OfflineRules.fits(usedBytes.value, stream.contentLength, limitBytes())) {
                set(song.videoId, OfflineState.Failed(OfflineFailure.NoRoom))
                return
            }
            store.put(OfflineSong(song, itag, bytes = 0, savedAtMillis = now(), complete = false))
            val request =
                DownloadRequest
                    .Builder(song.videoId.value, Uri.parse(StreamUri.of(song.videoId)))
                    .setCustomCacheKey(OfflineRules.cacheKey(song.videoId, itag))
                    .build()
            DownloadService.sendAddDownload(context, service, request, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: StreamResolutionException) {
            set(song.videoId, OfflineState.Failed(OfflineFailure.Unavailable))
        } catch (e: Exception) {
            set(song.videoId, OfflineState.Failed(OfflineFailure.Other))
        }
    }

    private fun show(download: Download) {
        val videoId = videoIdOf(download.request.id) ?: return
        when (download.state) {
            Download.STATE_COMPLETED -> {
                active.update { it - videoId }
                scope.launch { store.complete(videoId, download.bytesDownloaded) }
            }

            Download.STATE_QUEUED, Download.STATE_RESTARTING -> {
                set(videoId, OfflineState.Queued)
            }

            Download.STATE_DOWNLOADING -> {
                set(videoId, OfflineState.Downloading(download.percentDownloaded.takeIf { it >= 0 }?.div(PERCENT)))
            }

            Download.STATE_STOPPED -> {
                set(videoId, OfflineState.Paused)
            }

            Download.STATE_FAILED -> {
                set(videoId, OfflineState.Failed(OfflineFailure.Other))
            }

            Download.STATE_REMOVING -> {
                active.update { it - videoId }
            }
        }
    }

    private fun set(
        videoId: VideoId,
        state: OfflineState,
    ) = active.update { it + (videoId to state) }

    private inner class Listener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) = show(download)

        override fun onDownloadRemoved(
            downloadManager: DownloadManager,
            download: Download,
        ) {
            videoIdOf(download.request.id)?.let { id -> active.update { it - id } }
        }
    }

    private companion object {
        const val PERCENT = 100f
        val service = OfflineDownloadService::class.java
    }
}
