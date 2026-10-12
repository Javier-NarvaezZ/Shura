package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Parses LRC text into [LyricLine]s sorted by time.
 *
 * Accepts `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]` and `[mm:ss.xxx]` (also with `:` before the fraction), several time
 * tags on one line, and the `[offset:±ms]` tag. Other metadata tags, lines without a valid time tag and malformed
 * tags are skipped. Inline word timings (`<mm:ss.xx>`) are removed from the text.
 */
object LrcParser {
    private val LEADING_TAGS = Regex("""^\s*((?:\[[^\[\]]*])+)(.*)$""")
    private val TAG = Regex("""\[([^\[\]]*)]""")
    private val TIME = Regex("""^\s*(\d{1,3}):([0-5]?\d)(?:[.:](\d{1,3}))?\s*$""")
    private val OFFSET = Regex("""^\s*offset\s*:\s*([+-]?\d{1,7})\s*$""", RegexOption.IGNORE_CASE)
    private val WORD_TIMING = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val LINE_BREAK = Regex("""\r\n|\r|\n""")

    private const val MILLIS_DIGITS = 3

    fun parse(lrc: String): List<LyricLine> {
        var offset = Duration.ZERO
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.split(LINE_BREAK)) {
            val match = LEADING_TAGS.find(raw) ?: continue
            val tags = TAG.findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
            val times = tags.mapNotNull(::time)
            if (times.size == tags.size) {
                val text = match.groupValues[2].replace(WORD_TIMING, "").trim()
                times.forEach { lines += LyricLine(it, text) }
            } else if (tags.size == 1) {
                OFFSET.find(tags.single())?.let { offset = it.groupValues[1].toLong().milliseconds }
            }
        }
        // A positive offset shows lyrics sooner.
        return lines
            .map { it.copy(time = (it.time - offset).coerceAtLeast(Duration.ZERO)) }
            .sortedBy { it.time }
    }

    private fun time(tag: String): Duration? {
        val match = TIME.find(tag) ?: return null
        val (minutes, seconds, fraction) = match.destructured
        val millis = if (fraction.isEmpty()) 0 else fraction.padEnd(MILLIS_DIGITS, '0').toInt()
        return minutes.toInt().minutes + seconds.toInt().seconds + millis.milliseconds
    }
}
