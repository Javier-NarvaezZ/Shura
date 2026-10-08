package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackMappingTest {
    private val song = Song(VideoId("7fwUH0oRmkQ"), "La Camisa Negra", listOf(Artist("Juanes")))

    @Test
    fun everyPlaybackErrorSurvivesTheCodec() {
        val errors =
            StreamFailure.entries.map { PlaybackError.Stream(it) } +
                listOf(
                    PlaybackError.Http(403),
                    PlaybackError.Http(500),
                    PlaybackError.Network,
                    PlaybackError.Decoding,
                    PlaybackError.Unknown,
                )

        errors.forEach { assertEquals(it, PlaybackErrorCodec.decode(PlaybackErrorCodec.encode(it)), it.toString()) }
    }

    @Test
    fun codesAreShortAndCarryNoFreeText() {
        assertEquals(
            "stream:TokenUnavailable",
            PlaybackErrorCodec.encode(PlaybackError.Stream(StreamFailure.TokenUnavailable)),
        )
        assertEquals("http:403", PlaybackErrorCodec.encode(PlaybackError.Http(403)))
        assertEquals("network", PlaybackErrorCodec.encode(PlaybackError.Network))
    }

    @Test
    fun missingOrUnknownCodesDecodeAsUnknown() {
        listOf(null, "", "stream:Nope", "http:abc", "http:", "something", "https://example.com").forEach {
            assertEquals(PlaybackError.Unknown, PlaybackErrorCodec.decode(it), "$it")
        }
    }

    @Test
    fun errorWinsOverEveryOtherState() {
        val error = PlaybackError.Network

        assertEquals(
            PlaybackState.Failed(song, error),
            playbackStateOf(song, isPlaying = true, phase = PlayerPhase.Ready, error = error),
        )
    }

    @Test
    fun mapsPlayerPhasesLikeTheSpikeListener() {
        assertEquals(PlaybackState.Playing(song), playbackStateOf(song, true, PlayerPhase.Ready, null))
        assertEquals(PlaybackState.Paused(song), playbackStateOf(song, false, PlayerPhase.Ready, null))
        assertEquals(PlaybackState.Loading(song), playbackStateOf(song, false, PlayerPhase.Buffering, null))
        assertEquals(PlaybackState.Ended(song), playbackStateOf(song, false, PlayerPhase.Ended, null))
        assertEquals(PlaybackState.Loading(song), playbackStateOf(song, false, PlayerPhase.Idle, null))
    }

    @Test
    fun aRestoredQueueThatIsNotPreparedShowsAsPaused() {
        assertEquals(
            PlaybackState.Paused(song),
            playbackStateOf(song, isPlaying = false, phase = PlayerPhase.Idle, error = null, playWhenReady = false),
        )
    }

    @Test
    fun noSongMeansIdle() {
        assertEquals(PlaybackState.Idle, playbackStateOf(null, true, PlayerPhase.Ready, PlaybackError.Network))
    }

    @Test
    fun mediaIdsMustBeVideoIds() {
        assertEquals(VideoId("7fwUH0oRmkQ"), videoIdOf("7fwUH0oRmkQ"))
        listOf(
            "",
            "7fwUH0oRmk",
            "7fwUH0oRmkQQ",
            "7fwUH0oRm!Q",
            "https://evil.example/a.mp3",
            "shura://stream/7fwUH0oRmkQ",
        ).forEach { assertNull(videoIdOf(it), it) }
    }

    @Test
    fun gateRunsQueuedCommandsInOrderOnceConnected() {
        val gate = CommandGate<MutableList<String>>()
        gate.run { it += "a" }
        gate.run { it += "b" }
        val target = mutableListOf<String>()

        gate.connect(target)
        gate.run { it += "c" }

        assertEquals(listOf("a", "b", "c"), target)
    }

    @Test
    fun gateDropsCommandsAfterRelease() {
        val gate = CommandGate<MutableList<String>>()
        gate.run { it += "queued" }
        gate.release()
        val target = mutableListOf<String>()

        gate.connect(target)
        gate.run { it += "late" }

        assertTrue(target.isEmpty())
    }

    @Test
    fun gateReadsReturnNullUntilConnectedAndAfterRelease() {
        val gate = CommandGate<MutableList<String>>()
        assertNull(gate.read { it.size })

        gate.connect(mutableListOf("a", "b"))
        assertEquals(2, gate.read { it.size })

        gate.release()
        assertNull(gate.read { it.size })
    }

    @Test
    fun gateReadsAreNotQueuedAsCommands() {
        val gate = CommandGate<MutableList<String>>()
        gate.read { it += "read" }
        val target = mutableListOf<String>()

        gate.connect(target)

        assertTrue(target.isEmpty())
    }
}
