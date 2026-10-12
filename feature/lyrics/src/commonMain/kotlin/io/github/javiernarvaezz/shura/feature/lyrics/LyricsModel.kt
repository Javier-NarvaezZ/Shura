package io.github.javiernarvaezz.shura.feature.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.lyrics.LyricLine
import io.github.javiernarvaezz.shura.core.lyrics.Lyrics
import io.github.javiernarvaezz.shura.core.lyrics.LyricsQuery
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** The lyrics of the current song, as shown. */
sealed interface LyricsUiState {
    /** Not looked up yet (the lyrics have not been shown for this song). */
    data object Idle : LyricsUiState

    data object Loading : LyricsUiState

    data class Synced(
        val lyrics: Lyrics.Synced,
    ) : LyricsUiState

    data class Plain(
        val text: String,
    ) : LyricsUiState

    data object Instrumental : LyricsUiState

    data object NotFound : LyricsUiState

    /** [failureType] is the exception type, for diagnostics; never its message. */
    data class Failed(
        val failureType: String?,
    ) : LyricsUiState
}

/**
 * The current song's lyrics; plain so it can be tested without Android.
 *
 * Lyrics are looked up only while shown ([setVisible]): song details reach LRCLIB only when the user asks for the
 * lyrics. [lyrics] returns null when a song has none (see `LyricsRepository`).
 */
class LyricsModel(
    private val player: AudioPlayer,
    private val lyrics: suspend (key: String, query: LyricsQuery) -> Lyrics?,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    private val visible = MutableStateFlow(false)

    /** The song whose lyrics are in [state] (or loading); null when none are. */
    private var loaded: VideoId? = null
    private var job: Job? = null

    val state: StateFlow<LyricsUiState> = mutableState.asStateFlow()

    /** Whether the song is playing, so the shown line follows the music only then. */
    val isPlaying: StateFlow<Boolean> = isPlayingFlow()

    init {
        scope.launch {
            combine(player.queue.map { it.current }.distinctUntilChanged(), visible, ::Pair).collect { (song, shown) ->
                if (song?.videoId != loaded) clear()
                if (shown && song != null && loaded == null) load(song)
            }
        }
    }

    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    /** Looks the lyrics up again after a failure. */
    fun retry() {
        val song = player.queue.value.current ?: return
        if (mutableState.value is LyricsUiState.Failed) load(song)
    }

    /** Where the song is now; read on demand while synced lyrics are shown. */
    fun position(): Duration = player.progress().position

    fun seekTo(line: LyricLine) = player.seekTo(line.time)

    private fun clear() {
        job?.cancel()
        loaded = null
        mutableState.value = LyricsUiState.Idle
    }

    @Suppress("TooGenericExceptionCaught") // Any failure must become a visible, retryable state.
    private fun load(song: Song) {
        job?.cancel()
        loaded = song.videoId
        val query = queryFor(song)
        if (query == null) {
            mutableState.value = LyricsUiState.NotFound
            return
        }
        mutableState.value = LyricsUiState.Loading
        job =
            scope.launch {
                mutableState.value =
                    try {
                        lyrics(song.videoId.value, query).toUiState()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LyricsUiState.Failed(e::class.simpleName)
                    }
            }
    }

    /** Title, first artist, album and duration; null when the song lacks an artist or a known duration. */
    private fun queryFor(song: Song): LyricsQuery? {
        val artist = song.artists.firstOrNull()?.name
        val duration = (song.duration ?: player.progress().duration)?.takeIf { it.isPositive() && it.isFinite() }
        return if (artist != null && duration != null) {
            LyricsQuery(song.title, artist, song.album?.title, duration)
        } else {
            null
        }
    }

    private fun Lyrics?.toUiState(): LyricsUiState =
        when (this) {
            is Lyrics.Synced -> LyricsUiState.Synced(this)
            is Lyrics.Plain -> LyricsUiState.Plain(text)
            Lyrics.Instrumental -> LyricsUiState.Instrumental
            null -> LyricsUiState.NotFound
        }

    private fun isPlayingFlow(): StateFlow<Boolean> {
        val playing = MutableStateFlow(player.state.value is PlaybackState.Playing)
        scope.launch { player.state.collect { playing.value = it is PlaybackState.Playing } }
        return playing.asStateFlow()
    }
}

class LyricsViewModel(
    player: AudioPlayer,
    lyrics: suspend (key: String, query: LyricsQuery) -> Lyrics?,
) : ViewModel() {
    val model = LyricsModel(player, lyrics, viewModelScope)
}
