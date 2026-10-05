package io.github.javiernarvaezz.shura.core.stream

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilePreprocessedPlayerStoreTest {
    private val root: File = Files.createTempDirectory("shura-ejs-store").toFile()
    private val directory = File(root, "ejs-players")
    private val store = FilePreprocessedPlayerStore(directory, maxEntries = 2)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun writtenValueReadsBack() =
        runBlocking {
            store.write(KEY_A, "var player = 1;")

            assertEquals("var player = 1;", store.read(KEY_A))
        }

    @Test
    fun missingKeyReadsNull() =
        runBlocking {
            assertNull(store.read(KEY_A))
        }

    @Test
    fun nullValueDeletesTheEntry() =
        runBlocking {
            store.write(KEY_A, "x")
            store.write(KEY_A, null)

            assertNull(store.read(KEY_A))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun invalidKeysNeverTouchTheDisk() =
        runBlocking {
            val invalid = listOf("", "../$KEY_A", KEY_A.uppercase(), KEY_A.dropLast(1), KEY_A + "0", "a/b")
            invalid.forEach { store.write(it, "x") }

            invalid.forEach { assertNull(store.read(it)) }
            assertTrue(root.walk().none { it.isFile }, "files: ${root.walk().filter { it.isFile }.toList()}")
        }

    @Test
    fun oversizedValueIsNotWritten() =
        runBlocking {
            store.write(KEY_A, "a".repeat(FilePreprocessedPlayerStore.MAX_VALUE_BYTES + 1))

            assertNull(store.read(KEY_A))
        }

    @Test
    fun leastRecentlyUsedEntryIsEvicted() =
        runBlocking {
            store.write(KEY_A, "a")
            age(KEY_A, ageMs = 3_000)
            store.write(KEY_B, "b")
            age(KEY_B, ageMs = 2_000)
            // Reading A makes it the most recently used, so B is the one evicted.
            assertEquals("a", store.read(KEY_A))

            store.write(KEY_C, "c")

            assertEquals("a", store.read(KEY_A))
            assertNull(store.read(KEY_B))
            assertEquals("c", store.read(KEY_C))
        }

    @Test
    fun noTemporaryFilesAreLeftBehind() =
        runBlocking {
            store.write(KEY_A, "a")
            store.write(KEY_A, "a2")

            assertEquals(listOf("$KEY_A.js"), directory.list().orEmpty().toList())
        }

    @Test
    fun corruptedEntryIsDiscarded() =
        runBlocking {
            store.write(KEY_A, "var player = 1;")
            val file = File(directory, "$KEY_A.js")
            file.writeText(file.readText().replace("player = 1", "player = 2"))

            assertNull(store.read(KEY_A))
            assertFalse(file.exists())
        }

    @Test
    fun entryWithoutDigestIsDiscarded() =
        runBlocking {
            directory.mkdirs()
            val file = File(directory, "$KEY_A.js")
            file.writeText("var player = 1;")

            assertNull(store.read(KEY_A))
            assertFalse(file.exists())
        }

    @Test
    fun entryStoresTheSha256OfItsValue() =
        runBlocking {
            store.write(KEY_A, "abc")

            // SHA-256("abc"), a published test vector.
            assertTrue(
                File(directory, "$KEY_A.js")
                    .readText()
                    .startsWith("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\n"),
            )
        }

    private fun age(
        key: String,
        ageMs: Long,
    ) {
        File(directory, "$key.js").setLastModified(System.currentTimeMillis() - ageMs)
    }

    private companion object {
        const val KEY_A = "0123456789abcdef0123456789abcdef01234567"
        const val KEY_B = "1123456789abcdef0123456789abcdef01234567"
        const val KEY_C = "2123456789abcdef0123456789abcdef01234567"
    }
}
