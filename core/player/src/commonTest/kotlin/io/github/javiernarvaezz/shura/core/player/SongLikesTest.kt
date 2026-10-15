package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongLikesTest {
    private val song = Song(VideoId("abcdefghijk"), "Song", listOf(Artist("Artist")))

    /** Just enough of a library: liked songs in memory. */
    private class MemoryLibrary : Library {
        override val likedSongs = MutableStateFlow<List<Song>>(emptyList())
        override val albums = MutableStateFlow<List<AlbumSummary>>(emptyList())
        override val playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
        override val artists = MutableStateFlow<List<ArtistSummary>>(emptyList())

        override fun setLiked(
            song: Song,
            liked: Boolean,
        ) {
            likedSongs.value = if (liked) listOf(song) + likedSongs.value else likedSongs.value - song
        }

        override fun setSaved(
            album: AlbumSummary,
            saved: Boolean,
        ) = Unit

        override fun setSaved(
            playlist: PlaylistSummary,
            saved: Boolean,
        ) = Unit

        override fun setFollowed(
            artist: ArtistSummary,
            followed: Boolean,
        ) = Unit
    }

    @Test
    fun togglingLikesAndUnlikes() =
        runTest {
            val likes = SongLikes(MemoryLibrary(), backgroundScope)
            likes.toggle(song)
            runCurrent()
            assertEquals(setOf(song.videoId), likes.likedIds.value)
            likes.toggle(song)
            runCurrent()
            assertEquals(emptySet(), likes.likedIds.value)
        }

    @Test
    fun withoutALibraryNothingIsLikedAndToggleDoesNothing() =
        runTest {
            val likes = SongLikes(null, backgroundScope)
            assertFalse(likes.isAvailable)
            likes.toggle(song)
            assertTrue(likes.likedIds.value.isEmpty())
        }
}
