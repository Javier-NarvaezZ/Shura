package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/**
 * One song in a list: cover, title (with the explicit mark), a muted subtitle and optional trailing actions.
 * [isCurrent] highlights the song that is playing.
 */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    explicit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Shura.spacing.gutter, vertical = Shura.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
    ) {
        Artwork(artworkUrl, sizePx = ROW_ARTWORK_PX, contentDescription = null, modifier = Modifier.size(52.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) Shura.colors.accent else Shura.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (explicit) ExplicitMark(Modifier.padding(start = Shura.spacing.xs))
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Shura.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/** The "E" mark for explicit songs. */
@Composable
fun ExplicitMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(Shura.shapes.thumbnail)
            .background(Shura.colors.surface3)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("E", style = MaterialTheme.typography.labelSmall, color = Shura.colors.textMuted)
    }
}

private const val ROW_ARTWORK_PX = 120
