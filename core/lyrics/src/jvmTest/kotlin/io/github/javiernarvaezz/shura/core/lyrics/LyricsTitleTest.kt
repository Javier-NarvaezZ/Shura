package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTitleTest {
    @Test
    fun removesTheTagsSeenOnRealFailures() {
        assertEquals("Qué Locura Enamorarme De Ti", LyricsTitle.clean("Qué Locura Enamorarme De Ti (Visualizer)"))
        assertEquals("Lluvia", LyricsTitle.clean("Lluvia (Visualizer)"))
        assertEquals("Apiádate de mi", LyricsTitle.clean("Apiádate de mi - Versión editada"))
        assertEquals(
            "Que Locura Enamorarme De Ti",
            LyricsTitle.clean("Que Locura Enamorarme De Ti (Remastered 2026)"),
        )
        assertEquals("Tu Me Quemas", LyricsTitle.clean("Tu Me Quemas (En Vivo)"))
    }

    @Test
    fun removesVideoAudioLiveAndRemasterTags() {
        listOf(
            "Song (Video Oficial)",
            "Song (Official Music Video)",
            "Song [Official Video]",
            "Song (Lyric Video)",
            "Song (Letra)",
            "Song (Con Letra)",
            "Song (Audio Oficial)",
            "Song (Official Audio)",
            "Song (Remasterizado)",
            "Song (2011 Remaster)",
            "Song - Remastered 2009",
            "Song (Live)",
            "Song - Live",
            "Song - En Vivo",
            "Song - Radio Edit",
            "Song (Explicit)",
            "Song (Video Oficial) (Remastered)",
        ).forEach { assertEquals("Song", LyricsTitle.clean(it), it) }
    }

    @Test
    fun removesFeaturedArtists() {
        assertEquals("Secondhand", LyricsTitle.clean("Secondhand (con Rema)"))
        assertEquals("Song", LyricsTitle.clean("Song (feat. Someone)"))
        assertEquals("Song", LyricsTitle.clean("Song [ft. Someone & Other]"))
        assertEquals("Song", LyricsTitle.clean("Song feat. Someone"))
    }

    @Test
    fun keepsPartsThatAreThePartOfTheTitle() {
        listOf(
            "En La Intimidad (Como Cada Viernes)",
            "Buscando Tu Besos",
            "Live Forever",
            "Video Killed the Radio Star",
            "Mi Delito",
            "Ven Devorame Otra Vez",
        ).forEach { assertEquals(it, LyricsTitle.clean(it)) }
    }

    @Test
    fun neverCleansATitleAway() {
        assertEquals("(Live)", LyricsTitle.clean("(Live)"))
        assertEquals("Visualizer", LyricsTitle.clean("Visualizer"))
    }

    @Test
    fun cleansChannelSuffixesFromArtists() {
        assertEquals("Eddie Santiago", LyricsTitle.cleanArtist("Eddie Santiago - Topic"))
        assertEquals("Eddie Santiago", LyricsTitle.cleanArtist("Eddie Santiago VEVO"))
        assertEquals("Eddie Santiago", LyricsTitle.cleanArtist("Eddie Santiago"))
    }
}
