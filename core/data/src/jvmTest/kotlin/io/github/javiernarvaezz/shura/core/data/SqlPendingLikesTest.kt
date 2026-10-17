package io.github.javiernarvaezz.shura.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.PendingLike
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlPendingLikesTest {
    private val database =
        ShuraDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ShuraDatabase.Schema.create(it) })
    private val song = VideoId("abcdefghijk")
    private val other = VideoId("bcdefghijkl")

    private fun TestScope.store() = SqlPendingLikes(database, StandardTestDispatcher(testScheduler))

    @Test
    fun theLatestActionForASongWins() =
        runTest {
            val store = store()
            store.put(PendingLike(song, true, "fp", 1))
            store.put(PendingLike(song, false, "fp", 2))
            store.put(PendingLike(other, true, "fp", 3))
            assertEquals(listOf(PendingLike(song, false, "fp", 2), PendingLike(other, true, "fp", 3)), store.all())
        }

    @Test
    fun removingAnOlderActionKeepsANewerOne() =
        runTest {
            val store = store()
            val older = PendingLike(song, true, "fp", 1)
            store.put(older)
            store.put(PendingLike(song, false, "fp", 2))
            store.remove(older)
            assertEquals(listOf(PendingLike(song, false, "fp", 2)), store.all())
        }

    @Test
    fun clearingEmptiesTheQueue() =
        runTest {
            val store = store()
            store.put(PendingLike(song, true, "fp", 1))
            store.clear()
            assertEquals(emptyList(), store.all())
        }
}
