package io.github.javiernarvaezz.shura.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.javiernarvaezz.shura.core.ui.artwork.Artwork
import io.github.javiernarvaezz.shura.core.ui.artwork.rememberArtworkTint
import io.github.javiernarvaezz.shura.core.ui.theme.Shura

/**
 * The top of an album or playlist page: the cover centered on a wash of its own color that fades into the page
 * background, then the title, a linked line and details. The wash is read in the draw phase, so its fade-in
 * redraws without recomposing.
 */
@Composable
fun CoverHero(
    title: String,
    artworkUrl: String?,
    link: HeroLink?,
    details: String?,
    modifier: Modifier = Modifier,
) {
    val tint = rememberArtworkTint(artworkUrl)
    val background = Shura.colors.background
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Brush.verticalGradient(0f to tint.top, WASH_END to background, 1f to background))
            }.statusBarsPadding()
            .padding(top = TOP_BAR_SPACE)
            .padding(horizontal = Shura.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.s),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = (maxWidth * COVER_FRACTION).coerceAtMost(COVER_MAX)
            Artwork(
                artworkUrl,
                sizePx = HERO_PX,
                contentDescription = null,
                modifier = Modifier.size(side),
                shape = Shura.shapes.artwork,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Shura.colors.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Shura.spacing.m),
        )
        link?.let { HeroLinkText(it, Modifier) }
        details?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Shura.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The top of an artist page: the artist's image edge to edge, fading into the page background under the name and
 * a line of details.
 */
@Composable
fun ImageHero(
    title: String,
    imageUrl: String?,
    details: String?,
    modifier: Modifier = Modifier,
) {
    val background = Shura.colors.background
    Box(modifier.fillMaxWidth().aspectRatio(IMAGE_RATIO)) {
        Artwork(
            imageUrl,
            sizePx = IMAGE_PX,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
        )
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val scrim =
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = TOP_SCRIM),
                        SCRIM_START to Color.Transparent,
                        1f to background,
                    )
                onDrawBehind { drawRect(scrim) }
            },
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = Shura.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Shura.spacing.xs),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = Shura.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            details?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Shura.colors.textMuted) }
        }
    }
}

@Composable
private fun HeroLinkText(
    link: HeroLink,
    modifier: Modifier,
) {
    val onClick = link.onClick
    Text(
        link.text,
        style = MaterialTheme.typography.titleMedium,
        color = if (onClick != null) Shura.colors.accent else Shura.colors.textMuted,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}

/** Room for the page's top bar above a hero's content. */
val TOP_BAR_SPACE = 56.dp

private const val COVER_FRACTION = 0.62f
private val COVER_MAX = 280.dp
private const val HERO_PX = 544
private const val IMAGE_PX = 1080
private const val IMAGE_RATIO = 1.1f
private const val WASH_END = 0.85f
private const val TOP_SCRIM = 0.35f
private const val SCRIM_START = 0.3f
