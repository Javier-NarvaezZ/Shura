package io.github.javiernarvaezz.shura.core.innertube

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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * What signs an account request: the session's `Cookie` header, its `SAPISIDHASH` authorization and, for a brand
 * channel, who to act on behalf of. Secrets: [toString] hides them, and they are never logged.
 */
class AccountCredentials(
    val cookie: String,
    val authorization: String,
    val onBehalfOfUser: String?,
) {
    override fun toString(): String = "AccountCredentials(redacted)"
}

/** The account menu: whether the session is signed in and, if so, the account's display name. */
class AccountMenu(
    val signedIn: Boolean,
    val accountName: String?,
    /** `Set-Cookie` headers of the response: Google rotates some session cookies as they are used. */
    val setCookies: List<String>,
)

/**
 * YouTube Music requests made as the signed-in user. Separate from [InnerTubeClient], which stays anonymous:
 * search, catalog pages and streams never carry the session. The caller owns [httpClient].
 */
class InnerTubeAccountClient(
    private val httpClient: HttpClient,
    private val config: InnerTubeConfig = InnerTubeConfig(),
) {
    /** The account menu; tells whether the session is still signed in, and the account's name. */
    suspend fun accountMenu(credentials: AccountCredentials): AccountMenu {
        val (json, setCookies) = post("account/account_menu", credentials) {}
        // Null without an active account; empty when signed in but the name is missing.
        val name = AccountMenuParser.accountName(json)
        return AccountMenu(signedIn = name != null, accountName = name?.takeIf { it.isNotBlank() }, setCookies)
    }

    private suspend fun post(
        endpoint: String,
        credentials: AccountCredentials,
        fields: JsonObjectBuilder.() -> Unit,
    ): Pair<JsonElement, List<String>> {
        val body =
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", CLIENT_NAME)
                        put("clientVersion", config.clientVersion)
                        put("hl", config.hl)
                        put("gl", config.gl)
                    }
                    credentials.onBehalfOfUser?.let { putJsonObject("user") { put("onBehalfOfUser", it) } }
                }
                fields()
            }
        val response: HttpResponse =
            transport {
                httpClient.post("$API_BASE/$endpoint?prettyPrint=false") {
                    header(HttpHeaders.UserAgent, config.userAgent)
                    header("X-Goog-Api-Format-Version", "1")
                    header("X-YouTube-Client-Name", CLIENT_ID)
                    header("X-YouTube-Client-Version", config.clientVersion)
                    header(HttpHeaders.Origin, ORIGIN)
                    header("X-Origin", ORIGIN)
                    header(HttpHeaders.Referrer, "$ORIGIN/")
                    header("X-Goog-AuthUser", "0")
                    header(HttpHeaders.Cookie, credentials.cookie)
                    header(HttpHeaders.Authorization, credentials.authorization)
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                }
            }
        if (!response.status.isSuccess()) throw InnerTubeException.Http(response.status.value)
        val text = transport { response.bodyAsText() }
        val json =
            try {
                Json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                throw InnerTubeException.Parse(e)
            }
        return json to response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> transport(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InnerTubeException.Network(e)
        }

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val API_BASE = "$ORIGIN/youtubei/v1"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"
    }
}
