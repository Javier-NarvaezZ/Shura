package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.player.PlaybackProgress
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SearchControllerTest {
    private val song = Song(VideoId("7fwUH0oRmkQ"), "La Camisa Negra", listOf(Artist("Juanes")))

    private class FakePlayer : AudioPlayer {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)

        override val queue = MutableStateFlow(QueueState())

        override fun play(song: Song) {
            calls += "play:${song.videoId}"
        }

        override fun playQueue(
            songs: List<Song>,
            startIndex: Int,
        ) {
            calls += "playQueue:${songs.size}@$startIndex"
        }

        override fun next() {
            calls += "next"
        }

        override fun previous() {
            calls += "previous"
        }

        override fun skipTo(index: Int) {
            calls += "skipTo:$index"
        }

        override fun setShuffle(enabled: Boolean) {
            calls += "shuffle:$enabled"
        }

        override fun setRepeat(mode: RepeatMode) {
            calls += "repeat:$mode"
        }

        override fun playNext(songs: List<Song>) {
            calls += "playNext:${songs.size}"
        }

        override fun enqueue(songs: List<Song>) {
            calls += "enqueue:${songs.size}"
        }

        override fun remove(index: Int) {
            calls += "remove:$index"
        }

        override fun move(
            from: Int,
            to: Int,
        ) {
            calls += "move:$from>$to"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun resume() {
            calls += "resume"
        }

        override fun seekBy(offset: Duration) {
            calls += "seek:${offset.inWholeSeconds}"
        }

        override fun seekTo(position: Duration) {
            calls += "seekTo:${position.inWholeSeconds}"
        }

        override fun progress(): PlaybackProgress = PlaybackProgress.ZERO

        override fun retry() {
            calls += "retry"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun release() {
            calls += "release"
        }
    }

    private val player = FakePlayer()

    private fun TestScope.controller(search: suspend (String) -> List<Song>) =
        SearchController(search, player, backgroundScope)

    @Test
    fun typingOrSubmittingAQuerySignalsPlaybackIntent() =
        runTest {
            var intents = 0
            val controller = SearchController({ emptyList() }, player, backgroundScope) { intents++ }

            controller.onQueryChange("   ")
            assertEquals(0, intents)
            controller.onQueryChange("j")
            assertEquals(1, intents)
            controller.submit()
            assertEquals(2, intents)
        }

    @Test
    fun blankQueryDoesNotSignalPlaybackIntent() =
        runTest {
            var intents = 0
            val controller = SearchController({ emptyList() }, player, backgroundScope) { intents++ }

            controller.onQueryChange("")
            controller.submit()

            assertEquals(0, intents)
        }

    @Test
    fun blankQueryDoesNotSearch() =
        runTest {
            var searched = false
            val controller =
                controller {
                    searched = true
                    emptyList()
                }

            controller.onQueryChange("   ")
            controller.submit()
            runCurrent()

            assertEquals(false, searched)
            assertEquals(SearchStatus.Idle, controller.state.value.status)
        }

    @Test
    fun successfulSearchShowsResults() =
        runTest {
            val pending = CompletableDeferred<List<Song>>()
            val controller = controller { pending.await() }

            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()
            assertEquals(SearchStatus.Loading, controller.state.value.status)

            pending.complete(listOf(song))
            runCurrent()
            assertEquals(SearchStatus.Done, controller.state.value.status)
            assertEquals(listOf(song), controller.state.value.results)
        }

    @Test
    fun emptyResultsAreReported() =
        runTest {
            val controller = controller { emptyList() }

            controller.onQueryChange("qzxv")
            controller.submit()
            runCurrent()

            assertEquals(SearchStatus.Empty, controller.state.value.status)
        }

    @Test
    fun searchFailureIsVisibleAndRetryable() =
        runTest {
            var attempts = 0
            val controller =
                controller {
                    attempts++
                    if (attempts == 1) error("network down") else listOf(song)
                }

            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()
            assertEquals(SearchStatus.Failed, controller.state.value.status)

            controller.submit()
            runCurrent()
            assertEquals(SearchStatus.Done, controller.state.value.status)
            assertEquals(2, attempts)
        }

    @Test
    fun newSearchKeepsOnlyTheLatestResults() =
        runTest {
            val first = CompletableDeferred<List<Song>>()
            val controller = controller { query -> if (query == "a") first.await() else listOf(song) }

            controller.onQueryChange("a")
            controller.submit()
            runCurrent()
            controller.onQueryChange("b")
            controller.submit()
            runCurrent()
            first.complete(emptyList())
            runCurrent()

            assertEquals(listOf(song), controller.state.value.results)
        }

    @Test
    fun playbackActionsGoToThePlayer() =
        runTest {
            val controller = controller { emptyList() }

            controller.play(song)
            player.state.value = PlaybackState.Playing(song)
            controller.togglePause()
            player.state.value = PlaybackState.Paused(song)
            controller.togglePause()
            controller.seekBy(30.seconds)
            controller.seekBy((-10).seconds)
            player.state.value = PlaybackState.Failed(song, PlaybackError.Stream(StreamFailure.NoPlayableStream))
            controller.retryPlayback()

            assertEquals(listOf("play:7fwUH0oRmkQ", "pause", "resume", "seek:30", "seek:-10", "retry"), player.calls)
            assertTrue(controller.playback.value is PlaybackState.Failed)
        }

    @Test
    fun tappingAResultPlaysTheResultsAsAQueueFromThatSong() =
        runTest {
            val other = Song(VideoId("dQw4w9WgXcQ"), "Other", listOf(Artist("X")))
            val third = Song(VideoId("kJQP7kiw5Fk"), "Third", listOf(Artist("Y")))
            val controller = controller { listOf(other, song, third) }
            controller.onQueryChange("juanes")
            controller.submit()
            runCurrent()

            controller.play(song)

            assertEquals(listOf("playQueue:3@1"), player.calls)
        }

    @Test
    fun queueControlsGoToThePlayer() =
        runTest {
            val controller = controller { emptyList() }

            controller.playNext(song)
            controller.next()
            controller.previous()
            controller.toggleShuffle()
            player.queue.value = QueueState(shuffle = true)
            controller.toggleShuffle()
            controller.cycleRepeat()
            player.queue.value = QueueState(repeat = RepeatMode.All)
            controller.cycleRepeat()
            player.queue.value = QueueState(repeat = RepeatMode.One)
            controller.cycleRepeat()

            assertEquals(
                listOf(
                    "playNext:1",
                    "next",
                    "previous",
                    "shuffle:true",
                    "shuffle:false",
                    "repeat:All",
                    "repeat:One",
                    "repeat:Off",
                ),
                player.calls,
            )
        }
}
