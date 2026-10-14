package io.github.javiernarvaezz.shura.feature.lyrics

import io.github.javiernarvaezz.shura.core.lyrics.LyricLine
import io.github.javiernarvaezz.shura.core.lyrics.Lyrics
import io.github.javiernarvaezz.shura.core.lyrics.LyricsException
import io.github.javiernarvaezz.shura.core.lyrics.LyricsQuery
import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.PlaybackProgress
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LyricsModelTest {
    private val player = FakeAudioPlayer()
    private val synced = Lyrics.Synced(listOf(LyricLine(1.seconds, "one"), LyricLine(42.seconds, "two")))
    private val requests = mutableListOf<Pair<String, LyricsQuery>>()
    private val refreshes = mutableListOf<Boolean>()
    private var answer: suspend (LyricsQuery) -> Lyrics? = { synced }

    private fun song(
        n: Int,
        artists: List<Artist> = listOf(Artist("Artist $n")),
        duration: Duration? = 200.seconds,
        album: AlbumRef? = AlbumRef("Album $n"),
    ): Song = testSong(n).copy(artists = artists, duration = duration, album = album)

    private fun play(song: Song) {
        player.queue.value = QueueState(items = listOf(song), currentIndex = 0)
    }

    private fun TestScope.model(): LyricsModel {
        val model =
            LyricsModel(
                player,
                lyrics = { key, query, refresh ->
                    requests += key to query
                    refreshes += refresh
                    answer(query)
                },
                scope = backgroundScope,
            )
        runCurrent()
        return model
    }

    @Test
    fun loadsOnlyWhileShown() =
        runTest {
            play(song(1))
            val model = model()
            runCurrent()
            assertEquals(LyricsUiState.Idle, model.state.value)
            assertTrue(requests.isEmpty())

            model.setVisible(true)
            runCurrent()
            assertEquals(LyricsUiState.Synced(synced), model.state.value)
            assertEquals(1, requests.size)
        }

    @Test
    fun theQueryComesFromTheSong() =
        runTest {
            play(song(1, artists = listOf(Artist("First"), Artist("Second"))))
            model().setVisible(true)
            runCurrent()
            val (key, query) = requests.single()
            assertEquals(song(1).videoId.value, key)
            assertEquals("Song 1", query.title)
            assertEquals("First", query.artist)
            assertEquals("Album 1", query.album)
            assertEquals(200.seconds, query.duration)
        }

    @Test
    fun withoutAnAlbumTheQueryHasNone() =
        runTest {
            play(song(1, album = null))
            model().setVisible(true)
            runCurrent()
            assertEquals(null, requests.single().second.album)
        }

    @Test
    fun withoutItsOwnDurationThePlayersIsUsed() =
        runTest {
            play(song(1, duration = null))
            player.progress = PlaybackProgress(3.seconds, 181.seconds, 10.seconds)
            model().setVisible(true)
            runCurrent()
            assertEquals(181.seconds, requests.single().second.duration)
        }

    @Test
    fun theLookupWaitsForThePlayersDurationWhenItArrivesLater() =
        runTest {
            play(song(1, duration = null))
            val model = model()
            model.setVisible(true)
            runCurrent()
            assertEquals(LyricsUiState.Loading, model.state.value)

            advanceTimeBy(1.seconds)
            player.progress = PlaybackProgress(Duration.ZERO, 181.seconds, Duration.ZERO)
            advanceTimeBy(1.seconds)
            assertEquals(181.seconds, requests.single().second.duration)
            assertEquals(LyricsUiState.Synced(synced), model.state.value)
        }

    @Test
    fun aSongWithoutArtistOrDurationEndsInNotFoundWithoutAsking() =
        runTest {
            val model = model()
            model.setVisible(true)

            play(song(1, artists = emptyList()))
            runCurrent()
            assertEquals(LyricsUiState.NotFound, model.state.value)

            // No duration of its own, and the player does not learn it either.
            play(song(3, duration = null))
            // Past the wait for the player's duration; advanceUntilIdle would skip the model's backgroundScope.
            advanceTimeBy(6.seconds)
            runCurrent()
            assertEquals(LyricsUiState.NotFound, model.state.value)

            assertTrue(requests.isEmpty())
        }

    @Test
    fun aSongNotFoundForLackOfDurationIsLookedUpOnceItPlays() =
        runTest {
            play(song(1, duration = null))
            val model = model()
            model.setVisible(true)
            // Past the wait for the player's duration; advanceUntilIdle would skip the model's backgroundScope.
            advanceTimeBy(6.seconds)
            runCurrent()
            assertEquals(LyricsUiState.NotFound, model.state.value)

            player.progress = PlaybackProgress(Duration.ZERO, 181.seconds, Duration.ZERO)
            player.state.value = PlaybackState.Playing(song(1, duration = null))
            runCurrent()
            assertEquals(LyricsUiState.Synced(synced), model.state.value)
            assertEquals(181.seconds, requests.single().second.duration)

            // Lyrics that were truly not found are not asked again on play.
            answer = { null }
            play(song(2))
            runCurrent()
            player.state.value = PlaybackState.Paused(song(2))
            player.state.value = PlaybackState.Playing(song(2))
            runCurrent()
            assertEquals(2, requests.size)
        }

    @Test
    fun noLyricsIsNotFoundAndEachKindIsShown() =
        runTest {
            val model = model()
            model.setVisible(true)
            answer = { null }
            play(song(1))
            runCurrent()
            assertEquals(LyricsUiState.NotFound, model.state.value)

            answer = { Lyrics.Plain("words") }
            play(song(2))
            runCurrent()
            assertEquals(LyricsUiState.Plain("words"), model.state.value)

            answer = { Lyrics.Instrumental }
            play(song(3))
            runCurrent()
            assertEquals(LyricsUiState.Instrumental, model.state.value)
        }

    @Test
    fun aFailureCanBeRetried() =
        runTest {
            answer = { throw LyricsException.Http(503) }
            play(song(1))
            val model = model()
            model.setVisible(true)
            runCurrent()
            assertEquals(LyricsUiState.Failed("Http"), model.state.value)

            answer = { synced }
            model.retry()
            runCurrent()
            assertEquals(LyricsUiState.Synced(synced), model.state.value)
            assertEquals(2, requests.size)
        }

    @Test
    fun theLatestSongWinsWhenItChangesMidLoad() =
        runTest {
            val slow = CompletableDeferred<Lyrics?>()
            answer = { query -> if (query.title == "Song 1") slow.await() else Lyrics.Plain("second") }
            play(song(1))
            val model = model()
            model.setVisible(true)
            runCurrent()
            assertEquals(LyricsUiState.Loading, model.state.value)

            play(song(2))
            runCurrent()
            slow.complete(synced)
            runCurrent()
            assertEquals(LyricsUiState.Plain("second"), model.state.value)
        }

    @Test
    fun hidingKeepsTheLyricsAndShowingAgainDoesNotAskAgain() =
        runTest {
            play(song(1))
            val model = model()
            model.setVisible(true)
            runCurrent()
            model.setVisible(false)
            model.setVisible(true)
            runCurrent()
            assertEquals(LyricsUiState.Synced(synced), model.state.value)
            assertEquals(1, requests.size)
        }

    @Test
    fun aSongChangeWhileHiddenClearsTheOldLyricsWithoutLoading() =
        runTest {
            play(song(1))
            val model = model()
            model.setVisible(true)
            runCurrent()
            model.setVisible(false)
            play(song(2))
            runCurrent()
            assertEquals(LyricsUiState.Idle, model.state.value)
            assertEquals(1, requests.size)
        }

    @Test
    fun searchingAgainAsksPastTheMemoryOfNoLyrics() =
        runTest {
            answer = { null }
            play(song(1))
            val model = model()
            model.setVisible(true)
            runCurrent()
            assertEquals(LyricsUiState.NotFound, model.state.value)

            answer = { synced }
            model.searchAgain()
            runCurrent()
            assertEquals(LyricsUiState.Synced(synced), model.state.value)
            assertEquals(listOf(false, true), refreshes)
        }

    @Test
    fun searchingAgainOnlyActsOnNotFound() =
        runTest {
            play(song(1))
            val model = model()
            model.setVisible(true)
            runCurrent()
            model.searchAgain()
            runCurrent()
            assertEquals(listOf(false), refreshes)
        }

    @Test
    fun tappingALineSeeksToIt() =
        runTest {
            val model = model()
            model.seekTo(LyricLine(42.seconds, "two"))
            assertEquals(listOf("seekTo:42"), player.calls)
        }
}
