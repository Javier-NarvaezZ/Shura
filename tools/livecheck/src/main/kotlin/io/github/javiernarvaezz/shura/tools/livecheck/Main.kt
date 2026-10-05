package io.github.javiernarvaezz.shura.tools.livecheck

import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.network.ShuraNetwork
import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.InnerTubeXStreamResolver
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream
import io.github.javiernarvaezz.shura.core.stream.StreamResolutionException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Manual live check of stream resolution and download, built exactly like the app (ADR 0001 R7).
 *
 * Output rule: track labels, host names, client profiles and sizes only. Never URLs, video ids or tokens.
 * Audio goes to a temp file outside the repository and is deleted after the decode check.
 *
 * Usage: ./gradlew :tools:livecheck:run --args="<number of tracks, default all>"
 */
private val TRACKS =
    listOf(
        "ordinary 1" to VideoId("7fwUH0oRmkQ"),
        "ordinary 2" to VideoId("nOj6d-HOw2w"),
        "explicit" to VideoId("juRFjpB5Ppg"),
    )

private const val PAUSE_BETWEEN_TRACKS_MS = 8_000L
private const val PAUSE_BETWEEN_CHUNKS_MS = 300L
private const val UNBOUNDED_CHUNK_BYTES = 1_048_576L
private const val HTTP_PARTIAL = 206
private const val FFPROBE_TIMEOUT_SECONDS = 30L

private class HostRecorder : Interceptor {
    val hosts: MutableSet<String> = Collections.synchronizedSet(sortedSetOf())

    override fun intercept(chain: Interceptor.Chain) =
        chain.request().let {
            hosts += it.url.host
            chain.proceed(it)
        }
}

fun main(args: Array<String>) =
    runBlocking {
        val count = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, TRACKS.size) ?: TRACKS.size
        val recorder = HostRecorder()
        val network = ShuraNetwork(listOf(recorder))
        val resolver = InnerTubeXStreamResolver(network.ktor)

        TRACKS.take(count).forEachIndexed { index, (label, videoId) ->
            if (index > 0) delay(PAUSE_BETWEEN_TRACKS_MS)
            recorder.hosts.clear()
            val started = System.currentTimeMillis()
            val stream =
                try {
                    resolver.resolve(videoId, AudioQuality.High)
                } catch (e: StreamResolutionException) {
                    println(
                        "[$label] resolve FAILED: ${e.failure} attempts=${e.attempts.map {
                            "${it.profile}:${it.outcome}"
                        }}",
                    )
                    return@forEachIndexed
                }
            val resolveMs = System.currentTimeMillis() - started
            val format = "${stream.mimeType} ${stream.codecs}"
            println("[$label] resolved in ${resolveMs}ms profile=${stream.clientProfile} format=$format")
            val file = File.createTempFile("livecheck-", ".bin")
            try {
                val result = download(network, stream, file)
                println(
                    "[$label] downloaded ${result.bytes}/${stream.contentLength} bytes, statuses=${result.statuses}",
                )
                println("[$label] decode: ${probe(file)}")
            } finally {
                file.delete()
            }
            println("[$label] hosts=${recorder.hosts}")
        }
        network.ktor.close()
    }

private class DownloadResult(
    val bytes: Long,
    val statuses: Map<Int, Int>,
)

/** Bounded-range download through the app's single OkHttpClient, with the stream's own request headers. */
private suspend fun download(
    network: ShuraNetwork,
    stream: ResolvedStream,
    target: File,
): DownloadResult {
    val total = stream.contentLength
    val chunk = if (stream.requiresBoundedRange) stream.rangeChunkSizeBytes else UNBOUNDED_CHUNK_BYTES
    val statuses = mutableMapOf<Int, Int>()
    var position = 0L
    var finished = false
    target.outputStream().use { out ->
        while (!finished && (total == null || position < total)) {
            val end = (position + chunk).let { if (total != null) minOf(it, total) else it } - 1
            val request =
                Request
                    .Builder()
                    .url(stream.url)
                    .apply { stream.requestHeaders.forEach { (name, value) -> header(name, value) } }
                    .header("Range", "bytes=$position-$end")
                    .build()
            network.okHttp.newCall(request).execute().use { response ->
                statuses.merge(response.code, 1, Int::plus)
                val bytes = if (response.code == HTTP_PARTIAL) response.body.bytes() else ByteArray(0)
                out.write(bytes)
                position += bytes.size
                finished = bytes.isEmpty()
            }
            delay(PAUSE_BETWEEN_CHUNKS_MS)
        }
    }
    return DownloadResult(position, statuses)
}

/** Decodes the file with ffprobe when available; reports codec and duration only (never the path). */
private fun probe(file: File): String =
    runCatching {
        val process =
            ProcessBuilder(
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration:stream=codec_name",
                "-of",
                "default=nw=1:nk=1",
                file.path,
            ).redirectErrorStream(true).start()
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
                .replace("\n", " ")
        if (!process.waitFor(FFPROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) "ffprobe timed out" else "ok ($output)"
    }.getOrElse { "ffprobe unavailable (${it::class.simpleName})" }
