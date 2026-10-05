package io.github.javiernarvaezz.shura.core.innertube

/**
 * Identity of the anonymous `WEB_REMIX` (YouTube Music web) client used for catalog requests.
 *
 * [clientVersion] must track a version YouTube Music currently accepts. The [hl] and [gl] defaults are
 * placeholders for the Phase 1 spike; they will come from the device language and region.
 */
data class InnerTubeConfig(
    val clientVersion: String = DEFAULT_CLIENT_VERSION,
    val hl: String = "es-419",
    val gl: String = "CO",
    val userAgent: String = DEFAULT_USER_AGENT,
) {
    companion object {
        /** Observed working on 2026-10-04. */
        const val DEFAULT_CLIENT_VERSION = "1.20260707.12.00"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
