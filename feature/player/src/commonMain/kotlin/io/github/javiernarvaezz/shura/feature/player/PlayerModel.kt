package io.github.javiernarvaezz.shura.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlaybackError
import io.github.javiernarvaezz.shura.core.player.PlaybackProgress
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
}

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

class PlayerViewModel(
    player: AudioPlayer,
) : ViewModel() {
    val model = PlayerModel(player, viewModelScope)
}
