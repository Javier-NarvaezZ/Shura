package io.github.javiernarvaezz.shura.core.auth

import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedSessionFileTest {
    private val dir: File = Files.createTempDirectory("session").toFile()
    private val file = File(dir, "session.bin")
    private var key: SecretKey = newKey()
    private var keyDeleted = false

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun store() =
        EncryptedSessionFile(
            file,
            AesGcmSessionCipher(key = { key }, deleteKey = { keyDeleted = true }),
        )

    private val session =
        Session(
            cookies = linkedMapOf("SAPISID" to "secret-sapisid-value", "SID" to "secret-sid", "HSID" to "x=y"),
            dataSyncId = "UCchannel||user",
            accountName = "Test Account",
            signedInAtMillis = 1_800_000_000_000,
        )

    @Test
    fun aSavedSessionComesBackWhole() {
        store().save(session)
        assertEquals(StoredSession.Present(session), store().load())
    }

    @Test
    fun theFileHoldsNoReadableSecret() {
        store().save(session)
        val bytes = file.readBytes().decodeToString()
        listOf("secret-sapisid-value", "secret-sid", "UCchannel", "Test Account", "SAPISID").forEach {
            assertFalse(it in bytes, it)
        }
    }

    @Test
    fun everySaveUsesAFreshIv() {
        store().save(session)
        val first = file.readBytes()
        store().save(session)
        assertFalse(first.contentEquals(file.readBytes()))
    }

    @Test
    fun nothingSavedIsAbsent() {
        assertEquals(StoredSession.Absent, store().load())
    }

    @Test
    fun aChangedKeyMakesTheSessionUnreadableAndItIsDeleted() {
        store().save(session)
        key = newKey()
        assertEquals(StoredSession.Unreadable, store().load())
        assertFalse(file.exists())
        assertTrue(keyDeleted)
        // After that, simply signed out.
        assertEquals(StoredSession.Absent, store().load())
    }

    @Test
    fun aDamagedFileIsUnreadableAndDeleted() {
        store().save(session)
        file.writeBytes(file.readBytes().copyOf(10))
        assertEquals(StoredSession.Unreadable, store().load())
        assertFalse(file.exists())
    }

    @Test
    fun clearingDeletesTheFileAndTheKey() {
        store().save(session)
        store().clear()
        assertFalse(file.exists())
        assertTrue(keyDeleted)
        assertEquals(StoredSession.Absent, store().load())
    }
}
