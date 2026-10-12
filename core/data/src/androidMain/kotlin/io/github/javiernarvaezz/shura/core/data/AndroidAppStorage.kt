package io.github.javiernarvaezz.shura.core.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * The app's database (`shura.db`, app-private storage, excluded from backups), migrated to the current schema when
 * opened, behind the queue store and the settings.
 */
class AndroidAppStorage(
    context: Context,
    ioContext: CoroutineContext,
    scope: CoroutineScope,
) {
    private val database = ShuraDatabase(AndroidSqliteDriver(ShuraDatabase.Schema, context, "shura.db"))

    val queue = SqlQueueStore(database, ioContext)

    val settings = SqlPlaybackSettings(database, scope, ioContext)
}
