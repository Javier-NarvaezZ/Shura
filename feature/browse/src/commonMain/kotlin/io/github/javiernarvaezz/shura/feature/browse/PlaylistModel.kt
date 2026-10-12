package io.github.javiernarvaezz.shura.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.MusicCatalog
import io.github.javiernarvaezz.shura.core.model.PlaylistPage
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.QueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * A playlist page's state and actions. Tracks load a page at a time as the list nears its end; play and shuffle
 * first load what is missing (up to [MAX_TRACKS]), so they cover the whole playlist.
 */
class PlaylistModel(
    private val catalog: MusicCatalog,
    player: AudioPlayer,
    link: BrowseLink,
    private val scope: CoroutineScope,
    random: Random = Random.Default,
) {
    private val loader = PageLoader(scope) { catalog.playlist(link) }
    private val playback = TrackPlayback(player, random)
    private val mutablePreparing = MutableStateFlow(false)
    private val mutableMoreFailed = MutableStateFlow(false)
    private var moreJob: Job? = null

    val state: StateFlow<PageState<PlaylistPage>> = loader.state

    val queue: StateFlow<QueueState> = player.queue

    /** True while play or shuffle wait for the rest of the tracks. */
    val preparing: StateFlow<Boolean> = mutablePreparing.asStateFlow()

    /** True after loading more tracks failed; [loadMore] tries again. */
    val moreFailed: StateFlow<Boolean> = mutableMoreFailed.asStateFlow()

    fun retry() = loader.retry()

    /** Loads the next tracks, unless they are already loading or there are none. */
    fun loadMore() {
        if (moreJob?.isActive == true || loader.page?.hasMore != true) return
        moreJob = scope.launch { fetchMore() }
    }

    fun play() = prepareThen(playback::playAll)

    fun shuffle() = prepareThen(playback::shuffle)

    fun playTrack(index: Int) = playback.playFrom(tracks(), index)

    fun playNext(song: Song) = playback.playNext(song)

    fun enqueue(song: Song) = playback.enqueue(song)

    private fun prepareThen(action: (List<Song>) -> Unit) {
        if (loader.page == null || mutablePreparing.value) return
        mutablePreparing.value = true
        scope.launch {
            try {
                moreJob?.join()
                while (loader.page?.hasMore == true && tracks().size < MAX_TRACKS && fetchMore()) Unit
                action(tracks().take(MAX_TRACKS))
            } finally {
                mutablePreparing.value = false
            }
        }
    }

    /** Appends the next page; false when it failed (the tracks already loaded stay). */
    @Suppress("TooGenericExceptionCaught") // Any failure becomes a retryable "load more" state.
    private suspend fun fetchMore(): Boolean {
        val token = loader.page?.continuation ?: return false
        return try {
            val more = catalog.playlistContinuation(token)
            loader.update { it.copy(tracks = it.tracks + more.tracks, continuation = more.continuation) }
            mutableMoreFailed.value = false
            true
        } catch (e: CancellationException) {
            throw e
        } catch (
            // The tracks already loaded stay and the user can try again; the cause adds nothing for them.
            @Suppress("SwallowedException") e: Exception,
        ) {
            mutableMoreFailed.value = true
            false
        }
    }

    private fun tracks() = loader.page?.tracks.orEmpty()

    companion object {
        /** The most tracks play and shuffle queue. */
        const val MAX_TRACKS = 500
    }
}

class PlaylistViewModel(
    catalog: MusicCatalog,
    player: AudioPlayer,
    link: BrowseLink,
) : ViewModel() {
    val model = PlaylistModel(catalog, player, link, viewModelScope)
}
