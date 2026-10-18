package io.github.javiernarvaezz.shura.feature.settings

import io.github.javiernarvaezz.shura.core.auth.AuthState
import io.github.javiernarvaezz.shura.core.auth.Session
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.Library
import io.github.javiernarvaezz.shura.core.player.PendingLike
import io.github.javiernarvaezz.shura.core.player.PendingLikeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Me gusta" with an account: every like goes to the local library at once and, while signed in, to a queue that
 * is sent to YouTube Music ([send]). Offline, or with an expired session, it waits; it is sent when [flush] runs
 * again (network back, sign-in, start). Liking then unliking sends only the last action. Each queued action
 * carries an anonymous [fingerprint] of its account and is sent only to that account; one for another account is
 * discarded. Signing out empties the queue ([AccountModel]'s sign-out hook). Removing a like also removes it from
 * the videos that stand for the song ([videosOf]): songs from albums were once liked as the artist's video.
 */
class AccountLikes(
    private val local: Library,
    private val pending: PendingLikeStore,
    private val account: AccountModel,
    private val send: suspend (Session, VideoId, Boolean) -> Unit,
    private val fingerprint: (Session) -> String?,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val videosOf: (VideoId) -> List<VideoId> = { emptyList() },
) {
    private val mutex = Mutex()

    /** The library the app uses: the local one, whose likes also reach the account. */
    val library: Library = SyncedLibrary()

    init {
        scope.launch { account.state.collect { if (it is AuthState.SignedIn) flush() } }
    }

    /** Sends what is pending to the signed-in account; stops at the first failure and keeps the rest. */
    fun flush() {
        scope.launch {
            mutex.withLock {
                val session = account.session() ?: return@withLock
                val current = fingerprint(session) ?: return@withLock
                val (own, foreign) = pending.all().partition { it.account == current }
                foreign.forEach { pending.remove(it) }
                for (like in own) {
                    if (!sent(session, like)) break
                }
            }
        }
    }

    /** Whether [like] reached the account (and left the queue); false keeps it for the next flush. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // A failed send waits for the next flush.
    private suspend fun sent(
        session: Session,
        like: PendingLike,
    ): Boolean =
        try {
            send(session, like.videoId, like.liked)
            pending.remove(like)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    private fun queue(
        song: Song,
        liked: Boolean,
    ) {
        scope.launch {
            val session = account.session() ?: return@launch
            val owner = fingerprint(session) ?: return@launch
            val targets = if (liked) listOf(song.videoId) else listOf(song.videoId) + videosOf(song.videoId)
            mutex.withLock { targets.forEach { pending.put(PendingLike(it, liked, owner, now())) } }
            flush()
        }
    }

    private inner class SyncedLibrary : Library by local {
        override fun setLiked(
            song: Song,
            liked: Boolean,
        ) {
            local.setLiked(song, liked)
            queue(song, liked)
        }
    }
}
