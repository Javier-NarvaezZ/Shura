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
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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
) : ResolvingDataSource.Resolver {
    private val cache = ConcurrentHashMap<VideoId, ResolvedStream>()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = StreamUri.parse(dataSpec.uri.toString()) ?: return dataSpec
        val stream = cache[videoId]?.takeIf { it.isFresh() } ?: resolve(videoId).also { cache[videoId] = it }
        return dataSpec
            .buildUpon()
            .setUri(stream.url)
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + stream.requestHeaders)
            .setCustomData(stream)
            .build()
    }

    fun invalidate(videoId: VideoId) {
        cache.remove(videoId)
    }

    // Called on Media3's loader thread, never on the main thread.
    private fun resolve(videoId: VideoId): ResolvedStream =
        try {
            runBlocking { resolver.resolve(videoId, quality) }
        } catch (e: StreamResolutionException) {
            throw StreamResolutionIOException(e)
        }

    private fun ResolvedStream.isFresh(): Boolean {
        val expiresAt = expiresAt ?: return true
        return expiresAt - Clock.System.now() > MIN_REMAINING_VALIDITY
    }

    private companion object {
        val MIN_REMAINING_VALIDITY = 60.seconds
    }
}
