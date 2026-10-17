package io.github.javiernarvaezz.shura.feature.library

import io.github.javiernarvaezz.shura.core.auth.AuthState
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The account's library as last loaded. */
data class AccountLibrarySnapshot(
    val likedSongs: List<Song>,
    val playlists: List<PlaylistSummary>,
    val albums: List<AlbumSummary>,
    val artists: List<ArtistSummary>,
)

/** What the Library tab shows of the account. */
data class AccountLibraryState(
    /** Whether there is an account section (signed in, or a session to renew). */
    val visible: Boolean = false,
    /** The session expired or could not be read: the section asks to sign in again. */
    val signInNeeded: Boolean = false,
    val loading: Boolean = false,
    /** The last refresh could not reach YouTube Music; [snapshot] is what was loaded before. */
    val offline: Boolean = false,
    val snapshot: AccountLibrarySnapshot? = null,
)

/**
 * The signed-in account's library for the Library tab: loaded on sign-in and refreshed at most every
 * [REFRESH_MS] unless the user asks; offline it keeps the last one with a notice. Signing out or an expired
 * session forgets it.
 */
class AccountLibraryModel(
    auth: StateFlow<AuthState>,
    private val load: suspend () -> AccountLibrarySnapshot?,
    private val now: () -> Long,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AccountLibraryState())
    private var loadedAt: Long? = null
    private var job: Job? = null
    private var signedIn = false

    val state: StateFlow<AccountLibraryState> = mutableState.asStateFlow()

    /** The account's liked songs, empty without an account library. */
    val likedSongs: StateFlow<List<Song>> =
        state
            .map { it.snapshot?.likedSongs.orEmpty() }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            auth.collect { auth ->
                signedIn = auth is AuthState.SignedIn
                when (auth) {
                    is AuthState.SignedIn -> {
                        mutableState.update { it.copy(visible = true, signInNeeded = false) }
                        refresh()
                    }

                    AuthState.Expired, AuthState.NeedsSignIn -> {
                        forget()
                        mutableState.value = AccountLibraryState(visible = true, signInNeeded = true)
                    }

                    AuthState.SignedOut -> {
                        forget()
                        mutableState.value = AccountLibraryState()
                    }
                }
            }
        }
    }

    /** Loads the library again if it is older than [REFRESH_MS], or always with [force] (pull to refresh). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // A failed refresh keeps the last library.
    fun refresh(force: Boolean = false) {
        if (!signedIn || job?.isActive == true) return
        val last = loadedAt
        if (!force && last != null && now() - last < REFRESH_MS) return
        job =
            scope.launch {
                mutableState.update { it.copy(loading = true) }
                try {
                    val loaded = load()
                    loadedAt = now()
                    mutableState.update { it.copy(loading = false, offline = false, snapshot = loaded) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableState.update { it.copy(loading = false, offline = true) }
                }
            }
    }

    private fun forget() {
        job?.cancel()
        loadedAt = null
    }

    companion object {
        const val REFRESH_MS = 30 * 60 * 1000L
    }
}

/** The liked songs to show: the account's in its order, then local ones it does not have. */
fun mergeLiked(
    account: List<Song>,
    local: List<Song>,
): List<Song> {
    val seen = account.mapTo(HashSet()) { it.videoId }
    return account + local.filter { it.videoId !in seen }
}
