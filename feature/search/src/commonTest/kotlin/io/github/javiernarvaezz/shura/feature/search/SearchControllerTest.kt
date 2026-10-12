package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.FakeMusicCatalog
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchControllerTest {
    private val song = testSong(1, "La Camisa Negra")
    private val player = FakeAudioPlayer()

    private fun TestScope.controller(search: suspend (String) -> List<Song>) =
        SearchController(SearchSources(songs = search), player, backgroundScope)

    @Test
    fun typingOrSubmittingAQuerySignalsPlaybackIntent() =
        runTest {
            var intents = 0
            val controller =
                SearchController(SearchSources(songs = { emptyList() }), player, backgroundScope) { intents++ }

            controller.onQueryChange("   ")
            assertEquals(0, intents)
            controller.onQueryChange("j")
            assertEquals(1, intents)
            controller.submit()
            assertEquals(2, intents)
        }

    @Test
    fun blankQueryDoesNotSignalPlaybackIntent() =
        runTest {
            var intents = 0
            val controller =
                SearchController(SearchSources(songs = { emptyList() }), player, backgroundScope) { intents++ }

            controller.onQueryChange("")
            controller.submit()

            assertEquals(0, intents)
        }

    @Test
    fun blankQueryDoesNotSearch() =
        runTest {
            var searched = false
            val controller =
                controller {
                    searched = true
                    emptyList()
                }

            controller.onQueryChange("   ")
            controller.submit()
            runCurrent()

            assertEquals(false, searched)
            assertEquals(SearchStatus.Idle, controller.state.value.status)
        }

    @Test
    fun successfulSearchShowsResults() =
        runTest {
            val pending = CompletableDeferred<List<Song>>()
            val controller = controller { pending.await() }

            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()
            assertEquals(SearchStatus.Loading, controller.state.value.status)

            pending.complete(listOf(song))
            runCurrent()
            assertEquals(SearchStatus.Done, controller.state.value.status)
            assertEquals(listOf(song), controller.state.value.results)
        }

    @Test
    fun emptyResultsAreReported() =
        runTest {
            val controller = controller { emptyList() }

            controller.onQueryChange("qzxv")
            controller.submit()
            runCurrent()

            assertEquals(SearchStatus.Empty, controller.state.value.status)
        }

    @Test
    fun searchFailureIsVisibleAndRetryable() =
        runTest {
            var attempts = 0
            val controller =
                controller {
                    attempts++
                    if (attempts == 1) error("network down") else listOf(song)
                }

            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()
            assertEquals(SearchStatus.Failed, controller.state.value.status)

            controller.submit()
            runCurrent()
            assertEquals(SearchStatus.Done, controller.state.value.status)
            assertEquals(2, attempts)
        }

    @Test
    fun newSearchKeepsOnlyTheLatestResults() =
        runTest {
            val first = CompletableDeferred<List<Song>>()
            val controller = controller { query -> if (query == "a") first.await() else listOf(song) }

            controller.onQueryChange("a")
            controller.submit()
            runCurrent()
            controller.onQueryChange("b")
            controller.submit()
            runCurrent()
            first.complete(emptyList())
            runCurrent()

            assertEquals(listOf(song), controller.state.value.results)
        }

    @Test
    fun tappingAResultPlaysTheResultsAsAQueueFromThatSong() =
        runTest {
            val controller = controller { listOf(testSong(2, "Other"), song, testSong(3, "Third")) }
            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()

            controller.play(song)

            assertEquals(listOf("playQueue:Other,La Camisa Negra,Third@1"), player.calls)
        }

    @Test
    fun aSongOutsideTheResultsPlaysAlone() =
        runTest {
            val controller = controller { emptyList() }

            controller.play(song)

            assertEquals(listOf("play:La Camisa Negra"), player.calls)
        }

    @Test
    fun rowActionsQueueTheSong() =
        runTest {
            val controller = controller { emptyList() }

            controller.playNext(song)
            controller.enqueue(song)

            assertEquals(listOf("playNext:La Camisa Negra", "enqueue:La Camisa Negra"), player.calls)
        }

    @Test
    fun anotherKindIsSearchedOnceWhenChosen() =
        runTest {
            val catalog = FakeMusicCatalog()
            catalog.songs["juanes"] = listOf(song)
            catalog.albumResults["juanes"] = listOf(AlbumSummary("MPREb_1", "Mi Sangre"))
            val controller = SearchController(SearchSources.of(catalog), player, backgroundScope)

            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()
            controller.selectKind(SearchKind.Albums)
            runCurrent()
            controller.selectKind(SearchKind.Songs)
            controller.selectKind(SearchKind.Albums)
            runCurrent()

            assertEquals(SearchKind.Albums, controller.state.value.kind)
            assertEquals(SearchStatus.Done, controller.state.value.status)
            assertEquals(
                "Mi Sangre",
                controller.state.value.albums
                    .single()
                    .title,
            )
            assertEquals(listOf("searchSongs:juanes", "searchAlbums:juanes"), catalog.calls)
        }

    @Test
    fun aNewQuerySearchesTheChosenKindAndForgetsTheOthers() =
        runTest {
            val catalog = FakeMusicCatalog()
            catalog.artistResults["juanes"] = listOf(ArtistSummary("UC1", "Juanes"))
            catalog.artistResults["shakira"] = listOf(ArtistSummary("UC2", "Shakira"))
            catalog.songs["shakira"] = emptyList()
            val controller = SearchController(SearchSources.of(catalog), player, backgroundScope)
            controller.selectKind(SearchKind.Artists)

            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()
            controller.onQueryChange("shakira")
            controller.submit()
            runCurrent()
            controller.selectKind(SearchKind.Songs)
            runCurrent()

            assertEquals(SearchStatus.Empty, controller.state.value.status)
            assertEquals(
                "Shakira",
                controller.state.value.artists
                    .single()
                    .name,
            )
            assertEquals(
                listOf("searchArtists:juanes", "searchArtists:shakira", "searchSongs:shakira"),
                catalog.calls,
            )
        }

    @Test
    fun aKindThatFailedIsRetriedAlone() =
        runTest {
            val catalog = FakeMusicCatalog()
            catalog.songs["juanes"] = listOf(song)
            val controller = SearchController(SearchSources.of(catalog), player, backgroundScope)
            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()

            controller.selectKind(SearchKind.Playlists) // No answer: fails.
            runCurrent()
            assertEquals(SearchStatus.Failed, controller.state.value.status)

            catalog.playlistResults["juanes"] = listOf(PlaylistSummary("VLPL1", "Mix"))
            controller.retry()
            runCurrent()

            assertEquals(SearchStatus.Done, controller.state.value.status)
            assertEquals(listOf(song), controller.state.value.results)
            assertEquals(3, catalog.calls.size)
        }
}
