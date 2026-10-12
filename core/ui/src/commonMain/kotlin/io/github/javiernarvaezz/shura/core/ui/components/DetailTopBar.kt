package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/**
 * A page's top bar over its hero: back, and the title with a solid background once the hero scrolls away.
 * [revealed] (0..1) is read only in draw and layer lambdas, so scrolling never recomposes the bar.
 */
@Composable
fun DetailTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    revealed: () -> Float,
    modifier: Modifier = Modifier,
) {
    val background = Shura.colors.background
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(background.copy(alpha = revealed())) }
            .statusBarsPadding()
            .height(TOP_BAR_SPACE)
            .padding(horizontal = Shura.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.s),
    ) {
        Box(
            Modifier
                .size(Shura.spacing.touch)
                .clip(Shura.shapes.pill)
                // A scrim keeps the arrow readable over any cover or image; it fades as the bar appears.
                .drawBehind { drawRect(Color.Black.copy(alpha = SCRIM_ALPHA * (1f - revealed()))) }
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ShuraIcons.Back, contentDescription = backLabel, tint = Shura.colors.text)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Shura.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = revealed() },
        )
    }
}

/** How far a page's hero (its first item) has scrolled away, 0..1, from the list state alone. */
fun LazyListState.heroScrolled(heroHeightPx: () -> Int): Float =
    when {
        firstVisibleItemIndex > 0 -> 1f
        heroHeightPx() <= 0 -> 0f
        else -> (firstVisibleItemScrollOffset.toFloat() / heroHeightPx()).coerceIn(0f, 1f)
    }

private const val SCRIM_ALPHA = 0.35f
