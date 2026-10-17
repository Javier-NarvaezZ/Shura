package io.github.javiernarvaezz.shura.core.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The account's library pages, from real responses sanitized on the device (Phase 3, E5). */
class AccountLibraryTest {
    private fun page(name: String) = Json.parseToJsonElement(Fixtures.read("account/$name.json"))

    @Test
    fun likedSongsAreTheLikedMusicPlaylist() {
        val songs = AccountLibrary.likedSongs(page("liked_songs")).tracks
        assertEquals(
            listOf("See You Again (con Kali Uchis)", "Like Him (con Lola Young)", "Not Like Us"),
            songs.map {
                it.title
            },
        )
        assertEquals(
            "Tyler, The Creator",
            songs
                .first()
                .artists
                .first()
                .name,
        )
        assertTrue(songs.all { it.duration != null })
    }

    @Test
    fun playlistsAreTheUsersOwnLeavingOutLikedMusicAndEpisodes() {
        val playlists = AccountLibrary.playlists(page("liked_playlists"))
        assertEquals(2, playlists.size)
        assertTrue(playlists.none { it.id == "VLLM" || it.id == "VLSE" })
        assertTrue(playlists.all { it.id.startsWith("VL") })
    }

    @Test
    fun savedAlbums() {
        val albums = AccountLibrary.albums(page("liked_albums"))
        assertEquals(1, albums.size)
        assertEquals("DAMN. COLLECTORS EDITION.", albums.single().title)
        assertEquals(
            "Kendrick Lamar",
            albums
                .single()
                .artists
                .single()
                .name,
        )
        assertEquals(2017, albums.single().year)
    }

    @Test
    fun followedArtists() {
        val artists = AccountLibrary.artists(page("library_artists"))
        assertEquals(listOf("Kendrick Lamar"), artists.map { it.name })
        assertTrue(artists.single().id.startsWith("UC"))
    }

    @Test
    fun anEmptyOrUnexpectedPageGivesNothing() {
        val empty = Json.parseToJsonElement("{}")
        assertTrue(AccountLibrary.likedSongs(empty).tracks.isEmpty())
        assertTrue(AccountLibrary.playlists(empty).isEmpty())
        assertTrue(AccountLibrary.albums(empty).isEmpty())
        assertTrue(AccountLibrary.artists(empty).isEmpty())
    }
}
