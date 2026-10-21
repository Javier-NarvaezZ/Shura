package io.github.javiernarvaezz.shura.core.auth

import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * A random key for data the app keeps encrypted (the offline songs), stored in [file] wrapped by [cipher] (a key
 * that never leaves the Android Keystore), never in the clear. A key that cannot be unwrapped (the Keystore key was
 * invalidated, the file is damaged) is replaced by a new one, and [Loaded.replaced] says so: what was encrypted
 * with the old one is lost.
 */
class WrappedKeyFile(
    private val file: File,
    private val cipher: SessionCipher,
    private val random: SecureRandom = SecureRandom(),
) {
    class Loaded(
        val bytes: ByteArray,
        val replaced: Boolean,
    )

    fun load(): Loaded {
        if (!file.exists()) return Loaded(create(), replaced = false)
        return try {
            val bytes = cipher.decrypt(file.readBytes())
            if (bytes.size != KEY_BYTES) throw GeneralSecurityException("Wrong key size")
            Loaded(bytes, replaced = false)
        } catch (
            @Suppress("SwallowedException") e: GeneralSecurityException,
        ) {
            cipher.deleteKey()
            file.delete()
            Loaded(create(), replaced = true)
        }
    }

    private fun create(): ByteArray {
        val bytes = ByteArray(KEY_BYTES).also(random::nextBytes)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(cipher.encrypt(bytes))
        temporary.renameTo(file)
        return bytes
    }

    companion object {
        /** 256 bits. */
        const val KEY_BYTES = 32
    }
}
