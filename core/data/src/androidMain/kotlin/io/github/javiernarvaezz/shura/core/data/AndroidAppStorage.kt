package io.github.javiernarvaezz.shura.core.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * The app's database (`shura.db`, app-private storage), migrated to the current schema when opened, behind the
 * queue store, the settings, the lyrics cache and the library. It is the only file in Android backups and device
 * transfers, by name (the app's backup rules): nothing secret may be stored in it.
 */
class AndroidAppStorage(
    context: Context,
    private val ioContext: CoroutineContext,
    scope: CoroutineScope,
) {
    private val database = ShuraDatabase(AndroidSqliteDriver(ShuraDatabase.Schema, context, "shura.db"))

    val queue = SqlQueueStore(database, ioContext)

    val settings = SqlPlaybackSettings(database, scope, ioContext)

    val lyrics = SqlLyricsStore(database, ioContext)

    val lyricsOffsets = SqlLyricsOffsets(database, ioContext)

    /** Moves songs saved as the artist's video to their album track ([SavedSongRepair]). */
    fun savedSongRepair(albumTracks: suspend (albumId: String) -> Map<VideoId, VideoId>?) =
        SavedSongRepair(database, ioContext, albumTracks)

    val library = SqlLibrary(database, scope, ioContext)

    val pendingLikes = SqlPendingLikes(database, ioContext)
}
