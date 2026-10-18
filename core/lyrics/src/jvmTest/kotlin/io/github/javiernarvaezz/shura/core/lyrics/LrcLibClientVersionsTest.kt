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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Choosing between the versions LRCLIB has of one song. LRCLIB files the same timed text under records of very
 * different durations (seen on 2026-10-17: a 5:00 album take's text under 3:56–4:00 records), and some texts are
 * censored. Synthetic lyrics in the shape of those cases.
 */
class LrcLibClientVersionsTest {
    private val requests = mutableListOf<HttpRequestData>()

    /** Answers `/api/get` with [exact] (or not found) and every search with [results]. */
    private fun client(
        results: List<String>,
        exact: String? = null,
    ): LrcLibClient =
        LrcLibClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    when {
                        request.url.encodedPath == "/api/search" -> {
                            respond(results.joinToString(",", "[", "]"), HttpStatusCode.OK, json)
                        }

                        exact != null -> {
                            respond(exact, HttpStatusCode.OK, json)
                        }

                        else -> {
                            respond("{}", HttpStatusCode.NotFound, json)
                        }
                    }
                },
            ),
        )

    private fun record(
        duration: Double,
        synced: String,
    ): String =
        """{"duration":$duration,"instrumental":false,"plainLyrics":null,""" +
            """"syncedLyrics":"${synced.replace("\n", "\\n")}"}"""

    private fun query(
        duration: Double,
        album: String? = null,
        explicit: Boolean = false,
    ) = LyricsQuery("Song", "Artist", album, (duration * 1000).toLong().milliseconds, explicit)

    private val albumTake = "[00:18.35] Good times\n[04:53.27] The end"
    private val singleEdit = "[00:00.49] Good times\n[03:43.06] The end"

    @Test
    fun aTimedTextThatOutlastsTheSongIsNotFollowed() =
        runTest {
            // The closest record carries the album take's text, whose last line comes after the song has ended.
            val found = client(listOf(record(238.0, albumTake), record(236.0, singleEdit))).lyrics(query(238.0))

            assertEquals(LrcParser.parse(singleEdit), (found as Lyrics.Synced).lines)
        }

    @Test
    fun whenNoTimedTextFitsOnlyTheWordsAreShown() =
        runTest {
            val found = client(listOf(record(238.0, albumTake))).lyrics(query(238.0))

            assertEquals(Lyrics.Plain("Good times\nThe end"), found)
        }

    @Test
    fun aTimedTextThatEndsWithinTheMarginStillFits() =
        runTest {
            val found = client(listOf(record(222.0, singleEdit))).lyrics(query(222.0))

            assertEquals(LrcParser.parse(singleEdit), (found as Lyrics.Synced).lines)
        }

    @Test
    fun anExactMatchThatOutlastsTheSongGivesWayToOneThatFits() =
        runTest {
            val found =
                client(
                    results = listOf(record(236.0, singleEdit)),
                    exact = record(238.0, albumTake),
                ).lyrics(query(238.0, album = "Album"))

            assertEquals(LrcParser.parse(singleEdit), (found as Lyrics.Synced).lines)
        }

    @Test
    fun theTimingMostRecordsShareWinsOverTheClosestDuration() =
        runTest {
            val lone = "[00:05.00] Intro\n[02:30.00] Outro"
            val shared = "[00:09.00] Intro\n[02:34.00] Outro"
            val sharedAgain = "[00:09.20] Intro\n[02:34.20] Outro"
            val found =
                client(
                    listOf(record(200.0, lone), record(199.0, shared), record(201.5, sharedAgain)),
                ).lyrics(query(200.0))

            assertEquals(LrcParser.parse(shared), (found as Lyrics.Synced).lines)
        }

    private val censored = "[00:01.00] Ay co, qué calor\n[00:05.00] Dímelo, p, ya\n[00:09.00] What the f**k"
    private val uncensored = "[00:01.00] Ay coño, qué calor\n[00:05.00] Dímelo, puta, ya\n[00:09.00] What the fuck"

    @Test
    fun anExplicitSongGetsTheUncensoredText() =
        runTest {
            val found =
                client(
                    listOf(record(200.0, censored), record(201.0, uncensored)),
                ).lyrics(query(200.0, explicit = true))

            assertEquals(LrcParser.parse(uncensored), (found as Lyrics.Synced).lines)
        }

    @Test
    fun aSongNotMarkedExplicitKeepsTheUsualChoice() =
        runTest {
            val found = client(listOf(record(200.0, censored), record(201.0, uncensored))).lyrics(query(200.0))

            assertEquals(LrcParser.parse(censored), (found as Lyrics.Synced).lines)
        }

    @Test
    fun maskedWordsCountAsCensoredWithoutAnotherVersionToCompare() =
        runTest {
            val masked = "[00:01.00] What the f**k\n[00:05.00] Oh"
            val other = "[00:01.20] What the hell\n[00:05.20] Oh"
            val found =
                client(
                    listOf(record(200.0, masked), record(201.0, other)),
                ).lyrics(query(200.0, explicit = true))

            assertEquals(LrcParser.parse(other), (found as Lyrics.Synced).lines)
        }

    @Test
    fun anExplicitSongComparesTheExactMatchWithTheSearch() =
        runTest {
            val found =
                client(
                    results = listOf(record(201.0, uncensored)),
                    exact = record(200.0, censored),
                ).lyrics(query(200.0, album = "Album", explicit = true))

            assertEquals(LrcParser.parse(uncensored), (found as Lyrics.Synced).lines)
            assertEquals(1, requests.count { it.url.encodedPath == "/api/search" })
        }

    @Test
    fun anUncensoredExactMatchIsKeptForAnExplicitSong() =
        runTest {
            val found =
                client(
                    results = listOf(record(200.0, censored)),
                    exact = record(201.0, uncensored),
                ).lyrics(query(200.0, album = "Album", explicit = true))

            assertEquals(LrcParser.parse(uncensored), (found as Lyrics.Synced).lines)
        }

    @Test
    fun theSameTextUnderManyRecordsIsNotCensoredAgainstItself() =
        runTest {
            val found =
                client(
                    listOf(record(200.0, uncensored), record(201.0, uncensored)),
                ).lyrics(query(200.0, explicit = true))

            assertEquals(LrcParser.parse(uncensored), (found as Lyrics.Synced).lines)
            assertEquals(1.seconds, found.lines.first().time)
        }
}
