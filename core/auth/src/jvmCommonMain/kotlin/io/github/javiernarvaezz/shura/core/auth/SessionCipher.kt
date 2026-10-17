package io.github.javiernarvaezz.shura.core.auth

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the session at rest. Any failure to decrypt throws [GeneralSecurityException]. */
interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(sealed: ByteArray): ByteArray

    /** Forgets the key, so nothing encrypted with it can be read again. */
    fun deleteKey()
}

/**
 * AES-256-GCM with a fresh IV chosen by the cipher for every encryption (required by Android Keystore keys).
 * Layout: IV length (1 byte), IV, ciphertext with its 128-bit tag.
 */
class AesGcmSessionCipher(
    private val key: () -> SecretKey,
    private val deleteKey: () -> Unit,
) : SessionCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteBuffer
            .allocate(1 + iv.size + body.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(body)
            .array()
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.isEmpty()) throw GeneralSecurityException("Empty session")
        val ivLength = sealed[0].toInt()
        if (ivLength <= 0 || sealed.size < 1 + ivLength + TAG_BYTES) throw GeneralSecurityException("Short session")
        val iv = sealed.copyOfRange(1, 1 + ivLength)
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            }
        return cipher.doFinal(sealed, 1 + ivLength, sealed.size - 1 - ivLength)
    }

    override fun deleteKey() = deleteKey.invoke()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
    }
}
