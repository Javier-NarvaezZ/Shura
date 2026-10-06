package io.github.javiernarvaezz.shura.feature.search

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.ui.components.SongRow
import io.github.javiernarvaezz.shura.core.ui.components.rememberHideKeyboard
import io.github.javiernarvaezz.shura.core.ui.components.shimmer
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.text.toClock
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.search.resources.Res
import io.github.javiernarvaezz.shura.feature.search.resources.add_to_queue
import io.github.javiernarvaezz.shura.feature.search.resources.clear
import io.github.javiernarvaezz.shura.feature.search.resources.empty
import io.github.javiernarvaezz.shura.feature.search.resources.failed
import io.github.javiernarvaezz.shura.feature.search.resources.field_hint
import io.github.javiernarvaezz.shura.feature.search.resources.idle_hint
import io.github.javiernarvaezz.shura.feature.search.resources.more_actions
import io.github.javiernarvaezz.shura.feature.search.resources.play_next
import io.github.javiernarvaezz.shura.feature.search.resources.queued_end
import io.github.javiernarvaezz.shura.feature.search.resources.queued_next
import io.github.javiernarvaezz.shura.feature.search.resources.retry
import io.github.javiernarvaezz.shura.feature.search.resources.title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Search: a field fixed at the top and song results with play-next and add-to-queue actions. */
@Composable
fun SearchScreen(
    controller: SearchController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val queue by controller.queue.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Where focus goes on submit. Clearing focus is not enough: with nothing focused, the view restores it to the
    // field (seen on Android 12), which reopens the keyboard.
    val focusSink = remember { FocusRequester() }
    val hideKeyboard = rememberHideKeyboard()
    val notify: (suspend () -> String) -> Unit = { message ->
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message())
        }
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.size(0.dp).focusRequester(focusSink).focusable())
            Title()
            SearchField(
                query = state.query,
                onQueryChange = controller::onQueryChange,
                onSubmit = {
                    focusSink.requestFocus()
                    hideKeyboard()
                    controller.submit()
                },
            )
            SearchBody(
                state = state,
                currentId = queue.current?.videoId?.value,
                controller = controller,
                onQueued = { next ->
                    notify { getString(if (next) Res.string.queued_next else Res.string.queued_end) }
                },
            )
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Title() {
    Text(
        stringResource(Res.string.title),
        style = MaterialTheme.typography.headlineMedium,
        color = Shura.colors.text,
        modifier =
            Modifier
                .padding(horizontal = Shura.spacing.gutter)
                .padding(top = Shura.spacing.xl, bottom = Shura.spacing.m),
    )
}

/** What is below the field for each search status. [onQueued] reports a queue action (true: play next). */
@Composable
private fun SearchBody(
    state: SearchUiState,
    currentId: String?,
    controller: SearchController,
    onQueued: (Boolean) -> Unit,
) {
    when (state.status) {
        SearchStatus.Idle -> {
            Message(stringResource(Res.string.idle_hint))
        }

        SearchStatus.Loading -> {
            LoadingRows()
        }

        SearchStatus.Empty -> {
            Message(stringResource(Res.string.empty, state.query.trim()))
        }

        SearchStatus.Failed -> {
            Failed(onRetry = controller::submit)
        }

        SearchStatus.Done -> {
            Results(
                songs = state.results,
                currentId = currentId,
                onPlay = controller::play,
                onPlayNext = { song ->
                    controller.playNext(song)
                    onQueued(true)
                },
                onEnqueue = { song ->
                    controller.enqueue(song)
                    onQueued(false)
                },
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Shura.spacing.gutter),
        placeholder = { Text(stringResource(Res.string.field_hint)) },
        leadingIcon = { Icon(ShuraIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(ShuraIcons.Close, contentDescription = stringResource(Res.string.clear))
                }
            }
        },
        singleLine = true,
        shape = Shura.shapes.pill,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Shura.colors.surface2,
                unfocusedContainerColor = Shura.colors.surface2,
                focusedBorderColor = Shura.colors.line,
                unfocusedBorderColor = Shura.colors.surface2,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

@Composable
private fun Results(
    songs: List<Song>,
    currentId: String?,
    onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onEnqueue: (Song) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(top = Shura.spacing.m)) {
        items(songs, key = { it.videoId.value }) { song ->
            SongRow(
                title = song.title,
                subtitle = song.subtitle(),
                artworkUrl = song.thumbnailUrl,
                explicit = song.isExplicit,
                isCurrent = song.videoId.value == currentId,
                onClick = { onPlay(song) },
            ) {
                RowMenu(onPlayNext = { onPlayNext(song) }, onEnqueue = { onEnqueue(song) })
            }
        }
    }
}

@Composable
private fun RowMenu(
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                ShuraIcons.More,
                contentDescription = stringResource(Res.string.more_actions),
                tint = Shura.colors.textMuted,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.play_next)) },
                leadingIcon = { Icon(ShuraIcons.PlayNext, contentDescription = null) },
                onClick = {
                    open = false
                    onPlayNext()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.add_to_queue)) },
                leadingIcon = { Icon(ShuraIcons.AddToQueue, contentDescription = null) },
                onClick = {
                    open = false
                    onEnqueue()
                },
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Shura.colors.textMuted,
        modifier = Modifier.padding(Shura.spacing.gutter).padding(top = Shura.spacing.l),
    )
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Column(Modifier.padding(Shura.spacing.gutter).padding(top = Shura.spacing.l)) {
        Text(stringResource(Res.string.failed), style = MaterialTheme.typography.bodyLarge, color = Shura.colors.text)
        Spacer(Modifier.height(Shura.spacing.s))
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
private fun LoadingRows() {
    Column(Modifier.padding(top = Shura.spacing.m)) {
        repeat(LOADING_ROWS) {
            Row(
                Modifier.padding(horizontal = Shura.spacing.gutter, vertical = Shura.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(Shura.shapes.thumbnail).shimmer())
                Column(verticalArrangement = Arrangement.spacedBy(Shura.spacing.s)) {
                    Box(Modifier.size(width = 180.dp, height = 14.dp).clip(Shura.shapes.thumbnail).shimmer())
                    Box(Modifier.size(width = 120.dp, height = 12.dp).clip(Shura.shapes.thumbnail).shimmer())
                }
            }
        }
    }
}

private fun Song.subtitle(): String =
    listOfNotNull(artistNames.ifBlank { null }, album?.title, duration?.toClock()).joinToString(" • ")

private const val LOADING_ROWS = 6
