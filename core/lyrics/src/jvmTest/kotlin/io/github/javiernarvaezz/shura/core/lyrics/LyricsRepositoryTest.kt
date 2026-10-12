package io.github.javiernarvaezz.shura.core.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LyricsRepositoryTest {
    private val query = LyricsQuery("Title", "Artist", "Album", 200.seconds)
    private val synced = Lyrics.Synced(listOf(LyricLine(1.seconds, "line")))
    private var now = Instant.fromEpochSeconds(1_800_000_000)
    private val store = FakeLyricsStore()
    private var calls = 0
    private var answer: () -> Lyrics = { synced }

    private fun repository() =
        LyricsRepository(
            fetch = {
                calls++
                answer()
            },
            store = store,
            now = { now },
        )

    @Test
    fun fetchesOnceThenAnswersFromMemory() =
        runTest {
            val repository = repository()
            assertEquals(synced, repository.lyrics("v1", query))
            assertEquals(synced, repository.lyrics("v1", query))
            assertEquals(1, calls)
            assertEquals(CachedLyrics(synced, now), store.entries["v1"])
        }

    @Test
    fun answersFromTheStoreAfterARestart() =
        runTest {
            store.entries["v1"] = CachedLyrics(synced, now - 300.days)
            assertEquals(synced, repository().lyrics("v1", query))
            assertEquals(0, calls)
        }

    @Test
    fun plainAndInstrumentalLyricsNeverExpire() =
        runTest {
            val plain = Lyrics.Plain("words")
            store.entries["plain"] = CachedLyrics(plain, now - 300.days)
            store.entries["instrumental"] = CachedLyrics(Lyrics.Instrumental, now - 300.days)
            val repository = repository()
            assertEquals(plain, repository.lyrics("plain", query))
            assertEquals(Lyrics.Instrumental, repository.lyrics("instrumental", query))
            assertEquals(0, calls)
        }

    @Test
    fun notFoundIsRememberedForAWeek() =
        runTest {
            answer = { throw LyricsException.NotFound() }
            val repository = repository()
            assertNull(repository.lyrics("v1", query))
            assertEquals(CachedLyrics(null, now), store.entries["v1"])
            now += 6.days
            assertNull(repository.lyrics("v1", query))
            assertEquals(1, calls)

            now += 1.days + 1.hours
            answer = { synced }
            assertEquals(synced, repository.lyrics("v1", query))
            assertEquals(2, calls)
            assertEquals(CachedLyrics(synced, now), store.entries["v1"])
        }

    @Test
    fun anExpiredNotFoundInTheStoreIsFetchedAgain() =
        runTest {
            store.entries["v1"] = CachedLyrics(null, now - 8.days)
            assertEquals(synced, repository().lyrics("v1", query))
            assertEquals(1, calls)
        }

    @Test
    fun failuresAreNotCachedSoARetryFetchesAgain() =
        runTest {
            answer = { throw LyricsException.Http(503) }
            val repository = repository()
            assertFailsWith<LyricsException.Http> { repository.lyrics("v1", query) }
            assertNull(store.entries["v1"])

            answer = { synced }
            assertEquals(synced, repository.lyrics("v1", query))
            assertEquals(2, calls)
        }

    @Test
    fun cancellationPassesThroughAndIsNotCached() =
        runTest {
            answer = { throw CancellationException("gone") }
            assertFailsWith<CancellationException> { repository().lyrics("v1", query) }
            assertNull(store.entries["v1"])
        }

    @Test
    fun aStoreThatFailsToReadStillGetsLyricsFromTheNetwork() =
        runTest {
            store.failReads = true
            assertEquals(synced, repository().lyrics("v1", query))
            assertEquals(1, calls)
        }

    @Test
    fun aStoreThatFailsToWriteStillReturnsTheLyrics() =
        runTest {
            store.failWrites = true
            val repository = repository()
            assertEquals(synced, repository.lyrics("v1", query))
            // Kept in memory even though the store could not save it.
            assertEquals(synced, repository.lyrics("v1", query))
            assertEquals(1, calls)
        }

    @Test
    fun memoryKeepsOnlyTheMostRecentSongs() =
        runTest {
            val repository = repository()
            repeat(LyricsRepository.MEMORY_SIZE + 1) { repository.lyrics("v$it", query) }
            store.entries.clear()
            // The oldest song fell out of memory and the store no longer has it: fetched again.
            repository.lyrics("v0", query)
            assertEquals(LyricsRepository.MEMORY_SIZE + 2, calls)
            // The newest one is still in memory.
            repository.lyrics("v${LyricsRepository.MEMORY_SIZE}", query)
            assertEquals(LyricsRepository.MEMORY_SIZE + 2, calls)
        }

    private class FakeLyricsStore : LyricsStore {
        val entries = mutableMapOf<String, CachedLyrics>()
        var failReads = false
        var failWrites = false

        override suspend fun get(key: String): CachedLyrics? {
            if (failReads) error("disk I/O error")
            return entries[key]
        }

        override suspend fun put(
            key: String,
            entry: CachedLyrics,
        ) {
            if (failWrites) error("disk full")
            entries[key] = entry
        }
    }
}
