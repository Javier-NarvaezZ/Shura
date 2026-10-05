package io.github.javiernarvaezz.shura.core.network

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin

/**
 * Hosts the app may contact over HTTP (ADR 0001): YouTube for catalog and stream resolution, googlevideo
 * for media. Remote solver-config hosts are never allowed (R3).
 *
 * Does not cover the PoToken `WebView`, which has its own network stack and allowlist (R2, R7).
 */
object HostPolicy {
    private val allowedDomains = listOf("youtube.com", "googlevideo.com")

    fun isAllowed(host: String): Boolean {
        val normalized = host.lowercase()
        return allowedDomains.any { normalized == it || normalized.endsWith(".$it") }
    }
}

/** Thrown before any connection is opened to a host outside [HostPolicy]. Only the host is reported. */
class BlockedHostException(
    val host: String,
) : IllegalStateException("Blocked request to host $host")

/**
 * Enforces [HostPolicy] on every send of a Ktor client, including redirects.
 *
 * Defense in depth only: the binding enforcement is the OkHttp interceptor of the app's single client,
 * which also sees requests from clients that libraries build directly on the engine.
 */
val HostAllowlist =
    createClientPlugin("HostAllowlist") {
        on(Send) { request ->
            val host = request.url.host
            if (!HostPolicy.isAllowed(host)) throw BlockedHostException(host)
            proceed(request)
        }
    }
