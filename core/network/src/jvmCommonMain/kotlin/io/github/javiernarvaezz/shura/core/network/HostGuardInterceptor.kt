package io.github.javiernarvaezz.shura.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** OkHttp-level [HostPolicy] enforcement. Fails as an [IOException] so callers treat it as a network error. */
class BlockedHostIOException(
    val host: String,
) : IOException("Blocked request to host $host")

internal class HostGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (!HostPolicy.isAllowed(host)) throw BlockedHostIOException(host)
        return chain.proceed(chain.request())
    }
}
