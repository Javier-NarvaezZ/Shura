package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ResolvedStreamCacheTest {
    private val id = VideoId("7fwUH0oRmkQ")
    private var now = Instant.fromEpochSeconds(1_000_000)
    private var resolutions = 0

    private fun stream(validFor: Duration?) =
        ResolvedStream(
            videoId = id,
            url = "https://rr1.googlevideo.com/videoplayback",
            requestHeaders = emptyMap(),
            mimeType = "audio/webm",
            codecs = "opus",
            bitrate = 128_000,
            contentLength = 1_000,
            expiresAt = validFor?.let { now + it },
            clientProfile = "TEST",
            requiresBoundedRange = false,
            rangeChunkSizeBytes = 0,
        )

    private fun TestScope.cache() =
        ResolvedStreamCache(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))) { now }

    @Test
    fun aFreshEntryIsReturnedWithoutResolving() =
        runTest {
            val cache = cache()
            val first = cache.get(id) { resolutions++.let { stream(6.hours) } }

            val second = cache.get(id) { resolutions++.let { stream(6.hours) } }

            assertSame(first, second)
            assertEquals(1, resolutions)
        }

    @Test
    fun anEntryCloseToExpiryIsResolvedAgain() =
        runTest {
            val cache = cache()
            cache.get(id) { resolutions++.let { stream(90.seconds) } }
            now += 31.seconds // 59 s of validity left: below the 60 s margin.

            cache.get(id) { resolutions++.let { stream(6.hours) } }

            assertEquals(2, resolutions)
        }

    @Test
    fun aStreamWithoutExpiryStaysFresh() =
        runTest {
            val cache = cache()
            cache.get(id) { resolutions++.let { stream(null) } }
            now += 24.hours

            cache.get(id) { resolutions++.let { stream(null) } }

            assertEquals(1, resolutions)
        }

    @Test
    fun concurrentRequestsShareOneResolution() =
        runTest {
            val cache = cache()
            val gate = CompletableDeferred<Unit>()
            val resolve: suspend () -> ResolvedStream = {
                resolutions++
                gate.await()
                stream(6.hours)
            }

            val a = async { cache.get(id, resolve) }
            val b = async { cache.get(id, resolve) }
            runCurrent()
            gate.complete(Unit)

            assertSame(a.await(), b.await())
            assertEquals(1, resolutions)
        }

    @Test
    fun aFailureIsNotCachedAndTheNextRequestRetries() =
        runTest {
            val cache = cache()

            assertFailsWith<IllegalStateException> { cache.get(id) { resolutions++.let { error("offline") } } }
            cache.get(id) { resolutions++.let { stream(6.hours) } }

            assertEquals(2, resolutions)
        }

    @Test
    fun invalidateForcesANewResolution() =
        runTest {
            val cache = cache()
            cache.get(id) { resolutions++.let { stream(6.hours) } }

            cache.invalidate(id)
            cache.get(id) { resolutions++.let { stream(6.hours) } }

            assertEquals(2, resolutions)
        }

    @Test
    fun aCancelledWaiterDoesNotCancelTheSharedResolution() =
        runTest {
            val cache = cache()
            val gate = CompletableDeferred<Unit>()
            val resolve: suspend () -> ResolvedStream = {
                resolutions++
                gate.await()
                stream(6.hours)
            }
            val waiterScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            waiterScope.async { cache.get(id, resolve) }
            runCurrent()

            waiterScope.cancel()
            val survivor = async { cache.get(id, resolve) }
            runCurrent()
            gate.complete(Unit)

            survivor.await()
            assertEquals(1, resolutions)
        }

    @Test
    fun peekReportsOnlyFreshEntries() =
        runTest {
            val cache = cache()
            assertNull(cache.peek(id))
            cache.get(id) { stream(90.seconds) }

            now += 31.seconds

            assertNull(cache.peek(id))
        }
}
