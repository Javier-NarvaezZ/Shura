package io.github.javiernarvaezz.shura.core.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import kotlin.coroutines.CoroutineContext

/** The app's database (`shura.db`, app-private storage, excluded from backups) behind a [SqlQueueStore]. */
fun androidQueueStore(
    context: Context,
    ioContext: CoroutineContext,
): SqlQueueStore =
    SqlQueueStore(ShuraDatabase(AndroidSqliteDriver(ShuraDatabase.Schema, context, "shura.db")), ioContext)
