package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SyncedLineAtTest {
    private val lyrics =
        Lyrics.Synced(
            listOf(
                LyricLine(2.seconds, "one"),
                LyricLine(5.seconds, "two"),
                LyricLine(5.seconds, "two again"),
                LyricLine(9.seconds, ""),
                LyricLine(12.seconds, "three"),
            ),
        )

    @Test
    fun beforeTheFirstLineThereIsNone() {
        assertEquals(-1, lyrics.lineAt(0.seconds))
        assertEquals(-1, lyrics.lineAt(1999.milliseconds))
    }

    @Test
    fun aLineStartsExactlyAtItsTime() {
        assertEquals(0, lyrics.lineAt(2.seconds))
        assertEquals(4, lyrics.lineAt(12.seconds))
    }

    @Test
    fun betweenTwoLinesTheEarlierOneHolds() {
        assertEquals(0, lyrics.lineAt(4999.milliseconds))
        assertEquals(3, lyrics.lineAt(11.seconds))
    }

    @Test
    fun linesWithTheSameTimeResolveToTheLastOfThem() {
        assertEquals(2, lyrics.lineAt(5.seconds))
        assertEquals(2, lyrics.lineAt(8.seconds))
    }

    @Test
    fun afterTheLastLineItStays() {
        assertEquals(4, lyrics.lineAt(10_000.seconds))
    }

    @Test
    fun aSingleLine() {
        val single = Lyrics.Synced(listOf(LyricLine(3.seconds, "only")))
        assertEquals(-1, single.lineAt(2.seconds))
        assertEquals(0, single.lineAt(3.seconds))
    }

    @Test
    fun noLinesMeansNoLineAtAnyPosition() {
        val empty = Lyrics.Synced(emptyList())
        listOf(Duration.ZERO, 3.seconds, 10_000.seconds, (-1).seconds).forEach {
            assertEquals(-1, empty.lineAt(it))
        }
    }
}
