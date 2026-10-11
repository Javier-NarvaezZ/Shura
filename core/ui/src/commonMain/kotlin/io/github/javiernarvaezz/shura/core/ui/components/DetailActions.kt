package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/**
 * A page's playback actions: shuffle in a round button and a wide "play" button. While [loading] (e.g. the rest
 * of a playlist), both wait and play shows a spinner instead of its icon.
 */
@Composable
fun DetailActions(
    playLabel: String,
    shuffleLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Shura.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(BUTTON_HEIGHT)
                .clip(Shura.shapes.pill)
                .background(Shura.colors.surface2)
                .clickable(enabled = !loading, role = Role.Button, onClick = onShuffle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ShuraIcons.Shuffle, contentDescription = shuffleLabel, tint = Shura.colors.text)
        }
        Row(
            Modifier
                .weight(1f)
                .height(BUTTON_HEIGHT)
                .clip(Shura.shapes.pill)
                .background(Shura.colors.accent)
                .clickable(enabled = !loading, role = Role.Button, onClick = onPlay),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(SPINNER), color = Shura.colors.onAccent, strokeWidth = 2.dp)
            } else {
                Icon(ShuraIcons.Play, contentDescription = null, tint = Shura.colors.onAccent)
            }
            Text(
                playLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Shura.colors.onAccent,
                modifier = Modifier.padding(start = Shura.spacing.s),
            )
        }
    }
}

private val BUTTON_HEIGHT = 52.dp
private val SPINNER = 20.dp
