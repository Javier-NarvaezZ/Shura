package io.github.javiernarvaezz.shura.core.data

import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.lyrics.LyricsOffsets
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** [LyricsOffsets] in the app database, on [ioContext]. A song without a fix has no row. */
class SqlLyricsOffsets(
    private val database: ShuraDatabase,
    private val ioContext: CoroutineContext,
) : LyricsOffsets {
    private val queries get() = database.lyricsQueries

    override suspend fun get(key: String): Duration =
        withContext(ioContext) { queries.offset(key).executeAsOneOrNull()?.milliseconds ?: Duration.ZERO }

    override suspend fun set(
        key: String,
        offset: Duration,
    ) {
        withContext(ioContext) {
            if (offset == Duration.ZERO) {
                queries.removeOffset(key)
            } else {
                queries.putOffset(key, offset.inWholeMilliseconds)
            }
        }
    }
}
