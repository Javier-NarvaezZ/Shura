package io.github.javiernarvaezz.shura.core.data

import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.player.PendingLike
import io.github.javiernarvaezz.shura.core.player.PendingLikeStore
import io.github.javiernarvaezz.shura.core.player.videoIdOf
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [PendingLikeStore] in the app database, on [ioContext]. A row with an invalid video id is skipped. */
class SqlPendingLikes(
    private val database: ShuraDatabase,
    private val ioContext: CoroutineContext,
) : PendingLikeStore {
    private val queries get() = database.accountLikesQueries

    override suspend fun all(): List<PendingLike> =
        withContext(ioContext) {
            queries.pendingLikes().executeAsList().mapNotNull { row ->
                videoIdOf(row.video_id)?.let { PendingLike(it, row.liked != 0L, row.account, row.queued_at) }
            }
        }

    override suspend fun put(like: PendingLike) {
        withContext(ioContext) {
            queries.putPendingLike(like.videoId.value, like.liked.toLong(), like.account, like.queuedAtMillis)
        }
    }

    override suspend fun remove(like: PendingLike) {
        withContext(ioContext) {
            queries.removePendingLike(like.videoId.value, like.liked.toLong(), like.account, like.queuedAtMillis)
        }
    }

    override suspend fun clear() {
        withContext(ioContext) { queries.clearPendingLikes() }
    }
}
