package io.github.javiernarvaezz.shura.core.auth

import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WrappedKeyFileTest {
    private val dir: File = Files.createTempDirectory("offline").toFile()
    private val file = File(dir, "offline.key")
    private var key: SecretKey = newKey()
    private var keyDeleted = false

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun keys() = WrappedKeyFile(file, AesGcmSessionCipher(key = { key }, deleteKey = { keyDeleted = true }))

    @Test
    fun aKeyIsMadeOnceAndComesBackTheSame() {
        val first = keys().load()
        val again = keys().load()

        assertEquals(WrappedKeyFile.KEY_BYTES, first.bytes.size)
        assertFalse(first.replaced)
        assertContentEquals(first.bytes, again.bytes)
        assertFalse(again.replaced)
    }

    @Test
    fun theKeyIsNeverStoredInTheClear() {
        val loaded = keys().load()
        val stored = file.readBytes()

        assertFalse(stored.toList().windowed(loaded.bytes.size).any { it == loaded.bytes.toList() })
    }

    @Test
    fun aKeyThatCannotBeReadIsReplacedAndSaysSo() {
        val first = keys().load()
        // The wrapping key changed (invalidated, restored on another device).
        key = newKey()

        val second = keys().load()
        assertTrue(second.replaced)
        assertTrue(keyDeleted)
        assertFalse(first.bytes.contentEquals(second.bytes))
        assertFalse(keys().load().replaced)
    }

    @Test
    fun aDamagedFileIsReplacedToo() {
        keys().load()
        file.writeBytes(byteArrayOf(1, 2, 3))

        assertTrue(keys().load().replaced)
    }
}
