package io.github.javiernarvaezz.shura.feature.settings

import io.github.javiernarvaezz.shura.core.auth.Session
import io.github.javiernarvaezz.shura.core.auth.SessionStore
import io.github.javiernarvaezz.shura.core.auth.StoredSession
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.PendingLike
import io.github.javiernarvaezz.shura.core.player.PendingLikeStore
import io.github.javiernarvaezz.shura.core.testing.FakeLibrary
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountLikesTest {
    private val local = FakeLibrary()
    private val pending = MemoryPending()
    private val store = MemoryStore()
    private val sent = mutableListOf<Pair<String, Boolean>>()
    private var online = true
    private val song = testSong(1)
    private val accountA = Session(mapOf("SID" to "a"), "||userA", "A", 1)
    private val accountB = Session(mapOf("SID" to "b"), "||userB", "B", 1)
    private var clock = 100L

    private lateinit var account: AccountModel

    private fun TestScope.setUp(signedIn: Session?): AccountLikes {
        store.stored = signedIn?.let { StoredSession.Present(it) } ?: StoredSession.Absent
        account =
            AccountModel(
                store = store,
                check = { AccountCheck(true, it.accountName, emptyList()) },
                signOutRemotely = {},
                ioContext = StandardTestDispatcher(testScheduler),
                scope = backgroundScope,
                onSignedOut = { pending.clear() },
            )
        val likes =
            AccountLikes(
                local = local,
                pending = pending,
                account = account,
                send = { _, videoId, liked ->
                    if (!online) error("offline")
                    sent += videoId.value to liked
                },
                fingerprint = { it.dataSyncId },
                scope = backgroundScope,
                now = { clock++ },
            )
        runCurrent()
        return likes
    }

    @Test
    fun withANetworkALikeReachesTheAccountAndLeavesNothingPending() =
        runTest {
            val likes = setUp(accountA)
            likes.library.setLiked(song, true)
            runCurrent()
            assertEquals(listOf(song), local.likedSongs.value)
            assertEquals(listOf(song.videoId.value to true), sent)
            assertTrue(pending.all().isEmpty())
        }

    @Test
    fun removingALikeIsSentToo() =
        runTest {
            val likes = setUp(accountA)
            likes.library.setLiked(song, false)
            runCurrent()
            assertEquals(listOf(song.videoId.value to false), sent)
        }

    @Test
    fun offlineTheLikeWaitsAndGoesWhenTheNetworkIsBack() =
        runTest {
            val likes = setUp(accountA)
            online = false
            likes.library.setLiked(song, true)
            runCurrent()
            assertEquals(listOf(song), local.likedSongs.value)
            assertEquals(1, pending.all().size)
            assertTrue(sent.isEmpty())

            online = true
            likes.flush()
            runCurrent()
            assertEquals(listOf(song.videoId.value to true), sent)
            assertTrue(pending.all().isEmpty())
        }

    @Test
    fun likingThenUnlikingOfflineSendsOnlyTheLastAction() =
        runTest {
            val likes = setUp(accountA)
            online = false
            likes.library.setLiked(song, true)
            likes.library.setLiked(song, false)
            runCurrent()
            assertEquals(listOf(false), pending.all().map { it.liked })

            online = true
            likes.flush()
            runCurrent()
            assertEquals(listOf(song.videoId.value to false), sent)
        }

    @Test
    fun withoutASessionLikesStayLocalOnly() =
        runTest {
            val likes = setUp(signedIn = null)
            likes.library.setLiked(song, true)
            runCurrent()
            assertEquals(listOf(song), local.likedSongs.value)
            assertTrue(sent.isEmpty())
            assertTrue(pending.all().isEmpty())
        }

    @Test
    fun anActionForAnotherAccountIsDiscardedNotSent() =
        runTest {
            // Queued for account A (e.g. restored from a backup onto another phone), now B is signed in.
            pending.put(PendingLike(song.videoId, true, "||userA", 1))
            val likes = setUp(accountB)
            likes.flush()
            runCurrent()
            assertTrue(sent.isEmpty())
            assertTrue(pending.all().isEmpty())
        }

    @Test
    fun signingInWithAnotherAccountEmptiesTheQueue() =
        runTest {
            val likes = setUp(accountA)
            online = false
            likes.library.setLiked(song, true)
            runCurrent()
            // The session ends without reaching Google, and another account signs in.
            store.stored = StoredSession.Present(accountB)
            account.reload()
            online = true
            runCurrent()
            assertTrue(sent.isEmpty())
            assertTrue(pending.all().isEmpty())
        }

    @Test
    fun theSameAccountSigningInAgainGetsItsPendingActions() =
        runTest {
            pending.put(PendingLike(song.videoId, true, "||userA", 1))
            setUp(accountA)
            runCurrent()
            assertEquals(listOf(song.videoId.value to true), sent)
            assertTrue(pending.all().isEmpty())
        }

    @Test
    fun signingOutEmptiesTheQueue() =
        runTest {
            val likes = setUp(accountA)
            online = false
            likes.library.setLiked(song, true)
            runCurrent()
            account.signOut()
            runCurrent()
            assertTrue(pending.all().isEmpty())
        }

    private class MemoryPending : PendingLikeStore {
        private val items = LinkedHashMap<VideoId, PendingLike>()

        override suspend fun all() = items.values.toList()

        override suspend fun put(like: PendingLike) {
            items.remove(like.videoId)
            items[like.videoId] = like
        }

        override suspend fun remove(like: PendingLike) {
            if (items[like.videoId] == like) items.remove(like.videoId)
        }

        override suspend fun clear() = items.clear()
    }

    private class MemoryStore : SessionStore {
        var stored: StoredSession = StoredSession.Absent

        override fun load() = stored

        override fun save(session: Session) {
            stored = StoredSession.Present(session)
        }

        override fun clear() {
            stored = StoredSession.Absent
        }
    }
}
