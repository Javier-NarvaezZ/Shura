package io.github.javiernarvaezz.shura.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.player.PlaybackProgress
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration

data class PlayerUiState(
    val song: Song? = null,
    val playback: PlaybackState = PlaybackState.Idle,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
) {
    /** The mini-player shows whenever there is a song to play or resume. */
    val isVisible: Boolean get() = song != null

    /** Pause is offered while playing and while loading, since playback will start. */
    val showsPause: Boolean get() = playback is PlaybackState.Playing || playback is PlaybackState.Loading

    val isLoading: Boolean get() = playback is PlaybackState.Loading

    val error: PlaybackError? get() = (playback as? PlaybackState.Failed)?.error

    /** Moving songs follows the queue order, which is only what plays when shuffle is off. */
    val canReorder: Boolean get() = !shuffle
}

/**
 * The song after the current one in play order; with repeat-all, the first one once the queue ends. Repeat-one
 * still skips to the next song when asked, but at the end of the queue there is none.
 */
internal fun upcomingSong(queue: QueueState): Song? =
    queue.next
        ?: queue
            .takeIf { it.repeat == RepeatMode.All }
            ?.playOrder
            ?.firstOrNull()
            ?.let(queue.items::getOrNull)
            ?.takeIf { it != queue.current }

/** A queue item as shown: [index] is its position in the queue, rows come in play order. */
data class QueueRow(
    val index: Int,
    val song: Song,
    val isCurrent: Boolean,
)

/** What [PlayerModel.undoRemove] needs to put a removed song back. */
data class RemovedSong(
    val song: Song,
    val index: Int,
    val queueSizeAfter: Int,
)

/** The mini-player's and the player's state and actions; plain so it can be tested without Android. */
class PlayerModel(
    private val player: AudioPlayer,
    scope: CoroutineScope,
) {
    // Eager: the actions read state.value even when no screen is collecting it.
    val state: StateFlow<PlayerUiState> =
        combine(player.state, player.queue) { playback, queue ->
            PlayerUiState(song = queue.current, playback = playback, shuffle = queue.shuffle, repeat = queue.repeat)
        }.stateIn(scope, SharingStarted.Eagerly, PlayerUiState())

    /** The queue in play order, and the actions on it. */
    val queue = QueueModel(player, scope) { state.value.canReorder }

    /** The cover of the song that plays next, loaded ahead so a track change does not depend on the network. */
    val upcomingArtworkUrl: StateFlow<String?> =
        player.queue
            .map { upcomingSong(it)?.thumbnailUrl }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Read on demand (per frame while a progress bar is visible). Until the player knows the duration (e.g. a
     * restored queue that is not prepared yet), the song's own duration stands in.
     */
    fun progress(): PlaybackProgress {
        val progress = player.progress()
        return if (progress.duration != null) progress else progress.copy(duration = state.value.song?.duration)
    }

    fun togglePlay() {
        when (state.value.playback) {
            is PlaybackState.Playing, is PlaybackState.Loading -> {
                player.pause()
            }

            is PlaybackState.Ended -> {
                player.seekTo(Duration.ZERO)
                player.resume()
            }

            else -> {
                player.resume()
            }
        }
    }

    fun next() = player.next()

    fun previous() = player.previous()

    /** Seeks to [fraction] (0..1) of the track; ignored while the duration is unknown. */
    fun seekToFraction(fraction: Float) {
        val duration = progress().duration ?: return
        player.seekTo(duration * fraction.coerceIn(0f, 1f).toDouble())
    }

    fun toggleShuffle() = player.setShuffle(!state.value.shuffle)

    /** Off → all → one → off. */
    fun cycleRepeat() =
        player.setRepeat(
            when (state.value.repeat) {
                RepeatMode.Off -> RepeatMode.All
                RepeatMode.All -> RepeatMode.One
                RepeatMode.One -> RepeatMode.Off
            },
        )

    fun retry() = player.retry()
}

/** The queue in play order, and its actions: play a row, remove (with undo) and move songs. */
class QueueModel(
    private val player: AudioPlayer,
    scope: CoroutineScope,
    private val canReorder: () -> Boolean,
) {
    /** Rows in play order (shuffled when shuffle is on). */
    val queueRows: StateFlow<List<QueueRow>> =
        player.queue
            .map { queue ->
                val order = queue.playOrder.takeIf { it.size == queue.items.size } ?: queue.items.indices.toList()
                order.map { QueueRow(it, queue.items[it], it == queue.currentIndex) }
            }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun playAt(index: Int) = player.skipTo(index)

    /** Removes the song at queue [index]; the result undoes it with [undoRemove]. */
    fun remove(index: Int): RemovedSong? {
        val items = player.queue.value.items
        val song = items.getOrNull(index) ?: return null
        player.remove(index)
        return RemovedSong(song, index, queueSizeAfter = items.size - 1)
    }

    /** Puts a removed song back at its place: added at the end, then moved there. */
    fun undoRemove(removed: RemovedSong) {
        player.enqueue(listOf(removed.song))
        player.move(removed.queueSizeAfter, removed.index)
    }

    /** Moves the song at queue [index] one place earlier (only without shuffle). */
    fun moveUp(index: Int) {
        if (canReorder() && index > 0) player.move(index, index - 1)
    }

    /** Moves the song at queue [index] one place later (only without shuffle). */
    fun moveDown(index: Int) {
        if (canReorder() && index < player.queue.value.items.lastIndex) player.move(index, index + 1)
    }
}

class PlayerViewModel(
    player: AudioPlayer,
) : ViewModel() {
    val model = PlayerModel(player, viewModelScope)
}
