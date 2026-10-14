package io.github.javiernarvaezz.shura.core.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/** How search results are matched: duration tiers and the retry with a cleaned title. */
class LrcLibClientMatchingTest {
    private val requests = mutableListOf<HttpRequestData>()

    /** Answers each search by its track name; /api/get always finds nothing. */
    private fun client(results: Map<String, String>): LrcLibClient =
        LrcLibClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    if (request.url.encodedPath == "/api/get") {
                        respond("{}", HttpStatusCode.NotFound, json)
                    } else {
                        respond(results[request.url.parameters["track_name"]] ?: "[]", HttpStatusCode.OK, json)
                    }
                },
            ),
        )

    private fun record(
        duration: Double,
        synced: String? = "[00:01.00] first\n[00:05.00]\n[00:09.00] second",
        plain: String? = null,
    ): String {
        fun quoted(value: String?) = value?.let { "\"" + it.replace("\n", "\\n") + "\"" } ?: "null"
        return """{"duration":$duration,"instrumental":false,"plainLyrics":${quoted(plain)},""" +
            """"syncedLyrics":${quoted(synced)}}"""
    }

    private val synced =
        Lyrics.Synced(
            listOf(LyricLine(1.seconds, "first"), LyricLine(5.seconds, ""), LyricLine(9.seconds, "second")),
        )

    @Test
    fun withinTwoSecondsLyricsStaySynced() =
        runTest {
            val client = client(mapOf("Song" to "[${record(202.0)}]"))
            assertEquals(synced, client.lyrics(LyricsQuery("Song", "Artist", null, 200.seconds)))
        }

    @Test
    fun fromTwoToSixSecondsOnlyTheTextIsShown() =
        runTest {
            val client = client(mapOf("Song" to "[${record(205.0)}]"))
            assertEquals(Lyrics.Plain("first\nsecond"), client.lyrics(LyricsQuery("Song", "Artist", null, 200.seconds)))
        }

    @Test
    fun aDistantRecordsOwnPlainTextIsPreferredOverItsSyncedLines() =
        runTest {
            val client = client(mapOf("Song" to "[${record(196.0, plain = "First\n\nSecond")}]"))
            assertEquals(
                Lyrics.Plain("First\n\nSecond"),
                client.lyrics(LyricsQuery("Song", "Artist", null, 200.seconds)),
            )
        }

    @Test
    fun beyondSixSecondsNothingMatches() =
        runTest {
            val client = client(mapOf("Song" to "[${record(207.0)}]"))
            assertFailsWith<LyricsException.NotFound> {
                client.lyrics(
                    LyricsQuery("Song", "Artist", null, 200.seconds),
                )
            }
        }

    @Test
    fun aCloseSyncedRecordBeatsAnyTextOnlyOne() =
        runTest {
            val client = client(mapOf("Song" to "[${record(204.0, plain = "far")},${record(201.5)}]"))
            assertEquals(synced, client.lyrics(LyricsQuery("Song", "Artist", null, 200.seconds)))
        }

    @Test
    fun theClosestTextOnlyRecordWins() =
        runTest {
            val client =
                client(
                    mapOf(
                        "Song" to
                            "[${record(
                                206.0,
                                synced = null,
                                plain = "far",
                            )},${record(203.0, synced = null, plain = "near")}]",
                    ),
                )
            assertEquals(Lyrics.Plain("near"), client.lyrics(LyricsQuery("Song", "Artist", null, 200.seconds)))
        }

    @Test
    fun withoutAMatchTheCleanedTitleAndArtistAreTried() =
        runTest {
            val client = client(mapOf("Lluvia" to "[${record(298.0)}]"))
            val found = client.lyrics(LyricsQuery("Lluvia (Visualizer)", "Eddie Santiago - Topic", null, 298.seconds))
            assertEquals(synced, found)
            val searches = requests.filter { it.url.encodedPath == "/api/search" }
            assertEquals(listOf("Lluvia (Visualizer)", "Lluvia"), searches.map { it.url.parameters["track_name"] })
            assertEquals(
                listOf("Eddie Santiago - Topic", "Eddie Santiago"),
                searches.map { it.url.parameters["artist_name"] },
            )
        }

    @Test
    fun aMatchOnTheTitleAsItIsNeedsNoSecondSearch() =
        runTest {
            val client = client(mapOf("Song (Live)" to "[${record(200.0)}]"))
            assertEquals(synced, client.lyrics(LyricsQuery("Song (Live)", "Artist", null, 200.seconds)))
            assertEquals(1, requests.count { it.url.encodedPath == "/api/search" })
        }

    @Test
    fun aTitleWithNothingToCleanIsSearchedOnce() =
        runTest {
            assertFailsWith<LyricsException.NotFound> {
                client(emptyMap()).lyrics(LyricsQuery("Buscando Tu Besos", "Rubby Perez", null, 232.seconds))
            }
            assertEquals(1, requests.count { it.url.encodedPath == "/api/search" })
        }
}
