package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Anonymous YouTube Music catalog client. It never sends cookies, authorization or visitor data.
 *
 * The caller owns [httpClient] and its engine.
 */
class InnerTubeClient(
    private val httpClient: HttpClient,
    private val config: InnerTubeConfig = InnerTubeConfig(),
) {
    /** Searches album-audio songs. [query] is trimmed and must not be blank. */
    suspend fun searchSongs(query: String): List<Song> {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Query must not be blank" }
        val root = post("search", searchBody(trimmed, SearchParams.SONGS))
        return SearchParser.parseSongs(root)
    }

    private fun searchBody(
        query: String,
        params: String,
    ) = buildJsonObject {
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", CLIENT_NAME)
                put("clientVersion", config.clientVersion)
                put("hl", config.hl)
                put("gl", config.gl)
            }
        }
        put("query", query)
        put("params", params)
    }

    private suspend fun post(
        endpoint: String,
        body: JsonElement,
    ): JsonElement {
        val response: HttpResponse =
            transport {
                httpClient.post("$API_BASE/$endpoint?prettyPrint=false") {
                    header(HttpHeaders.UserAgent, config.userAgent)
                    header("X-Goog-Api-Format-Version", "1")
                    header("X-YouTube-Client-Name", CLIENT_ID)
                    header("X-YouTube-Client-Version", config.clientVersion)
                    header(HttpHeaders.Origin, ORIGIN)
                    header(HttpHeaders.Referrer, "$ORIGIN/")
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                }
            }
        if (!response.status.isSuccess()) throw InnerTubeException.Http(response.status.value)
        return parseJson(transport { response.bodyAsText() })
    }

    /** Wraps engine-specific transport errors as [InnerTubeException.Network]; cancellation passes through. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> transport(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InnerTubeException.Network(e)
        }

    private fun parseJson(text: String): JsonElement =
        try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw InnerTubeException.Parse(e)
        }

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val API_BASE = "$ORIGIN/youtubei/v1"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"
    }
}
