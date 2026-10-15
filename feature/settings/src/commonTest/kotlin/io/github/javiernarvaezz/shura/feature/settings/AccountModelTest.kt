package io.github.javiernarvaezz.shura.feature.settings

import io.github.javiernarvaezz.shura.core.auth.AuthState
import io.github.javiernarvaezz.shura.core.auth.Session
import io.github.javiernarvaezz.shura.core.auth.SessionStore
import io.github.javiernarvaezz.shura.core.auth.StoredSession
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountModelTest {
    private val store = MemoryStore()
    private val session = Session(linkedMapOf("SID" to "1", "SIDCC" to "old"), "||u", null, 5)
    private var check: suspend (Session) -> AccountCheck = { AccountCheck(true, "Test Account", emptyList()) }
    private val signOuts = mutableListOf<Session>()
    private var signOutFails = false

    private fun TestScope.model(): AccountModel {
        val model =
            AccountModel(
                store = store,
                check = { check(it) },
                signOutRemotely = {
                    signOuts += it
                    if (signOutFails) error("offline")
                },
                ioContext = StandardTestDispatcher(testScheduler),
                scope = backgroundScope,
            )
        runCurrent()
        return model
    }

    @Test
    fun withoutASessionTheUserIsSignedOut() =
        runTest {
            assertEquals(AuthState.SignedOut, model().state.value)
        }

    @Test
    fun aStoredSessionIsCheckedAndShowsTheAccountName() =
        runTest {
            store.stored = StoredSession.Present(session)
            val model = model()
            runCurrent()
            assertEquals(AuthState.SignedIn("Test Account"), model.state.value)
            assertEquals("Test Account", (store.stored as StoredSession.Present).session.accountName)
        }

    @Test
    fun rotatedCookiesAreKept() =
        runTest {
            store.stored = StoredSession.Present(session)
            check = { AccountCheck(true, "Test Account", listOf("SIDCC=new; path=/")) }
            model()
            runCurrent()
            assertEquals(mapOf("SID" to "1", "SIDCC" to "new"), (store.stored as StoredSession.Present).session.cookies)
        }

    @Test
    fun aSessionGoogleNoLongerAcceptsExpiresAndIsDeleted() =
        runTest {
            store.stored = StoredSession.Present(session)
            check = { AccountCheck(false, null, emptyList()) }
            val model = model()
            runCurrent()
            assertEquals(AuthState.Expired, model.state.value)
            assertEquals(StoredSession.Absent, store.stored)
        }

    @Test
    fun aCheckThatCannotReachGoogleKeepsTheSession() =
        runTest {
            store.stored = StoredSession.Present(session.copy(accountName = "Known"))
            check = { error("offline") }
            val model = model()
            runCurrent()
            assertEquals(AuthState.SignedIn("Known"), model.state.value)
            assertTrue(store.stored is StoredSession.Present)
        }

    @Test
    fun anUnreadableSessionAsksToSignInAgain() =
        runTest {
            store.stored = StoredSession.Unreadable
            assertEquals(AuthState.NeedsSignIn, model().state.value)
        }

    @Test
    fun signingInSavesTheSessionAndFetchesTheName() =
        runTest {
            val model = model()
            model.onSignedIn(session)
            runCurrent()
            assertEquals(AuthState.SignedIn("Test Account"), model.state.value)
            assertTrue(store.stored is StoredSession.Present)
        }

    @Test
    fun signingOutEndsTheSessionAtGoogleThenDeletesIt() =
        runTest {
            store.stored = StoredSession.Present(session)
            val model = model()
            runCurrent()
            model.signOut()
            runCurrent()
            assertEquals(1, signOuts.size)
            assertEquals(StoredSession.Absent, store.stored)
            assertEquals(AuthState.SignedOut, model.state.value)
        }

    @Test
    fun signingOutStillDeletesTheSessionWhenGoogleCannotBeReached() =
        runTest {
            store.stored = StoredSession.Present(session)
            signOutFails = true
            val model = model()
            runCurrent()
            model.signOut()
            runCurrent()
            assertEquals(StoredSession.Absent, store.stored)
            assertEquals(AuthState.SignedOut, model.state.value)
        }

    @Test
    fun theSessionForAccountRequestsIsTheStoredOne() =
        runTest {
            store.stored = StoredSession.Present(session)
            val model = model()
            runCurrent()
            assertEquals(session.cookies, model.session()?.cookies)
            model.signOut()
            runCurrent()
            assertNull(model.session())
        }

    private class MemoryStore : SessionStore {
        var stored: StoredSession = StoredSession.Absent

        override fun load(): StoredSession =
            stored.also { if (it == StoredSession.Unreadable) stored = StoredSession.Absent }

        override fun save(session: Session) {
            stored = StoredSession.Present(session)
        }

        override fun clear() {
            stored = StoredSession.Absent
        }
    }
}
