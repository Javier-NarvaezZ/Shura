package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class LrcParserTest {
    private fun line(
        millis: Long,
        text: String,
    ) = LyricLine(millis.milliseconds, text)

    @Test
    fun parsesCentisecondTimestamps() {
        val lines = LrcParser.parse("[00:12.34] First line\n[01:02.50] Second line")
        assertEquals(listOf(line(12_340, "First line"), line(62_500, "Second line")), lines)
    }

    @Test
    fun acceptsSecondsTenthsAndMilliseconds() {
        val lines = LrcParser.parse("[00:01] a\n[00:02.5] b\n[00:03.250] c\n[00:04:75] d")
        assertEquals(listOf(line(1_000, "a"), line(2_500, "b"), line(3_250, "c"), line(4_750, "d")), lines)
    }

    @Test
    fun repeatedLineWithSeveralTimestampsIsSortedByTime() {
        val lines = LrcParser.parse("[00:30.00][00:10.00] Chorus\n[00:20.00] Verse")
        assertEquals(listOf(line(10_000, "Chorus"), line(20_000, "Verse"), line(30_000, "Chorus")), lines)
    }

    @Test
    fun keepsBlankTimedLinesAsPauses() {
        val lines = LrcParser.parse("[00:05.00] Sung\n[00:09.00]\n[00:12.00] Again")
        assertEquals(line(9_000, ""), lines[1])
    }

    @Test
    fun skipsMetadataAndMalformedLines() {
        val lrc =
            """
            [ar:Someone]
            [ti:Something]
            [length: 03:20]
            no tag at all
            [00:61.00] seconds out of range
            [aa:bb.cc] not a time
            [00:01.00 missing bracket
            [00:02.00][xx] mixed tags
            [00:03.00] valid
            """.trimIndent()
        assertEquals(listOf(line(3_000, "valid")), LrcParser.parse(lrc))
    }

    @Test
    fun handlesWindowsAndOldMacLineBreaks() {
        assertEquals(2, LrcParser.parse("[00:01.00] a\r\n[00:02.00] b").size)
        assertEquals(2, LrcParser.parse("[00:01.00] a\r[00:02.00] b").size)
    }

    @Test
    fun positiveOffsetShowsLyricsSooner() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00] a\n[00:00.20] b")
        assertEquals(listOf(line(0, "b"), line(9_500, "a")), lines)
    }

    @Test
    fun negativeOffsetShowsLyricsLater() {
        assertEquals(listOf(line(10_250, "a")), LrcParser.parse("[00:10.00] a\n[offset:-250]"))
    }

    @Test
    fun removesInlineWordTimings() {
        val lines = LrcParser.parse("[00:01.00] <00:01.00>Hello <00:01.50>world")
        assertEquals(listOf(line(1_000, "Hello world")), lines)
    }

    @Test
    fun emptyOrUntimedTextGivesNoLines() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("Just plain lyrics\nwith no tags").isEmpty())
    }

    @Test
    fun longSongsKeepMinutesAbove59() {
        assertEquals(Duration.parse("75m 3s"), LrcParser.parse("[75:03.00] late").single().time)
    }
}
