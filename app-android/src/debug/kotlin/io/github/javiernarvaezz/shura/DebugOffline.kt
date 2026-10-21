package io.github.javiernarvaezz.shura

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "SunloopOffline"
private const val EXTRA = "shura.debug.offline"
private const val QUEUE_WAIT_MS = 10_000L

/**
 * Debug builds only, until the download UI exists (`--es shura.debug.offline download|remove|state`): keeps or
 * removes the song playing, or logs each kept song's state. Logs video ids and states only.
 */
internal fun debugOffline(
    intent: Intent,
    graph: AppGraph,
) {
    val action = intent.getStringExtra(EXTRA) ?: return
    CoroutineScope(Dispatchers.Main).launch {
        // The queue is restored once the player connects.
        val current =
            withTimeoutOrNull(QUEUE_WAIT_MS) {
                graph.player.queue
                    .first { it.current != null }
                    .current
            }
        when (action) {
            "download" -> current?.let(graph.offline::download)
            "remove" -> current?.let { graph.offline.remove(it.videoId) }
            "removeAll" -> graph.offline.removeAll()
        }
        Log.i(TAG, "$action: ${current?.videoId?.value}")
        graph.offline.states.collect { states ->
            Log.i(TAG, "states: ${states.entries.joinToString { "${it.key.value}=${it.value}" }}")
            Log.i(TAG, "used bytes: ${graph.offline.usedBytes.value}")
        }
    }
}
