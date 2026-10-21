package io.github.javiernarvaezz.shura.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.AesCipherDataSink
import androidx.media3.datasource.AesCipherDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import io.github.javiernarvaezz.shura.core.stream.Trace
import okhttp3.Call
import java.io.File
import java.util.concurrent.Executors

/**
 * Where the songs kept to listen without a connection live: a cache in `noBackupFilesDir` (private, outside
 * backups, never exported or shared), every byte encrypted with AES-CTR under [key] (256 bits, kept wrapped by the
 * Android Keystore). Files are keyed "<video id>:<itag>"; nothing is evicted to make room. Downloads resolve their
 * stream like playback does, through the single client: a URL that expires mid-download is resolved again and the
 * download goes on from the bytes already kept. Create one per process, on the main thread.
 */
@OptIn(UnstableApi::class)
class OfflineStorage(
    context: Context,
    private val key: ByteArray,
    callFactory: Call.Factory,
    resolver: StreamResolver,
    quality: AudioQuality = AudioQuality.Auto,
    trace: Trace = Trace.NONE,
    /** True when the key was replaced (the old one could not be unwrapped): what was kept is unreadable. */
    keyReplaced: Boolean = false,
) {
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val directory = File(context.noBackupFilesDir, DIRECTORY)

    init {
        if (keyReplaced) SimpleCache.delete(directory, databaseProvider)
    }

    private val cache = SimpleCache(directory, NoOpCacheEvictor(), databaseProvider)

    private val downloadResolver = StreamSpecResolver(resolver, quality, trace)

    // A URL refused mid-download (expired) is forgotten, so the download manager's retry resolves a new one.
    private val upstream: DataSource.Factory =
        RejectedUrlDataSource.Factory(
            ResolvingDataSource.Factory(
                BoundedRangeDataSource.Factory(OkHttpDataSource.Factory(callFactory), trace),
                downloadResolver,
            ),
            downloadResolver::invalidate,
        )

    /** Downloads one song at a time, and by default only on an unmetered network ("Solo con Wi-Fi"). */
    val downloads: DownloadManager =
        DownloadManager(
            context,
            DefaultDownloadIndex(databaseProvider),
            DefaultDownloaderFactory(writing(upstream), Executors.newSingleThreadExecutor()),
        ).apply {
            maxParallelDownloads = 1
            minRetryCount = DOWNLOAD_RETRIES
            requirements = requirementsFor(OfflineNetwork.Unmetered)
        }

    /** Which network downloads may use. */
    fun setNetwork(network: OfflineNetwork) {
        downloads.requirements = requirementsFor(network)
    }

    /**
     * Playback's sources: what is kept is read (and decrypted) from the cache, with no network and no resolution;
     * the rest streams from [playbackUpstream] as before. Streaming writes nothing here. A song's key is looked up
     * ([keyOf]) when it opens, so a queue restored before the records are read still plays what is kept.
     */
    fun reading(
        playbackUpstream: DataSource.Factory,
        keyOf: (VideoId) -> String?,
    ): DataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(cache)
            .setCacheKeyFactory { spec ->
                StreamUri.parse(spec.uri.toString())?.let(keyOf) ?: spec.key ?: spec.uri.toString()
            }.setCacheReadDataSourceFactory { AesCipherDataSource(key, FileDataSource()) }
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(playbackUpstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Removes the kept files and their records in the cache (the download index keeps its own). */
    fun clearCache() {
        cache.keys.toList().forEach(cache::removeResource)
    }

    private fun writing(source: DataSource.Factory): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(cache)
            .setCacheReadDataSourceFactory { AesCipherDataSource(key, FileDataSource()) }
            .setCacheWriteDataSinkFactory {
                // A scratch buffer, so the downloader's own buffer is not encrypted in place.
                AesCipherDataSink(key, CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE), ByteArray(SCRATCH))
            }.setUpstreamDataSourceFactory(source)

    private fun requirementsFor(network: OfflineNetwork) =
        Requirements(
            when (network) {
                OfflineNetwork.Any -> Requirements.NETWORK
                OfflineNetwork.Unmetered -> Requirements.NETWORK_UNMETERED
            },
        )

    private companion object {
        const val DIRECTORY = "offline"
        const val DOWNLOAD_RETRIES = 5
        const val SCRATCH = 64 * 1024
    }
}
