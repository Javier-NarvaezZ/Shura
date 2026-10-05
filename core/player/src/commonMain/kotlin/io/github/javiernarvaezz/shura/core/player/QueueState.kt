package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import kotlin.time.Duration.Companion.milliseconds

enum class RepeatMode { Off, All, One }

/**
 * The play queue as the UI sees it. [items] are in queue order; [playOrder] lists their indices in the order they
 * will play (the queue order, or the shuffled one when [shuffle] is on).
 */
data class QueueState(
    val items: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val playOrder: List<Int> = emptyList(),
) {
    val current: Song? get() = items.getOrNull(currentIndex)

    /** The item that plays after the current one, ignoring repeat. */
    val next: Song? get() =
        playOrder
            .indexOf(currentIndex)
            .takeIf {
                it >= 0
            }?.let { playOrder.getOrNull(it + 1) }
            ?.let(items::getOrNull)
}

/**
 * A [Song] as flat fields, so it can travel inside a media item (and later be stored) without losing structured
 * artists or album references. [artistIds] holds an empty string where an artist has no id.
 */
data class SongFields(
    val videoId: String,
    val title: String,
    val artistNames: List<String>,
    val artistIds: List<String>,
    val albumTitle: String?,
    val albumId: String?,
    val durationMs: Long?,
    val thumbnailUrl: String?,
    val explicit: Boolean,
)

fun Song.toFields(): SongFields =
    SongFields(
        videoId = videoId.value,
        title = title,
        artistNames = artists.map { it.name },
        artistIds = artists.map { it.id.orEmpty() },
        albumTitle = album?.title,
        albumId = album?.id,
        durationMs = duration?.inWholeMilliseconds,
        thumbnailUrl = thumbnailUrl,
        explicit = isExplicit,
    )

/** The song, or null when the fields are invalid (e.g. a malformed video id from an untrusted controller). */
fun SongFields.toSong(): Song? =
    runCatching {
        require(artistNames.size == artistIds.size) { "Artist fields mismatch" }
        Song(
            videoId = requireNotNull(videoIdOf(videoId)),
            title = title,
            artists = artistNames.zip(artistIds) { name, id -> Artist(name, id.ifEmpty { null }) },
            album = albumTitle?.let { AlbumRef(it, albumId) },
            duration = durationMs?.milliseconds,
            thumbnailUrl = thumbnailUrl,
            isExplicit = explicit,
        )
    }.getOrNull()
