package io.github.javiernarvaezz.shura.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream

/**
 * Splits one logical read into bounded `Range` requests of [ResolvedStream.rangeChunkSizeBytes] when the
 * stream requires it; googlevideo throttles or cuts open-ended ranges. Other streams pass straight through.
 */
@OptIn(UnstableApi::class)
internal class BoundedRangeDataSource(
    private val upstream: DataSource,
) : DataSource {
    class Factory(
        private val upstream: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = BoundedRangeDataSource(upstream.createDataSource())
    }

    private var spec: DataSpec? = null
    private var chunkSize = 0L
    private var position = 0L
    private var endExclusive: Long? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val stream = dataSpec.customData as? ResolvedStream
        chunkSize = stream?.takeIf { it.requiresBoundedRange }?.rangeChunkSizeBytes ?: 0L
        if (chunkSize <= 0L) {
            spec = null
            return upstream.open(dataSpec)
        }
        spec = dataSpec
        position = dataSpec.position
        val requestedLength = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() }
        endExclusive = requestedLength?.let { dataSpec.position + it } ?: stream?.contentLength
        openChunk()
        if (endExclusive == null) {
            endExclusive = RangeChunks.totalFromContentRange(upstream.responseHeaders["Content-Range"]?.firstOrNull())
        }
        return endExclusive?.let { it - dataSpec.position } ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        // Known end of the logical read; null when passing through or when the size is unknown.
        val end = endExclusive.takeIf { spec != null }
        var read =
            if (end != null && position >= end) {
                C.RESULT_END_OF_INPUT
            } else {
                upstream.read(buffer, offset, length)
            }
        // A chunk ended before the known end of the stream: continue with the next bounded range.
        if (read == C.RESULT_END_OF_INPUT && end != null && position < end) {
            upstream.close()
            openChunk()
            read = upstream.read(buffer, offset, length)
        }
        if (spec != null && read > 0) position += read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        upstream.close()
    }

    private fun openChunk() {
        val current = requireNotNull(spec)
        val chunkEnd = RangeChunks.chunkEnd(position, chunkSize, endExclusive)
        upstream.open(
            current
                .buildUpon()
                .setPosition(position)
                .setLength(chunkEnd - position)
                .build(),
        )
    }
}
