package io.github.javiernarvaezz.shura.feature.browse

import io.github.javiernarvaezz.shura.core.model.AlbumPage
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.FakeMusicCatalog
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AlbumModelTest {
    private val catalog = FakeMusicCatalog()
    private val player = FakeAudioPlayer()
    private val tracks = List(4) { testSong(it, "T$it") }
    private val page = AlbumPage(AlbumSummary("MPREb_1", "Mi Sangre"), tracks)

    private fun TestScope.model(): AlbumModel {
        val model = AlbumModel(catalog, player, "MPREb_1", backgroundScope, Random(SEED))
        runCurrent()
        return model
    }

    @Test
    fun loadsThePageOnStart() =
        runTest {
            catalog.albums["MPREb_1"] = page

            assertEquals(PageState.Loaded(page), model().state.value)
            assertEquals(listOf("album:MPREb_1"), catalog.calls)
        }

    @Test
    fun aFailureIsShownAndRetryLoadsAgain() =
        runTest {
            catalog.failure = IllegalStateException("offline")
            val model = model()
            assertIs<PageState.Failed>(model.state.value)
            assertEquals("IllegalStateException", (model.state.value as PageState.Failed).type)

            catalog.failure = null
            catalog.albums["MPREb_1"] = page
            model.retry()
            runCurrent()

            assertEquals(PageState.Loaded(page), model.state.value)
        }

    @Test
    fun playStartsAtTheFirstTrackInOrder() =
        runTest {
            catalog.albums["MPREb_1"] = page
            model().play()

            assertEquals(listOf("shuffle:false", "playQueue:T0,T1,T2,T3@0"), player.calls)
        }

    @Test
    fun shuffleTurnsShuffleOnAndStartsAtARandomTrack() =
        runTest {
            catalog.albums["MPREb_1"] = page
            model().shuffle()

            val start = Random(SEED).nextInt(tracks.size)
            assertEquals(listOf("shuffle:true", "playQueue:T0,T1,T2,T3@$start"), player.calls)
        }

    @Test
    fun aTrackPlaysWithTheWholeAlbumQueued() =
        runTest {
            catalog.albums["MPREb_1"] = page
            val model = model()
            model.playTrack(2)
            model.playTrack(9)
            model.playNext(tracks[1])
            model.enqueue(tracks[3])

            assertEquals(listOf("playQueue:T0,T1,T2,T3@2", "playNext:T1", "enqueue:T3"), player.calls)
        }

    @Test
    fun nothingPlaysBeforeThePageLoads() =
        runTest {
            catalog.failure = IllegalStateException("offline")
            val model = model()
            model.play()
            model.shuffle()

            assertEquals(emptyList(), player.calls)
        }

    private companion object {
        const val SEED = 42
    }
}
