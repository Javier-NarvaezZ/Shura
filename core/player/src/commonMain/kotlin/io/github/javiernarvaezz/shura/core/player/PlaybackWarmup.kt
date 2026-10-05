package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.stream.Trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Runs [warmUp] at most once per instance (one instance per process) when the user shows an intent to play, such
 * as typing a search. Conditions are checked at each intent: a skipped intent does not use up the single run.
 *
 * Data Saver only skips the warm-up when it actually applies: the active network is metered and the system
 * restricts this app's background data ([isDataSaverRestricting] is false when the user exempted the app).
 */
@OptIn(ExperimentalAtomicApi::class)
class PlaybackWarmup(
    private val warmUp: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val isPowerSaveMode: () -> Boolean,
    private val isActiveNetworkMetered: () -> Boolean,
    private val isDataSaverRestricting: () -> Boolean,
    private val trace: Trace = Trace.NONE,
) {
    private val started = AtomicBoolean(false)

    // Only to avoid repeating the same skip event on every keystroke.
    private var lastSkipReason: String? = null

    fun onPlaybackIntent() {
        if (started.load()) return
        val skipReason = skipReason()
        if (skipReason != null) {
            if (skipReason != lastSkipReason) trace.event("warmup: skipped", mapOf("reason" to skipReason))
            lastSkipReason = skipReason
        } else if (started.compareAndSet(expectedValue = false, newValue = true)) {
            scope.launch { runWarmUp() }
        }
    }

    private fun skipReason(): String? =
        when {
            !isEnabled() -> "disabled"
            isPowerSaveMode() -> "powerSave"
            isActiveNetworkMetered() && isDataSaverRestricting() -> "dataSaver"
            else -> null
        }

    private suspend fun runWarmUp() {
        try {
            warmUp()
        } catch (e: CancellationException) {
            throw e
        } catch (
            // A failed warm-up only means the first playback takes the usual path.
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            return
        }
    }
}
