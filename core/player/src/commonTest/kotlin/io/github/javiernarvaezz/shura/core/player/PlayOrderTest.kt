package io.github.javiernarvaezz.shura.core.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayOrderTest {
    private fun PlayOrder.assertPermutation(size: Int) = assertEquals((0 until size).toList(), toList().sorted())

    @Test
    fun identityPlaysInOriginalOrder() {
        val order = PlayOrder.identity(4)

        assertEquals(listOf(0, 1, 2, 3), order.toList())
        assertEquals(0, order.first)
        assertEquals(3, order.last)
        assertEquals(2, order.next(1))
        assertNull(order.next(3))
        assertNull(order.previous(0))
    }

    @Test
    fun shuffleKeepsTheCurrentItemFirstAndIsAPermutation() {
        repeat(50) { seed ->
            val order = PlayOrder.shuffled(size = 20, current = 7, random = Random(seed))

            assertEquals(7, order.first)
            order.assertPermutation(20)
        }
    }

    @Test
    fun consecutiveShufflesGiveDifferentOrders() {
        val random = Random(42)

        val a = PlayOrder.shuffled(20, 0, random)
        val b = PlayOrder.shuffled(20, 0, random)

        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun shuffleOfEmptyOrSingleQueues() {
        assertEquals(emptyList(), PlayOrder.shuffled(0, 0, Random(1)).toList())
        assertEquals(listOf(0), PlayOrder.shuffled(1, 0, Random(1)).toList())
        assertNull(PlayOrder.identity(0).first)
    }

    @Test
    fun insertingAfterAPositionShiftsLaterIndices() {
        // Play order 2, 0, 1; two items inserted at original index 1 must play right after item 2 (position 0).
        val order = PlayOrder.fromList(listOf(2, 0, 1))

        val inserted = order.insert(index = 1, count = 2, atPosition = 1)

        // Old 0 stays 0, old 1 -> 3, old 2 -> 4; new items are 1 and 2.
        assertEquals(listOf(4, 1, 2, 0, 3), inserted.toList())
        inserted.assertPermutation(5)
    }

    @Test
    fun appendingPutsNewItemsAtTheEndOfThePlayOrder() {
        val order = PlayOrder.fromList(listOf(1, 0))

        assertEquals(listOf(1, 0, 2, 3), order.insert(index = 2, count = 2, atPosition = 2).toList())
    }

    @Test
    fun removingDropsTheRangeAndReindexes() {
        val order = PlayOrder.fromList(listOf(3, 0, 4, 1, 2))

        val removed = order.remove(fromIndex = 1, toIndex = 3)

        assertEquals(listOf(1, 0, 2), removed.toList())
        removed.assertPermutation(3)
    }

    @Test
    fun movingRemapsIndicesButKeepsThePlaySequence() {
        // Items: A0 B1 C2 D3, play order C B D A. Move B (index 1) to index 3: items become A C D B.
        val order = PlayOrder.fromList(listOf(2, 1, 3, 0))

        val moved = order.move(fromIndex = 1, toIndex = 2, newFromIndex = 3)

        // C is now 1, B is 3, D is 2, A is 0: play order stays C B D A.
        assertEquals(listOf(1, 3, 2, 0), moved.toList())
    }

    @Test
    fun fromListRejectsNonPermutations() {
        assertFailsWith<IllegalArgumentException> { PlayOrder.fromList(listOf(0, 0)) }
        assertFailsWith<IllegalArgumentException> { PlayOrder.fromList(listOf(1, 2)) }
    }

    @Test
    fun positionOfReportsWhereAnItemPlays() {
        val order = PlayOrder.fromList(listOf(2, 0, 1))

        assertEquals(0, order.positionOf(2))
        assertEquals(2, order.positionOf(1))
        assertTrue(order.positionOf(5) < 0)
    }
}
