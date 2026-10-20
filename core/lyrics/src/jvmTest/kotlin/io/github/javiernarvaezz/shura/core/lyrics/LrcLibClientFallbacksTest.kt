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

/**
 * The last searches, when the song as named finds nothing: "Artist - Title" in a fan upload's title, then the title
 * alone (a track published under the uploader's name, as "wiseman" by SkyScale96 is Frank Ocean's). Synthetic
 * lyrics in the shape of those cases.
 */
class LrcLibClientFallbacksTest {
    private val requests = mutableListOf<HttpRequestData>()

    /** Answers a search by its track and artist ("" when the search has no artist). */
    private fun client(results: Map<Pair<String, String>, List<String>>): LrcLibClient =
        LrcLibClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    val key =
                        request.url.parameters["track_name"].orEmpty() to
                            request.url.parameters["artist_name"].orEmpty()
                    respond(results[key].orEmpty().joinToString(",", "[", "]"), HttpStatusCode.OK, json)
                },
            ),
        )

    private fun record(
        track: String,
        artist: String,
        duration: Double,
        synced: String = "[00:10.00] First line\n[03:30.00] Last line",
    ): String =
        """{"trackName":"$track","artistName":"$artist","duration":$duration,"instrumental":false,""" +
            """"plainLyrics":null,"syncedLyrics":"${synced.replace("\n", "\\n")}"}"""

    private val lines = LrcParser.parse("[00:10.00] First line\n[03:30.00] Last line")

    private fun searches() = requests.map { it.url.parameters["track_name"] to it.url.parameters["artist_name"] }

    @Test
    fun aTrackUnderTheUploadersNameIsFoundByItsTitleAndLength() =
        runTest {
            val found =
                client(
                    mapOf(
                        ("wiseman" to "") to
                            listOf(
                                record("Wiseman", "Frank Ocean", 231.0),
                                record("wiseman", "frank ocean", 232.0),
                                record("Wiseman", "Someone Else", 160.0),
                            ),
                    ),
                ).lyrics(LyricsQuery("wiseman", "SkyScale96", null, 232.seconds))

            assertEquals(lines, (found as Lyrics.Synced).lines)
            assertEquals(listOf("wiseman" to "SkyScale96", "wiseman" to null), searches())
        }

    @Test
    fun theSameTitleWithAnotherLengthIsNotTaken() =
        runTest {
            val client = client(mapOf(("wiseman" to "") to listOf(record("Wiseman", "Someone Else", 160.0))))

            assertFailsWith<LyricsException.NotFound> {
                client.lyrics(LyricsQuery("wiseman", "SkyScale96", null, 232.seconds))
            }
        }

    @Test
    fun anotherTitleIsNotTakenEvenWithTheSameLength() =
        runTest {
            val client = client(mapOf(("wiseman" to "") to listOf(record("Wiseman Blues", "Frank Ocean", 232.0))))

            assertFailsWith<LyricsException.NotFound> {
                client.lyrics(LyricsQuery("wiseman", "SkyScale96", null, 232.seconds))
            }
        }

    @Test
    fun twoArtistsAsLikelyAsEachOtherGiveNothing() =
        runTest {
            val client =
                client(
                    mapOf(
                        ("wiseman" to "") to
                            listOf(record("Wiseman", "Frank Ocean", 232.0), record("Wiseman", "Kai Wave", 232.0)),
                    ),
                )

            assertFailsWith<LyricsException.NotFound> {
                client.lyrics(LyricsQuery("wiseman", "SkyScale96", null, 232.seconds))
            }
        }

    @Test
    fun accentsAndCaseDoNotMakeAnotherTitle() =
        runTest {
            val found =
                client(mapOf(("Canción" to "") to listOf(record("cancion", "Artista", 232.0))))
                    .lyrics(LyricsQuery("Canción", "Uploader", null, 232.seconds))

            assertEquals(lines, (found as Lyrics.Synced).lines)
        }

    @Test
    fun aFanUploadNamedArtistDashTitleIsSearchedThatWay() =
        runTest {
            val found =
                client(mapOf(("Wiseman" to "Frank Ocean") to listOf(record("Wiseman", "Frank Ocean", 232.0))))
                    .lyrics(LyricsQuery("Frank Ocean - Wiseman (Lyrics)", "7Tears", null, 232.seconds))

            assertEquals(lines, (found as Lyrics.Synced).lines)
            // As named, cleaned, then split.
            assertEquals(
                listOf(
                    "Frank Ocean - Wiseman (Lyrics)" to "7Tears",
                    "Frank Ocean - Wiseman" to "7Tears",
                    "Wiseman" to "Frank Ocean",
                ),
                searches(),
            )
        }

    @Test
    fun whatTheSongAsNamedFindsNeedsNoFallback() =
        runTest {
            client(mapOf(("Song" to "Artist") to listOf(record("Song", "Artist", 232.0))))
                .lyrics(LyricsQuery("Song", "Artist", null, 232.seconds))

            assertEquals(listOf("Song" to "Artist"), searches())
        }

    @Test
    fun aTitleAloneThatFindsNothingEndsTheSearch() =
        runTest {
            assertFailsWith<LyricsException.NotFound> {
                client(emptyMap()).lyrics(LyricsQuery("Song", "Artist", null, 232.seconds))
            }
            assertEquals(listOf("Song" to "Artist", "Song" to null), searches())
        }
}
