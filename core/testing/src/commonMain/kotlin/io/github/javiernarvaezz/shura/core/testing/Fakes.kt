package io.github.javiernarvaezz.shura.core.testing

import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlayHistory
import io.github.javiernarvaezz.shura.core.player.PlaybackProgress
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/** A song with a valid, distinct video id per [n] (0..999). */
fun testSong(
    n: Int,
    title: String = "Song $n",
): Song = Song(VideoId("test${n.toString().padStart(ID_DIGITS, '0')}"), title, listOf(Artist("Artist $n")))

// "test" plus 7 digits: the 11 characters of a video id.
private const val ID_DIGITS = 7

/** Records every command as a string in [calls]; [state] and [queue] are set by the test. */
class FakeAudioPlayer : AudioPlayer {
    val calls = mutableListOf<String>()
    override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val queue = MutableStateFlow(QueueState())
    var progress = PlaybackProgress.ZERO

    override fun play(song: Song) {
        calls += "play:${song.title}"
    }

    override fun pause() {
        calls += "pause"
    }

    override fun resume() {
        calls += "resume"
    }

    override fun seekBy(offset: Duration) {
        calls += "seekBy:${offset.inWholeSeconds}"
    }

    override fun seekTo(position: Duration) {
        calls += "seekTo:${position.inWholeSeconds}"
    }

    override fun progress(): PlaybackProgress = progress

    override fun retry() {
        calls += "retry"
    }

    override fun stop() {
        calls += "stop"
    }

    override fun release() {
        calls += "release"
    }

    override fun playQueue(
        songs: List<Song>,
        startIndex: Int,
    ) {
        calls += "playQueue:${songs.joinToString(",") { it.title }}@$startIndex"
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
        calls += "playNext:${songs.joinToString(",") { it.title }}"
    }

    override fun enqueue(songs: List<Song>) {
        calls += "enqueue:${songs.joinToString(",") { it.title }}"
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
}

/** History that emits nothing until [songs] is set (like a database that has not answered yet). */
class FakePlayHistory : PlayHistory {
    val songs = MutableStateFlow<List<Song>?>(null)

    override fun recent(limit: Int): Flow<List<Song>> = songs.filterNotNull().map { it.take(limit) }
}
