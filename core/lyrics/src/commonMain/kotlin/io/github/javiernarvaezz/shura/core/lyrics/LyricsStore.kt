package io.github.javiernarvaezz.shura.core.lyrics

import kotlin.time.Instant

/** Lyrics looked up earlier for a song, at [fetchedAt]; a null [lyrics] means none were found. */
data class CachedLyrics(
    val lyrics: Lyrics?,
    val fetchedAt: Instant,
)

/** Where lyrics outlive the process (the app database on Android). Keys are opaque song ids. */
interface LyricsStore {
    suspend fun get(key: String): CachedLyrics?

    suspend fun put(
        key: String,
        entry: CachedLyrics,
    )
}
