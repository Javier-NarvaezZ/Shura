package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.lyrics.CachedLyrics
import io.github.javiernarvaezz.shura.core.lyrics.LyricLine
import io.github.javiernarvaezz.shura.core.lyrics.Lyrics
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SqlLyricsStoreTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) }
    private val database = ShuraDatabase(driver)
    private val at = Instant.fromEpochMilliseconds(1_800_000_000_123)

    private fun TestScope.store() = SqlLyricsStore(database, StandardTestDispatcher(testScheduler))

    @Test
    fun everyKindComesBackAsItWasSaved() =
        runTest {
            val store = store()
            val entries =
                mapOf(
                    "synced" to
                        Lyrics.Synced(
                            listOf(
                                LyricLine(1_250.milliseconds, "Hola, \"mundo\""),
                                LyricLine(3.seconds, ""),
                                LyricLine(65.seconds, "ñandú [x] <y>"),
                            ),
                        ),
                    "plain" to Lyrics.Plain("First line\nSecond line"),
                    "instrumental" to Lyrics.Instrumental,
                    "none" to null,
                    "empty" to Lyrics.Synced(emptyList()),
                )
            entries.forEach { (key, lyrics) -> store.put(key, CachedLyrics(lyrics, at)) }
            entries.forEach { (key, lyrics) -> assertEquals(CachedLyrics(lyrics, at), store.get(key), key) }
        }

    @Test
    fun aMissingSongIsNull() =
        runTest {
            assertNull(store().get("nothing"))
        }

    @Test
    fun savingAgainReplacesTheEntry() =
        runTest {
            val store = store()
            store.put("v", CachedLyrics(null, at))
            store.put("v", CachedLyrics(Lyrics.Plain("now found"), at + 8.seconds))
            assertEquals(CachedLyrics(Lyrics.Plain("now found"), at + 8.seconds), store.get("v"))
        }

    @Test
    fun anUnreadableRowIsAMiss() =
        runTest {
            database.lyricsQueries.put("broken", "synced", "[[1,", 0)
            database.lyricsQueries.put("unknown", "karaoke", null, 0)
            database.lyricsQueries.put("plainWithoutText", "plain", null, 0)
            val store = store()
            assertNull(store.get("broken"))
            assertNull(store.get("unknown"))
            assertNull(store.get("plainWithoutText"))
        }

    @Test
    fun keepsOnlyTheMostRecentlyLookedUpSongs() =
        runTest {
            val store = store()
            // Oldest first; the newest 1000 survive.
            repeat(1_002) { store.put("v$it", CachedLyrics(null, Instant.fromEpochMilliseconds(it.toLong()))) }
            assertNull(store.get("v0"))
            assertNull(store.get("v1"))
            assertEquals(CachedLyrics(null, Instant.fromEpochMilliseconds(2)), store.get("v2"))
            assertEquals(1_000L, database.lyricsQueries.count().executeAsOne())
        }

    @Test
    fun theMigrationTo4ForgetsOnlySongsWithoutLyrics() =
        runTest {
            database.lyricsQueries.put("none", "none", null, 1)
            database.lyricsQueries.put("plain", "plain", "words", 1)
            database.lyricsQueries.put("instrumental", "instrumental", null, 1)
            ShuraDatabase.Schema.migrate(driver, oldVersion = 3, newVersion = 4)
            val store = store()
            assertNull(store.get("none"))
            assertEquals(CachedLyrics(Lyrics.Plain("words"), Instant.fromEpochMilliseconds(1)), store.get("plain"))
            assertEquals(CachedLyrics(Lyrics.Instrumental, Instant.fromEpochMilliseconds(1)), store.get("instrumental"))
        }
}
