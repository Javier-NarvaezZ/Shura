package io.github.javiernarvaezz.shura.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * Media3 [ShuffleOrder] backed by [PlayOrder]. Items inserted by the player go to the end of the play order (the
 * "add to queue" behaviour); "play next" is placed explicitly by [ShuraPlayer].
 */
@OptIn(UnstableApi::class)
internal class QueueShuffleOrder(
    val playOrder: PlayOrder,
) : ShuffleOrder {
    override fun getLength(): Int = playOrder.size

    override fun getNextIndex(index: Int): Int = playOrder.next(index) ?: C.INDEX_UNSET

    override fun getPreviousIndex(index: Int): Int = playOrder.previous(index) ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = playOrder.last ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = playOrder.first ?: C.INDEX_UNSET

    override fun cloneAndInsert(
        insertionIndex: Int,
        insertionCount: Int,
    ): ShuffleOrder = QueueShuffleOrder(playOrder.insert(insertionIndex, insertionCount, atPosition = playOrder.size))

    override fun cloneAndRemove(
        indexFrom: Int,
        indexToExclusive: Int,
    ): ShuffleOrder = QueueShuffleOrder(playOrder.remove(indexFrom, indexToExclusive))

    override fun cloneAndMove(
        indexFrom: Int,
        indexToExclusive: Int,
        newIndexFrom: Int,
    ): ShuffleOrder = QueueShuffleOrder(playOrder.move(indexFrom, indexToExclusive, newIndexFrom))

    override fun cloneAndClear(): ShuffleOrder = QueueShuffleOrder(PlayOrder.identity(0))
}
