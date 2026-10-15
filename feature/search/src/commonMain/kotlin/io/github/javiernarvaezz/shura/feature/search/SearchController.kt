package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.MusicCatalog
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.Library
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.SongLikes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchStatus { Idle, Loading, Done, Empty, Failed }

/** What a search looks for: one of YouTube Music's search filters. */
enum class SearchKind { Songs, Albums, Artists, Playlists }

/** Where each kind of result comes from. */
class SearchSources(
    val songs: suspend (String) -> List<Song>,
    val albums: suspend (String) -> List<AlbumSummary> = { emptyList() },
    val artists: suspend (String) -> List<ArtistSummary> = { emptyList() },
    val playlists: suspend (String) -> List<PlaylistSummary> = { emptyList() },
) {
    companion object {
        fun of(catalog: MusicCatalog) =
            SearchSources(catalog::searchSongs, catalog::searchAlbums, catalog::searchArtists, catalog::searchPlaylists)
    }
}

/**
 * The search: the query, the kind shown, and each kind's results and status. A submitted query is searched for
 * the kind shown; other kinds are searched once, when first shown.
 */
data class SearchUiState(
    val query: String = "",
    val kind: SearchKind = SearchKind.Songs,
    val statuses: Map<SearchKind, SearchStatus> = emptyMap(),
    val results: List<Song> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    /** Exception type of the last failed search, for diagnostics; never the message (may hold the query). */
    val failureType: String? = null,
) {
    /** The status of the kind shown. */
    val status: SearchStatus get() = statuses[kind] ?: SearchStatus.Idle
}

/**
 * Search state and the actions on its results; plain so it can be tested without Android.
 *
 * [onPlaybackIntent] is called while the user types or submits a query, a likely prelude to playing a result; the
 * receiver decides whether to warm anything up.
 */
class SearchController(
    private val sources: SearchSources,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
    library: Library? = null,
    private val onPlaybackIntent: () -> Unit = {},
) {
    /** "Me gusta" for the results; without a library the menus offer none. */
    val likes = SongLikes(library, scope)

    private val mutableState = MutableStateFlow(SearchUiState())
    private val jobs = mutableMapOf<SearchKind, Job>()
    private var submitted = ""

    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
        if (query.isNotBlank()) onPlaybackIntent()
    }

    /** Runs (or re-runs, as a retry) the search for the current query and kind; only the latest search wins. */
    fun submit() {
        val query = mutableState.value.query.trim()
        if (query.isEmpty()) return
        onPlaybackIntent()
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        submitted = query
        mutableState.update {
            SearchUiState(query = it.query, kind = it.kind)
        }
        load(mutableState.value.kind)
    }

    /** Shows [kind], searching it if the current query has not been searched for it yet. */
    fun selectKind(kind: SearchKind) {
        mutableState.update { it.copy(kind = kind) }
        if (submitted.isNotEmpty() && mutableState.value.status == SearchStatus.Idle) load(kind)
    }

    /** Searches the kind shown again (after a failure). */
    fun retry() {
        if (submitted.isNotEmpty()) load(mutableState.value.kind)
    }

    @Suppress("TooGenericExceptionCaught") // Any failure must become a visible, retryable state.
    private fun load(kind: SearchKind) {
        val query = submitted
        jobs[kind]?.cancel()
        setStatus(kind, SearchStatus.Loading)
        jobs[kind] =
            scope.launch {
                try {
                    val found = fetch(kind, query)
                    mutableState.update {
                        found.apply(it).withStatus(kind, if (found.isEmpty) SearchStatus.Empty else SearchStatus.Done)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableState.update {
                        it.withStatus(kind, SearchStatus.Failed).copy(failureType = e::class.simpleName)
                    }
                }
            }
    }

    /** A kind's results, and how they go into the state. */
    private class Found(
        val isEmpty: Boolean,
        val apply: (SearchUiState) -> SearchUiState,
    )

    private suspend fun fetch(
        kind: SearchKind,
        query: String,
    ): Found =
        when (kind) {
            SearchKind.Songs -> {
                sources.songs(query).let { found ->
                    Found(found.isEmpty()) { it.copy(results = found) }
                }
            }

            SearchKind.Albums -> {
                sources.albums(query).let { found -> Found(found.isEmpty()) { it.copy(albums = found) } }
            }

            SearchKind.Artists -> {
                sources.artists(query).let { found -> Found(found.isEmpty()) { it.copy(artists = found) } }
            }

            SearchKind.Playlists -> {
                sources.playlists(query).let { found -> Found(found.isEmpty()) { it.copy(playlists = found) } }
            }
        }

    private fun setStatus(
        kind: SearchKind,
        status: SearchStatus,
    ) = mutableState.update { it.withStatus(kind, status) }

    private fun SearchUiState.withStatus(
        kind: SearchKind,
        status: SearchStatus,
    ) = copy(statuses = statuses + (kind to status))

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
