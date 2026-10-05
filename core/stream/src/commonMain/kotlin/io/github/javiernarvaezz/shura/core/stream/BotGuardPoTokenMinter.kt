package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.extraction.potoken.parseAttestationChallengeData
import com.metrolist.innertubex.extraction.potoken.parseAttestationInterpreterUrl
import com.metrolist.innertubex.extraction.potoken.parseIntegrityTokenData
import com.metrolist.innertubex.extraction.potoken.parseWebPageAttestationContext
import com.metrolist.innertubex.extraction.potoken.requireTrustedAttestationInterpreterUrl
import com.metrolist.innertubex.extraction.potoken.stringToU8
import com.metrolist.innertubex.extraction.potoken.u8ToBase64
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Web BotGuard PoToken minter (ADR 0001 R2), following the flow InnerTubeX's own live harness uses:
 * watch page → BotGuard challenge → interpreter → BotGuard run in [JsRuntime] → `GenerateIT` → minter.
 *
 * All HTTP goes through [http] (the app's single client, R7); the runtime only computes. Any failure resets the
 * attestation and surfaces as [PoTokenUnavailableException] with the stage name only.
 */
class BotGuardPoTokenMinter internal constructor(
    private val http: HttpClient,
    private val runtimeFactory: suspend () -> JsRuntime,
    private val clock: Clock,
    private val attestationTimeout: Duration,
    private val mintTimeout: Duration,
) : PoTokenMinter {
    constructor(http: HttpClient, runtimeFactory: suspend () -> JsRuntime) :
        this(http, runtimeFactory, Clock.System, ATTESTATION_TIMEOUT, MINT_TIMEOUT)

    private class Session(
        val runtime: JsRuntime,
        val visitorData: String,
        val playerToken: String,
        val expiresAt: Instant,
    )

    private val mutex = Mutex()
    private var session: Session? = null

    override suspend fun mint(
        videoId: VideoId,
        visitorData: String,
    ): PoTokens =
        mutex.withLock {
            val stage = Stage()
            try {
                val current =
                    session?.takeIf { it.visitorData == visitorData && clock.now() < it.expiresAt }
                        ?: run {
                            reset()
                            withTimeout(
                                attestationTimeout,
                            ) { attest(videoId, visitorData, stage) }.also { session = it }
                        }
                stage.name = "mint"
                val streaming = withTimeout(mintTimeout) { mintFor(current.runtime, videoId.value) }
                PoTokens(current.playerToken, streaming, visitorData)
            } catch (
                // Converted on purpose into a typed failure; the timeout cause adds nothing useful.
                @Suppress("SwallowedException") e: TimeoutCancellationException,
            ) {
                reset()
                throw PoTokenUnavailableException(stage.name, "timeout")
            } catch (e: CancellationException) {
                reset()
                throw e
            } catch (e: PoTokenUnavailableException) {
                reset()
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                reset()
                throw PoTokenUnavailableException(stage.name, e::class.simpleName)
            }
        }

    override suspend fun invalidate() = mutex.withLock { reset() }

    override fun close() = reset()

    private class Stage(
        var name: String = "runtime",
    )

    private suspend fun attest(
        videoId: VideoId,
        visitorData: String,
        stage: Stage,
    ): Session {
        val runtime = runtimeFactory()
        try {
            runtime.load(BotGuardScripts.BOOTSTRAP)
            stage.name = "watch"
            val page =
                fetchText("watch") {
                    http.get(WATCH_URL) {
                        parameter("v", videoId.value)
                        browserHeaders(runtime.userAgent)
                        header(HttpHeaders.Accept, "text/html")
                        header("X-Goog-Visitor-Id", visitorData)
                        header(HttpHeaders.Cookie, CONSENT_COOKIE)
                    }
                }
            stage.name = "challenge"
            val attestation = parseWebPageAttestationContext(page)
            val interpreterUrl =
                requireTrustedAttestationInterpreterUrl(parseAttestationInterpreterUrl(attestation.challenge))
            stage.name = "interpreter"
            val interpreter =
                fetchText("interpreter") { http.get(interpreterUrl) { browserHeaders(runtime.userAgent) } }
            val challengeData = parseAttestationChallengeData(attestation.challenge, interpreter)
            stage.name = "botguard"
            val botguardResponse =
                runtime.call(
                    BotGuardScripts.RUN_BOTGUARD,
                    "$challengeData, ${Json.encodeToString(attestation.eventId)}",
                )
            stage.name = "integrity"
            val integrity =
                parseIntegrityTokenData(
                    fetchText("integrity") {
                        http.post(GENERATE_IT_URL) {
                            browserHeaders(runtime.userAgent)
                            header(HttpHeaders.Accept, "application/json")
                            header("x-goog-api-key", WEB_API_KEY)
                            header("x-user-agent", "grpc-web-javascript/0.1")
                            val body = "[${Json.encodeToString(REQUEST_KEY)},${Json.encodeToString(botguardResponse)}]"
                            setBody(TextContent(body, ContentType.parse("application/json+protobuf")))
                        }
                    },
                )
            stage.name = "minter"
            runtime.call(BotGuardScripts.CREATE_MINTER, integrity.tokenJavaScript)
            stage.name = "player"
            val playerToken = mintFor(runtime, visitorData)
            return Session(runtime, visitorData, playerToken, expiryFor(integrity.lifetimeSeconds))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Throwable,
        ) {
            runtime.close()
            throw e
        }
    }

    private suspend fun mintFor(
        runtime: JsRuntime,
        identifier: String,
    ): String = u8ToBase64(runtime.call(BotGuardScripts.MINT, stringToU8(identifier)))

    /** Never extends the server lifetime: keeps a margin of 10% (at most 5 minutes). */
    private fun expiryFor(lifetimeSeconds: Long): Instant {
        val lifetime = lifetimeSeconds.coerceAtLeast(0).seconds
        val margin = minOf(lifetime / EXPIRY_MARGIN_DIVISOR, MAX_EXPIRY_MARGIN)
        return clock.now() + lifetime - margin
    }

    private suspend fun fetchText(
        stage: String,
        request: suspend () -> HttpResponse,
    ): String {
        val response = request()
        if (!response.status.isSuccess()) throw PoTokenUnavailableException(stage, "HTTP ${response.status.value}")
        return response.bodyAsText()
    }

    private fun HttpRequestBuilder.browserHeaders(userAgent: String) {
        header(HttpHeaders.UserAgent, userAgent)
    }

    private fun reset() {
        session?.runtime?.close()
        session = null
    }

    private companion object {
        val ATTESTATION_TIMEOUT = 10.seconds
        val MINT_TIMEOUT = 3.seconds
        val MAX_EXPIRY_MARGIN = 5.minutes
        const val EXPIRY_MARGIN_DIVISOR = 10
        const val WATCH_URL = "https://www.youtube.com/watch"
        const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
        const val CONSENT_COOKIE = "SOCS=CAI"

        // Public values of YouTube's web player (also used by InnerTubeX's harness and bgutils-js); not secrets.
        const val WEB_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    }
}
