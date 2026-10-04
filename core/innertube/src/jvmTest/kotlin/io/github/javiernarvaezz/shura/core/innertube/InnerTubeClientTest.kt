package io.github.javiernarvaezz.shura.core.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InnerTubeClientTest {
    private val config = InnerTubeConfig(clientVersion = "1.20260707.12.00", hl = "es-419", gl = "CO")
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): InnerTubeClient {
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        return InnerTubeClient(HttpClient(engine), config)
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    @Test
    fun searchSongsSendsWebRemixRequestWithSongsFilter() =
        runTest {
            client { json(Fixtures.read("search/search_songs_bad_bunny.json")) }.searchSongs("bad bunny")

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", request.url.toString())
            assertEquals("67", request.headers["X-YouTube-Client-Name"])
            assertEquals("1.20260707.12.00", request.headers["X-YouTube-Client-Version"])
            assertEquals("https://music.youtube.com", request.headers[HttpHeaders.Origin])
            assertFalse(request.headers.contains(HttpHeaders.Cookie), "anonymous requests must not send cookies")
            assertFalse(request.headers.contains(HttpHeaders.Authorization))

            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            val clientContext = body["context"]!!.jsonObject["client"]!!.jsonObject
            assertEquals("WEB_REMIX", clientContext["clientName"]!!.jsonPrimitive.content)
            assertEquals("1.20260707.12.00", clientContext["clientVersion"]!!.jsonPrimitive.content)
            assertEquals("es-419", clientContext["hl"]!!.jsonPrimitive.content)
            assertEquals("CO", clientContext["gl"]!!.jsonPrimitive.content)
            assertFalse(clientContext.containsKey("visitorData"))
            assertEquals("bad bunny", body["query"]!!.jsonPrimitive.content)
            assertEquals(SearchParams.SONGS, body["params"]!!.jsonPrimitive.content)
        }

    @Test
    fun searchSongsReturnsParsedSongs() =
        runTest {
            val songs = client { json(Fixtures.read("search/search_songs_bad_bunny.json")) }.searchSongs("bad bunny")

            assertEquals(20, songs.size)
            assertEquals("Amorfoda", songs.first().title)
        }

    @Test
    fun noResultsReturnsEmptyList() =
        runTest {
            val songs = client { json(Fixtures.read("search/search_songs_no_results.json")) }.searchSongs("qzxv")

            assertTrue(songs.isEmpty())
        }

    @Test
    fun trimsQueryAndRejectsBlankWithoutRequest() =
        runTest {
            val client = client { json(Fixtures.read("search/search_songs_no_results.json")) }

            assertFailsWith<IllegalArgumentException> { client.searchSongs("   ") }
            assertTrue(requests.isEmpty())

            client.searchSongs("  juanes  ")
            val body =
                Json
                    .parseToJsonElement(
                        requests
                            .single()
                            .body
                            .toByteArray()
                            .decodeToString(),
                    ).jsonObject
            assertEquals("juanes", body["query"]!!.jsonPrimitive.content)
        }

    @Test
    fun httpErrorBecomesTypedFailure() =
        runTest {
            val error =
                assertFailsWith<InnerTubeException.Http> {
                    client { json("{}", HttpStatusCode.TooManyRequests) }.searchSongs("juanes")
                }

            assertEquals(429, error.status)
        }

    @Test
    fun malformedBodyBecomesParseFailure() =
        runTest {
            assertFailsWith<InnerTubeException.Parse> {
                client { json("<html>not json</html>") }.searchSongs("juanes")
            }
        }

    @Test
    fun transportErrorBecomesNetworkFailure() =
        runTest {
            assertFailsWith<InnerTubeException.Network> {
                client { throw IOException("connection reset") }.searchSongs("juanes")
            }
        }

    @Test
    fun failureMessagesDoNotLeakQueryOrUrl() =
        runTest {
            val error =
                assertFailsWith<InnerTubeException> {
                    client { json("{}", HttpStatusCode.Forbidden) }.searchSongs("private query")
                }

            val message = error.message.orEmpty()
            assertFalse(message.contains("private query"))
            assertFalse(message.contains("youtube.com"))
        }
}
