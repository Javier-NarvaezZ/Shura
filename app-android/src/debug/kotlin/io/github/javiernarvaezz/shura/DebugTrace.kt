package io.github.javiernarvaezz.shura

import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.javiernarvaezz.shura.core.stream.InnerTubeXStreamResolver
import io.github.javiernarvaezz.shura.core.stream.Trace
import io.github.javiernarvaezz.shura.core.stream.event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/*
 * Debug builds only: start-up timing diagnostics (perf/playback-startup). Logs event names, request categories,
 * hosts, sizes and timings; never URLs, query strings, headers with secrets, video ids or tokens.
 */

private const val TAG = "ShuraTrace"
private const val MS_PER_S = 1000L
private const val BYTES_PER_KB = 1024L

/** Every event carries the monotonic clock, so stages can be laid out on one timeline from logcat. */
internal fun debugTrace(): Trace =
    Trace { name, details ->
        val extra = details.entries.joinToString(" ") { "${it.key}=${it.value}" }
        Log.d(TAG, "t=${SystemClock.elapsedRealtime()} $name $extra".trimEnd())
    }

private val totalBodyBytes = AtomicLong()

/** Per-call network timings by request category (never the URL). */
internal fun debugEventListenerFactory(): EventListener.Factory = EventListener.Factory { RequestTimer() }

private class RequestTimer : EventListener() {
    private var callStart = 0L
    private var headersEnd = 0L
    private var cacheControl: String? = null

    override fun callStart(call: Call) {
        callStart = SystemClock.elapsedRealtime()
    }

    override fun responseHeadersEnd(
        call: Call,
        response: Response,
    ) {
        headersEnd = SystemClock.elapsedRealtime()
        if (category(call) == "player.js") cacheControl = response.header("Cache-Control")
    }

    override fun responseBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        val now = SystemClock.elapsedRealtime()
        totalBodyBytes.addAndGet(byteCount)
        val bodyMs = (now - headersEnd).coerceAtLeast(1)
        Log.d(
            TAG,
            "t=$now net category=${category(call)} host=${call.request().url.host} " +
                "ttfbMs=${headersEnd - callStart} bodyMs=$bodyMs bytes=$byteCount " +
                "kbPerS=${byteCount * MS_PER_S / BYTES_PER_KB / bodyMs} totalMs=${now - callStart}" +
                (cacheControl?.let { " cacheControl=$it" } ?: ""),
        )
    }

    private fun category(call: Call): String {
        val url = call.request().url
        val path = url.encodedPath
        return when {
            url.host.endsWith("googlevideo.com") -> "media"
            path.startsWith("/s/player/") && path.endsWith(".js") -> "player.js"
            path.startsWith("/youtubei/v1/") -> "innertube:" + path.removePrefix("/youtubei/v1/")
            path == "/watch" -> "watch-page"
            path.startsWith("/embed/") -> "embed-page"
            path == "/iframe_api" -> "iframe_api"
            path == "/sw.js_data" -> "sw.js_data"
            path.startsWith("/api/jnn/") -> "jnn:" + path.substringAfterLast('/')
            url.host == "www.google.com" || url.host == "www.gstatic.com" -> "botguard-interpreter"
            else -> "other"
        }
    }
}

private val debugScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** With `--ez shura.debug.prewarm true`: runs InnerTubeX prewarm once and reports its time and data. */
internal fun debugMaybePrewarm(
    resolver: InnerTubeXStreamResolver,
    trace: Trace,
) {
    if (!prewarmRequested) return
    debugScope.launch {
        val bytesBefore = totalBodyBytes.get()
        val started = SystemClock.elapsedRealtime()
        trace.event("prewarm: start")
        runCatching { resolver.prewarm() }
        trace.event(
            "prewarm: done",
            mapOf(
                "ms" to (SystemClock.elapsedRealtime() - started).toString(),
                "bodyBytes" to (totalBodyBytes.get() - bytesBefore).toString(),
            ),
        )
    }
}

/** Reports the warm-up's duration, response-body bytes and this process's CPU time; never what was fetched. */
internal fun debugWarmUp(
    warmUp: suspend () -> Unit,
    trace: Trace,
): suspend () -> Unit =
    {
        val bytesBefore = totalBodyBytes.get()
        val cpuBefore = Process.getElapsedCpuTime()
        val started = SystemClock.elapsedRealtime()
        trace.event("warmup: start")
        try {
            warmUp()
        } finally {
            trace.event(
                "warmup: done",
                mapOf(
                    "ms" to (SystemClock.elapsedRealtime() - started).toString(),
                    "bodyBytes" to (totalBodyBytes.get() - bytesBefore).toString(),
                    "processCpuMs" to (Process.getElapsedCpuTime() - cpuBefore).toString(),
                ),
            )
        }
    }
