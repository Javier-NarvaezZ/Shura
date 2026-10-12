package io.github.javiernarvaezz.shura.core.data

import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.lyrics.CachedLyrics
import io.github.javiernarvaezz.shura.core.lyrics.LyricLine
import io.github.javiernarvaezz.shura.core.lyrics.Lyrics
import io.github.javiernarvaezz.shura.core.lyrics.LyricsStore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * [LyricsStore] in the app database, read and written on [ioContext]. Keeps the [MAX_SONGS] most recently looked up
 * songs. A row that cannot be read back (an unknown kind, a broken body) counts as missing.
 */
class SqlLyricsStore(
    private val database: ShuraDatabase,
    private val ioContext: CoroutineContext,
) : LyricsStore {
    override suspend fun get(key: String): CachedLyrics? =
        withContext(ioContext) {
            val row = database.lyricsQueries.entry(key).executeAsOneOrNull() ?: return@withContext null
            decode(row.kind, row.body)?.let { CachedLyrics(it.value, Instant.fromEpochMilliseconds(row.fetched_at)) }
        }

    override suspend fun put(
        key: String,
        entry: CachedLyrics,
    ) = withContext(ioContext) {
        val (kind, body) = encode(entry.lyrics)
        database.transaction {
            database.lyricsQueries.put(key, kind, body, entry.fetchedAt.toEpochMilliseconds())
            database.lyricsQueries.prune(MAX_SONGS)
        }
    }

    /** A decoded value, so "no lyrics" (null) is told apart from "unreadable". */
    private class Decoded(
        val value: Lyrics?,
    )

    private companion object {
        const val MAX_SONGS = 1_000L

        const val SYNCED = "synced"
        const val PLAIN = "plain"
        const val INSTRUMENTAL = "instrumental"
        const val NONE = "none"

        fun encode(lyrics: Lyrics?): Pair<String, String?> =
            when (lyrics) {
                is Lyrics.Synced -> {
                    SYNCED to
                        JsonArray(
                            lyrics.lines.map {
                                JsonArray(listOf(JsonPrimitive(it.time.inWholeMilliseconds), JsonPrimitive(it.text)))
                            },
                        ).toString()
                }

                is Lyrics.Plain -> {
                    PLAIN to lyrics.text
                }

                Lyrics.Instrumental -> {
                    INSTRUMENTAL to null
                }

                null -> {
                    NONE to null
                }
            }

        @Suppress("TooGenericExceptionCaught", "SwallowedException") // A broken row is a cache miss.
        fun decode(
            kind: String,
            body: String?,
        ): Decoded? =
            try {
                when (kind) {
                    SYNCED -> body?.let { Decoded(Lyrics.Synced(lines(it))) }
                    PLAIN -> body?.let { Decoded(Lyrics.Plain(it)) }
                    INSTRUMENTAL -> Decoded(Lyrics.Instrumental)
                    NONE -> Decoded(null)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }

        fun lines(json: String): List<LyricLine> =
            Json.parseToJsonElement(json).jsonArray.map { pair ->
                val (time, text) = pair.jsonArray
                LyricLine(time.jsonPrimitive.long.milliseconds, text.jsonPrimitive.content)
            }
    }
}
