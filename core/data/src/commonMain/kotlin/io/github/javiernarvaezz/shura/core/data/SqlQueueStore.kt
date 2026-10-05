package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.javiernarvaezz.shura.core.data.db.Play_history
import io.github.javiernarvaezz.shura.core.data.db.Queue_item
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.QueueSnapshot
import io.github.javiernarvaezz.shura.core.player.QueueStore
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import io.github.javiernarvaezz.shura.core.player.SongFields
import io.github.javiernarvaezz.shura.core.player.toFields
import io.github.javiernarvaezz.shura.core.player.toSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

/**
 * [QueueStore] on SQLite. Every call runs on [context] (an IO dispatcher in the app). A saved queue that cannot be
 * restored (corrupt rows, a play order that is not a permutation) is deleted and reported as absent.
 */
class SqlQueueStore(
    private val database: ShuraDatabase,
    private val context: CoroutineContext,
) : QueueStore {
    private val queue get() = database.queueQueries
    private val history get() = database.historyQueries

    override suspend fun save(snapshot: QueueSnapshot) =
        withContext(context) {
            database.transaction {
                queue.clearItems()
                queue.clearState()
                if (snapshot.items.isEmpty()) return@transaction
                snapshot.items.forEachIndexed {
                    position,
                    song,
                    ->
                    queue.insertItem(song.toFields().toQueueItem(position))
                }
                queue.upsertState(
                    current_index = snapshot.currentIndex.toLong(),
                    position_ms = snapshot.positionMs,
                    shuffle = snapshot.shuffle.toLong(),
                    repeat_mode = snapshot.repeat.name,
                    play_order = encodeInts(snapshot.playOrder),
                )
            }
        }

    override suspend fun load(): QueueSnapshot? =
        withContext(context) {
            val snapshot = readSnapshot()
            if (snapshot == null && queue.countItems().executeAsOne() > 0) clear()
            snapshot
        }

    override suspend fun recordPlay(
        song: Song,
        atMillis: Long,
    ) = withContext(context) {
        val fields = song.toFields()
        database.transaction {
            history.insertPlay(
                video_id = fields.videoId,
                title = fields.title,
                artist_names = encodeStrings(fields.artistNames),
                artist_ids = encodeStrings(fields.artistIds),
                album_title = fields.albumTitle,
                album_id = fields.albumId,
                duration_ms = fields.durationMs,
                thumbnail_url = fields.thumbnailUrl,
                explicit = fields.explicit.toLong(),
                played_at = atMillis,
            )
            history.prune(MAX_HISTORY.toLong())
        }
    }

    /** Most recently played songs first, for the home screen. */
    fun recentHistory(limit: Int): Flow<List<Song>> =
        history
            .recent(limit.toLong())
            .asFlow()
            .mapToList(context)
            .map { rows -> rows.mapNotNull { it.toSong() } }

    private fun readSnapshot(): QueueSnapshot? =
        runCatching {
            val state = queue.state().executeAsOneOrNull() ?: return@runCatching null
            val items = queue.items().executeAsList().map { requireNotNull(it.toSong()) { "Invalid queue item" } }
            QueueSnapshot(
                items = items,
                currentIndex = state.current_index.toInt(),
                positionMs = state.position_ms,
                shuffle = state.shuffle != 0L,
                repeat = RepeatMode.valueOf(state.repeat_mode),
                playOrder = decodeInts(state.play_order),
            )
        }.getOrNull()?.takeIf { it.isRestorable }

    private fun clear() =
        database.transaction {
            queue.clearItems()
            queue.clearState()
        }

    companion object {
        /** Play history entries kept; older ones are pruned. */
        const val MAX_HISTORY = 500

        private fun Boolean.toLong(): Long = if (this) 1L else 0L

        private fun encodeStrings(values: List<String>): String = JsonArray(values.map(::JsonPrimitive)).toString()

        private fun encodeInts(values: List<Int>): String = JsonArray(values.map(::JsonPrimitive)).toString()

        private fun decodeStrings(json: String): List<String> =
            Json.parseToJsonElement(json).jsonArray.map {
                it.jsonPrimitive.content
            }

        private fun decodeInts(json: String): List<Int> =
            Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.int }

        private fun SongFields.toQueueItem(position: Int) =
            Queue_item(
                position = position.toLong(),
                video_id = videoId,
                title = title,
                artist_names = encodeStrings(artistNames),
                artist_ids = encodeStrings(artistIds),
                album_title = albumTitle,
                album_id = albumId,
                duration_ms = durationMs,
                thumbnail_url = thumbnailUrl,
                explicit = explicit.toLong(),
            )

        private fun Queue_item.toSong(): Song? =
            runCatching {
                SongFields(
                    videoId = video_id,
                    title = title,
                    artistNames = decodeStrings(artist_names),
                    artistIds = decodeStrings(artist_ids),
                    albumTitle = album_title,
                    albumId = album_id,
                    durationMs = duration_ms,
                    thumbnailUrl = thumbnail_url,
                    explicit = explicit != 0L,
                ).toSong()
            }.getOrNull()

        private fun Play_history.toSong(): Song? =
            runCatching {
                SongFields(
                    videoId = video_id,
                    title = title,
                    artistNames = decodeStrings(artist_names),
                    artistIds = decodeStrings(artist_ids),
                    albumTitle = album_title,
                    albumId = album_id,
                    durationMs = duration_ms,
                    thumbnailUrl = thumbnail_url,
                    explicit = explicit != 0L,
                ).toSong()
            }.getOrNull()
    }
}
