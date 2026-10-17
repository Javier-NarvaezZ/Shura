package io.github.javiernarvaezz.shura.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * The session in one encrypted file (on Android, in `noBackupFilesDir`, outside backups). A file that cannot be
 * read back (the key changed or was invalidated, the file is damaged) is deleted with its key: the user signs in
 * again, and the app never fails over it. Blocking: call off the main thread.
 */
class EncryptedSessionFile(
    private val file: File,
    private val cipher: SessionCipher,
) : SessionStore {
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any failure to read means "sign in again".
    override fun load(): StoredSession {
        if (!file.exists()) return StoredSession.Absent
        return try {
            StoredSession.Present(decode(cipher.decrypt(file.readBytes())))
        } catch (e: Exception) {
            clear()
            StoredSession.Unreadable
        }
    }

    override fun save(session: Session) {
        val sealed = cipher.encrypt(encode(session))
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(sealed)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not store the session" }
        }
    }

    override fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
        cipher.deleteKey()
    }

    private fun encode(session: Session): ByteArray =
        buildJsonObject {
            put("version", 1)
            putJsonObject("cookies") { session.cookies.forEach { (name, value) -> put(name, value) } }
            putJsonObject("googleCookies") { session.googleCookies.forEach { (name, value) -> put(name, value) } }
            session.dataSyncId?.let { put("dataSyncId", it) }
            session.accountName?.let { put("accountName", it) }
            put("signedInAt", session.signedInAtMillis)
        }.toString().encodeToByteArray()

    private fun decode(bytes: ByteArray): Session {
        val json = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        return Session(
            cookies = json.cookies("cookies"),
            dataSyncId = json.string("dataSyncId"),
            accountName = json.string("accountName"),
            signedInAtMillis = json.getValue("signedInAt").jsonPrimitive.long,
            googleCookies = json.cookies("googleCookies"),
        )
    }

    private fun JsonObject.cookies(key: String): Map<String, String> {
        val cookies = LinkedHashMap<String, String>()
        (get(key) as? JsonObject)?.forEach { (name, value) -> cookies[name] = value.jsonPrimitive.content }
        return cookies
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.content
}
