package io.github.javiernarvaezz.shura.core.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Lyrics from LRCLIB (`lrclib.net`), anonymous and read-only.
 *
 * The caller owns [httpClient] and its engine. LRCLIB asks clients to identify themselves with [userAgent].
 */
class LrcLibClient(
    private val httpClient: HttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    /**
     * The best lyrics for [query]: synced lyrics when any match, otherwise plain ones (see [choose]).
     *
     * With an album, LRCLIB's exact match (title, artist, album and duration) is tried first; synced lyrics that fit
     * the song, or an instrumental, need no search, unless the song is explicit: then the search's versions are
     * compared too, so an uncensored one can win. Search results count when their duration is within
     * [TEXT_TOLERANCE] of the song's, and keep their timing only within [DURATION_TOLERANCE] and when they fit
     * ([Candidate.of]). When the title and artist as they are match nothing, they are searched once more without
     * upload tags ([LyricsTitle]).
     *
     * @throws LyricsException.NotFound when nothing matches.
     */
    suspend fun lyrics(query: LyricsQuery): Lyrics {
        val exact = query.album?.let { album -> exactMatch(query, album) }
        val settled = exact?.lyrics is Lyrics.Instrumental || (exact?.lyrics is Lyrics.Synced && !query.explicit)
        if (exact != null && settled) return exact.lyrics
        // Search only looks for better versions than the exact match; if it fails, the exact match still counts.
        val searched = if (exact == null) searchWithCleanedTitle(query) else searchOrEmpty(query)
        return choose(listOfNotNull(exact) + searched, query.explicit) ?: throw LyricsException.NotFound()
    }

    /**
     * `null` when LRCLIB has no record with this exact signature, the record has no lyrics, or the server is busy
     * (LRCLIB's exact match answers 503 or 429 at times while search still works).
     */
    private suspend fun exactMatch(
        query: LyricsQuery,
        album: String,
    ): Candidate? {
        val response =
            get("get") {
                parameter("track_name", query.title)
                parameter("artist_name", query.artist)
                parameter("album_name", album)
                parameter("duration", query.duration.inWholeSecondsRounded())
            }
        return if (response.status == HttpStatusCode.NotFound || response.status in BUSY) {
            null
        } else {
            Candidate.of(LrcLibRecord.from(body(response)), query.duration, exact = true)
        }
    }

    @Suppress("SwallowedException") // The caller already has lyrics to show instead.
    private suspend fun searchOrEmpty(query: LyricsQuery): List<Candidate> =
        try {
            searchWithCleanedTitle(query)
        } catch (e: LyricsException) {
            emptyList()
        }

    /** Search as the song is named; if nothing matches, once more without upload tags, when that changes anything. */
    private suspend fun searchWithCleanedTitle(query: LyricsQuery): List<Candidate> {
        searchMatches(query.title, query.artist, query.duration).ifEmpty { null }?.let { return it }
        val title = LyricsTitle.clean(query.title)
        val artist = LyricsTitle.cleanArtist(query.artist)
        val changed = title != query.title || artist != query.artist
        return if (changed) searchMatches(title, artist, query.duration) else emptyList()
    }

    private suspend fun searchMatches(
        title: String,
        artist: String,
        duration: Duration,
    ): List<Candidate> {
        val response =
            get("search") {
                parameter("track_name", title)
                parameter("artist_name", artist)
            }
        return LrcLibRecord.listFrom(body(response)).mapNotNull { Candidate.of(it, duration) }
    }

    private suspend fun get(
        endpoint: String,
        parameters: HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        transport {
            httpClient.get("$API_BASE/$endpoint") {
                header(HttpHeaders.UserAgent, userAgent)
                parameters()
            }
        }

    private suspend fun body(response: HttpResponse): JsonElement {
        if (!response.status.isSuccess()) throw LyricsException.Http(response.status.value)
        val text = transport { response.bodyAsText() }
        return try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw LyricsException.Format(e)
        }
    }

    /** Wraps engine-specific transport errors as [LyricsException.Network]; cancellation passes through. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> transport(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LyricsException.Network(e)
        }

    private fun Duration.inWholeSecondsRounded(): Long = (inWholeMilliseconds / MILLIS_PER_SECOND).roundToLong()

    companion object {
        /** LRCLIB's own tolerance when it matches a duration. */
        val DURATION_TOLERANCE: Duration = 2.seconds

        /** How far off a duration may be for a record's words (without timing) to count: edits and video cuts. */
        val TEXT_TOLERANCE: Duration = 6.seconds

        const val DEFAULT_USER_AGENT = "Shura (https://github.com/Javier-NarvaezZ/Shura)"

        /** The User-Agent LRCLIB asks for: the app's name, its version and a link to its homepage. */
        fun userAgent(
            app: String,
            version: String,
            homepage: String,
        ): String = "$app v$version ($homepage)"

        private const val API_BASE = "https://lrclib.net/api"

        /** Answers that mean "try again later": the exact match then falls back to search. */
        private val BUSY = setOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.TooManyRequests)
        private const val MILLIS_PER_SECOND = 1000.0
    }
}
