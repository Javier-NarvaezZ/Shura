package io.github.javiernarvaezz.shura.core.auth

import java.security.MessageDigest

/**
 * An anonymous fingerprint of the signed-in account: SHA-256 of its DATASYNC_ID, so pending actions can be tied
 * to the account they were made with without storing the id (they live in the app database, which is backed up).
 */
object AccountFingerprint {
    fun of(session: Session): String? {
        val id = session.dataSyncId?.takeIf { it.isNotBlank() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest("sunloop-account:$id".encodeToByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
