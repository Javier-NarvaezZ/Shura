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
}
