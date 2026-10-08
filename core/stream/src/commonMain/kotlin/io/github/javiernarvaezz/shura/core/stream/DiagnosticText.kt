package io.github.javiernarvaezz.shura.core.stream

/**
 * Makes diagnostic text from libraries (attempt outcomes, exception types) safe to store: URLs become `<url>`,
 * characters outside a small safe set become `?`, and loose 11-character tokens that could be a video id become
 * `<id>`, except known labels (profile names, stages and outcome words) that happen to have that length.
 */
object DiagnosticText {
    const val MAX_LENGTH = 120

    private const val ID_MARK = "<id>"
    private const val URL_MARK = "<url>"

    // A URL, or a mark left by an earlier pass (sanitizing is idempotent).
    private val urlOrMark = Regex("""$ID_MARK|$URL_MARK|[A-Za-z][A-Za-z0-9+.-]*://\S*""")
    private val unsafe = Regex("""[^A-Za-z0-9 :._+-]""")
    private val looseIdLike = Regex("""(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{11}(?![A-Za-z0-9_-])""")

    /** Known 11-character labels; matched case-insensitively. Anything else of that shape is masked. */
    private val knownLabels =
        setOf(
            // InnerTubeX client profile names.
            "web_creator",
            // Stage and outcome words.
            "playability",
            "unavailable",
            "unsupported",
            "ioexception",
        )

    fun sanitize(
        text: String,
        maxLength: Int = MAX_LENGTH,
    ): String {
        var rest = 0
        val sanitized =
            buildString {
                for (match in urlOrMark.findAll(text)) {
                    append(clean(text.substring(rest, match.range.first)))
                    append(if (match.value == ID_MARK) ID_MARK else URL_MARK)
                    rest = match.range.last + 1
                }
                append(clean(text.substring(rest)))
            }
        return sanitized.take(maxLength)
    }

    private fun clean(part: String): String =
        part
            .replace(unsafe, "?")
            .replace(looseIdLike) { if (it.value.lowercase() in knownLabels) it.value else ID_MARK }
}
