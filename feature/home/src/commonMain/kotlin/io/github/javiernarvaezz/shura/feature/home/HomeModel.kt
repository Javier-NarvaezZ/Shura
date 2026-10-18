package io.github.javiernarvaezz.shura.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlayHistory
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import io.github.javiernarvaezz.shura.core.player.SongRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class DayPart { Morning, Afternoon, Night }

fun dayPartOf(hour: Int): DayPart =
    when (hour) {
        in MORNING -> DayPart.Morning
        in AFTERNOON -> DayPart.Afternoon
        else -> DayPart.Night
    }

data class HomeUiState(
    val dayPart: DayPart,
    /** "Continue listening": the current (or restored) queue item. */
    val current: Song? = null,
    val isPlaying: Boolean = false,
    /** The queue played to its end; play starts it again from the first song. */
    val queueEnded: Boolean = false,
    /** Recently played, most recent first, without the current song. */
    val recent: List<Song> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && current == null && recent.isEmpty()
}

/** Home's state and actions; plain so it can be tested without Android. [hour] is the local hour (0..23). */
class HomeModel(
    private val player: AudioPlayer,
    history: PlayHistory,
    hour: () -> Int,
    scope: CoroutineScope,
    related: suspend (VideoId) -> List<Song> = { emptyList() },
    trackOf: (VideoId) -> VideoId? = { null },
) {
    private val radio = SongRadio(player, related, scope, trackOf)

    // Eager: the actions read state.value even when no screen is collecting it.
    val state: StateFlow<HomeUiState> =
        combine(player.queue, player.state, history.recent(RECENT_LIMIT)) { queue, playback, recent ->
            val current = queue.current
            HomeUiState(
                dayPart = dayPartOf(hour()),
                current = current,
                isPlaying = playback is PlaybackState.Playing || playback is PlaybackState.Loading,
                queueEnded = playback is PlaybackState.Ended,
                recent = recent.filter { it.videoId != current?.videoId },
                loading = false,
            )
        }.stateIn(scope, SharingStarted.Eagerly, HomeUiState(dayPartOf(hour())))

    /** Plays a recently played song followed by its radio (the row's songs are unrelated to each other). */
    fun playRecent(index: Int) {
        state.value.recent
            .getOrNull(index)
            ?.let(radio::start)
    }

    fun togglePlay() = if (state.value.isPlaying) player.pause() else player.resume()

    private companion object {
        const val RECENT_LIMIT = 20
    }
}

class HomeViewModel(
    player: AudioPlayer,
    history: PlayHistory,
    hour: () -> Int,
    related: suspend (VideoId) -> List<Song> = { emptyList() },
    trackOf: (VideoId) -> VideoId? = { null },
) : ViewModel() {
    val model = HomeModel(player, history, hour, viewModelScope, related, trackOf)
}

private val MORNING = 5..11
private val AFTERNOON = 12..18
