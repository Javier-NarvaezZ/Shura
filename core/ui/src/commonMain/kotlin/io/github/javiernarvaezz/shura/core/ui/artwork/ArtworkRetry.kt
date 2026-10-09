package io.github.javiernarvaezz.shura.core.ui.artwork

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

/**
 * Emits each time the device gets a network again. Provided by the app (on Android, from a connectivity callback),
 * so covers that ran out of retries try once more; by default it never emits.
 */
val LocalNetworkRegained = staticCompositionLocalOf<Flow<Unit>> { NeverRegained }

/** A network signal that never fires (desktop, previews and tests). */
val NeverRegained: Flow<Unit> = flow { awaitCancellation() }

/**
 * When a failed cover load is tried again: after growing delays, then once each time the network comes back.
 * Failed loads are not cached by Coil, so every retry reaches the network.
 */
internal object ArtworkRetry {
    private val delays = listOf(2.seconds, 5.seconds, 10.seconds, 30.seconds, 30.seconds)

    /** Waits before the next attempt, after [failures] failed ones (at least one). */
    suspend fun awaitNextAttempt(
        failures: Int,
        networkRegained: Flow<Unit>,
    ) {
        val wait = delays.getOrNull(failures - 1)
        if (wait != null) delay(wait) else networkRegained.first()
    }
}

/** Runs [load] until it returns a value, waiting between failures as [ArtworkRetry] says. Cancel to give up. */
internal suspend fun <T : Any> retryingLoad(
    networkRegained: Flow<Unit>,
    load: suspend () -> T?,
): T {
    var failures = 0
    while (true) {
        load()?.let { return it }
        failures++
        ArtworkRetry.awaitNextAttempt(failures, networkRegained)
    }
}
