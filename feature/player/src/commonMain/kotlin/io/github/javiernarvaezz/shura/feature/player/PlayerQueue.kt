package io.github.javiernarvaezz.shura.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.ui.components.SongRow
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.player.resources.Res
import io.github.javiernarvaezz.shura.feature.player.resources.move_down
import io.github.javiernarvaezz.shura.feature.player.resources.move_up
import io.github.javiernarvaezz.shura.feature.player.resources.queue_shuffled_hint
import io.github.javiernarvaezz.shura.feature.player.resources.remove_from_queue
import org.jetbrains.compose.resources.stringResource

/**
 * The queue as a mode of the player: rows in play order with the current song marked. Tap a row to play it;
 * move songs up or down (only without shuffle, where the queue order is the play order) or remove them.
 */
@Composable
internal fun PlayerQueue(
    model: PlayerModel,
    canReorder: Boolean,
    onRemoved: (RemovedSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by model.queue.queueRows.collectAsStateWithLifecycle()
    val keyed = remember(rows) { withStableKeys(rows) }
    Column(modifier) {
        if (!canReorder) {
            Text(
                stringResource(Res.string.queue_shuffled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Shura.colors.textMuted,
                modifier = Modifier.padding(horizontal = Shura.spacing.gutter).padding(bottom = Shura.spacing.s),
            )
        }
        LazyColumn {
            items(keyed, key = { it.first }) { (_, row) ->
                SongRow(
                    title = row.song.title,
                    subtitle = row.song.artistNames,
                    artworkUrl = row.song.thumbnailUrl,
                    explicit = row.song.isExplicit,
                    isCurrent = row.isCurrent,
                    onClick = { model.queue.playAt(row.index) },
                    modifier = if (row.isCurrent) Modifier.background(Shura.colors.surface2) else Modifier,
                ) {
                    if (canReorder) {
                        IconButton(modifier = Modifier.size(ACTION_SIZE), onClick = { model.queue.moveUp(row.index) }) {
                            Icon(ShuraIcons.MoveUp, stringResource(Res.string.move_up), tint = Shura.colors.textMuted)
                        }
                        IconButton(
                            modifier = Modifier.size(ACTION_SIZE),
                            onClick = { model.queue.moveDown(row.index) },
                        ) {
                            Icon(
                                ShuraIcons.MoveDown,
                                stringResource(Res.string.move_down),
                                tint = Shura.colors.textMuted,
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier.size(ACTION_SIZE),
                        onClick = { model.queue.remove(row.index)?.let(onRemoved) },
                    ) {
                        Icon(
                            ShuraIcons.Delete,
                            stringResource(Res.string.remove_from_queue),
                            tint = Shura.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Each row with a key that is stable across moves: the song's id plus which occurrence of it this is (a song can
 * be queued twice). The queue index changes on every move or removal and would rebuild the rows. Keys travel with
 * their rows, so the list never looks up a key from an older version of the queue.
 */
private fun withStableKeys(rows: List<QueueRow>): List<Pair<String, QueueRow>> {
    val seen = HashMap<String, Int>()
    return rows.map { row ->
        val id = row.song.videoId.value
        val n = seen.getOrElse(id) { 0 }
        seen[id] = n + 1
        "$id#$n" to row
    }
}

private val ACTION_SIZE = 40.dp
