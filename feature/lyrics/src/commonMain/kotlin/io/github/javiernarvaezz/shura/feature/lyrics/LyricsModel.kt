package io.github.javiernarvaezz.shura.feature.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.lyrics.LyricLine
import io.github.javiernarvaezz.shura.core.lyrics.Lyrics
import io.github.javiernarvaezz.shura.core.lyrics.LyricsOffsets
import io.github.javiernarvaezz.shura.core.lyrics.LyricsQuery
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
 * lyrics. [lyrics] returns null when a song has none (see `LyricsRepository`). The user's timing fix for each song
 * ([timing]) is kept in [offsets].
 */
class LyricsModel(
    private val player: AudioPlayer,
    private val lyrics: suspend (key: String, query: LyricsQuery, refresh: Boolean) -> Lyrics?,
    private val scope: CoroutineScope,
    offsets: LyricsOffsets? = null,
) {
    private val mutableState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    private val visible = MutableStateFlow(false)

    /** The song whose lyrics are in [state] (or loading); null when none are. */
    private var loaded: VideoId? = null
    private var job: Job? = null

    /** The current song was NotFound only because no duration was known; it is looked up again once it plays. */
    private var missedDuration = false

    val state: StateFlow<LyricsUiState> = mutableState.asStateFlow()

    /** The current song's timing fix ([LyricsTiming.offset]); applied to [position] and [seekTo]. */
    val timing = LyricsTiming(offsets, scope)

    /** Whether the song is playing, so the shown line follows the music only then. */
    val isPlaying: StateFlow<Boolean> = isPlayingFlow()

    init {
        scope.launch {
            combine(player.queue.map { it.current }.distinctUntilChanged(), visible, ::Pair).collect { (song, shown) ->
                timing.show(song?.videoId)
                if (song?.videoId != loaded) clear()
                if (shown && song != null && loaded == null) load(song)
            }
        }
        // Once the song plays, the player knows its duration.
        scope.launch {
            player.state.collect { playback ->
                val song =
                    player.queue.value.current
                        ?.takeIf { missedDuration && visible.value }
                if (playback is PlaybackState.Playing && song != null) load(song)
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

    /** Asks LRCLIB again for a song with no lyrics found, ignoring the memory of that answer. */
    fun searchAgain() {
        val song = player.queue.value.current ?: return
        if (mutableState.value == LyricsUiState.NotFound) load(song, refresh = true)
    }

    /** Where the song is now in the lyrics' timing (the timing fix applied); read on demand while they are shown. */
    fun position(): Duration = player.progress().position + timing.offset.value

    fun seekTo(line: LyricLine) = player.seekTo((line.time - timing.offset.value).coerceAtLeast(Duration.ZERO))

    private fun clear() {
        job?.cancel()
        loaded = null
        missedDuration = false
        mutableState.value = LyricsUiState.Idle
    }

    /**
     * Looks up [song] by title, first artist, album and duration. Without an artist there is nothing to ask. Without
     * a duration of its own, the player's is awaited for a moment; if it does not come, NotFound for now.
     */
    @Suppress("TooGenericExceptionCaught") // Any failure must become a visible, retryable state.
    private fun load(
        song: Song,
        refresh: Boolean = false,
    ) {
        job?.cancel()
        loaded = song.videoId
        missedDuration = false
        val artist = song.artists.firstOrNull()?.name
        if (artist == null) {
            mutableState.value = LyricsUiState.NotFound
            return
        }
        mutableState.value = LyricsUiState.Loading
        job =
            scope.launch {
                val duration = song.duration.orNullIfUnusable() ?: awaitPlayerDuration()
                missedDuration = duration == null
                mutableState.value =
                    try {
                        if (duration == null) {
                            LyricsUiState.NotFound
                        } else {
                            val query = LyricsQuery(song.title, artist, song.album?.title, duration, song.isExplicit)
                            lyrics(song.videoId.value, query, refresh).toUiState()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LyricsUiState.Failed(e::class.simpleName)
                    }
            }
    }

    /** The player's duration of the current song, once it knows it; null if it does not within [DURATION_WAIT]. */
    private suspend fun awaitPlayerDuration(): Duration? =
        withTimeoutOrNull(DURATION_WAIT) {
            var duration = player.progress().duration.orNullIfUnusable()
            while (duration == null) {
                delay(DURATION_POLL)
                duration = player.progress().duration.orNullIfUnusable()
            }
            duration
        }

    private fun Duration?.orNullIfUnusable(): Duration? = this?.takeIf { it.isPositive() && it.isFinite() }

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

private val DURATION_WAIT = 5.seconds
private val DURATION_POLL = 250.milliseconds

class LyricsViewModel(
    player: AudioPlayer,
    lyrics: suspend (key: String, query: LyricsQuery, refresh: Boolean) -> Lyrics?,
    offsets: LyricsOffsets? = null,
) : ViewModel() {
    val model = LyricsModel(player, lyrics, viewModelScope, offsets)
}
