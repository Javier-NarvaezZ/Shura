package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.QueueSnapshot
import io.github.javiernarvaezz.shura.core.player.RepeatMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SqlQueueStoreTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) }
    private val database = ShuraDatabase(driver)

    private fun TestScope.store() = SqlQueueStore(database, StandardTestDispatcher(testScheduler))

    private val full =
        Song(
            videoId = VideoId("7fwUH0oRmkQ"),
            title = "La Camisa Negra",
            artists = listOf(Artist("Juanes", "UC123"), Artist("Otro")),
            album = AlbumRef("Mi Sangre", "MPREb_1"),
            duration = 217.seconds,
            thumbnailUrl = "https://lh3.googleusercontent.com/x=w120-h120",
            isExplicit = true,
        )
    private val minimal = Song(VideoId("dQw4w9WgXcQ"), "Minimal", emptyList())
    private val third = Song(VideoId("kJQP7kiw5Fk"), "Third", listOf(Artist("Y")))

    private fun songs(count: Int) =
        List(count) { i -> Song(VideoId("abcdefgh" + i.toString().padStart(3, '0')), "Song $i", listOf(Artist("A$i"))) }

    @Test
    fun emptyDatabaseLoadsNothing() =
        runTest {
            assertNull(store().load())
        }

    @Test
    fun snapshotRoundTrips() =
        runTest {
            val snapshot =
                QueueSnapshot(
                    items = listOf(full, minimal, third),
                    currentIndex = 1,
                    positionMs = 42_000,
                    shuffle = true,
                    repeat = RepeatMode.All,
                    playOrder = listOf(1, 2, 0),
                )

            store().save(snapshot)

            assertEquals(snapshot, store().load())
        }

    @Test
    fun aLongQueueRoundTrips() =
        runTest {
            val items = songs(50)
            val snapshot = QueueSnapshot(items, 49, 0, false, RepeatMode.Off, items.indices.toList())

            store().save(snapshot)

            assertEquals(snapshot, store().load())
        }

    @Test
    fun savingReplacesThePreviousQueueAndAnEmptyQueueClearsIt() =
        runTest {
            val store = store()
            store.save(QueueSnapshot(songs(5), 0, 0, false, RepeatMode.Off, listOf(0, 1, 2, 3, 4)))
            val second = QueueSnapshot(listOf(third), 0, 5, false, RepeatMode.One, listOf(0))

            store.save(second)
            assertEquals(second, store.load())

            store.save(QueueSnapshot(emptyList(), 0, 0, false, RepeatMode.Off, emptyList()))
            assertNull(store.load())
        }

    @Test
    fun corruptDataIsDiscarded() =
        runTest {
            val store = store()
            store.save(QueueSnapshot(listOf(full, minimal), 0, 0, true, RepeatMode.Off, listOf(1, 0)))
            // Simulate a broken row: the play order is no longer a permutation.
            driver.execute(null, "UPDATE queue_state SET play_order = '[0,0]'", 0)

            assertNull(store.load())
            // The broken queue is gone, not reloaded every time.
            assertEquals(0L, database.queueQueries.countItems().executeAsOne())
        }

    @Test
    fun historyIsNewestFirstAndCapped() =
        runTest {
            val store = store()
            val many = songs(SqlQueueStore.MAX_HISTORY + 20)
            many.forEachIndexed { i, song -> store.recordPlay(song, atMillis = 1_000L + i) }

            val recent = store.recent(limit = 3).first()

            assertEquals(many.takeLast(3).reversed(), recent)
            assertEquals(SqlQueueStore.MAX_HISTORY.toLong(), database.historyQueries.countPlays().executeAsOne())
        }

    @Test
    fun aSongPlayedAgainAppearsOnceAtItsLatestPlay() =
        runTest {
            val store = store()
            store.recordPlay(full, atMillis = 1_000)
            store.recordPlay(minimal, atMillis = 2_000)
            store.recordPlay(third, atMillis = 3_000)
            store.recordPlay(full, atMillis = 4_000)

            assertEquals(listOf(full, third, minimal), store.recent(limit = 10).first())
        }

    @Test
    fun theLimitCountsDistinctSongs() =
        runTest {
            val store = store()
            repeat(5) { store.recordPlay(full, atMillis = 1_000L + it) }
            store.recordPlay(minimal, atMillis = 2_000)
            store.recordPlay(third, atMillis = 3_000)

            assertEquals(listOf(third, minimal), store.recent(limit = 2).first())
        }

    @Test
    fun theLatestPlayCarriesTheSongDetails() =
        runTest {
            val store = store()
            store.recordPlay(minimal, atMillis = 1_000)
            val renamed = minimal.copy(title = "Minimal (Remastered)")
            store.recordPlay(renamed, atMillis = 2_000)

            assertEquals(listOf(renamed), store.recent(limit = 10).first())
        }
}
