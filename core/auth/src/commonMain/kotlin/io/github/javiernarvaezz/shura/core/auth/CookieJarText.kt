package io.github.javiernarvaezz.shura.core.auth

/** Cookies as `CookieManager` returns them and as the `Cookie` header sends them, and rotations from responses. */
object CookieJarText {
    /** "a=1; b=2" to name→value; values may contain "="; nameless parts are dropped. */
    fun parse(text: String): Map<String, String> {
        val cookies = LinkedHashMap<String, String>()
        text.split(";").map { it.trim() }.forEach { part ->
            val name = part.substringBefore("=").trim()
            if (name.isNotEmpty() && "=" in part) cookies[name] = part.substringAfter("=")
        }
        return cookies
    }

    fun header(cookies: Map<String, String>): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    /**
     * The session's cookies updated by a response's `Set-Cookie` headers: Google rotates some (`SIDCC`,
     * `__Secure-*PSIDTS`) as the session is used. Only cookies the session already has are taken, and one the
     * server deletes (empty, or `Max-Age=0`) is dropped.
     */
    fun merge(
        session: Map<String, String>,
        setCookies: List<String>,
    ): Map<String, String> {
        val merged = LinkedHashMap(session)
        setCookies.forEach { header ->
            val parts = header.split(";").map { it.trim() }
            val pair = parts.firstOrNull() ?: return@forEach
            val name = pair.substringBefore("=").trim()
            if (name !in merged || "=" !in pair) return@forEach
            val value = pair.substringAfter("=")
            val deleted = value.isEmpty() || parts.any { it.equals("Max-Age=0", ignoreCase = true) }
            if (deleted) merged.remove(name) else merged[name] = value
        }
        return merged
    }
}
