package io.github.javiernarvaezz.shura.core.player

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream
import io.github.javiernarvaezz.shura.core.stream.Trace

/**
 * Splits every logical read of a resolved stream into bounded `Range` requests sized by [RangeChunks]: googlevideo
 * throttles or cuts open-ended ranges whatever the client profile. Specs without a [ResolvedStream] pass through.
 */
@OptIn(UnstableApi::class)
internal class BoundedRangeDataSource(
    private val upstream: DataSource,
    private val trace: Trace = Trace.NONE,
) : DataSource {
    class Factory(
        private val upstream: DataSource.Factory,
        private val trace: Trace = Trace.NONE,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = BoundedRangeDataSource(upstream.createDataSource(), trace)
    }

    private val timer = RangeTimer(trace)

    private var spec: DataSpec? = null
    private var streamLimit: Long? = null
    private var chunkIndex = 0
    private var position = 0L
    private var endExclusive: Long? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val stream = dataSpec.customData as? ResolvedStream
        if (stream == null) {
            spec = null
            timer.opened()
            return upstream.open(dataSpec)
        }
        spec = dataSpec
        streamLimit = stream.rangeChunkSizeBytes.takeIf { stream.requiresBoundedRange }
        chunkIndex = 0
        position = dataSpec.position
        val requestedLength = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() }
        endExclusive = requestedLength?.let { dataSpec.position + it } ?: stream.contentLength
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
        timer.progress(read)
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        upstream.close()
    }

    private fun openChunk() {
        timer.opened()
        val current = requireNotNull(spec)
        val chunkEnd = RangeChunks.chunkEnd(position, RangeChunks.chunkSize(chunkIndex++, streamLimit), endExclusive)
        upstream.open(
            current
                .buildUpon()
                .setPosition(position)
                .setLength(chunkEnd - position)
                .build(),
        )
    }
}

/** Debug timing of upstream ranges: first byte, byte milestones and per-range speed, reported through [trace]. */
private class RangeTimer(
    private val trace: Trace,
) {
    private var bytes = 0L
    private var openedAt = 0L

    fun opened() {
        if (trace === Trace.NONE) return
        if (bytes > 0) reportRange()
        openedAt = SystemClock.elapsedRealtime()
        bytes = 0
    }

    fun progress(read: Int) {
        if (trace === Trace.NONE) return
        if (read > 0) {
            val before = bytes
            bytes += read
            if (before == 0L) trace.event("player: first byte", mapOf("afterOpenMs" to elapsed().toString()))
            BYTE_MILESTONES.firstOrNull { before < it && bytes >= it }?.let(::reportMilestone)
        } else if (read == C.RESULT_END_OF_INPUT && bytes > 0) {
            reportRange()
        }
    }

    private fun reportMilestone(milestone: Long) {
        val ms = elapsed().coerceAtLeast(1)
        trace.event(
            "player: bytes milestone",
            mapOf(
                "kb" to (milestone / KB).toString(),
                "afterOpenMs" to ms.toString(),
                "kbPerS" to speed(milestone, ms),
            ),
        )
    }

    private fun reportRange() {
        val ms = elapsed().coerceAtLeast(1)
        trace.event(
            "player: range done",
            mapOf(
                "bytes" to bytes.toString(),
                "ms" to ms.toString(),
                "kbPerS" to speed(bytes, ms),
            ),
        )
        bytes = 0
    }

    private fun elapsed() = SystemClock.elapsedRealtime() - openedAt

    private fun speed(
        byteCount: Long,
        ms: Long,
    ) = (byteCount * MS_PER_S / KB / ms).toString()

    private companion object {
        const val MS_PER_S = 1000L
        const val KB = 1024L
        val BYTE_MILESTONES = listOf(64L * KB, 256L * KB, 1024L * KB)
    }
}
