package io.github.javiernarvaezz.shura.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.PlaybackState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Spike UI: hardcoded Spanish strings; resources and branding come in Phase 1B/2.
@Composable
fun SearchScreen(controller: SearchController) {
    val state by controller.state.collectAsState()
    val playback by controller.playback.collectAsState()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = controller::onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Buscar canciones") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { controller.submit() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = controller::submit) { Text("Buscar") }
        }
        SearchStatusLine(state.status, onRetry = controller::submit)
        LazyColumn(Modifier.weight(1f)) {
            items(state.results, key = { it.videoId.value }) { song ->
                SongRow(song, onClick = { controller.play(song) })
                HorizontalDivider()
            }
        }
        NowPlayingBar(
            playback,
            onToggle = controller::togglePause,
            onSeek = controller::seekBy,
            onRetry = controller::retryPlayback,
        )
    }
}

@Composable
private fun SearchStatusLine(
    status: SearchStatus,
    onRetry: () -> Unit,
) {
    val modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    when (status) {
        SearchStatus.Loading -> {
            Text("Buscando…", modifier)
        }

        SearchStatus.Empty -> {
            Text("Sin resultados.", modifier)
        }

        SearchStatus.Failed -> {
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Text("No se pudo buscar.")
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        }

        SearchStatus.Idle, SearchStatus.Done -> {
            Unit
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (song.isExplicit) Text("  E", style = MaterialTheme.typography.labelSmall)
        }
        val details = listOfNotNull(song.artistNames.ifBlank { null }, song.album?.title, song.duration?.toClock())
        Text(
            details.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NowPlayingBar(
    playback: PlaybackState,
    onToggle: () -> Unit,
    onSeek: (Duration) -> Unit,
    onRetry: () -> Unit,
) {
    val (song, label, action) =
        when (playback) {
            PlaybackState.Idle -> {
                return
            }

            is PlaybackState.Loading -> {
                Triple(playback.song, "Cargando…", null)
            }

            is PlaybackState.Playing -> {
                Triple(playback.song, "Reproduciendo", "Pausa" to onToggle)
            }

            is PlaybackState.Paused -> {
                Triple(playback.song, "En pausa", "Reanudar" to onToggle)
            }

            is PlaybackState.Ended -> {
                Triple(playback.song, "Terminó", null)
            }

            is PlaybackState.Failed -> {
                Triple(
                    playback.song,
                    playbackErrorMessage(playback.error),
                    "Reintentar" to onRetry,
                )
            }
        }
    val canSeek = playback is PlaybackState.Playing || playback is PlaybackState.Paused
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
                action?.let { (text, onClick) -> TextButton(onClick = onClick) { Text(text) } }
            }
            if (canSeek) {
                Row {
                    TextButton(onClick = { onSeek(-SEEK_BACK) }) { Text("−10 s") }
                    TextButton(onClick = { onSeek(SEEK_FORWARD) }) { Text("+30 s") }
                }
            }
        }
    }
}

private val SEEK_BACK = 10.seconds
private val SEEK_FORWARD = 30.seconds

private fun Duration.toClock(): String =
    toComponents { minutes, seconds, _ -> "$minutes:${seconds.toString().padStart(2, '0')}" }
