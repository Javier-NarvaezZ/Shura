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
            val engine =
                MockEngine { request ->
                    hosts += request.url.host
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
            val resolver = InnerTubeXStreamResolver(HttpClient(engine))

            assertFailsWith<StreamResolutionException> {
                withTimeout(60_000) { resolver.resolve(VideoId("7fwUH0oRmkQ")) }
            }

            assertTrue(hosts.isNotEmpty(), "expected the library to attempt requests")
            val disallowed = hosts.filterNot(HostPolicy::isAllowed).distinct()
            assertTrue(disallowed.isEmpty(), "requests to disallowed hosts: $disallowed")
        }
}
