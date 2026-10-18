package io.github.javiernarvaezz.shura.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javiernarvaezz.shura.core.model.DiscographyFilter
import io.github.javiernarvaezz.shura.core.model.DiscographyPage
import io.github.javiernarvaezz.shura.core.ui.components.DetailTopBar
import io.github.javiernarvaezz.shura.core.ui.components.MediaTile
import io.github.javiernarvaezz.shura.core.ui.components.TILE_SIZE
import io.github.javiernarvaezz.shura.core.ui.components.TOP_BAR_SPACE
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.feature.browse.resources.Res
import io.github.javiernarvaezz.shura.feature.browse.resources.back
import io.github.javiernarvaezz.shura.feature.browse.resources.discography_empty
import org.jetbrains.compose.resources.stringResource

/**
 * An artist's whole discography as a grid of covers, under its filters ("Álbumes", "Sencillos y EP"); a cover
 * opens its album. [title] is the shelf it was opened from.
 */
@Composable
fun DiscographyScreen(
    model: DiscographyModel,
    title: String,
    navigation: BrowseNavigation,
) {
    val state by model.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Shura.colors.background)) {
        when (val shown = state) {
            // Plain background while it loads: the grid replaces it when it arrives.
            is PageState.Loading -> {
                Unit
            }

            is PageState.Failed -> {
                FailedPage(model::retry)
            }

            is PageState.Loaded -> {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = TOP_BAR_SPACE)) {
                    Discography(shown.page, model::select, navigation)
                }
            }
        }
        DetailTopBar(title, stringResource(Res.string.back), navigation.onBack, revealed = { 1f })
    }
}

@Composable
private fun Discography(
    page: DiscographyPage,
    onFilter: (DiscographyFilter) -> Unit,
    navigation: BrowseNavigation,
) {
    if (page.filters.size > 1) Filters(page.filters, onFilter)
    if (page.albums.isEmpty()) {
        Notice(stringResource(Res.string.discography_empty))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_SIZE),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Shura.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.m),
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.l),
    ) {
        items(page.albums, key = { it.id }) { album ->
            MediaTile(
                album.title,
                album.caption(),
                album.thumbnailUrl,
                onClick = { navigation.onAlbum(album.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Filters(
    filters: List<DiscographyFilter>,
    onFilter: (DiscographyFilter) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Shura.spacing.gutter, vertical = Shura.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Shura.spacing.s),
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter.selected,
                onClick = { onFilter(filter) },
                label = { Text(filter.label) },
                shape = Shura.shapes.pill,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Shura.colors.accent,
                        selectedLabelColor = Shura.colors.onAccent,
                    ),
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(Shura.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Shura.spacing.l),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Shura.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}
