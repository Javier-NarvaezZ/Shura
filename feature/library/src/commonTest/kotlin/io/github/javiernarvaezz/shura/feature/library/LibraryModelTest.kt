package io.github.javiernarvaezz.shura.feature.library

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.testing.FakeAudioPlayer
import io.github.javiernarvaezz.shura.core.testing.FakeLibrary
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryModelTest {
    private val library = FakeLibrary()
    private val player = FakeAudioPlayer()
    private val songs = List(4) { testSong(it, "S$it") }

    @Test
    fun anEmptyLibrarySaysSo() =
        runTest {
            val model = LibraryModel(library, backgroundScope)
            runCurrent()
            assertTrue(model.state.value.isEmpty)
        }

    @Test
    fun theTabFollowsTheLibrary() =
        runTest {
            val model = LibraryModel(library, backgroundScope)
            songs.forEach { library.setLiked(it, true) }
            library.setSaved(AlbumSummary("MPREb_1", "Album"), true)
            library.setSaved(PlaylistSummary("VLPL1", "Mix"), true)
            library.setFollowed(ArtistSummary("UC1", "Artist"), true)
            runCurrent()
            val state = model.state.value
            assertFalse(state.isEmpty)
            assertEquals(4, state.likedCount)
            assertEquals(listOf("MPREb_1"), state.albums.map { it.id })
            assertEquals(listOf("VLPL1"), state.playlists.map { it.id })
            assertEquals(listOf("UC1"), state.artists.map { it.id })
        }

    @Test
    fun likedSongsPlayInOrderShuffledOrFromOne() =
        runTest {
            songs.forEach { library.setLiked(it, true) }
            val liked = library.likedSongs.value
            val model = LikedSongsModel(library, player, backgroundScope, Random(1))
            runCurrent()

            model.play()
            model.playTrack(2)
            model.shuffle()
            val names = liked.joinToString(",") { it.title }
            assertEquals("shuffle:false", player.calls[0])
            assertEquals("playQueue:$names@0", player.calls[1])
            assertEquals("playQueue:$names@2", player.calls[2])
            assertEquals("shuffle:true", player.calls[3])
            assertTrue(player.calls[4].startsWith("playQueue:$names@"))
        }

    @Test
    fun unlikingFromTheListRemovesTheSong() =
        runTest {
            songs.forEach { library.setLiked(it, true) }
            val model = LikedSongsModel(library, player, backgroundScope, Random(1))
            runCurrent()
            model.likes.toggle(songs[0])
            runCurrent()
            assertEquals(3, model.songs.value.size)
            assertFalse(songs[0] in model.songs.value)
        }

    @Test
    fun withAnAccountTheLikedSongsJoinWithoutDuplicates() =
        runTest {
            library.setLiked(songs[0], true)
            library.setLiked(songs[2], true)
            val account = MutableStateFlow(listOf(songs[1], songs[2]))
            val model = LikedSongsModel(library, player, backgroundScope, Random(1), accountLiked = account)
            val tab = LibraryModel(library, backgroundScope, accountLiked = account)
            runCurrent()
            assertEquals(listOf(songs[1], songs[2], songs[0]), model.songs.value)
            assertEquals(3, tab.state.value.likedCount)
        }
}
