package io.github.javiernarvaezz.shura.feature.lyrics

import io.github.javiernarvaezz.shura.core.lyrics.LyricsOffsets
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** The user's timing fix for the current song's lyrics, read from and saved to [offsets] (none without them). */
class LyricsTiming internal constructor(
    private val offsets: LyricsOffsets?,
    private val scope: CoroutineScope,
) {
    private val mutableOffset = MutableStateFlow(Duration.ZERO)
    private var song: VideoId? = null
    private var job: Job? = null

    /** Positive shows each line sooner. */
    val offset: StateFlow<Duration> = mutableOffset.asStateFlow()

    /** Moves the lyrics [by] (one [LyricsOffsets.STEP] per tap), up to [LyricsOffsets.LIMIT] either way. */
    fun shift(by: Duration) = save(offset.value + by)

    fun reset() = save(Duration.ZERO)

    /** The song now playing: its own fix replaces the last one's. */
    internal fun show(next: VideoId?) {
        if (next == song) return
        job?.cancel()
        song = next
        mutableOffset.value = Duration.ZERO
        if (next == null || offsets == null) return
        job = scope.launch { quietly { offsets.get(next.value) }?.let { mutableOffset.value = it } }
    }

    private fun save(value: Duration) {
        val current = song ?: return
        val clamped = value.coerceIn(-LyricsOffsets.LIMIT, LyricsOffsets.LIMIT)
        job?.cancel()
        mutableOffset.value = clamped
        job = scope.launch { quietly { offsets?.set(current.value, clamped) } }
    }

    // A fix that cannot be read or saved must not cost the user the lyrics.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun <T> quietly(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
