package io.github.javiernarvaezz.shura.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app's single HTTP stack (ADR 0001 R7). Create exactly one instance per process and hand [okHttp] or
 * [ktor] to every component that talks to the network; nothing else may build its own client.
 *
 * [HostPolicy] is enforced as an application interceptor (before any connection) and as a network
 * interceptor (on every redirect hop). [extraNetworkInterceptors] run after the guard; use them only for
 * diagnostics such as the debug host counter.
 */
class ShuraNetwork internal constructor(
    extraNetworkInterceptors: List<Interceptor>,
    terminalForTests: Interceptor?,
) {
    constructor(extraNetworkInterceptors: List<Interceptor> = emptyList()) : this(extraNetworkInterceptors, null)

    val okHttp: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(HostGuardInterceptor())
            .apply { terminalForTests?.let(::addInterceptor) }
            .addNetworkInterceptor(HostGuardInterceptor())
            .apply { extraNetworkInterceptors.forEach(::addNetworkInterceptor) }
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /** Ktor client on the same [okHttp]; libraries that build clients on its engine still go through it. */
    val ktor: HttpClient =
        HttpClient(OkHttp) {
            engine { preconfigured = okHttp }
            install(HostAllowlist)
        }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
    }
}
