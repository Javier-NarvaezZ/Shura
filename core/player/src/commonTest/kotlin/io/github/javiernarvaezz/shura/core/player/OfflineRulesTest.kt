package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineRulesTest {
    private val song = Song(VideoId("Fb3j_yuFh1s"), "DNA.", listOf(Artist("Kendrick Lamar")))
    private val video = song.copy(videoId = VideoId("NLZRYQMLDW4"), isVideo = true)
    private val gb = 1_000_000_000L

    @Test
    fun aSongIsKeptUnderItsVideoIdAndFormat() {
        assertEquals("Fb3j_yuFh1s:251", OfflineRules.cacheKey(song.videoId, 251))
    }

    @Test
    fun aSongIsDownloadedAsItIs() {
        assertEquals(song, OfflineRules.downloadable(song) { null })
    }

    @Test
    fun aVideoWithAKnownTrackIsDownloadedAsTheTrack() {
        val track = OfflineRules.downloadable(video) { if (it == video.videoId) song.videoId else null }

        assertEquals(song.videoId, track.videoId)
        assertFalse(track.isVideo)
        assertEquals(video.title, track.title)
    }

    @Test
    fun aVideoWithoutATrackIsDownloadedAsTheVideo() {
        assertEquals(video, OfflineRules.downloadable(video) { null })
    }

    @Test
    fun aDownloadMustFitTheSpaceLimit() {
        assertTrue(OfflineRules.fits(usedBytes = gb, incomingBytes = gb, limitBytes = 2 * gb))
        assertFalse(OfflineRules.fits(usedBytes = gb, incomingBytes = gb + 1, limitBytes = 2 * gb))
        // Without a limit, anything fits.
        assertTrue(OfflineRules.fits(usedBytes = 9 * gb, incomingBytes = gb, limitBytes = null))
        // An unknown size counts as nothing more, but a full limit still refuses.
        assertTrue(OfflineRules.fits(usedBytes = gb, incomingBytes = null, limitBytes = 2 * gb))
        assertFalse(OfflineRules.fits(usedBytes = 2 * gb, incomingBytes = null, limitBytes = 2 * gb))
    }

    @Test
    fun wiFiOnlyOrTheSystemsDataSaverWaitForAnUnmeteredNetwork() {
        assertEquals(OfflineNetwork.Unmetered, OfflineRules.network(wifiOnly = true, dataSaver = false))
        assertEquals(OfflineNetwork.Unmetered, OfflineRules.network(wifiOnly = false, dataSaver = true))
        assertEquals(OfflineNetwork.Any, OfflineRules.network(wifiOnly = false, dataSaver = false))
    }
}
