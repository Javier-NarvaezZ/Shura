package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.videoIdOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Moves songs saved before album rows were mapped to their tracks (Phase 3, E5b) from the artist's video upload to
 * the album track: the history, the liked songs on this device, the queue and a song's lyrics timing fix. Their
 * albums are asked once each with [albumTracks] (the album page's videos and their tracks; null when it cannot be
 * reached now). The known videos are kept ([tracks]), so a queue saved again with a video id is restored as the
 * track. The account's own likes are not changed.
 */
class SavedSongRepair(
    private val database: ShuraDatabase,
    private val ioContext: CoroutineContext,
    private val albumTracks: suspend (albumId: String) -> Map<VideoId, VideoId>?,
) {
    private val repair get() = database.repairQueries

    /** Videos known to stand for an album track, and that track. */
    val tracks: Flow<Map<VideoId, VideoId>> =
        repair
            .videoTracks()
            .asFlow()
            .mapToList(ioContext)
            .map { rows ->
                rows
                    .mapNotNull { row ->
                        videoIdOf(row.video_id)?.let { v -> videoIdOf(row.track_id)?.let { v to it } }
                    }.toMap()
            }

    /**
     * Repairs what is saved, once; true when done (now or before). An album that cannot be reached now leaves it
     * to be finished on a later call; what was already repaired stays.
     */
    suspend fun repairOnce(): Boolean = isDone() || repairAll()

    private suspend fun isDone(): Boolean =
        withContext(ioContext) { database.settingsQueries.valueOf(DONE_KEY).executeAsOneOrNull() } == DONE

    private suspend fun repairAll(): Boolean {
        val albums = withContext(ioContext) { repair.savedAlbumIds().executeAsList().mapNotNull { it.album_id } }
        for (album in albums) {
            val moves = albumTracks(album) ?: return false
            withContext(ioContext) { database.transaction { moves.forEach { (video, track) -> move(video, track) } } }
        }
        withContext(ioContext) { database.settingsQueries.put(DONE_KEY, DONE) }
        return true
    }

    private fun move(
        video: VideoId,
        track: VideoId,
    ) {
        repair.putVideoTrack(video.value, track.value)
        repair.moveQueueItem(track = track.value, video = video.value)
        repair.moveHistory(track = track.value, video = video.value)
        repair.moveLike(track = track.value, video = video.value)
        repair.removeLike(video.value)
        repair.moveLyricsOffset(track = track.value, video = video.value)
        repair.removeLyricsOffset(video.value)
        repair.forgetLyrics(video.value)
    }

    private companion object {
        const val DONE_KEY = "repair.album_videos"
        const val DONE = "done"
    }
}
