package io.github.javiernarvaezz.shura.feature.settings

import io.github.javiernarvaezz.shura.core.auth.AuthState
import io.github.javiernarvaezz.shura.core.auth.CookieJarText
import io.github.javiernarvaezz.shura.core.auth.Session
import io.github.javiernarvaezz.shura.core.auth.SessionStore
import io.github.javiernarvaezz.shura.core.auth.StoredSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** What asking Google about the session found. */
class AccountCheck(
    val signedIn: Boolean,
    val accountName: String?,
    /** `Set-Cookie` headers of the answer: rotated session cookies to keep. */
    val setCookies: List<String>,
)

/**
 * The YouTube Music account: whether the user is signed in, the account's name, and signing in and out. The
 * stored session is checked with Google at start: rotated cookies are kept, a session Google no longer accepts is
 * deleted (Expired), and a check that cannot reach Google changes nothing. Signing out first ends the session at
 * Google ([signOutRemotely], best effort), then deletes it here.
 */
class AccountModel(
    private val store: SessionStore,
    private val check: suspend (Session) -> AccountCheck,
    private val signOutRemotely: suspend (Session) -> Unit,
    private val ioContext: CoroutineContext,
    private val scope: CoroutineScope,
    /** Told whether Google accepted the session each time it answered a check (to measure how long one lasts). */
    private val onChecked: (alive: Boolean) -> Unit = {},
    /** Told when the user signs out, after the session is deleted (e.g. to drop actions queued for it). */
    private val onSignedOut: suspend () -> Unit = {},
) {
    private val mutableState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val mutex = Mutex()
    private var current: Session? = null

    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    init {
        reload()
    }

    /** The session for account requests (Phase 3, E5); null when signed out. */
    suspend fun session(): Session? = mutex.withLock { current }

    /**
     * Reads the stored session again (at start, and after the sign-in screen, which stores a new session from its
     * own process), then checks it with Google.
     */
    fun reload() {
        scope.launch {
            mutex.withLock {
                when (val stored = withContext(ioContext) { store.load() }) {
                    is StoredSession.Present -> {
                        current = stored.session
                        mutableState.value = AuthState.SignedIn(stored.session.accountName)
                    }

                    StoredSession.Absent -> {
                        if (current != null || mutableState.value is AuthState.SignedIn) {
                            mutableState.value = AuthState.SignedOut
                        }
                        current = null
                    }

                    StoredSession.Unreadable -> {
                        current = null
                        mutableState.value = AuthState.NeedsSignIn
                    }
                }
            }
            refresh()
        }
    }

    /** Asks Google whether the session is still signed in; see the class description. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Unreachable Google leaves the session as is.
    fun refresh() {
        scope.launch {
            val session = mutex.withLock { current } ?: return@launch
            val answer =
                try {
                    check(session)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch
                }
            withContext(ioContext) { onChecked(answer.signedIn) }
            mutex.withLock {
                if (current !== session) return@withLock
                if (answer.signedIn) {
                    val updated =
                        session.copy(
                            cookies = CookieJarText.merge(session.cookies, answer.setCookies),
                            accountName = answer.accountName ?: session.accountName,
                        )
                    withContext(ioContext) { store.save(updated) }
                    current = updated
                    mutableState.value = AuthState.SignedIn(updated.accountName)
                } else {
                    withContext(ioContext) { store.clear() }
                    current = null
                    mutableState.value = AuthState.Expired
                }
            }
        }
    }

    /** Keeps session cookies an account response rotated ([setCookies]), so the stored session stays current. */
    fun keepCookies(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        scope.launch {
            mutex.withLock {
                val session = current ?: return@withLock
                val cookies = CookieJarText.merge(session.cookies, setCookies)
                if (cookies == session.cookies) return@withLock
                val updated = session.copy(cookies = cookies)
                withContext(ioContext) { store.save(updated) }
                current = updated
            }
        }
    }

    /** Ends the session at Google (best effort), then deletes it here; always ends signed out. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // The local session is deleted either way.
    fun signOut() {
        scope.launch {
            mutex.withLock {
                val session = current
                if (session != null) {
                    try {
                        signOutRemotely(session)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Offline or refused: the session is still deleted from this device.
                    }
                }
                withContext(ioContext) { store.clear() }
                current = null
                mutableState.value = AuthState.SignedOut
            }
            onSignedOut()
        }
    }
}
