package io.github.javiernarvaezz.shura.feature.library

import io.github.javiernarvaezz.shura.core.auth.AuthState
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.testing.testSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountLibraryModelTest {
    private val auth = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private var clock = 0L
    private var loads = 0
    private var online = true
    private val snapshot =
        AccountLibrarySnapshot(
            likedSongs = listOf(testSong(1), testSong(2)),
            playlists = emptyList(),
            albums = listOf(AlbumSummary("MPREb_1", "Album")),
            artists = emptyList(),
        )

    private fun TestScope.model(): AccountLibraryModel {
        val model =
            AccountLibraryModel(
                auth = auth,
                load = {
                    loads++
                    if (!online) error("offline")
                    snapshot
                },
                now = { clock },
                scope = backgroundScope,
            )
        runCurrent()
        return model
    }

    @Test
    fun signedOutThereIsNoAccountSection() =
        runTest {
            val model = model()
            assertFalse(model.state.value.visible)
            assertEquals(0, loads)
        }

    @Test
    fun signingInLoadsTheAccountLibrary() =
        runTest {
            val model = model()
            auth.value = AuthState.SignedIn("A")
            runCurrent()
            assertTrue(model.state.value.visible)
            assertEquals(snapshot, model.state.value.snapshot)
            assertEquals(snapshot.likedSongs, model.likedSongs.value)
        }

    @Test
    fun itRefreshesAtMostEveryHalfHourUnlessAsked() =
        runTest {
            auth.value = AuthState.SignedIn("A")
            val model = model()
            runCurrent()
            clock += 10 * MINUTE
            model.refresh()
            runCurrent()
            assertEquals(1, loads)
            clock += 25 * MINUTE
            model.refresh()
            runCurrent()
            assertEquals(2, loads)
            model.refresh(force = true)
            runCurrent()
            assertEquals(3, loads)
        }

    @Test
    fun offlineTheLastLibraryStaysWithANotice() =
        runTest {
            auth.value = AuthState.SignedIn("A")
            val model = model()
            runCurrent()
            online = false
            model.refresh(force = true)
            runCurrent()
            assertTrue(model.state.value.offline)
            assertEquals(snapshot, model.state.value.snapshot)
        }

    @Test
    fun anExpiredSessionAsksToSignInAndShowsNothingOfTheAccount() =
        runTest {
            auth.value = AuthState.SignedIn("A")
            val model = model()
            runCurrent()
            auth.value = AuthState.Expired
            runCurrent()
            assertTrue(model.state.value.signInNeeded)
            assertNull(model.state.value.snapshot)
            assertEquals(emptyList(), model.likedSongs.value)
        }

    @Test
    fun signingOutForgetsTheAccountLibrary() =
        runTest {
            auth.value = AuthState.SignedIn("A")
            val model = model()
            runCurrent()
            auth.value = AuthState.SignedOut
            runCurrent()
            assertFalse(model.state.value.visible)
            assertNull(model.state.value.snapshot)
        }

    @Test
    fun likedSongsJoinTheAccountsFirstThenLocalOnesNotThere() {
        val a = testSong(1)
        val b = testSong(2)
        val c = testSong(3)
        assertEquals(listOf(a, b, c), mergeLiked(account = listOf(a, b), local = listOf(b, c)))
        assertEquals(listOf(c), mergeLiked(account = emptyList(), local = listOf(c)))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
