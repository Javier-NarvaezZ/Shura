package io.github.javiernarvaezz.shura.core.data

import io.github.javiernarvaezz.shura.core.data.db.ShuraDatabase
import io.github.javiernarvaezz.shura.core.player.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * [PlaybackSettings] in the app database, read and written on [ioContext] (never on the main thread). Until the
 * stored values are read (milliseconds after launch) the defaults stand in; a change made meanwhile wins.
 */
class SqlPlaybackSettings(
    private val database: ShuraDatabase,
    private val scope: CoroutineScope,
    private val ioContext: CoroutineContext,
) : PlaybackSettings {
    private val mutableAutoplay = MutableStateFlow(AUTOPLAY_DEFAULT)
    private var changed = false

    override val autoplay: StateFlow<Boolean> = mutableAutoplay.asStateFlow()

    init {
        scope.launch(ioContext) {
            val stored =
                database.settingsQueries
                    .valueOf(AUTOPLAY)
                    .executeAsOneOrNull()
                    ?.toBooleanStrictOrNull()
            if (stored != null) mutableAutoplay.update { if (changed) it else stored }
        }
    }

    override fun setAutoplay(enabled: Boolean) {
        changed = true
        mutableAutoplay.value = enabled
        scope.launch(ioContext) { database.settingsQueries.put(AUTOPLAY, enabled.toString()) }
    }

    private companion object {
        const val AUTOPLAY = "autoplay"
        const val AUTOPLAY_DEFAULT = true
    }
}
