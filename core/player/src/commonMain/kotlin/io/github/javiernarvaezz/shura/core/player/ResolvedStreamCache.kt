package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Resolved streams by video id, shared by playback and pre-resolution.
 * - A fresh entry is returned as is; one that expires within a minute is resolved again.
 * - A request for an id that is already being resolved joins that resolution instead of starting another.
 * - Resolutions run in [scope], so a cancelled waiter never cancels one that others still wait for.
 * - Failures reach every waiter and are never cached: the next request tries again.
 */
class ResolvedStreamCache(
    private val scope: CoroutineScope,
    private val now: () -> Instant = { Clock.System.now() },
) {
    // Guards both maps. Held only for map updates, never across a resolution.
    private val lock = Mutex()
    private val entries = HashMap<VideoId, ResolvedStream>()
    private val inFlight = HashMap<VideoId, Deferred<ResolvedStream>>()

    suspend fun get(
        id: VideoId,
        resolve: suspend () -> ResolvedStream,
    ): ResolvedStream {
        val pending =
            lock.withLock {
                entries[id]?.takeIf { it.isFresh() }?.let { return it }
                inFlight.getOrPut(id) { start(id, resolve) }
            }
        return pending.await()
    }

    /** The fresh cached entry for [id], without resolving. */
    suspend fun peek(id: VideoId): ResolvedStream? = lock.withLock { entries[id]?.takeIf { it.isFresh() } }

    /** Forgets [id]: the next request resolves it again, and a resolution already running is not stored. */
    suspend fun invalidate(id: VideoId) {
        lock.withLock {
            entries.remove(id)
            inFlight.remove(id)
        }
    }

    // Called under [lock], so the deferred is registered in [inFlight] before its body can take the lock.
    private fun start(
        id: VideoId,
        resolve: suspend () -> ResolvedStream,
    ): Deferred<ResolvedStream> =
        scope.async {
            var result: ResolvedStream? = null
            try {
                resolve().also { result = it }
            } finally {
                val self = currentCoroutineContext().job
                withContext(NonCancellable) {
                    lock.withLock {
                        // Skip the store if it was invalidated meanwhile.
                        if (inFlight[id] === self) {
                            inFlight.remove(id)
                            result?.let { entries[id] = it }
                        }
                    }
                }
            }
        }

    private fun ResolvedStream.isFresh(): Boolean {
        val expiresAt = expiresAt ?: return true
        return expiresAt - now() > MIN_REMAINING_VALIDITY
    }

    private companion object {
        val MIN_REMAINING_VALIDITY = 60.seconds
    }
}
