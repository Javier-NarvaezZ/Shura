package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/** A song's "more" button and its menu; the "go to" entries show only when given an action. */
@Composable
fun SongMenu(
    labels: SongMenuLabels,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(ShuraIcons.More, contentDescription = labels.more, tint = Shura.colors.textMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun entry(
                text: String,
                icon: ImageVector,
                action: () -> Unit,
            ) = DropdownMenuItem(
                text = { Text(text) },
                leadingIcon = { Icon(icon, contentDescription = null) },
                onClick = {
                    open = false
                    action()
                },
            )
            entry(labels.playNext, ShuraIcons.PlayNext, onPlayNext)
            entry(labels.addToQueue, ShuraIcons.AddToQueue, onEnqueue)
            onGoToAlbum?.let { entry(labels.goToAlbum, ShuraIcons.Album, it) }
            onGoToArtist?.let { entry(labels.goToArtist, ShuraIcons.Person, it) }
        }
    }
}
