package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

enum class SearchStatus { Idle, Loading, Done, Empty, Failed }

data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val status: SearchStatus = SearchStatus.Idle,
    /** Exception type of the last failed search, for diagnostics; never the message (may hold the query). */
    val failureType: String? = null,
)

/** Search-and-play state holder for the spike screen. Wired manually (no DI framework yet). */
class SearchController(
    private val search: suspend (String) -> List<Song>,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(SearchUiState())
    private var searchJob: Job? = null

    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    val playback: StateFlow<PlaybackState> = player.state

    fun onQueryChange(query: String) = mutableState.update { it.copy(query = query) }

    /** Runs (or re-runs, as a retry) the search for the current query; only the latest search wins. */
    @Suppress("TooGenericExceptionCaught") // Any failure must become a visible, retryable state.
    fun submit() {
        val query = mutableState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        mutableState.update { it.copy(status = SearchStatus.Loading) }
        searchJob =
            scope.launch {
                val next =
                    try {
                        val results = search(query)
                        mutableState.value.copy(
                            results = results,
                            status = if (results.isEmpty()) SearchStatus.Empty else SearchStatus.Done,
                            failureType = null,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mutableState.value.copy(status = SearchStatus.Failed, failureType = e::class.simpleName)
                    }
                mutableState.value = next
            }
    }

    fun play(song: Song) = player.play(song)

    fun togglePause() {
        when (playback.value) {
            is PlaybackState.Playing -> player.pause()
            is PlaybackState.Paused -> player.resume()
            else -> Unit
        }
    }

    fun seekBy(offset: Duration) = player.seekBy(offset)

    fun retryPlayback() = player.retry()
}
