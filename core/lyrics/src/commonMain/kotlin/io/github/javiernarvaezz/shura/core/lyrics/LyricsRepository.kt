package io.github.javiernarvaezz.shura.core.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Lyrics for a song, from memory, then [store], then [fetch] (LRCLIB). Lyrics found are kept for good; "no lyrics"
 * is kept for [NOT_FOUND_TTL], so lyrics published later still show up. Failures are not kept, so a retry asks
 * again. The store is a cache: if it fails, the lyrics still come from the network.
 */
class LyricsRepository(
    private val fetch: suspend (LyricsQuery) -> Lyrics,
    private val store: LyricsStore,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val mutex = Mutex()

    // Insertion order is recency order: an entry is moved to the end each time it is used.
    private val memory = LinkedHashMap<String, CachedLyrics>()

    /**
     * The lyrics of the song with [key], looked up with [query]; null when it has none.
     *
     * @throws LyricsException when the lookup fails (other than finding nothing).
     */
    @Suppress("SwallowedException") // NotFound is an answer ("no lyrics"), not a failure.
    suspend fun lyrics(
        key: String,
        query: LyricsQuery,
    ): Lyrics? {
        val cached = remembered(key) ?: stored(key)?.also { remember(key, it) }
        if (cached != null && cached.isFresh()) return cached.lyrics
        val found =
            try {
                fetch(query)
            } catch (e: LyricsException.NotFound) {
                null
            }
        val entry = CachedLyrics(found, now())
        remember(key, entry)
        save(key, entry)
        return found
    }

    private fun CachedLyrics.isFresh(): Boolean = lyrics != null || now() - fetchedAt < NOT_FOUND_TTL

    private suspend fun remembered(key: String): CachedLyrics? =
        mutex.withLock { memory.remove(key)?.also { memory[key] = it } }

    private suspend fun remember(
        key: String,
        entry: CachedLyrics,
    ) = mutex.withLock {
        memory.remove(key)
        memory[key] = entry
        if (memory.size > MEMORY_SIZE) memory.remove(memory.keys.first())
    }

    // A broken cache must not cost the user the lyrics.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun stored(key: String): CachedLyrics? =
        try {
            store.get(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    // Same: the lyrics are returned even if they cannot be saved.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun save(
        key: String,
        entry: CachedLyrics,
    ) {
        try {
            store.put(key, entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept in memory only.
        }
    }

    companion object {
        /** Songs whose lyrics stay in memory. */
        const val MEMORY_SIZE = 32

        /** How long "no lyrics" is believed before LRCLIB is asked again. */
        val NOT_FOUND_TTL = 7.days
    }
}
