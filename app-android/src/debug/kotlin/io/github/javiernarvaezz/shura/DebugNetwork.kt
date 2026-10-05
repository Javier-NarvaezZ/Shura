package io.github.javiernarvaezz.shura

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Debug builds only (ADR 0001 R7): counts requests per host. Logs host names only, never URLs or headers. */
internal fun debugNetworkInterceptors(): List<Interceptor> = listOf(HostCounterInterceptor())

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
