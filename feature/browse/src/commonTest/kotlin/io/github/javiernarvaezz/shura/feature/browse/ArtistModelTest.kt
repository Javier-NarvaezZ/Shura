package io.github.javiernarvaezz.shura.feature.browse

import io.github.javiernarvaezz.shura.core.model.ArtistPage
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArtistModelTest {
    private val catalog = FakeMusicCatalog()
    private val player = FakeAudioPlayer()
    private val top = List(5) { testSong(it, "P$it") }
    private val page = ArtistPage(ArtistSummary("UC1", "Juanes"), topSongs = top)

    private fun TestScope.model(): ArtistModel {
        val model = ArtistModel(catalog, player, "UC1", backgroundScope, Random(SEED))
        runCurrent()
        return model
    }

    @Test
    fun loadsThePageAndRetriesAfterAFailure() =
        runTest {
            catalog.failure = IllegalStateException("offline")
            val model = model()
            assertIs<PageState.Failed>(model.state.value)

            catalog.failure = null
            catalog.artists["UC1"] = page
            model.retry()
            runCurrent()

            assertEquals(PageState.Loaded(page), model.state.value)
        }

    @Test
    fun playAndShuffleUseTheTopSongs() =
        runTest {
            catalog.artists["UC1"] = page
            val model = model()
            model.play()
            model.shuffle()
            model.playTrack(3)

            val start = Random(SEED).nextInt(top.size)
            assertEquals(
                listOf(
                    "shuffle:false",
                    "playQueue:P0,P1,P2,P3,P4@0",
                    "shuffle:true",
                    "playQueue:P0,P1,P2,P3,P4@$start",
                    "playQueue:P0,P1,P2,P3,P4@3",
                ),
                player.calls,
            )
        }

    private companion object {
        const val SEED = 7
    }

    @Test
    fun theArtistIsFollowedAndUnfollowed() =
        runTest {
            catalog.artists["UC1"] = page
            val library = FakeLibrary()
            val model = ArtistModel(catalog, player, "UC1", backgroundScope, Random(SEED), library)
            runCurrent()
            assertTrue(model.canFollow)

            model.toggleFollowed()
            runCurrent()
            assertTrue(model.followed.value)
            assertEquals(listOf(page.artist), library.artists.value)

            model.toggleFollowed()
            runCurrent()
            assertFalse(model.followed.value)
        }
}
