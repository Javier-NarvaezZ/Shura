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
     * The best lyrics for [query]: synced lyrics when any match, otherwise plain ones.
     *
     * With an album, LRCLIB's exact match (title, artist, album and duration) is tried first. Search results count
     * only when their duration is within [DURATION_TOLERANCE] of the song's.
     *
     * @throws LyricsException.NotFound when nothing matches.
     */
    suspend fun lyrics(query: LyricsQuery): Lyrics {
        val exact = query.album?.let { album -> exactMatch(query, album) }
        if (exact != null && exact !is Lyrics.Plain) return exact
        val searched = searchMatch(query)
        return when {
            searched is Lyrics.Synced -> searched
            exact != null -> exact
            searched != null -> searched
            else -> throw LyricsException.NotFound()
        }
    }

    /**
     * `null` when LRCLIB has no record with this exact signature, the record has no lyrics, or the server is busy
     * (LRCLIB's exact match answers 503 or 429 at times while search still works).
     */
    private suspend fun exactMatch(
        query: LyricsQuery,
        album: String,
    ): Lyrics? {
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
            LrcLibRecord.from(body(response)).lyrics()
        }
    }

    private suspend fun searchMatch(query: LyricsQuery): Lyrics? {
        val response =
            get("search") {
                parameter("track_name", query.title)
                parameter("artist_name", query.artist)
            }
        return LrcLibRecord
            .listFrom(body(response))
            .filter { (it.duration - query.duration).absoluteValue <= DURATION_TOLERANCE }
            .mapNotNull { record -> record.lyrics()?.let { it to (record.duration - query.duration).absoluteValue } }
            .minWithOrNull(compareBy<Pair<Lyrics, Duration>> { rank(it.first) }.thenBy { it.second })
            ?.first
    }

    private fun rank(lyrics: Lyrics): Int =
        when (lyrics) {
            is Lyrics.Synced -> 0
            is Lyrics.Plain -> 1
            Lyrics.Instrumental -> 2
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
