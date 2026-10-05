package io.github.javiernarvaezz.shura.core.stream

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SerializedRequestBodiesTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val client =
        HttpClient(
            MockEngine { request ->
                requests += request
                respondOk()
            },
        ) { install(serializedRequestBodies(Json { ignoreUnknownKeys = true })) }

    private suspend fun sent() = requests.single().let { it.body.contentType to it.body.toByteArray().decodeToString() }

    @Test
    fun serializesSerializableBodiesAsJson() =
        runTest {
            val body: Map<String, Int> = mapOf("contentCheckOk" to 1)
            client.post("https://www.youtube.com/youtubei/v1/player") { setBody(body) }

            val (contentType, text) = sent()
            assertEquals(ContentType.Application.Json, contentType?.withoutParameters())
            assertEquals("""{"contentCheckOk":1}""", text)
        }

    @Test
    fun serializesJsonElements() =
        runTest {
            val body: JsonObject = buildJsonObject { put("videoId", "7fwUH0oRmkQ") }
            client.post("https://www.youtube.com/youtubei/v1/player") { setBody(body) }

            assertEquals("""{"videoId":"7fwUH0oRmkQ"}""", sent().second)
        }

    @Test
    fun leavesExplicitContentUntouched() =
        runTest {
            client.post("https://www.youtube.com/x") { setBody(TextContent("raw", ContentType.Text.Plain)) }

            val (contentType, text) = sent()
            assertEquals(ContentType.Text.Plain, contentType?.withoutParameters())
            assertEquals("raw", text)
        }

    @Test
    fun leavesStringBodiesToKtorDefaults() =
        runTest {
            client.post("https://www.youtube.com/x") { setBody("plain") }

            assertEquals("plain", sent().second)
        }

    @Test
    fun withoutThePluginSerializableBodiesCannotBeSent() =
        runTest {
            val bare = HttpClient(MockEngine { respondOk() })
            val body: Map<String, Int> = mapOf("a" to 1)

            assertFailsWith<IllegalStateException> { bare.post("https://www.youtube.com/x") { setBody(body) } }
            assertNull(requests.firstOrNull())
        }
}
