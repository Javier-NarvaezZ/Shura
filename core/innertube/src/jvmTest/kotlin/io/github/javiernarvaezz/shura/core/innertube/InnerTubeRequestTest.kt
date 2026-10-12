package io.github.javiernarvaezz.shura.core.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InnerTubeRequestTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val client =
        InnerTubeClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    respond("{}", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                },
            ),
            InnerTubeConfig(clientVersion = "1.20260707.12.00", hl = "es", gl = "CO"),
        )

    private suspend fun body(): JsonObject =
        Json
            .parseToJsonElement(
                requests
                    .last()
                    .body
                    .toByteArray()
                    .decodeToString(),
            ).jsonObject

    @Test
    fun browseSendsTheIdAndOptionalParams() =
        runTest {
            client.raw(InnerTubeRequest.Browse("MPREb_xDw1x6Hl5Jo"))
            assertEquals(
                "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",
                requests.last().url.toString(),
            )
            assertEquals("MPREb_xDw1x6Hl5Jo", body()["browseId"]!!.jsonPrimitive.content)
            assertFalse(body().containsKey("params"))

            client.raw(InnerTubeRequest.Browse("VLOLAK5uy_nhiZ58OtcuzRST9aW5qI1xAxW9XwtG0RE", params = "ggMCCAI%3D"))
            assertEquals("ggMCCAI%3D", body()["params"]!!.jsonPrimitive.content)
            assertEquals(
                "es",
                body()["context"]!!
                    .jsonObject["client"]!!
                    .jsonObject["hl"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun continuationsGoToBrowseWithTheToken() =
        runTest {
            client.raw(InnerTubeRequest.Continuation("4qmFsgKVARIr-_%3D"))

            assertTrue(
                requests
                    .last()
                    .url.encodedPath
                    .endsWith("/browse"),
            )
            assertEquals("4qmFsgKVARIr-_%3D", body()["continuation"]!!.jsonPrimitive.content)
            assertFalse(body().containsKey("browseId"))
        }

    @Test
    fun eachSearchFilterSendsItsParams() =
        runTest {
            for (filter in SearchFilter.entries) {
                client.raw(InnerTubeRequest.Search("juanes", filter))
                assertTrue(
                    requests
                        .last()
                        .url.encodedPath
                        .endsWith("/search"),
                )
                assertEquals(SearchParams.of(filter), body()["params"]!!.jsonPrimitive.content)
            }
            assertEquals(
                SearchFilter.entries.size,
                SearchFilter.entries
                    .map(SearchParams::of)
                    .toSet()
                    .size,
            )
        }

    @Test
    fun malformedIdsAreRejectedWithoutARequest() =
        runTest {
            for (bad in listOf("", "MPREb x", "../browse", "UC\"}", "a".repeat(201))) {
                assertFailsWith<IllegalArgumentException>(bad) { client.raw(InnerTubeRequest.Browse(bad)) }
            }
            assertFailsWith<IllegalArgumentException> { client.raw(InnerTubeRequest.Continuation("tok en")) }
            assertFailsWith<IllegalArgumentException> { client.raw(InnerTubeRequest.Search("  ", SearchFilter.Songs)) }
            assertTrue(requests.isEmpty())
        }
}
