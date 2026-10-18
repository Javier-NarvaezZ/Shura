package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SqlLibraryTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) }
    private val database = ShuraDatabase(driver)
    private var clock = 1_000L

    private fun TestScope.library(): SqlLibrary {
        val library = SqlLibrary(database, backgroundScope, StandardTestDispatcher(testScheduler), now = { clock++ })
        runCurrent()
        return library
    }

    private fun song(n: Int) =
        Song(
            VideoId("song${n.toString().padStart(7, '0')}"),
            "Song $n",
            listOf(Artist("Artist $n", "UC$n"), Artist("Guest")),
            AlbumRef("Album $n", "MPREb_$n"),
            (180 + n).seconds,
            "https://lh3.googleusercontent.com/$n",
            isExplicit = n % 2 == 0,
            isVideo = n == 2,
        )

    private val album =
        AlbumSummary(
            "MPREb_1",
            "Album",
            listOf(Artist("Eddie Santiago", "UC1")),
            1986,
            "Álbum",
            "https://lh3.googleusercontent.com/a",
            isExplicit = true,
        )
    private val playlist =
        PlaylistSummary("VLPL1", "Salsa", "Someone", "https://lh3.googleusercontent.com/p", "50 canciones")
    private val artist = ArtistSummary("UC1", "Eddie Santiago", "https://yt3.ggpht.com/a", "1 M de suscriptores")

    @Test
    fun likedSongsComeBackWholeNewestFirst() =
        runTest {
            val library = library()
            library.setLiked(song(1), true)
            library.setLiked(song(2), true)
            runCurrent()
            assertEquals(listOf(song(2), song(1)), library.likedSongs.value)
            // A new launch reads the same.
            assertEquals(listOf(song(2), song(1)), library().likedSongs.value)
        }

    @Test
    fun likingAgainMovesASongToTheTopAndUnlikingRemovesIt() =
        runTest {
            val library = library()
            library.setLiked(song(1), true)
            library.setLiked(song(2), true)
            library.setLiked(song(1), true)
            runCurrent()
            assertEquals(listOf(song(1), song(2)), library.likedSongs.value)

            library.setLiked(song(1), false)
            runCurrent()
            assertEquals(listOf(song(2)), library.likedSongs.value)
        }

    @Test
    fun albumsPlaylistsAndArtistsAreSavedAndRemoved() =
        runTest {
            val library = library()
            library.setSaved(album, true)
            library.setSaved(playlist, true)
            library.setFollowed(artist, true)
            runCurrent()
            assertEquals(listOf(album), library.albums.value)
            // What a list needs to show it; the search subtitle is not kept.
            assertEquals(listOf(playlist.copy(subtitle = null)), library.playlists.value)
            assertEquals(listOf(artist.copy(subtitle = null)), library.artists.value)

            library.setSaved(album, false)
            library.setSaved(playlist, false)
            library.setFollowed(artist, false)
            runCurrent()
            assertEquals(emptyList(), library.albums.value)
            assertEquals(emptyList(), library.playlists.value)
            assertEquals(emptyList(), library.artists.value)
        }

    @Test
    fun anUnreadableRowIsSkippedNotFatal() =
        runTest {
            database.libraryQueries.like("bad", "Broken", "[not json", "[]", null, null, null, null, 0, 5, 0)
            val library = library()
            library.setLiked(song(1), true)
            runCurrent()
            assertEquals(listOf(song(1)), library.likedSongs.value)
        }
}
