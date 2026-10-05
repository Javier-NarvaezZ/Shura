package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song

/** Everything needed to restore the play queue where it was left. [playOrder] lists queue indices in play order. */
data class QueueSnapshot(
    val items: List<Song>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val playOrder: List<Int>,
) {
    /** A restorable queue: non-empty, current item in range, and a play order that is a permutation of the items. */
    val isRestorable: Boolean
        get() =
            items.isNotEmpty() &&
                currentIndex in items.indices &&
                positionMs >= 0 &&
                playOrder.sorted() == items.indices.toList()
}

/** Persistence used by the player. Implementations must not throw for storage errors that the caller can ignore. */
interface QueueStore {
    /** Replaces the saved queue; an empty [QueueSnapshot.items] clears it. */
    suspend fun save(snapshot: QueueSnapshot)

    /** The saved queue, or null when there is none or it is not restorable. */
    suspend fun load(): QueueSnapshot?

    /** Adds [song] to the play history at [atMillis] (epoch milliseconds). */
    suspend fun recordPlay(
        song: Song,
        atMillis: Long,
    )
}
