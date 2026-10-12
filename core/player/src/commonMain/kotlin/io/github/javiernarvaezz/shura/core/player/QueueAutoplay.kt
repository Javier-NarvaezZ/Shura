package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song

/**
 * When and what to add so a queue does not end (autoplay): once the song playing is the last in play order, its
 * related songs not already queued, a batch at a time. Repeat wins (the queue never ends), and the queue stops
 * growing at [MAX_QUEUE] songs.
 */
object QueueAutoplay {
    const val BATCH = 20
    const val MAX_QUEUE = 500

    /** The song to ask related songs for, or null when nothing should be added now. */
    fun seedFor(
        queue: QueueState,
        enabled: Boolean,
    ): Song? {
        val last = queue.playOrder.lastOrNull() ?: queue.items.lastIndex
        val ready = enabled && queue.repeat == RepeatMode.Off && queue.items.size < MAX_QUEUE
        return queue.current?.takeIf { ready && queue.currentIndex == last }
    }

    /** The songs to append from [related]: not already in the queue, at most a batch, never past the cap. */
    fun toAppend(
        related: List<Song>,
        queue: QueueState,
    ): List<Song> {
        val queued = queue.items.mapTo(HashSet()) { it.videoId }
        val room = (MAX_QUEUE - queue.items.size).coerceAtLeast(0)
        return related
            .filter { it.videoId !in queued }
            .distinctBy { it.videoId }
            .take(minOf(BATCH, room))
    }
}
