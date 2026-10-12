package io.github.javiernarvaezz.shura.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CatalogTest {
    private fun song(
        n: Int,
        seconds: Int?,
    ) = Song(VideoId("abcdefghi%02d".format(n)), "Song $n", emptyList(), duration = seconds?.seconds)

    private val album = AlbumSummary("MPREb_1", "Mi Sangre", listOf(Artist("Juanes", "UC1")), year = 2004)

    @Test
    fun summariesNeedAnIdAndATitle() {
        assertFailsWith<IllegalArgumentException> { AlbumSummary(" ", "Mi Sangre") }
        assertFailsWith<IllegalArgumentException> { AlbumSummary("MPREb_1", "") }
        assertFailsWith<IllegalArgumentException> { ArtistSummary("UC1", " ") }
        assertFailsWith<IllegalArgumentException> { PlaylistSummary("", "Mix") }
        assertFailsWith<IllegalArgumentException> { BrowseLink(" ") }
    }

    @Test
    fun anAlbumLastsAsLongAsItsKnownTracks() {
        val page = AlbumPage(album, listOf(song(1, 200), song(2, null), song(3, 100)))

        assertEquals(300.seconds, page.knownDuration)
        assertNull(AlbumPage(album, listOf(song(1, null))).knownDuration)
        assertEquals("Juanes", page.album.artistNames)
    }

    @Test
    fun aPlaylistHasMoreWhileItHasAContinuation() {
        val playlist = PlaylistSummary("VLPL1", "Mix")

        assertTrue(PlaylistPage(playlist, listOf(song(1, 1)), continuation = "tok").hasMore)
        assertFalse(PlaylistPage(playlist, listOf(song(1, 1))).hasMore)
        assertTrue(TrackPage(emptyList(), "tok").hasMore)
    }
}
