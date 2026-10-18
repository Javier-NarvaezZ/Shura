package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A song tapped on its own (a search result, a recently played song): it plays first, followed by its radio, the
 * related songs autoplay also uses ([QueueAutoplay.toAppend]: none already queued, a batch at most). A video with
 * a known album track ([trackOf]) starts the radio from the track, since a video's radio is all videos. The radio
 * is added only to the queue this started: if another song or a list starts meanwhile, nothing is added. Without
 * a network the song plays alone.
 */
class SongRadio(
    private val player: AudioPlayer,
    private val related: suspend (VideoId) -> List<Song>,
    private val scope: CoroutineScope,
    private val trackOf: (VideoId) -> VideoId? = { null },
) {
    private var job: Job? = null

    fun start(song: Song) {
        job?.cancel()
        player.setShuffle(false)
        player.play(song)
        val seed = if (song.isVideo) trackOf(song.videoId) ?: song.videoId else song.videoId
        job = scope.launch { relatedOrNull(seed)?.let { append(song, it) } }
    }

    private fun append(
        song: Song,
        found: List<Song>,
    ) {
        val queue = player.queue.value
        if (queue.items.firstOrNull()?.videoId != song.videoId) return
        val radio = QueueAutoplay.toAppend(found.filter { it.videoId != song.videoId }, queue)
        if (radio.isNotEmpty()) player.enqueue(radio)
    }

    // No radio is no error: the song still plays.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun relatedOrNull(seed: VideoId): List<Song>? =
        try {
            related(seed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
