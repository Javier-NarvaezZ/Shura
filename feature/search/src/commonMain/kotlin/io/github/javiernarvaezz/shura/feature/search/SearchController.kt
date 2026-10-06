package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.QueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchStatus { Idle, Loading, Done, Empty, Failed }

data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val status: SearchStatus = SearchStatus.Idle,
    /** Exception type of the last failed search, for diagnostics; never the message (may hold the query). */
    val failureType: String? = null,
)

/**
 * Search state and the actions on its results; plain so it can be tested without Android.
 *
 * [onPlaybackIntent] is called while the user types or submits a query, a likely prelude to playing a result; the
 * receiver decides whether to warm anything up.
 */
class SearchController(
    private val search: suspend (String) -> List<Song>,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
    private val onPlaybackIntent: () -> Unit = {},
) {
    private val mutableState = MutableStateFlow(SearchUiState())
    private var searchJob: Job? = null

    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
        if (query.isNotBlank()) onPlaybackIntent()
    }

    /** Runs (or re-runs, as a retry) the search for the current query; only the latest search wins. */
    @Suppress("TooGenericExceptionCaught") // Any failure must become a visible, retryable state.
    fun submit() {
        val query = mutableState.value.query.trim()
        if (query.isEmpty()) return
        onPlaybackIntent()
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

    /** The queue, to mark the song that is playing in the results. */
    val queue: StateFlow<QueueState> = player.queue

    /** Plays [song] with the current results as the queue (as YouTube Music does), or alone if not in them. */
    fun play(song: Song) {
        val results = mutableState.value.results
        val index = results.indexOf(song)
        if (index >= 0) player.playQueue(results, index) else player.play(song)
    }

    /** Plays [song] right after the current one (or starts it if nothing is queued). */
    fun playNext(song: Song) = player.playNext(listOf(song))

    /** Adds [song] at the end of the queue. */
    fun enqueue(song: Song) = player.enqueue(listOf(song))
}
