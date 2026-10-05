package io.github.javiernarvaezz.shura.core.stream

import io.github.javiernarvaezz.shura.core.model.VideoId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BotGuardPoTokenMinterTest {
    private val video = VideoId("7fwUH0oRmkQ")
    private val otherVideo = VideoId("nOj6d-HOw2w")
    private val visitor = "CgtWaXNpdG9yRGF0YQ"

    private class TestClock(
        var now: Instant = Instant.fromEpochSeconds(1_800_000_000),
    ) : Clock {
        override fun now(): Instant = now
    }

    private class FakeRuntime(
        val behavior: (function: String, args: String) -> String,
    ) : JsRuntime {
        override val userAgent = "TestAgent/1.0"
        val loaded = mutableListOf<String>()
        val calls = mutableListOf<Pair<String, String>>()
        var closed = false

        override suspend fun load(script: String) {
            loaded += script
        }

        override suspend fun call(
            function: String,
            argumentsJs: String,
        ): String {
            calls += function to argumentsJs
            return behavior(function, argumentsJs)
        }

        override fun close() {
            closed = true
        }
    }

    private fun defaultBehavior(
        function: String,
        args: String,
    ): String =
        when (function) {
            BotGuardScripts.RUN_BOTGUARD -> "bg-response"

            BotGuardScripts.CREATE_MINTER -> "ready"

            // Player token is minted from the visitor data, streaming tokens from video ids.
            BotGuardScripts.MINT -> if (args.length > 60) "4,5,6" else "7,8,9"

            else -> error("unexpected $function")
        }

    private val requests = mutableListOf<HttpRequestData>()
    private val runtimes = mutableListOf<FakeRuntime>()
    private val clock = TestClock()
    private var interpreterUrl = "//www.google.com/js/th/AAAAAAAAAAAAAAAAAAAAAAAA.js"
    private var generateItStatus = HttpStatusCode.OK
    private var behavior: (String, String) -> String = ::defaultBehavior

    private fun page() =
        "<script>ytcfg.set({\"EVENT_ID\":\"page-event\"});" +
            "window.ytAtN({R:'{\"bgChallenge\":{\"interpreterUrl\":{" +
            "\"privateDoNotAccessOrElseTrustedResourceUrlWrappedValue\":" +
            "\"$interpreterUrl\"},\"interpreterHash\":\"hash\",\"program\":\"program-data\",\"globalName\":\"bgvm\"}}'});</script>"

    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData =
        when {
            request.url.encodedPath == "/watch" -> {
                respond(page())
            }

            request.url.host == "www.google.com" -> {
                respond("var bgvm = {};")
            }

            request.url.encodedPath == "/api/jnn/v1/GenerateIT" -> {
                if (generateItStatus ==
                    HttpStatusCode.OK
                ) {
                    respond("[\"AQID\",43200]")
                } else {
                    respondError(generateItStatus)
                }
            }

            else -> {
                respondError(HttpStatusCode.NotFound)
            }
        }

    /** Runs the mock engine on the test scheduler so virtual-time timeouts do not race real threads. */
    private fun TestScope.engine(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            },
        )

    private fun TestScope.minter(attestationTimeoutSeconds: Int = 10) =
        BotGuardPoTokenMinter(
            http =
                HttpClient(
                    engine { request ->
                        requests += request
                        answer(request)
                    },
                ),
            runtimeFactory = { FakeRuntime(behavior).also { runtimes += it } },
            clock = clock,
            attestationTimeout = attestationTimeoutSeconds.seconds,
            mintTimeout = 3.seconds,
        )

    @Test
    fun firstMintRunsTheWholeFlowThroughKotlinHttp() =
        runTest {
            val tokens = minter().mint(video, visitor)

            assertEquals("BAUG", tokens.player)
            assertEquals("BwgJ", tokens.streaming)
            assertEquals(visitor, tokens.visitorData)

            assertEquals(listOf("www.youtube.com", "www.google.com", "www.youtube.com"), requests.map { it.url.host })
            val watch = requests[0]
            assertEquals("/watch", watch.url.encodedPath)
            assertEquals(video.value, watch.url.parameters["v"])
            assertEquals(visitor, watch.headers["X-Goog-Visitor-Id"])
            assertEquals("SOCS=CAI", watch.headers["Cookie"])
            assertEquals("TestAgent/1.0", watch.headers["User-Agent"])
            assertEquals("/js/th/AAAAAAAAAAAAAAAAAAAAAAAA.js", requests[1].url.encodedPath)
            val generateIt = requests[2]
            assertEquals(HttpMethod.Post, generateIt.method)
            assertEquals(
                "application/json+protobuf",
                generateIt.body.contentType
                    ?.withoutParameters()
                    .toString(),
            )
            assertEquals("grpc-web-javascript/0.1", generateIt.headers["x-user-agent"])
            assertTrue(generateIt.headers["x-goog-api-key"].orEmpty().isNotBlank())
            assertEquals("[\"O43z0dpjhgX20SCx4KAo\",\"bg-response\"]", generateIt.body.toByteArray().decodeToString())

            val runtime = runtimes.single()
            assertEquals(listOf(BotGuardScripts.BOOTSTRAP), runtime.loaded)
            assertEquals(
                listOf(
                    BotGuardScripts.RUN_BOTGUARD,
                    BotGuardScripts.CREATE_MINTER,
                    BotGuardScripts.MINT,
                    BotGuardScripts.MINT,
                ),
                runtime.calls.map { it.first },
            )
            assertTrue(runtime.calls[0].second.endsWith(", \"page-event\""))
            assertEquals("new Uint8Array([1,2,3])", runtime.calls[1].second)
            assertFalse(runtime.closed)
        }

    @Test
    fun sameVisitorReusesThePlayerTokenAndOnlyMintsTheNewVideo() =
        runTest {
            val minter = minter()
            minter.mint(video, visitor)
            val requestsAfterFirst = requests.size

            val second = minter.mint(otherVideo, visitor)

            assertEquals(requestsAfterFirst, requests.size)
            assertEquals("BAUG", second.player)
            assertEquals(1, runtimes.size)
            assertEquals(5, runtimes.single().calls.size)
        }

    @Test
    fun newVisitorOrExpiredAttestationStartsOver() =
        runTest {
            val minter = minter()
            minter.mint(video, visitor)
            minter.mint(video, "CgtPdGhlclZpc2l0b3I")
            assertEquals(2, runtimes.size)
            assertTrue(runtimes[0].closed)

            clock.now += 12.hours
            minter.mint(video, "CgtPdGhlclZpc2l0b3I")
            assertEquals(3, runtimes.size)
            assertTrue(runtimes[1].closed)
        }

    @Test
    fun untrustedInterpreterIsNeverFetched() =
        runTest {
            interpreterUrl = "//evil.example.com/js/th/AAAAAAAAAAAAAAAAAAAAAAAA.js"

            val error = assertFailsWith<PoTokenUnavailableException> { minter().mint(video, visitor) }

            assertEquals("challenge", error.stage)
            assertEquals(listOf("www.youtube.com"), requests.map { it.url.host })
            assertTrue(runtimes.single().closed)
        }

    @Test
    fun scriptFailureResetsAndTheNextMintStartsOver() =
        runTest {
            behavior = { function, args ->
                if (function ==
                    BotGuardScripts.RUN_BOTGUARD
                ) {
                    throw JsRuntimeException("vm-unavailable")
                } else {
                    defaultBehavior(function, args)
                }
            }
            val minter = minter()

            val error = assertFailsWith<PoTokenUnavailableException> { minter.mint(video, visitor) }
            assertEquals("botguard", error.stage)
            assertEquals("JsRuntimeException", error.causeType)
            assertTrue(runtimes.single().closed)

            behavior = ::defaultBehavior
            minter.mint(video, visitor)
            assertEquals(2, runtimes.size)
        }

    @Test
    fun integrityHttpErrorIsReportedWithStatusOnly() =
        runTest {
            generateItStatus = HttpStatusCode.Forbidden

            val error = assertFailsWith<PoTokenUnavailableException> { minter().mint(video, visitor) }

            assertEquals("integrity", error.stage)
            assertEquals("HTTP 403", error.causeType)
        }

    @Test
    fun slowBotGuardTimesOutWithoutAToken() =
        runTest {
            behavior = { function, args ->
                if (function == BotGuardScripts.RUN_BOTGUARD) "" else defaultBehavior(function, args)
            }
            val hanging =
                BotGuardPoTokenMinter(
                    http = HttpClient(engine { request -> answer(request) }),
                    runtimeFactory = {
                        object : JsRuntime by FakeRuntime(behavior) {
                            override suspend fun call(
                                function: String,
                                argumentsJs: String,
                            ): String = awaitCancellation()
                        }
                    },
                    clock = clock,
                    attestationTimeout = 10.seconds,
                    mintTimeout = 3.seconds,
                )

            val error = assertFailsWith<PoTokenUnavailableException> { hanging.mint(video, visitor) }

            assertEquals("botguard", error.stage)
            assertEquals("timeout", error.causeType)
        }

    @Test
    fun tokensAndFailuresNeverRevealValues() =
        runTest {
            val tokens = minter().mint(video, visitor)

            val text = tokens.toString()
            listOf(tokens.player, tokens.streaming, visitor).forEach { assertFalse(text.contains(it)) }
            val failure = PoTokenUnavailableException("integrity", "HTTP 403")
            assertFalse(failure.message.orEmpty().contains(visitor))
        }
}

class JsonShapeTest {
    @Test
    fun describesStructureWithoutValues() {
        assertEquals("array(2)[s,n]", jsonShape("[\"AQID\",43200]"))
        assertEquals("array(4)[null,n,null,s]", jsonShape("[null,43200,null,\"fallback-token\"]"))
        assertEquals("object{error}", jsonShape("{\"error\":{\"message\":\"secret detail\"}}"))
        assertEquals("non-json(6)", jsonShape("<html>"))
        assertEquals("s", jsonShape("\"text\""))
    }

    @Test
    fun neverIncludesValues() {
        val shape = jsonShape("[\"AQIDBAUGBwgJ\",43200,null,\"fallback-token\"]")

        listOf("AQID", "43200", "fallback").forEach { assertFalse(shape.contains(it), shape) }
    }
}
