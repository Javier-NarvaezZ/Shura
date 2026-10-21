package io.github.javiernarvaezz.shura.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import io.github.javiernarvaezz.shura.core.model.VideoId

/**
 * Forgets a song's resolved URL ([onRejected]) when googlevideo refuses it (an HTTP error: the signed URL expired
 * or was revoked), so the next attempt resolves a new one instead of trying the same URL again. Wraps the
 * resolving source, so it sees the app's `shura://stream/<id>` URI. The error still reaches the caller, whose
 * retry (the download manager's) goes on from the bytes already kept.
 */
@OptIn(UnstableApi::class)
internal class RejectedUrlDataSource(
    private val upstream: DataSource,
    private val onRejected: (VideoId) -> Unit,
) : DataSource {
    private var videoId: VideoId? = null

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        videoId = StreamUri.parse(dataSpec.uri.toString())
        return rejecting { upstream.open(dataSpec) }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = rejecting { upstream.read(buffer, offset, length) }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    private inline fun <T> rejecting(block: () -> T): T =
        try {
            block()
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            videoId?.let(onRejected)
            throw e
        }

    class Factory(
        private val upstream: DataSource.Factory,
        private val onRejected: (VideoId) -> Unit,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RejectedUrlDataSource(upstream.createDataSource(), onRejected)
    }
}
