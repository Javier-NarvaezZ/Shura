package io.github.javiernarvaezz.shura.core.lyrics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The fields Shura uses from an LRCLIB lyrics record. */
internal class LrcLibRecord(
    val duration: Duration,
    private val instrumental: Boolean,
    private val plainLyrics: String?,
    private val syncedLyrics: String?,
) {
    /** Synced lyrics if any line parses, else plain lyrics, else [Lyrics.Instrumental] if flagged so, else `null`. */
    fun lyrics(): Lyrics? {
        val lines = syncedLyrics?.let(LrcParser::parse).orEmpty()
        return when {
            lines.isNotEmpty() -> Lyrics.Synced(lines)
            !plainLyrics.isNullOrBlank() -> Lyrics.Plain(plainLyrics.trimEnd())
            instrumental -> Lyrics.Instrumental
            else -> null
        }
    }

    companion object {
        /** A single record, as `/api/get` returns it. */
        fun from(json: JsonElement): LrcLibRecord =
            (json as? JsonObject)?.let(::parse) ?: throw LyricsException.Format()

        /** Search results, as `/api/search` returns them. Entries without a valid duration are skipped. */
        fun listFrom(json: JsonElement): List<LrcLibRecord> {
            val array = json as? JsonArray ?: throw LyricsException.Format()
            return array.mapNotNull { (it as? JsonObject)?.let(::parse) }
        }

        private fun parse(json: JsonObject): LrcLibRecord? {
            val seconds = json.primitive("duration")?.doubleOrNull?.takeIf { it.isFinite() && it > 0 } ?: return null
            return LrcLibRecord(
                duration = seconds.seconds,
                instrumental = json.primitive("instrumental")?.booleanOrNull ?: false,
                plainLyrics = json.string("plainLyrics"),
                syncedLyrics = json.string("syncedLyrics"),
            )
        }

        private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

        private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content
    }
}
