package io.github.javiernarvaezz.shura.core.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Request shape and failure mapping; matching on real LRCLIB responses is in [LrcLibClientTest]. */
class LrcLibClientErrorsTest {
    private val requests = mutableListOf<HttpRequestData>()

    private val query = LyricsQuery("Secret Title", "Secret Artist", "Secret Album", 201.6.seconds)

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): LrcLibClient {
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        return LrcLibClient(HttpClient(engine))
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun assertNoSongData(error: Throwable) {
        val message = error.message.orEmpty()
        listOf("Secret", "lrclib", "http://", "https://").forEach { assertFalse(it in message, message) }
        // The underlying error is dropped: it could carry the request URL.
        assertNull(error.cause)
    }

    @Test
    fun exactMatchSendsTheFullSignatureThenSearchesByTitleAndArtist() =
        runTest {
            val client =
                client { request ->
                    if (request.url.encodedPath == "/api/get") json("{}", HttpStatusCode.NotFound) else json("[]")
                }

            assertFailsWith<LyricsException.NotFound> { client.lyrics(query) }

            val (get, search) = requests
            assertEquals(HttpMethod.Get, get.method)
            assertEquals("lrclib.net", get.url.host)
            assertEquals("/api/get", get.url.encodedPath)
            assertEquals("Secret Title", get.url.parameters["track_name"])
            assertEquals("Secret Artist", get.url.parameters["artist_name"])
            assertEquals("Secret Album", get.url.parameters["album_name"])
            assertEquals("202", get.url.parameters["duration"])
            assertEquals(LrcLibClient.DEFAULT_USER_AGENT, get.headers[HttpHeaders.UserAgent])

            assertEquals("/api/search", search.url.encodedPath)
            assertEquals("Secret Title", search.url.parameters["track_name"])
            assertEquals("Secret Artist", search.url.parameters["artist_name"])
            assertEquals(setOf("track_name", "artist_name"), search.url.parameters.names())
            assertEquals(LrcLibClient.DEFAULT_USER_AGENT, search.headers[HttpHeaders.UserAgent])
        }

    @Test
    fun withoutAlbumOnlySearches() =
        runTest {
            val client = client { json("[]") }
            assertFailsWith<LyricsException.NotFound> {
                client.lyrics(LyricsQuery("Secret Title", "Secret Artist", null, 200.seconds))
            }
            assertEquals(listOf("/api/search"), requests.map { it.url.encodedPath })
        }

    @Test
    fun unexpectedStatusIsTypedWithoutSongData() =
        runTest {
            val error =
                assertFailsWith<LyricsException.Http> {
                    client { json("", HttpStatusCode.InternalServerError) }.lyrics(query)
                }
            assertEquals(500, error.status)
            assertEquals(1, requests.size)
            assertNoSongData(error)
        }

    @Test
    fun rateLimitIsTypedAsHttp() =
        runTest {
            val client =
                client { request ->
                    if (request.url.encodedPath == "/api/get") {
                        json("{}", HttpStatusCode.NotFound)
                    } else {
                        json("", HttpStatusCode.TooManyRequests)
                    }
                }
            assertEquals(429, assertFailsWith<LyricsException.Http> { client.lyrics(query) }.status)
        }

    @Test
    fun transportFailureIsTypedAsNetworkWithoutSongData() =
        runTest {
            val error =
                assertFailsWith<LyricsException.Network> {
                    client { throw IOException("connect to https://lrclib.net/api/get?track_name=Secret failed") }
                        .lyrics(query)
                }
            assertNoSongData(error)
            assertEquals("IOException", error.causeType)
            assertFalse("Secret" in error.stackTraceToString())
        }

    @Test
    fun invalidJsonIsTypedAsFormat() =
        runTest {
            val error = assertFailsWith<LyricsException.Format> { client { json("<html>") }.lyrics(query) }
            assertNoSongData(error)
        }

    @Test
    fun truncatedJsonKeepsOnlyTheParserErrorType() =
        runTest {
            val error =
                assertFailsWith<LyricsException.Format> { client { json("""{"trackName":"Secret""") }.lyrics(query) }
            assertNoSongData(error)
            val causeType = assertNotNull(error.causeType)
            assertTrue(causeType.endsWith("Exception"), causeType)
        }

    @Test
    fun wrongJsonShapeIsTypedAsFormat() =
        runTest {
            assertFailsWith<LyricsException.Format> { client { json("[]") }.lyrics(query) }
            requests.clear()
            val searchOnly = LyricsQuery("Secret Title", "Secret Artist", null, 200.seconds)
            assertFailsWith<LyricsException.Format> { client { json("{}") }.lyrics(searchOnly) }
        }
}
