package io.github.javiernarvaezz.shura

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Debug builds only (ADR 0001 R7): counts requests per host. Logs host names only, never URLs or headers. */
internal fun debugNetworkInterceptors(): List<Interceptor> = listOf(HostCounterInterceptor(), RejectOnceInterceptor())

/**
 * Debug: `--ez shura.debug.rejectMidDownload true` makes googlevideo "refuse" one range request past the first
 * 2 MB, once, as an expired URL does, to check that a download resolves a new URL and goes on.
 */
@Volatile
internal var rejectMidDownload = false

private class RejectOnceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start =
            request
                .header("Range")
                ?.substringAfter("bytes=")
                ?.substringBefore('-')
                ?.toLongOrNull()
        // A network interceptor must proceed: the real answer is replaced by the refusal.
        val response = chain.proceed(request)
        if (rejectMidDownload && request.url.host.endsWith("googlevideo.com") && (start ?: 0) >= REJECT_FROM) {
            rejectMidDownload = false
            Log.w("ShuraNet", "Refusing one range request past $REJECT_FROM bytes (debug)")
            response.close()
            return response
                .newBuilder()
                .code(HTTP_FORBIDDEN)
                .message("Forbidden (debug)")
                .body("".toResponseBody())
                .build()
        }
        return response
    }

    private companion object {
        const val REJECT_FROM = 2_000_000L
        const val HTTP_FORBIDDEN = 403
    }
}

private class HostCounterInterceptor : Interceptor {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val count = counts.getOrPut(host) { AtomicInteger() }.incrementAndGet()
        Log.d(TAG, "host=$host count=$count")
        return chain.proceed(chain.request())
    }

    private companion object {
        const val TAG = "ShuraNet"
    }
}
