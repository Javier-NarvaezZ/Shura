package io.github.javiernarvaezz.shura.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The session store on Android: an AES-256-GCM key that never leaves the Android Keystore, and the encrypted
 * file in `noBackupFilesDir` (also outside the backup rules, which include only the app database).
 */
object KeystoreSessionStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "sunloop_session"
    private const val KEY_BITS = 256

    fun create(context: Context): SessionStore =
        EncryptedSessionFile(
            File(context.noBackupFilesDir, "session.bin"),
            AesGcmSessionCipher(key = ::key, deleteKey = ::deleteKey),
        )

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(): SecretKey =
        (keyStore().getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_BITS)
                        .build(),
                )
            }.generateKey()

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }
}

/** The offline songs' key: random, wrapped by its own Android Keystore key, in `noBackupFilesDir/offline.key`. */
object KeystoreOfflineKey {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "sunloop_offline"
    private const val KEY_BITS = 256

    fun load(context: Context): WrappedKeyFile.Loaded =
        WrappedKeyFile(
            File(context.noBackupFilesDir, "offline.key"),
            AesGcmSessionCipher(key = ::key, deleteKey = ::deleteKey),
        ).load()

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(): SecretKey =
        (keyStore().getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_BITS)
                        .build(),
                )
            }.generateKey()

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }
}
