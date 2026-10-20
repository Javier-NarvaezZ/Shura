package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.OfflineSong
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SqlOfflineSongsTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) }
    private val database = ShuraDatabase(driver)

    private fun TestScope.store() = SqlOfflineSongs(database, StandardTestDispatcher(testScheduler))

    private fun kept(
        n: Int,
        at: Long,
    ) = OfflineSong(
        Song(
            VideoId("offline${n.toString().padStart(4, '0')}"),
            "Song $n",
            listOf(Artist("Artist", "UC1"), Artist("Guest")),
            AlbumRef("Album", "MPREb_1"),
            200.seconds,
            "https://lh3.googleusercontent.com/$n",
            isExplicit = true,
        ),
        itag = 251,
        bytes = 3_500_000,
        savedAtMillis = at,
    )

    @Test
    fun keptSongsComeBackWholeNewestFirst() =
        runTest {
            val store = store()
            store.put(kept(1, at = 1))
            store.put(kept(2, at = 2))

            assertEquals(listOf(kept(2, at = 2), kept(1, at = 1)), store.songs.first())
            assertEquals(
                "offline0001:251",
                store.songs
                    .first()
                    .last()
                    .cacheKey,
            )
        }

    @Test
    fun aSongCanBeRemovedOrAllCleared() =
        runTest {
            val store = store()
            store.put(kept(1, at = 1))
            store.put(kept(2, at = 2))
            store.remove(kept(1, at = 1).song.videoId)
            assertEquals(listOf(kept(2, at = 2)), store.songs.first())

            store.clear()
            assertEquals(emptyList(), store.songs.first())
        }

    @Test
    fun theMigrationTo10AddsTheTable() =
        runTest {
            driver.execute(null, "DROP TABLE offline_song", 0)
            ShuraDatabase.Schema.migrate(driver, oldVersion = 9, newVersion = 10)
            store().put(kept(1, at = 1))
            assertEquals(1, store().songs.first().size)
        }
}
