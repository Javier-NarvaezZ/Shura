package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueAutoplayTest {
    private fun song(n: Int) = Song(VideoId("autoplay%03d".format(n)), "S$n", listOf(Artist("A")))

    private fun queue(
        size: Int,
        current: Int,
        repeat: RepeatMode = RepeatMode.Off,
        playOrder: List<Int> = List(size) { it },
    ) = QueueState(List(size) { song(it) }, current, repeat = repeat, playOrder = playOrder)

    @Test
    fun theLastSongInPlayOrderIsTheSeed() {
        assertEquals(song(2), QueueAutoplay.seedFor(queue(3, current = 2), enabled = true))
        assertNull(QueueAutoplay.seedFor(queue(3, current = 1), enabled = true))
    }

    @Test
    fun withShuffleItIsTheLastOfThePlayOrderNotOfTheQueue() {
        val shuffled = queue(3, current = 0, playOrder = listOf(2, 1, 0))

        assertEquals(song(0), QueueAutoplay.seedFor(shuffled, enabled = true))
        assertNull(QueueAutoplay.seedFor(queue(3, current = 2, playOrder = listOf(2, 1, 0)), enabled = true))
    }

    @Test
    fun nothingWhenOffRepeatingOrFull() {
        assertNull(QueueAutoplay.seedFor(queue(3, current = 2), enabled = false))
        assertNull(QueueAutoplay.seedFor(queue(3, current = 2, repeat = RepeatMode.All), enabled = true))
        assertNull(QueueAutoplay.seedFor(queue(3, current = 2, repeat = RepeatMode.One), enabled = true))
        assertNull(QueueAutoplay.seedFor(queue(QueueAutoplay.MAX_QUEUE, current = QueueAutoplay.MAX_QUEUE - 1), true))
        assertNull(QueueAutoplay.seedFor(QueueState(), enabled = true))
    }

    @Test
    fun appendsUpToABatchOfSongsNotAlreadyQueued() {
        val related = listOf(song(1)) + List(30) { song(100 + it) } + song(100)

        val added = QueueAutoplay.toAppend(related, queue(3, current = 2))

        assertEquals(List(QueueAutoplay.BATCH) { song(100 + it) }, added)
    }

    @Test
    fun neverGrowsTheQueuePastTheCap() {
        val nearlyFull = queue(QueueAutoplay.MAX_QUEUE - 3, current = QueueAutoplay.MAX_QUEUE - 4)

        assertEquals(3, QueueAutoplay.toAppend(List(30) { song(900 + it) }, nearlyFull).size)
    }
}
