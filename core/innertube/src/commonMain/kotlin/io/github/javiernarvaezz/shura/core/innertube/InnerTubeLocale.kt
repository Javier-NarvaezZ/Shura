package io.github.javiernarvaezz.shura.core.innertube

/**
 * Maps a device language and region to InnerTube `hl` and `gl` values.
 *
 * Inputs are the parts of a platform locale (for example `java.util.Locale.getLanguage()` and `getCountry()`).
 * Anything missing or malformed falls back to [FALLBACK_HL] and [FALLBACK_GL] independently.
 */
internal object InnerTubeLocale {
    const val FALLBACK_HL = "es-419"
    const val FALLBACK_GL = "CO"

    private val LANGUAGE = Regex("[a-z]{2,3}")
    private val REGION = Regex("[A-Z]{2}")

    /** Older ISO 639 codes some platforms still report. */
    private val LEGACY_LANGUAGES = mapOf("in" to "id", "ji" to "yi")

    fun hl(
        language: String?,
        region: String?,
    ): String {
        val lang = language?.trim()?.lowercase()?.let { LEGACY_LANGUAGES[it] ?: it }
        if (lang == null || !LANGUAGE.matches(lang)) return FALLBACK_HL
        return variant(lang, normalizedRegion(region)) ?: lang
    }

    fun gl(region: String?): String = normalizedRegion(region) ?: FALLBACK_GL

    private fun normalizedRegion(region: String?): String? = region?.trim()?.uppercase()?.takeIf { REGION.matches(it) }

    /** Regional variants YouTube Music lists as separate interface languages. */
    private fun variant(
        language: String,
        region: String?,
    ): String? =
        when (language) {
            "es" -> spanish(region)
            "en" -> if (region == "GB" || region == "IN") "en-$region" else null
            "fr" -> if (region == "CA") "fr-CA" else null
            "pt" -> if (region == "PT") "pt-PT" else null
            "zh" -> if (region == "TW" || region == "HK") "zh-$region" else "zh-CN"
            else -> null
        }

    private fun spanish(region: String?): String =
        when (region) {
            "ES" -> "es"
            "US" -> "es-US"
            else -> FALLBACK_HL
        }
}
