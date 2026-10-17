package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.VideoId

/**
 * A "Me gusta" not yet sent to the account. [account] is an anonymous fingerprint of the account it was made with
 * (a hash, never the id itself): it is sent only to that same account.
 */
data class PendingLike(
    val videoId: VideoId,
    val liked: Boolean,
    val account: String,
    val queuedAtMillis: Long,
)

/** Likes waiting to reach the account; one per song, the latest action wins. */
interface PendingLikeStore {
    suspend fun all(): List<PendingLike>

    /** Replaces any pending action for the same song. */
    suspend fun put(like: PendingLike)

    /** Removes [like], unless a newer action for the song replaced it meanwhile. */
    suspend fun remove(like: PendingLike)

    suspend fun clear()
}
