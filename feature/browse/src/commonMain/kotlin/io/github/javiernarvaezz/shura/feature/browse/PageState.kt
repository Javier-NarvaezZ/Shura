package io.github.javiernarvaezz.shura.feature.browse

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A catalog page while it loads, once loaded, or after a failure the user can retry (ADR 0001 R4). */
sealed interface PageState<out T> {
    data object Loading : PageState<Nothing>

    data class Loaded<T>(
        val page: T,
    ) : PageState<T>

    /** [type] is the exception type, for diagnostics; never its message. */
    data class Failed(
        val type: String?,
    ) : PageState<Nothing>
}

/** Loads a page on start and on [retry]; only the latest load wins. */
internal class PageLoader<T>(
    private val scope: CoroutineScope,
    private val load: suspend () -> T,
) {
    private val mutableState = MutableStateFlow<PageState<T>>(PageState.Loading)
    private var job: Job? = null

    val state: StateFlow<PageState<T>> = mutableState.asStateFlow()

    val page: T? get() = (state.value as? PageState.Loaded<T>)?.page

    init {
        retry()
    }

    @Suppress("TooGenericExceptionCaught") // Any failure must become a visible, retryable state.
    fun retry() {
        job?.cancel()
        mutableState.value = PageState.Loading
        job =
            scope.launch {
                mutableState.value =
                    try {
                        PageState.Loaded(load())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        PageState.Failed(e::class.simpleName)
                    }
            }
    }

    /** Replaces the loaded page (e.g. with more tracks); ignored while not loaded. */
    fun update(transform: (T) -> T) {
        page?.let { mutableState.value = PageState.Loaded(transform(it)) }
    }
}

/** How a page plays its tracks: all in order, shuffled, from one of them, or one song next or last. */
internal class TrackPlayback(
    private val player: AudioPlayer,
    private val random: Random,
) {
    fun playAll(tracks: List<Song>) {
        if (tracks.isEmpty()) return
        player.setShuffle(false)
        player.playQueue(tracks, 0)
    }

    /** Shuffled from a random first song; the player draws the rest of the order. */
    fun shuffle(tracks: List<Song>) {
        if (tracks.isEmpty()) return
        player.setShuffle(true)
        player.playQueue(tracks, random.nextInt(tracks.size))
    }

    /** From [index], with the whole list as the queue, keeping the current shuffle setting. */
    fun playFrom(
        tracks: List<Song>,
        index: Int,
    ) {
        if (index in tracks.indices) player.playQueue(tracks, index)
    }

    fun playNext(song: Song) = player.playNext(listOf(song))

    fun enqueue(song: Song) = player.enqueue(listOf(song))
}
