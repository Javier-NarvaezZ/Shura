package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlPlaybackSettingsTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) }
    private val database = ShuraDatabase(driver)

    /** Settings as after a launch: the stored values have been read. */
    private fun TestScope.settings(): SqlPlaybackSettings {
        val settings = SqlPlaybackSettings(database, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return settings
    }

    @Test
    fun autoplayIsOnByDefault() =
        runTest {
            assertTrue(settings().autoplay.value)
        }

    @Test
    fun aChangeIsSeenAtOnceAndKeptForTheNextLaunch() =
        runTest {
            val settings = settings()
            settings.setAutoplay(false)
            assertFalse(settings.autoplay.value)
            runCurrent()

            assertFalse(settings().autoplay.value)

            settings.setAutoplay(true)
            runCurrent()
            assertTrue(settings().autoplay.value)
        }

    @Test
    fun aChangeBeforeTheStoredValueIsReadWins() =
        runTest {
            database.settingsQueries.put("autoplay", "false")
            val settings = SqlPlaybackSettings(database, backgroundScope, StandardTestDispatcher(testScheduler))
            settings.setAutoplay(true)
            runCurrent()

            assertTrue(settings.autoplay.value)
        }

    @Test
    fun anUnreadableValueFallsBackToTheDefault() =
        runTest {
            database.settingsQueries.put("autoplay", "maybe")

            assertTrue(settings().autoplay.value)
        }

    @Test
    fun aDatabaseFromVersionOneGainsTheSettingsTable() {
        // A copy of the released version 1 schema (kept for migration checks).
        val file = File.createTempFile("shura-v1", ".db").apply { deleteOnExit() }
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        val old = JdbcSqliteDriver("jdbc:sqlite:${file.path}")
        ShuraDatabase.Schema.migrate(old, oldVersion = 1, newVersion = ShuraDatabase.Schema.version)

        ShuraDatabase(old).settingsQueries.put("autoplay", "false")
        assertEquals("false", ShuraDatabase(old).settingsQueries.valueOf("autoplay").executeAsOne())
    }
}
