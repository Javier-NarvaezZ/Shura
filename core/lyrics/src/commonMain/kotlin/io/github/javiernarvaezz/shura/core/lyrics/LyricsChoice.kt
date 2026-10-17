package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The lyrics one LRCLIB record offers for a song, and how far its duration is from the song's. */
internal class Candidate(
    val lyrics: Lyrics,
    val off: Duration,
    val exact: Boolean,
) {
    /** The sung lines (blank pauses left out), with their time when synced. */
    val sung: List<LyricLine> =
        when (lyrics) {
            is Lyrics.Synced -> {
                lyrics.lines.filter { it.text.isNotBlank() }
            }

            is Lyrics.Plain -> {
                lyrics.text
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { LyricLine(Duration.ZERO, it) }
            }

            Lyrics.Instrumental -> {
                emptyList()
            }
        }

    companion object {
        /**
         * What [record] offers for a song of [duration]. Its timing is kept only within [LrcLibClient.DURATION_TOLERANCE]
         * and when the text fits the song: a last line after the song has ended means the text was timed for another
         * cut, so every line could be off. Otherwise only its words count, within [LrcLibClient.TEXT_TOLERANCE].
         */
        fun of(
            record: LrcLibRecord,
            duration: Duration,
            exact: Boolean = false,
        ): Candidate? {
            val off = (record.duration - duration).absoluteValue
            if (off > LrcLibClient.TEXT_TOLERANCE) return null
            val timed = record.lyrics()?.takeIf { off <= LrcLibClient.DURATION_TOLERANCE }
            val lyrics = if (timed is Lyrics.Synced && !timed.fits(duration)) record.text() else timed ?: record.text()
            return lyrics?.let { Candidate(it, off, exact) }
        }

        private fun Lyrics.Synced.fits(duration: Duration): Boolean {
            val last = lines.lastOrNull { it.text.isNotBlank() } ?: return true
            return last.time <= duration + FIT_MARGIN
        }

        /** How long after the song's end a last line may still start: LRCLIB's own duration tolerance. */
        private val FIT_MARGIN = LrcLibClient.DURATION_TOLERANCE
    }
}

/**
 * The best of [candidates]: synced lyrics first, then plain, then instrumental. For an [explicit] song, the version
 * with the fewest censored words comes next ([LyricsCensorship]). Then LRCLIB's exact match, then the timing most
 * other records share (the same text is often filed under records of a different cut), then the closest duration.
 */
internal fun choose(
    candidates: List<Candidate>,
    explicit: Boolean,
): Lyrics? {
    val censored = if (explicit) LyricsCensorship.scores(candidates) else null
    return candidates
        .withIndex()
        .minWithOrNull(
            compareBy<IndexedValue<Candidate>> { rank(it.value.lyrics) }
                .thenBy { censored?.get(it.index) ?: 0 }
                .thenBy { if (it.value.exact) 0 else 1 }
                .thenByDescending { support(it.value, candidates) }
                .thenBy { it.value.off },
        )?.value
        ?.lyrics
}

private fun rank(lyrics: Lyrics): Int =
    when (lyrics) {
        is Lyrics.Synced -> 0
        is Lyrics.Plain -> 1
        Lyrics.Instrumental -> 2
    }

/** How many other synced candidates start and end their sung lines at about the same times as [candidate]. */
private fun support(
    candidate: Candidate,
    candidates: List<Candidate>,
): Int {
    if (candidate.lyrics !is Lyrics.Synced || candidate.sung.isEmpty()) return 0
    return candidates.count { other ->
        other !== candidate &&
            other.lyrics is Lyrics.Synced &&
            other.sung.isNotEmpty() &&
            (other.sung.first().time - candidate.sung.first().time).absoluteValue <= SAME_TIMING &&
            (other.sung.last().time - candidate.sung.last().time).absoluteValue <= SAME_TIMING
    }
}

private val SAME_TIMING = 500.milliseconds

/**
 * Censored lyrics, told apart without a word list: masked words ("f**k"), and words cut short where another
 * version of the same line has the whole word ("co" where another has "coño", "p" for "puta").
 */
internal object LyricsCensorship {
    private val WORD_EDGES = Regex("""^[^\p{L}\p{N}*]+|[^\p{L}\p{N}*]+$""")
    private val MASKED = Regex("""\p{L}\*+\p{L}?|\p{L}\*|\*\p{L}""")
    private val ALIGN_WITHIN = 1.seconds
    private const val MIN_LETTERS_CUT = 2

    /** For each candidate, its masked words plus the words it cuts short against any other candidate. */
    fun scores(candidates: List<Candidate>): List<Int> {
        val words = candidates.map { candidate -> candidate.sung.map { words(it.text) } }
        return candidates.indices.map { i ->
            val masked = words[i].sumOf { line -> line.count { MASKED.containsMatchIn(it) } }
            val cut =
                candidates.indices.filter { it != i }.sumOf { j ->
                    alignedLines(candidates[i], candidates[j]).sumOf { (a, b) -> cutWords(words[i][a], words[j][b]) }
                }
            masked + cut
        }
    }

    private fun words(line: String): List<String> =
        line.split(' ', '\t').map { it.replace(WORD_EDGES, "").lowercase() }.filter { it.isNotEmpty() }

    /** Pairs of line indices that sing the same line: by time when both are synced, else by position. */
    private fun alignedLines(
        a: Candidate,
        b: Candidate,
    ): List<Pair<Int, Int>> =
        if (a.lyrics is Lyrics.Synced && b.lyrics is Lyrics.Synced) {
            a.sung.indices.mapNotNull { i ->
                val time = a.sung[i].time
                b.sung.indices
                    .minByOrNull { (b.sung[it].time - time).absoluteValue }
                    ?.takeIf { (b.sung[it].time - time).absoluteValue <= ALIGN_WITHIN }
                    ?.let { i to it }
            }
        } else if (a.sung.size == b.sung.size) {
            a.sung.indices.map { it to it }
        } else {
            emptyList()
        }

    /** Words of [line] that are the start of a longer word at the same place in [other]. */
    private fun cutWords(
        line: List<String>,
        other: List<String>,
    ): Int {
        if (line.size != other.size) return 0
        return line.indices.count { k ->
            val word = line[k].trimEnd('-')
            val whole = other[k]
            word.isNotEmpty() && '*' !in word && whole.startsWith(word) && whole.length - word.length >= MIN_LETTERS_CUT
        }
    }
}
