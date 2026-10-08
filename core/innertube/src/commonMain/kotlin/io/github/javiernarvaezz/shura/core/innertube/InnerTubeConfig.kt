package io.github.javiernarvaezz.shura.core.innertube

/**
 * Identity of the anonymous `WEB_REMIX` (YouTube Music web) client used for catalog requests.
 *
 * [clientVersion] must track a version YouTube Music currently accepts. [hl] and [gl] default to the fallback
 * locale; use [forDevice] to derive them from the device language and region.
 */
data class InnerTubeConfig(
    val clientVersion: String = DEFAULT_CLIENT_VERSION,
    val hl: String = InnerTubeLocale.FALLBACK_HL,
    val gl: String = InnerTubeLocale.FALLBACK_GL,
    val userAgent: String = DEFAULT_USER_AGENT,
) {
    companion object {
        /** Observed working on 2026-10-04. */
        const val DEFAULT_CLIENT_VERSION = "1.20260707.12.00"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        /**
         * Config whose `hl` and `gl` follow the device [language] and [region] (ISO 639 and ISO 3166 codes, as in
         * `java.util.Locale`). Missing or malformed values fall back to `es-419` and `CO`.
         */
        fun forDevice(
            language: String?,
            region: String?,
        ): InnerTubeConfig = InnerTubeConfig(hl = InnerTubeLocale.hl(language, region), gl = InnerTubeLocale.gl(region))
    }
}
