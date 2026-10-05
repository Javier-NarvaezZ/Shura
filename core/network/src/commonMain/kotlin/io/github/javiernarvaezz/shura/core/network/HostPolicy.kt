package io.github.javiernarvaezz.shura.core.stream

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin

/**
 * Hosts the stream resolver may contact (ADR 0001): YouTube for resolution and googlevideo for media.
 * Remote solver-config hosts are never allowed (R3).
 */
object StreamHostPolicy {
    private val allowedDomains = listOf("youtube.com", "googlevideo.com")

    fun isAllowed(host: String): Boolean {
        val normalized = host.lowercase()
        return allowedDomains.any { normalized == it || normalized.endsWith(".$it") }
    }
}

/** Thrown before any connection is opened to a host outside [StreamHostPolicy]. Only the host is reported. */
class BlockedHostException(
    val host: String,
) : IllegalStateException("Blocked request to host $host")

/**
 * Enforces [StreamHostPolicy] on every send of a Ktor client, including redirects.
 *
 * Not sufficient on its own: InnerTubeX also builds clients directly on the caller's engine (watch page,
 * player script), which bypass client plugins. Platform wiring must enforce the same policy at the engine level.
 */
val StreamHostAllowlist =
    createClientPlugin("StreamHostAllowlist") {
        on(Send) { request ->
            val host = request.url.host
            if (!StreamHostPolicy.isAllowed(host)) throw BlockedHostException(host)
            proceed(request)
        }
    }
