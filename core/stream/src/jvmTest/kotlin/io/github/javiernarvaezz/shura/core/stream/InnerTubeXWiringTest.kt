package io.github.javiernarvaezz.shura.core.stream

import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.network.HostPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the real InnerTubeX wiring offline. InnerTubeX builds some clients directly on the caller's
 * engine, so a [MockEngine] sees every request the library makes, not only those of the configured client.
 */
class InnerTubeXWiringTest {
    @Test
    fun offlineFailureIsTypedAndOnlyAllowedHostsAreContacted() =
        runBlocking {
            val hosts = Collections.synchronizedList(mutableListOf<String>())
            val paths = Collections.synchronizedList(mutableListOf<String>())
            val playerContentTypes = Collections.synchronizedList(mutableListOf<String?>())
            val engine =
                MockEngine { request ->
                    hosts += request.url.host
                    paths += request.url.encodedPath
                    if (request.url.encodedPath.endsWith("/player")) {
                        playerContentTypes +=
                            request.body.contentType
                                ?.withoutParameters()
                                ?.toString()
                    }
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
            val resolver = InnerTubeXStreamResolver(HttpClient(engine))

            val error =
                assertFailsWith<StreamResolutionException> {
                    withTimeout(60_000) { resolver.resolve(VideoId("7fwUH0oRmkQ")) }
                }

            // The player request must actually be sent: a client-side failure (e.g. a body InnerTubeX cannot
            // serialize) never reaches the engine and shows up as "request:<Exception>" attempts.
            assertTrue(paths.any { it.endsWith("/youtubei/v1/player") }, "no player request reached the engine: $paths")
            // InnerTubeHttpException is the mocked 503 (server side); anything else failed before reaching the server.
            assertTrue(playerContentTypes.all { it == "application/json" }, "player bodies: $playerContentTypes")
            val clientSide =
                error.attempts.filter {
                    it.outcome.startsWith("request:") && it.outcome != "request:InnerTubeHttpException"
                }
            assertTrue(clientSide.isEmpty(), "client-side request failures: $clientSide")

            assertTrue(hosts.isNotEmpty(), "expected the library to attempt requests")
            val disallowed = hosts.filterNot(HostPolicy::isAllowed).distinct()
            assertTrue(disallowed.isEmpty(), "requests to disallowed hosts: $disallowed")
        }

    @Test
    fun withAMinterInnerTubeXNoLongerSkipsTokenClients() =
        runBlocking {
            val hosts = Collections.synchronizedList(mutableListOf<String>())
            val engine =
                MockEngine { request ->
                    hosts += request.url.host
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
            val minter =
                object : PoTokenMinter {
                    override suspend fun mint(
                        videoId: VideoId,
                        visitorData: String,
                    ) = PoTokens("player", "streaming", visitorData)

                    override suspend fun invalidate() = Unit

                    override fun close() = Unit
                }
            val resolver = InnerTubeXStreamResolver(HttpClient(engine), minter)

            val error =
                assertFailsWith<StreamResolutionException> {
                    withTimeout(60_000) { resolver.resolve(VideoId("7fwUH0oRmkQ")) }
                }

            // Without a minter WEB_REMIX is skipped with "GVS PO-token provider unavailable"; with one it is eligible.
            val webRemix = error.attempts.filter { it.profile == "WEB_REMIX" }
            assertTrue(webRemix.isNotEmpty(), "WEB_REMIX was not considered: ${error.attempts}")
            assertTrue(
                webRemix.none { "PO-token provider unavailable" in it.outcome },
                "minter not recognized: $webRemix",
            )
            val disallowed = hosts.filterNot(HostPolicy::isAllowed).distinct()
            assertTrue(disallowed.isEmpty(), "requests to disallowed hosts: $disallowed")
        }

    @Test
    fun withAPreprocessedPlayerStoreResolutionAndPrewarmStillComplete() =
        runBlocking<Unit> {
            val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
            val store =
                object : PreprocessedPlayerStore {
                    override suspend fun read(key: String): String? = null

                    override suspend fun write(
                        key: String,
                        value: String?,
                    ) = Unit
                }
            val resolver = InnerTubeXStreamResolver(HttpClient(engine), preprocessedPlayerStore = store)

            withTimeout(60_000) { resolver.prewarm() }
            // Offline the library never reaches the cipher, so only installation is exercised here; reads and
            // writes are verified on device.
            assertFailsWith<StreamResolutionException> {
                withTimeout(60_000) { resolver.resolve(VideoId("7fwUH0oRmkQ")) }
            }
            assertFailsWith<StreamResolutionException> {
                withTimeout(60_000) { resolver.resolve(VideoId("7fwUH0oRmkQ")) }
            }
        }
}
