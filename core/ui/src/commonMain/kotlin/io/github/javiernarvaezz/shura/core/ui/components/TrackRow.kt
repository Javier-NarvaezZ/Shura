package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/**
 * A track in a page's list: its [number] (albums) or its cover (playlists, an artist's songs), title with the
 * explicit mark, a muted subtitle, the duration and optional trailing actions. [isCurrent] marks the song playing.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String?,
    duration: String?,
    explicit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    artworkUrl: String? = null,
    isCurrent: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Shura.spacing.gutter, top = Shura.spacing.s, bottom = Shura.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
    ) {
        if (number != null) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) Shura.colors.accent else Shura.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(NUMBER_WIDTH),
            )
        } else {
            Artwork(artworkUrl, sizePx = ROW_ARTWORK_PX, contentDescription = null, modifier = Modifier.size(ARTWORK))
        }
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
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Shura.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        duration?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shura.colors.textMuted) }
        trailing()
    }
}

private val NUMBER_WIDTH = 28.dp
private val ARTWORK = 48.dp
private const val ROW_ARTWORK_PX = 120
