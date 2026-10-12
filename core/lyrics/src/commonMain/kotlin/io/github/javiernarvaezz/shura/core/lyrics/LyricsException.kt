package io.github.javiernarvaezz.shura.core.lyrics

/**
 * Typed lyrics failures. Messages never include the song, URLs, headers or response bodies, and the underlying
 * exception is not kept (transport errors can carry the request URL, which holds the title and artist): only its
 * type, in [causeType].
 */
sealed class LyricsException(
    message: String,
    /** The simple class name of the error behind this one, if any. */
    val causeType: String? = null,
) : Exception(message) {
    /** No lyrics, synced or plain, and no instrumental record match the song. */
    class NotFound : LyricsException("No lyrics found")

    /** The server answered with an unexpected HTTP [status]. */
    class Http(
        val status: Int,
    ) : LyricsException("Lyrics request failed with HTTP $status")

    class Network(
        cause: Throwable,
    ) : LyricsException("Lyrics request failed: ${cause::class.simpleName}", cause::class.simpleName)

    /** The response is not the JSON LRCLIB documents. */
    class Format(
        cause: Throwable? = null,
    ) : LyricsException("Lyrics response has an unexpected format", cause?.let { it::class.simpleName })
}
