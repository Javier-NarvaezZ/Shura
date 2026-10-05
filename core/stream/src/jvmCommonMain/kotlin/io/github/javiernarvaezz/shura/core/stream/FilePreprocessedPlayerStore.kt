package io.github.javiernarvaezz.shura.core.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Keeps up to [maxEntries] preprocessed players as `<key>.js` files in [directory], which must be private to
 * the app (Android: a folder in `cacheDir`). Least recently used entries are evicted first. Logs nothing.
 *
 * The stored script is later executed, so each file starts with a `sha256:<hex>` line covering the rest; an
 * entry that fails the check is deleted and reads as a miss, and the library regenerates it.
 */
class FilePreprocessedPlayerStore(
    private val directory: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : PreprocessedPlayerStore {
    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            val file = fileFor(key)?.takeIf { it.isFile } ?: return@withContext null
            try {
                val value = file.takeIf { it.length() <= HEADER_BYTES + MAX_VALUE_BYTES }?.readBytes()?.let(::verified)
                if (value == null) {
                    file.delete()
                } else {
                    file.setLastModified(System.currentTimeMillis())
                }
                value
            } catch (_: IOException) {
                null
            }
        }

    override suspend fun write(
        key: String,
        value: String?,
    ) {
        withContext(Dispatchers.IO) {
            val file = fileFor(key) ?: return@withContext
            try {
                if (value == null) {
                    file.delete()
                    return@withContext
                }
                val bytes = value.encodeToByteArray()
                if (bytes.size > MAX_VALUE_BYTES) return@withContext
                directory.mkdirs()
                writeAtomically(file, header(bytes) + bytes)
                evictBeyondLimit()
            } catch (_: IOException) {
                // A failed write only means the next cold start regenerates the player.
            }
        }
    }

    private fun fileFor(key: String): File? = if (KEY_REGEX.matches(key)) File(directory, "$key$SUFFIX") else null

    private fun writeAtomically(
        file: File,
        bytes: ByteArray,
    ) {
        val temp = File.createTempFile(TEMP_PREFIX, null, directory)
        try {
            temp.writeBytes(bytes)
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    private fun header(body: ByteArray): ByteArray = "$DIGEST_PREFIX${sha256Hex(body)}\n".encodeToByteArray()

    /** The body when the header's digest matches it; null for a missing or wrong digest. */
    private fun verified(content: ByteArray): String? {
        if (content.size < HEADER_BYTES) return null
        val body = content.copyOfRange(HEADER_BYTES, content.size)
        val expected = header(body)
        return if (content.copyOfRange(0, HEADER_BYTES).contentEquals(expected)) body.decodeToString() else null
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun evictBeyondLimit() {
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(maxEntries)
            .forEach { it.delete() }
    }

    companion object {
        /** InnerTubeX's own limit for a preprocessed player. */
        const val MAX_VALUE_BYTES = 8 * 1024 * 1024

        private const val DEFAULT_MAX_ENTRIES = 3
        private const val SUFFIX = ".js"
        private const val TEMP_PREFIX = "write-"
        private val KEY_REGEX = Regex("[a-f0-9]{40}")
        private const val DIGEST_PREFIX = "sha256:"

        // "sha256:" + 64 hex chars + "\n".
        private const val HEADER_BYTES = 72
    }
}
