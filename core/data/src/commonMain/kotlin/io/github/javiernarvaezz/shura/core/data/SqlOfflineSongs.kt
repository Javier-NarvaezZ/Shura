package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.javiernarvaezz.shura.core.data.db.Offline_song
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.OfflineSong
import io.github.javiernarvaezz.shura.core.player.OfflineSongStore
import io.github.javiernarvaezz.shura.core.player.SongFields
import io.github.javiernarvaezz.shura.core.player.toFields
import io.github.javiernarvaezz.shura.core.player.toSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [OfflineSongStore] in the app database, on [ioContext]. A row that cannot be read back is left out. */
class SqlOfflineSongs(
    private val database: ShuraDatabase,
    private val ioContext: CoroutineContext,
) : OfflineSongStore {
    private val queries get() = database.offlineQueries

    override val songs: Flow<List<OfflineSong>> =
        queries
            .offlineSongs()
            .asFlow()
            .mapToList(ioContext)
            .map { rows -> rows.mapNotNull { runCatching { it.toOfflineSong() }.getOrNull() } }

    override suspend fun put(song: OfflineSong) {
        val fields = song.song.toFields()
        withContext(ioContext) {
            queries.putOfflineSong(
                Offline_song(
                    video_id = fields.videoId,
                    title = fields.title,
                    artist_names = encodeStrings(fields.artistNames),
                    artist_ids = encodeStrings(fields.artistIds),
                    album_title = fields.albumTitle,
                    album_id = fields.albumId,
                    duration_ms = fields.durationMs,
                    thumbnail_url = fields.thumbnailUrl,
                    explicit = fields.explicit.toLong(),
                    itag = song.itag.toLong(),
                    bytes = song.bytes,
                    saved_at = song.savedAtMillis,
                    complete = song.complete.toLong(),
                ),
            )
        }
    }

    override suspend fun complete(
        videoId: VideoId,
        bytes: Long,
    ) {
        withContext(ioContext) { queries.completeOfflineSong(bytes = bytes, videoId = videoId.value) }
    }

    override suspend fun remove(videoId: VideoId) {
        withContext(ioContext) { queries.removeOfflineSong(videoId.value) }
    }

    override suspend fun clear() {
        withContext(ioContext) { queries.clearOfflineSongs() }
    }

    private fun Offline_song.toOfflineSong(): OfflineSong? {
        val song =
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
            ).toSong() ?: return null
        return OfflineSong(song, itag.toInt(), bytes, saved_at, complete != 0L)
    }
}
