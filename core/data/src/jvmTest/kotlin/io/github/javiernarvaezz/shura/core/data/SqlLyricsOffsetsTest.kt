package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SqlLyricsOffsetsTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) }
    private val database = ShuraDatabase(driver)

    private fun TestScope.offsets() = SqlLyricsOffsets(database, StandardTestDispatcher(testScheduler))

    @Test
    fun aSongWithoutAFixHasNone() =
        runTest {
            assertEquals(Duration.ZERO, offsets().get("v1"))
        }

    @Test
    fun eachSongKeepsItsOwnFix() =
        runTest {
            val offsets = offsets()
            offsets.set("v1", 1.5.seconds)
            offsets.set("v2", (-500).milliseconds)
            offsets.set("v1", 2.seconds)

            assertEquals(2.seconds, offsets.get("v1"))
            assertEquals((-500).milliseconds, offsets.get("v2"))
        }

    @Test
    fun aFixBackToZeroLeavesNoRow() =
        runTest {
            val offsets = offsets()
            offsets.set("v1", 1.seconds)
            offsets.set("v1", Duration.ZERO)

            assertEquals(Duration.ZERO, offsets.get("v1"))
            assertEquals(0L, driver.count("lyrics_offset"))
        }

    @Test
    fun theMigrationTo7AddsTheFixesAndStartsTheLyricsCacheOver() =
        runTest {
            // A version 6 database: no fixes table yet, and lyrics chosen by the earlier rules.
            driver.execute(null, "DROP TABLE lyrics_offset", 0)
            database.lyricsQueries.put("v1", "plain", "words", 1)
            ShuraDatabase.Schema.migrate(driver, oldVersion = 6, newVersion = 7)

            assertEquals(0L, driver.count("lyrics_cache"))
            val offsets = offsets()
            offsets.set("v1", 1.seconds)
            assertEquals(1.seconds, offsets.get("v1"))
        }

    private fun JdbcSqliteDriver.count(table: String): Long =
        executeQuery(null, "SELECT count(*) FROM $table", { cursor ->
            cursor.next()
            app.cash.sqldelight.db.QueryResult
                .Value(cursor.getLong(0) ?: 0L)
        }, 0).value
}
