package io.github.javiernarvaezz.shura.core.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Matching on recorded LRCLIB responses (see [Fixtures]). */
class LrcLibClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private class Recorded(
        val fixture: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
    )

    /** Answers `/api/get` with [get] and `/api/search` with [search]; a missing one fails the test. */
    private fun client(
        get: Recorded? = null,
        search: Recorded? = null,
    ): LrcLibClient {
        val engine =
            MockEngine { request ->
                requests += request
                val recorded =
                    when (request.url.encodedPath) {
                        "/api/get" -> get
                        "/api/search" -> search
                        else -> null
                    } ?: error("Unexpected request to ${request.url.encodedPath}")
                respond(
                    Fixtures.read(recorded.fixture),
                    recorded.status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return LrcLibClient(HttpClient(engine))
    }

    private fun synced(record: JsonObject) = LrcParser.parse(record.getValue("syncedLyrics").jsonPrimitive.content)

    private fun plain(record: JsonObject) =
        record
            .getValue("plainLyrics")
            .jsonPrimitive.content
            .trimEnd()

    @Test
    fun exactSyncedMatchNeedsNoSearch() =
        runTest {
            val lyrics =
                client(get = Recorded("get/pedro_infante_synced.json"))
                    .lyrics(LyricsQuery("Cielito Lindo", "Pedro Infante", "Discografía Completa", 187.seconds))

            val synced = assertIs<Lyrics.Synced>(lyrics)
            assertEquals(LyricLine(15_760.milliseconds, "De la sierra morena"), synced.lines.first())
            assertEquals(synced(Fixtures.getRecord("get/pedro_infante_synced.json")), synced.lines)
            assertEquals(synced.lines.sortedBy { it.time }, synced.lines)
            assertEquals(1, requests.size)
        }

    @Test
    fun exactPlainMatchGivesWayToSyncedSearchResult() =
        runTest {
            val lyrics =
                client(
                    get = Recorded("get/silent_night_elvis_plain.json"),
                    search = Recorded("search/silent_night_elvis.json"),
                ).lyrics(LyricsQuery("Silent Night", "Elvis Presley", SILENT_NIGHT_ALBUM, 144_440.milliseconds))

            val synced = assertIs<Lyrics.Synced>(lyrics)
            assertTrue(synced.lines.any { it.text.isNotEmpty() })
            assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
        }

    @Test
    fun exactPlainMatchIsKeptWhenNoSearchResultIsSynced() =
        runTest {
            val lyrics =
                client(
                    get = Recorded("get/mariachi_vargas_plain.json"),
                    search = Recorded("search/mariachi_vargas.json"),
                ).lyrics(LyricsQuery("Cielito Lindo", "Mariachi Vargas De Tecalitlan", "Cielo Rojo", 131.seconds))

            assertEquals(Lyrics.Plain(plain(Fixtures.getRecord("get/mariachi_vargas_plain.json"))), lyrics)
        }

    @Test
    fun notFoundExactMatchFallsBackToSearch() =
        runTest {
            val lyrics =
                client(
                    get = Recorded("error/not_found_404.json", HttpStatusCode.NotFound),
                    search = Recorded("search/pedro_infante.json"),
                ).lyrics(LyricsQuery("Cielito Lindo", "Pedro Infante", "Unknown Album", 187.seconds))

            assertIs<Lyrics.Synced>(lyrics)
            assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
        }

    @Test
    fun searchKeepsOnlyTheWordsOfATextTimedForAnotherCut() =
        runTest {
            // Within 2 s of 165 s only "Tesoros De Colección" (164.05 s) qualifies, but its text was timed for the
            // 187 s recording: its last line starts at 2:56, after the song has ended.
            val lyrics =
                client(search = Recorded("search/pedro_infante.json"))
                    .lyrics(LyricsQuery("Cielito Lindo", "Pedro Infante", null, 165.seconds))

            assertEquals(Lyrics.Plain(plain(Fixtures.searchRecord("search/pedro_infante.json", 20_696_091))), lyrics)
        }

    @Test
    fun searchResultsOutsideTheDurationToleranceDoNotMatch() =
        runTest {
            assertFailsWith<LyricsException.NotFound> {
                client(search = Recorded("search/pedro_infante.json"))
                    .lyrics(LyricsQuery("Cielito Lindo", "Pedro Infante", null, 300.seconds))
            }
        }

    @Test
    fun searchFallsBackToPlainLyrics() =
        runTest {
            val lyrics =
                client(search = Recorded("search/mariachi_vargas.json"))
                    .lyrics(LyricsQuery("Cielito Lindo", "Mariachi Vargas De Tecalitlan", null, 222.seconds))

            assertIs<Lyrics.Plain>(lyrics)
        }

    @Test
    fun exactInstrumentalMatchNeedsNoSearch() =
        runTest {
            val lyrics =
                client(get = Recorded("get/fur_elise_instrumental.json"))
                    .lyrics(LyricsQuery("Fur Elise", "Ludwig van Beethoven", "The Best of Beethoven", 182.seconds))

            assertEquals(Lyrics.Instrumental, lyrics)
            assertEquals(1, requests.size)
        }

    @Test
    fun searchFindsInstrumentalRecords() =
        runTest {
            val lyrics =
                client(search = Recorded("search/fur_elise.json"))
                    .lyrics(LyricsQuery("Für Elise", "Ludwig van Beethoven", null, 173.seconds))

            assertEquals(Lyrics.Instrumental, lyrics)
        }

    @Test
    fun overloadedServerIsTypedAsHttp() =
        runTest {
            val error =
                assertFailsWith<LyricsException.Http> {
                    client(search = Recorded("error/overloaded_503.json", HttpStatusCode.ServiceUnavailable))
                        .lyrics(LyricsQuery("Cielito Lindo", "Pedro Infante", null, 187.seconds))
                }
            assertEquals(503, error.status)
        }

    private companion object {
        const val SILENT_NIGHT_ALBUM = "The Complete Releases 1954-1962"
    }
}
