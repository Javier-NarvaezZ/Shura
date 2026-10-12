package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/** A section's title, with an optional action at its end ("Ver todas"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = Shura.spacing.gutter, end = Shura.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Shura.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Shura.colors.accent) }
    }
}

/**
 * A cover with a title and a muted subtitle under it (an album, a playlist, a recently played song); a round
 * image for artists ([round]). Only the cover is clipped, so the text is never cut at the corners.
 */
@Composable
fun MediaTile(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TILE_SIZE,
    round: Boolean = false,
) {
    Column(
        modifier.width(size).clickable(onClick = onClick),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Artwork(
            artworkUrl,
            sizePx = TILE_ARTWORK_PX,
            contentDescription = null,
            modifier = Modifier.size(size),
            shape = if (round) CircleShape else Shura.shapes.card,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Shura.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.padding(top = Shura.spacing.s),
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Shura.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

/** A titled horizontal row of tiles; [key] keeps each tile's state with its item. */
@Composable
fun <T : Any> MediaRail(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    tile: @Composable (T) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Shura.spacing.s)) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Shura.spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
        ) {
            items(items, key = key) { tile(it) }
        }
    }
}

/** The tiles' default side. */
val TILE_SIZE = 150.dp

/** The pixel size tile covers are requested at (also for prefetching them). */
const val TILE_ARTWORK_PX = 300
