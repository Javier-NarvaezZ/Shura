package io.github.javiernarvaezz.shura.core.player

import kotlin.random.Random

/**
 * The order in which queue items play: a permutation of their indices in the queue. Immutable.
 *
 * Shuffling is "real": each shuffle keeps the current item first and draws a fresh Fisher–Yates order for the rest,
 * and turning shuffle off simply plays the queue in its original order again.
 */
class PlayOrder private constructor(
    private val order: IntArray,
) {
    val size: Int get() = order.size

    /** Queue index of the first item to play, or null when empty. */
    val first: Int? get() = order.firstOrNull()

    /** Queue index of the last item to play, or null when empty. */
    val last: Int? get() = order.lastOrNull()

    /** Position of queue item [index] in the play order, or -1 if it is not in the queue. */
    fun positionOf(index: Int): Int = order.indexOf(index)

    /** Queue index that plays after [index], or null at the end. */
    fun next(index: Int): Int? = positionOf(index).let { if (it < 0) null else order.getOrNull(it + 1) }

    /** Queue index that plays before [index], or null at the start. */
    fun previous(index: Int): Int? = positionOf(index).let { if (it <= 0) null else order[it - 1] }

    /**
     * Inserts [count] new queue items at queue index [index] (later items shift up) and places them, in queue order,
     * at play position [atPosition]: right after the current item for "play next", or at the end for "add to queue".
     */
    fun insert(
        index: Int,
        count: Int,
        atPosition: Int,
    ): PlayOrder {
        require(index in 0..size && count >= 0 && atPosition in 0..size) { "Invalid insertion" }
        val shifted = order.map { if (it >= index) it + count else it }.toMutableList()
        shifted.addAll(atPosition, (index until index + count).toList())
        return PlayOrder(shifted.toIntArray())
    }

    /** Removes queue items [fromIndex] until [toIndex]; later items shift down. */
    fun remove(
        fromIndex: Int,
        toIndex: Int,
    ): PlayOrder {
        require(fromIndex in 0..toIndex && toIndex <= size) { "Invalid removal" }
        val count = toIndex - fromIndex
        return PlayOrder(
            order
                .filter { it !in fromIndex until toIndex }
                .map { if (it >= toIndex) it - count else it }
                .toIntArray(),
        )
    }

    /**
     * Moves queue items [fromIndex] until [toIndex] so that they start at [newFromIndex] (Media3 semantics). Only
     * queue indices change: the play sequence stays the same.
     */
    fun move(
        fromIndex: Int,
        toIndex: Int,
        newFromIndex: Int,
    ): PlayOrder {
        require(fromIndex in 0..toIndex && toIndex <= size) { "Invalid move" }
        val count = toIndex - fromIndex
        require(newFromIndex in 0..size - count) { "Invalid move target" }
        val queue = (0 until size).toMutableList()
        val moved = queue.subList(fromIndex, toIndex).toList()
        repeat(count) { queue.removeAt(fromIndex) }
        queue.addAll(newFromIndex, moved)
        val newIndexOf = IntArray(size).also { map -> queue.forEachIndexed { newIndex, old -> map[old] = newIndex } }
        return PlayOrder(order.map { newIndexOf[it] }.toIntArray())
    }

    fun toList(): List<Int> = order.toList()

    override fun equals(other: Any?): Boolean = other is PlayOrder && order.contentEquals(other.order)

    override fun hashCode(): Int = order.contentHashCode()

    override fun toString(): String = "PlayOrder(${order.joinToString()})"

    companion object {
        fun identity(size: Int): PlayOrder = PlayOrder(IntArray(size) { it })

        /** The [current] item first, then the rest in a Fisher–Yates order drawn from [random]. */
        fun shuffled(
            size: Int,
            current: Int,
            random: Random = Random.Default,
        ): PlayOrder {
            if (size == 0) return identity(0)
            require(current in 0 until size) { "Current item out of range" }
            val rest = (0 until size).filter { it != current }.toIntArray()
            for (i in rest.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                rest[i] = rest[j].also { rest[j] = rest[i] }
            }
            return PlayOrder(intArrayOf(current) + rest)
        }

        /** Restores a saved order; it must be a permutation of `0 until size`. */
        fun fromList(order: List<Int>): PlayOrder {
            require(order.sorted() == order.indices.toList()) { "Not a permutation" }
            return PlayOrder(order.toIntArray())
        }
    }
}
