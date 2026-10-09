package io.github.javiernarvaezz.shura.feature.player

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpcomingArtworkTest {
    private val a = testSong(1, "A").copy(thumbnailUrl = "https://lh3.googleusercontent.com/a=w60-h60")
    private val b = testSong(2, "B").copy(thumbnailUrl = "https://lh3.googleusercontent.com/b=w60-h60")
    private val c = testSong(3, "C").copy(thumbnailUrl = "https://lh3.googleusercontent.com/c=w60-h60")

    private fun queue(
        current: Int,
        repeat: RepeatMode = RepeatMode.Off,
        playOrder: List<Int> = listOf(0, 1, 2),
        items: List<Song> = listOf(a, b, c),
    ) = QueueState(items = items, currentIndex = current, repeat = repeat, playOrder = playOrder)

    @Test
    fun theNextSongInPlayOrder() {
        assertEquals(b, upcomingSong(queue(current = 0)))
        assertEquals(a, upcomingSong(queue(current = 2, playOrder = listOf(2, 0, 1))))
    }

    @Test
    fun theEndOfTheQueueHasNothingUpcomingWithoutRepeatAll() {
        assertNull(upcomingSong(queue(current = 2)))
        assertNull(upcomingSong(queue(current = 2, repeat = RepeatMode.One)))
        assertNull(upcomingSong(QueueState()))
    }

    @Test
    fun repeatAllWrapsToTheFirstSongInPlayOrder() {
        assertEquals(a, upcomingSong(queue(current = 2, repeat = RepeatMode.All)))
        assertEquals(c, upcomingSong(queue(current = 1, repeat = RepeatMode.All, playOrder = listOf(2, 0, 1))))
    }

    @Test
    fun aSingleSongRepeatingHasNothingElseToLoad() {
        assertNull(upcomingSong(queue(current = 0, repeat = RepeatMode.All, playOrder = listOf(0), items = listOf(a))))
    }

    @Test
    fun theModelExposesTheUpcomingCover() =
        runTest {
            val player = FakeAudioPlayer()
            val model = PlayerModel(player, backgroundScope)

            player.queue.value = queue(current = 0)
            runCurrent()
            assertEquals(b.thumbnailUrl, model.upcomingArtworkUrl.value)

            player.queue.value = queue(current = 2)
            runCurrent()
            assertNull(model.upcomingArtworkUrl.value)

            player.queue.value = queue(current = 2, repeat = RepeatMode.All)
            runCurrent()
            assertEquals(a.thumbnailUrl, model.upcomingArtworkUrl.value)
        }
}
