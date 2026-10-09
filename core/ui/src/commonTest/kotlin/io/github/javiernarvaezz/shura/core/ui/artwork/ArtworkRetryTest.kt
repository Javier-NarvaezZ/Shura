package io.github.javiernarvaezz.shura.core.ui.artwork

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkRetryTest {
    private val regained = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fails [failures] times, then succeeds; records when each attempt ran. */
    private class Loader(
        private val failures: Int,
        private val now: () -> Long,
    ) {
        val attemptsAt = mutableListOf<Long>()

        fun load(): String? {
            attemptsAt += now()
            return if (attemptsAt.size > failures) "cover" else null
        }
    }

    @Test
    fun aFirstSuccessDoesNotWait() =
        runTest {
            val loader = Loader(failures = 0) { currentTime }

            assertEquals("cover", retryingLoad(regained) { loader.load() })
            assertEquals(listOf(0L), loader.attemptsAt)
        }

    @Test
    fun failuresAreRetriedWithGrowingDelays() =
        runTest {
            val loader = Loader(failures = 3) { currentTime }

            assertEquals("cover", retryingLoad(regained) { loader.load() })
            assertEquals(listOf(0L, 2_000L, 7_000L, 17_000L), loader.attemptsAt)
        }

    @Test
    fun afterTheLastDelayItWaitsForTheNetworkToComeBack() =
        runTest {
            val loader = Loader(failures = 7) { currentTime }
            var result: String? = null
            val job = launch { result = retryingLoad(regained) { loader.load() } }

            advanceTimeBy(10 * 60_000L)
            assertEquals(listOf(0L, 2_000L, 7_000L, 17_000L, 47_000L, 77_000L), loader.attemptsAt)
            assertNull(result)

            // Each time the network comes back, exactly one more attempt.
            regained.emit(Unit)
            runCurrent()
            assertEquals(7, loader.attemptsAt.size)
            assertNull(result)

            regained.emit(Unit)
            runCurrent()
            assertEquals(8, loader.attemptsAt.size)
            assertEquals("cover", result)
            job.join()
        }

    @Test
    fun cancellingStopsTheAttempts() =
        runTest {
            val loader = Loader(failures = Int.MAX_VALUE) { currentTime }
            val job = launch { retryingLoad<String>(regained) { loader.load() } }

            advanceTimeBy(3_000L)
            job.cancel()
            advanceTimeBy(10 * 60_000L)
            regained.emit(Unit)
            runCurrent()

            assertEquals(listOf(0L, 2_000L), loader.attemptsAt)
        }
}
