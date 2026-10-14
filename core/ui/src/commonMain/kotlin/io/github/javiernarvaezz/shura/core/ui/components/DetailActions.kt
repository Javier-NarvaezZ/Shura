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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/**
 * A page's actions: an optional [toggle] (save, follow) and shuffle in round buttons, and a wide "play" button on
 * the brand gradient. While [loading] (e.g. the rest of a playlist), playback waits and play shows a spinner
 * instead of its icon.
 */
@Composable
fun DetailActions(
    playLabel: String,
    shuffleLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    toggle: DetailToggle? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Shura.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        toggle?.let { DetailToggleButton(it) }
        RoundButton(ShuraIcons.Shuffle, shuffleLabel, Shura.colors.text, enabled = !loading, onClick = onShuffle)
        Row(
            Modifier
                .weight(1f)
                .height(BUTTON_HEIGHT)
                .clip(Shura.shapes.pill)
                // The brand gradient (the sun) marks the main action; its text and icon stay dark (AA).
                .background(Shura.colors.brandGradient)
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

/** [toggle] on its own, for a page without playback actions (an artist with no songs listed). */
@Composable
fun DetailToggleButton(
    toggle: DetailToggle,
    modifier: Modifier = Modifier,
) = RoundButton(
    icon = if (toggle.active) toggle.activeIcon else toggle.icon,
    label = if (toggle.active) toggle.activeLabel else toggle.label,
    tint = if (toggle.active) Shura.colors.accent else Shura.colors.text,
    onClick = toggle.onToggle,
    modifier = modifier,
)

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(BUTTON_HEIGHT)
            .clip(Shura.shapes.pill)
            .background(Shura.colors.surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

private val BUTTON_HEIGHT = 52.dp
private val SPINNER = 20.dp
