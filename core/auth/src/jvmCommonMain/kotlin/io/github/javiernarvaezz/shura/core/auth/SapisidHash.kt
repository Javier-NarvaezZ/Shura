package io.github.javiernarvaezz.shura.core.auth

import java.security.MessageDigest

/** The `Authorization` header signed requests carry: `SAPISIDHASH <time>_<sha1(time secret origin)>`. */
object SapisidHash {
    fun header(
        secret: String,
        origin: String,
        epochSeconds: Long,
    ): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$epochSeconds $secret $origin".encodeToByteArray())
        return "SAPISIDHASH ${epochSeconds}_${digest.joinToString("") { "%02x".format(it) }}"
    }

    /** `SAPISID`, or `__Secure-3PAPISID` when only that one is set; null when the cookies cannot sign. */
    fun secret(cookies: Map<String, String>): String? = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"]
}
