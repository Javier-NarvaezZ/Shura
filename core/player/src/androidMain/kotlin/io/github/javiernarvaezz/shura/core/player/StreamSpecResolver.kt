package io.github.javiernarvaezz.shura.core.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream
import io.github.javiernarvaezz.shura.core.stream.StreamResolutionException
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Carries a typed stream failure through Media3, which only propagates [IOException]s from data sources. */
internal class StreamResolutionIOException(
    cause: StreamResolutionException,
) : IOException("Stream resolution failed", cause)

/**
 * Turns `shura://stream/<id>` into the signed googlevideo URL at open time, with the client's request headers.
 * The [ResolvedStream] travels in `DataSpec.customData` so the bounded-range source can size its chunks.
 */
@OptIn(UnstableApi::class)
internal class StreamSpecResolver(
    private val resolver: StreamResolver,
    private val quality: AudioQuality,
    private val trace: Trace = Trace.NONE,
) : ResolvingDataSource.Resolver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ResolvedStreamCache(scope)

    // Called on Media3's loader thread, never on the main thread.
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = StreamUri.parse(dataSpec.uri.toString()) ?: return dataSpec
        val stream =
            try {
                runBlocking {
                    trace.event("player: resolve start", mapOf("cached" to (cache.peek(videoId) != null).toString()))
                    cache.get(videoId) { resolver.resolve(videoId, quality) }
                }
            } catch (e: StreamResolutionException) {
                throw StreamResolutionIOException(e)
            }
        trace.event("player: resolve done", mapOf("profile" to stream.clientProfile))
        return dataSpec
            .buildUpon()
            .setUri(stream.url)
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + stream.requestHeaders)
            .setCustomData(stream)
            .build()
    }

    // The lock is only held for map updates, so this blocks the caller for microseconds at most.
    fun invalidate(videoId: VideoId) {
        runBlocking { cache.invalidate(videoId) }
    }

    /** Cancels resolutions still running; for the service's teardown. */
    fun close() {
        scope.cancel()
    }
}
