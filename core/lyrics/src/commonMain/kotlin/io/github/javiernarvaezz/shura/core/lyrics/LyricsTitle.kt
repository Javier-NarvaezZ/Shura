package io.github.javiernarvaezz.shura.core.lyrics

/**
 * Titles and artists as LRCLIB knows them. Catalog titles often carry tags of the upload rather than the song
 * ("(Visualizer)", "(Remastered 2026)", "- Versión editada", "(con Rema)"); only such known tags at the end are
 * removed, so titles like "En La Intimidad (Como Cada Viernes)" stay whole.
 */
object LyricsTitle {
    private const val TAG =
        """(?:\d{4}\s+)?(?:visualizer|v[ií]deo\s+oficial|(?:official\s+)?(?:music\s+)?video|""" +
            """videoclip(?:\s+oficial)?|lyrics?\s+video|v[ií]deo\s+lyrics?|(?:con\s+)?letras?|lyrics|""" +
            """audio(?:\s+oficial)?|official\s+audio|remaster(?:ed|izad[oa])?|en\s+vivo|live|en\s+directo|""" +
            """versi[oó]n\s+editada|(?:radio\s+)?edit|expl[ií]cit[oa]?|explicit)(?:\s+\d{4})?"""
    private const val FEATURING = """(?:con|feat\.?|ft\.?|featuring)\s+[^()\[\]]+"""

    private val BRACKETED = Regex("""\s*[(\[]\s*(?:$TAG|$FEATURING)\s*[)\]]\s*$""", RegexOption.IGNORE_CASE)
    private val DASHED = Regex("""\s+[-–—]\s+$TAG\s*$""", RegexOption.IGNORE_CASE)
    private val TRAILING_FEATURING = Regex("""\s+(?:feat\.?|ft\.?)\s+.+$""", RegexOption.IGNORE_CASE)
    private val ARTIST_CHANNEL = Regex("""\s*(?:-\s*Topic|VEVO)\s*$""", RegexOption.IGNORE_CASE)

    /** [title] without trailing upload tags; the title itself if cleaning would leave nothing. */
    fun clean(title: String): String {
        var current = title.trim()
        while (true) {
            val next =
                listOf(
                    BRACKETED,
                    DASHED,
                    TRAILING_FEATURING,
                ).fold(current) { text, tag -> text.replace(tag, "") }.trim()
            if (next == current || next.isEmpty()) return current
            current = next
        }
    }

    private val ARTIST_DASH_TITLE = Regex("""^(.+?)\s+[-–—]\s+(.+)$""")
    private val NOT_A_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val ACCENTS =
        mapOf(
            'á' to 'a',
            'à' to 'a',
            'â' to 'a',
            'ä' to 'a',
            'ã' to 'a',
            'å' to 'a',
            'é' to 'e',
            'è' to 'e',
            'ê' to 'e',
            'ë' to 'e',
            'í' to 'i',
            'ì' to 'i',
            'î' to 'i',
            'ï' to 'i',
            'ó' to 'o',
            'ò' to 'o',
            'ô' to 'o',
            'ö' to 'o',
            'õ' to 'o',
            'ú' to 'u',
            'ù' to 'u',
            'û' to 'u',
            'ü' to 'u',
            'ñ' to 'n',
            'ç' to 'c',
        )

    /**
     * A fan upload's "Artist - Title" (its channel is not the artist): the artist and the cleaned title, or null
     * when the title has no such dash.
     */
    fun artistAndTitle(title: String): Pair<String, String>? {
        val match = ARTIST_DASH_TITLE.matchEntire(clean(title)) ?: return null
        val artist = match.groupValues[1].trim()
        val song = clean(match.groupValues[2])
        return if (artist.isEmpty() || song.isEmpty()) null else artist to song
    }

    /** [text] for comparing names: cleaned, lower case, without accents or punctuation. */
    fun normalized(text: String): String =
        clean(text)
            .lowercase()
            .map { ACCENTS[it] ?: it }
            .joinToString("")
            .replace(NOT_A_WORD, " ")
            .trim()

    /** [artist] without a channel suffix (" - Topic", "VEVO"). */
    fun cleanArtist(artist: String): String = artist.replace(ARTIST_CHANNEL, "").trim().ifEmpty { artist.trim() }
}
