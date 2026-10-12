package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LyricsQueryTest {
    @Test
    fun trimsFieldsAndTreatsBlankAlbumAsUnknown() {
        val query = LyricsQuery(" Title ", " Artist ", "  ", 200.seconds)
        assertEquals("Title", query.title)
        assertEquals("Artist", query.artist)
        assertNull(query.album)
    }

    @Test
    fun rejectsBlankTitleArtistAndNonPositiveDuration() {
        assertFailsWith<IllegalArgumentException> { LyricsQuery(" ", "Artist", null, 200.seconds) }
        assertFailsWith<IllegalArgumentException> { LyricsQuery("Title", "", null, 200.seconds) }
        assertFailsWith<IllegalArgumentException> { LyricsQuery("Title", "Artist", null, Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { LyricsQuery("Title", "Artist", null, Duration.INFINITE) }
    }

    @Test
    fun validationMessagesDoNotContainTheSong() {
        val error = assertFailsWith<IllegalArgumentException> { LyricsQuery("Title", "  ", "Secret Album", 1.seconds) }
        assertEquals("Artist must not be blank", error.message)
    }
}
