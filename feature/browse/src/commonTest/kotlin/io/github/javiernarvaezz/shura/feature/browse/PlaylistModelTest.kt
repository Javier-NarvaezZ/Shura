package io.github.javiernarvaezz.shura.feature.browse

import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.PlaylistPage
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.TrackPage
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.FakeLibrary
import io.github.javiernarvaezz.shura.core.testing.FakeMusicCatalog
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistModelTest {
    private val catalog = FakeMusicCatalog()
    private val player = FakeAudioPlayer()
    private val link = BrowseLink("VLPL1")

    private fun songs(
        from: Int,
        count: Int,
    ) = List(count) { testSong(from + it, "S${from + it}") }

    private fun page(
        tracks: Int,
        continuation: String? = null,
    ) = PlaylistPage(PlaylistSummary("VLPL1", "Mix"), songs(0, tracks), continuation = continuation)

    private fun TestScope.model(): PlaylistModel {
        val model = PlaylistModel(catalog, player, link, backgroundScope, Random(1))
        runCurrent()
        return model
    }

    private fun PlaylistModel.tracks() = (state.value as PageState.Loaded).page.tracks

    @Test
    fun moreTracksAreAppendedUntilThereAreNoMore() =
        runTest {
            catalog.playlists["VLPL1"] = page(2, continuation = "a")
            catalog.continuations["a"] = TrackPage(songs(2, 2), "b")
            catalog.continuations["b"] = TrackPage(songs(4, 1))
            val model = model()

            model.loadMore()
            model.loadMore() // Ignored while the first is in flight.
            runCurrent()
            model.loadMore()
            runCurrent()
            model.loadMore() // Nothing left.
            runCurrent()

            assertEquals(listOf("S0", "S1", "S2", "S3", "S4"), model.tracks().map { it.title })
            assertEquals(listOf("playlist:VLPL1", "continuation:a", "continuation:b"), catalog.calls)
        }

    @Test
    fun aFailedPageKeepsTheTracksAndCanBeRetried() =
        runTest {
            catalog.playlists["VLPL1"] = page(2, continuation = "a")
            val model = model()

            model.loadMore() // No answer for "a": fails.
            runCurrent()
            assertTrue(model.moreFailed.value)
            assertEquals(2, model.tracks().size)

            catalog.continuations["a"] = TrackPage(songs(2, 1))
            model.loadMore()
            runCurrent()
            assertFalse(model.moreFailed.value)
            assertEquals(3, model.tracks().size)
        }

    @Test
    fun playLoadsTheRestFirstAndThenPlaysEverything() =
        runTest {
            catalog.playlists["VLPL1"] = page(2, continuation = "a")
            catalog.continuations["a"] = TrackPage(songs(2, 2))
            val model = model()

            model.play()
            assertTrue(model.preparing.value)
            runCurrent()

            assertFalse(model.preparing.value)
            assertEquals(listOf("shuffle:false", "playQueue:S0,S1,S2,S3@0"), player.calls)
        }

    @Test
    fun aFailingPageWhilePreparingPlaysWhatIsLoaded() =
        runTest {
            catalog.playlists["VLPL1"] = page(2, continuation = "a")
            val model = model()

            model.shuffle()
            runCurrent()

            assertEquals("shuffle:true", player.calls.first())
            assertTrue(player.calls.last().startsWith("playQueue:S0,S1@"))
        }

    @Test
    fun preparingStopsAtTheCap() =
        runTest {
            catalog.playlists["VLPL1"] = page(PlaylistModel.MAX_TRACKS - 1, continuation = "a")
            catalog.continuations["a"] = TrackPage(songs(PlaylistModel.MAX_TRACKS - 1, 2), "b")
            val model = model()

            model.play()
            runCurrent()

            assertEquals(listOf("playlist:VLPL1", "continuation:a"), catalog.calls)
            assertEquals(
                PlaylistModel.MAX_TRACKS,
                player.calls
                    .last()
                    .substringAfter(":")
                    .substringBefore("@")
                    .split(",")
                    .size,
            )
        }

    @Test
    fun thePlaylistIsSavedOnceLoaded() =
        runTest {
            catalog.playlists["VLPL1"] = page(3)
            val library = FakeLibrary()
            val model = PlaylistModel(catalog, player, link, backgroundScope, Random(1), library)
            runCurrent()
            assertFalse(model.saved.value)

            model.toggleSaved()
            runCurrent()
            assertTrue(model.saved.value)
            assertEquals(listOf(PlaylistSummary("VLPL1", "Mix")), library.playlists.value)
        }
}
