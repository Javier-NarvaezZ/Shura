package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.time.Duration

/** What to look lyrics up for. [title] and [artist] are trimmed and must not be blank; a blank [album] is unknown. */
class LyricsQuery(
    title: String,
    artist: String,
    album: String?,
    val duration: Duration,
) {
    val title: String = title.trim()
    val artist: String = artist.trim()
    val album: String? = album?.trim()?.takeIf { it.isNotEmpty() }

    init {
        require(this.title.isNotEmpty()) { "Title must not be blank" }
        require(this.artist.isNotEmpty()) { "Artist must not be blank" }
        require(duration.isPositive() && duration.isFinite()) { "Duration must be positive" }
    }
}

/** Lyrics of one song, best kind first: [Synced], then [Plain]; [Instrumental] means the song has no lyrics. */
sealed interface Lyrics {
    /** Time-stamped lines in display order. Blank lines mark where sung text pauses. */
    data class Synced(
        val lines: List<LyricLine>,
    ) : Lyrics {
        /**
         * The index of the line shown at [position]: the last one started by then, or -1 before the first (and
         * always with no lines). [lines] are sorted by time, as [LrcParser] returns them.
         */
        fun lineAt(position: Duration): Int {
            // Binary search for the first line that starts after the position; the line before it is shown.
            var low = 0
            var high = lines.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (lines[mid].time <= position) low = mid + 1 else high = mid
            }
            return low - 1
        }
    }

    data class Plain(
        val text: String,
    ) : Lyrics

    data object Instrumental : Lyrics
}

/** One line of synced lyrics, shown from [time] (from the start of the song) until the next line. */
data class LyricLine(
    val time: Duration,
    val text: String,
)
