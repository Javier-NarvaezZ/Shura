package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Where a song stands in "escuchar sin conexión" (listening without a connection). */
sealed interface OfflineState {
    /** Waiting its turn, or for the network it is allowed to use. */
    data object Queued : OfflineState

    /** [fraction] 0..1 once the size is known. */
    data class Downloading(
        val fraction: Float?,
    ) : OfflineState

    /** Stopped before the end (the system's time limit, the network); it goes on later from where it was. */
    data object Paused : OfflineState

    data object Downloaded : OfflineState

    /** It could not be downloaded (no stream, no room); [reason] for the message shown. */
    data class Failed(
        val reason: OfflineFailure,
    ) : OfflineState
}

enum class OfflineFailure { NoRoom, Unavailable, Other }

/** The network a download may use. */
enum class OfflineNetwork { Any, Unmetered }

/**
 * Songs kept to listen without a connection: in the app's private storage, encrypted, never exported or shared.
 * Only the songs listed in [states] are kept; a song not there is streamed as usual.
 */
interface OfflineLibrary {
    val states: StateFlow<Map<VideoId, OfflineState>>

    /** Space the kept songs use, in bytes. */
    val usedBytes: StateFlow<Long>

    /** Keeps [song] (the album track for a video known to stand for one). */
    fun download(song: Song)

    fun remove(videoId: VideoId)

    fun removeAll()
}

/** The rules of "escuchar sin conexión", apart from the platform. */
object OfflineRules {
    /** A kept song's key: its video id and audio format, so another format is another file. */
    fun cacheKey(
        videoId: VideoId,
        itag: Int,
    ): String = "${videoId.value}:$itag"

    /** What is kept for [song]: the album track a video is known to stand for ([trackOf]), else the song as it is. */
    fun downloadable(
        song: Song,
        trackOf: (VideoId) -> VideoId?,
    ): Song {
        val track = if (song.isVideo) trackOf(song.videoId) else null
        return if (track == null) song else song.copy(videoId = track, isVideo = false)
    }

    /**
     * Whether [incomingBytes] more fit the user's [limitBytes] (none: no limit). An unknown size adds nothing, but
     * a full limit refuses it. Nothing kept is ever removed to make room.
     */
    fun fits(
        usedBytes: Long,
        incomingBytes: Long?,
        limitBytes: Long?,
    ): Boolean =
        when {
            limitBytes == null -> true
            incomingBytes == null -> usedBytes < limitBytes
            else -> usedBytes + incomingBytes <= limitBytes
        }

    /** "Solo con Wi-Fi", or the system's data saver, wait for an unmetered network. */
    fun network(
        wifiOnly: Boolean,
        dataSaver: Boolean,
    ): OfflineNetwork = if (wifiOnly || dataSaver) OfflineNetwork.Unmetered else OfflineNetwork.Any
}

/** A song kept to listen without a connection: what to show without a network, and its file's key and size. */
data class OfflineSong(
    val song: Song,
    val itag: Int,
    val bytes: Long,
    val savedAtMillis: Long,
) {
    val cacheKey: String get() = OfflineRules.cacheKey(song.videoId, itag)
}

/** The kept songs' records (the app database on Android); the files themselves are the cache's. */
interface OfflineSongStore {
    /** Newest first. */
    val songs: Flow<List<OfflineSong>>

    suspend fun put(song: OfflineSong)

    suspend fun remove(videoId: VideoId)

    suspend fun clear()
}
