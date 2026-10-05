package io.github.javiernarvaezz.shura.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShuraNetworkTest {
    /** Answers every request without touching the network and records which hosts reached it. */
    private class Terminal : Interceptor {
        val hosts = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            hosts += chain.request().url.host
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }
    }

    private val terminal = Terminal()
    private val network = ShuraNetwork(emptyList(), terminal)

    private fun call(url: String) =
        network.okHttp
            .newCall(Request.Builder().url(url).build())
            .execute()
            .use { it.code }

    @Test
    fun okHttpBlocksDisallowedHostBeforeAnyConnection() {
        val error = assertFailsWith<BlockedHostIOException> { call("https://raw.githubusercontent.com/x") }

        assertEquals("raw.githubusercontent.com", error.host)
        assertTrue(terminal.hosts.isEmpty())
    }

    @Test
    fun okHttpLetsAllowedHostsThrough() {
        assertEquals(200, call("https://rr1---sn-test.googlevideo.com/videoplayback"))
        assertEquals(listOf("rr1---sn-test.googlevideo.com"), terminal.hosts)
    }

    @Test
    fun ktorClientRunsOnTheSameOkHttpClient() =
        runTest {
            network.ktor.get("https://music.youtube.com/youtubei/v1/search")

            assertEquals(listOf("music.youtube.com"), terminal.hosts)
        }

    @Test
    fun clientsBuiltDirectlyOnTheEngineAreStillGuarded() =
        runTest {
            // InnerTubeX builds HttpClient(engine) for the watch page and player script, bypassing client plugins.
            val engineLevel = HttpClient(network.ktor.engine)

            val response: HttpResponse = engineLevel.get("https://www.youtube.com/watch")
            assertEquals(200, response.status.value)
            assertEquals(listOf("www.youtube.com"), terminal.hosts)

            val error = assertFailsWith<BlockedHostIOException> { engineLevel.get("https://cdn.jsdelivr.net/gh/x") }
            assertEquals("cdn.jsdelivr.net", error.host)
            assertEquals(listOf("www.youtube.com"), terminal.hosts)
        }

    @Test
    fun guardAlsoRunsOnEveryNetworkHopBeforeExtraInterceptors() {
        val counter = Interceptor { it.proceed(it.request()) }
        val withExtra = ShuraNetwork(listOf(counter))

        assertIs<HostGuardInterceptor>(withExtra.okHttp.interceptors.first())
        assertIs<HostGuardInterceptor>(withExtra.okHttp.networkInterceptors.first())
        assertEquals(counter, withExtra.okHttp.networkInterceptors.last())
    }
}
