package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.VideoId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InnerTubeAccountClientTest {
    private val config = InnerTubeConfig(clientVersion = "1.20260707.12.00", hl = "es-419", gl = "CO")
    private val requests = mutableListOf<HttpRequestData>()
    private val credentials = AccountCredentials("SID=s; SAPISID=p", "SAPISIDHASH 1_abc", onBehalfOfUser = null)

    private fun client(
        status: HttpStatusCode = HttpStatusCode.OK,
        setCookies: List<String> = emptyList(),
        body: String,
    ): InnerTubeAccountClient {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    body,
                    status,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to setCookies,
                    ),
                )
            }
        return InnerTubeAccountClient(HttpClient(engine), config)
    }

    @Test
    fun theAccountMenuGivesTheAccountName() =
        runTest {
            val menu = client(body = Fixtures.read("account/account_menu_synthetic.json")).accountMenu(credentials)
            assertTrue(menu.signedIn)
            assertEquals("Test Account", menu.accountName)
        }

    @Test
    fun withoutAnAccountHeaderTheSessionIsNotSignedIn() =
        runTest {
            val menu =
                client(
                    body = Fixtures.read("account/account_menu_signed_out_synthetic.json"),
                ).accountMenu(credentials)
            assertFalse(menu.signedIn)
            assertNull(menu.accountName)
        }

    @Test
    fun accountRequestsCarryTheSessionHeaders() =
        runTest {
            client(body = "{}").accountMenu(credentials)
            val request = requests.single()
            assertEquals("/youtubei/v1/account/account_menu", request.url.encodedPath)
            assertEquals("SID=s; SAPISID=p", request.headers[HttpHeaders.Cookie])
            assertEquals("SAPISIDHASH 1_abc", request.headers[HttpHeaders.Authorization])
            assertEquals("0", request.headers["X-Goog-AuthUser"])
            assertEquals("https://music.youtube.com", request.headers["X-Origin"])
            val context =
                Json
                    .parseToJsonElement(
                        (request.body as TextContent).text,
                    ).jsonObject
                    .getValue("context")
                    .jsonObject
            assertFalse("user" in context)
        }

    @Test
    fun aBrandChannelIsActedOnBehalfOf() =
        runTest {
            client(
                body = "{}",
            ).accountMenu(AccountCredentials("SID=s", "SAPISIDHASH 1_abc", onBehalfOfUser = "UCbrand"))
            val context =
                Json
                    .parseToJsonElement(
                        (requests.single().body as TextContent).text,
                    ).jsonObject
                    .getValue("context")
                    .jsonObject
            assertEquals(
                "UCbrand",
                context
                    .getValue("user")
                    .jsonObject
                    .getValue("onBehalfOfUser")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun rotatedCookiesAreHandedBack() =
        runTest {
            val menu = client(setCookies = listOf("SIDCC=new; path=/"), body = "{}").accountMenu(credentials)
            assertEquals(listOf("SIDCC=new; path=/"), menu.setCookies)
        }

    @Test
    fun aRejectedSessionIsTypedAndItsMessageHoldsNoSecret() =
        runTest {
            val error =
                assertFailsWith<InnerTubeException.Http> {
                    client(
                        HttpStatusCode.Unauthorized,
                        body = "",
                    ).accountMenu(credentials)
                }
            assertEquals(401, error.status)
            listOf("SID=", "SAPISID", "SAPISIDHASH").forEach { assertFalse(it in error.message.orEmpty(), it) }
        }

    @Test
    fun credentialsNeverPrintTheirValues() {
        val text = credentials.toString()
        listOf("SID=s", "SAPISIDHASH 1_abc").forEach { assertFalse(it in text, it) }
    }

    @Test
    fun theAnonymousClientSendsNoSessionHeaders() =
        runTest {
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        """{"contents":{}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            runCatching { InnerTubeClient(HttpClient(engine), config).raw(InnerTubeRequest.Browse("MPREb_x")) }
            val request = requests.single()
            listOf(HttpHeaders.Cookie, HttpHeaders.Authorization, "X-Goog-AuthUser").forEach {
                assertNull(request.headers[it], it)
            }
        }

    @Test
    fun likingAndUnlikingTargetTheSongAsTheUser() =
        runTest {
            val client = client(body = "{}")
            client.setLiked(credentials, VideoId("abcdefghijk"), liked = true)
            client.setLiked(credentials, VideoId("abcdefghijk"), liked = false)
            assertEquals(
                listOf("/youtubei/v1/like/like", "/youtubei/v1/like/removelike"),
                requests.map { it.url.encodedPath },
            )
            requests.forEach { request ->
                assertEquals("SAPISIDHASH 1_abc", request.headers[HttpHeaders.Authorization])
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(
                    "abcdefghijk",
                    body
                        .getValue("target")
                        .jsonObject
                        .getValue("videoId")
                        .jsonPrimitive.content,
                )
            }
        }

    @Test
    fun libraryPagesAreBrowsedAsTheUser() =
        runTest {
            val response =
                client(setCookies = listOf("SIDCC=n; path=/"), body = """{"contents":{}}""")
                    .raw(credentials, InnerTubeRequest.Browse("FEmusic_liked_playlists"))
            val request = requests.single()
            assertEquals("/youtubei/v1/browse", request.url.encodedPath)
            assertEquals("SID=s; SAPISID=p", request.headers[HttpHeaders.Cookie])
            assertEquals(listOf("SIDCC=n; path=/"), response.setCookies)
        }
}
