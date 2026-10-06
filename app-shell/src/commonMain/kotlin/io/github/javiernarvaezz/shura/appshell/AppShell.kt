package io.github.javiernarvaezz.shura.appshell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.javiernarvaezz.shura.appshell.resources.Res
import io.github.javiernarvaezz.shura.appshell.resources.tab_home
import io.github.javiernarvaezz.shura.appshell.resources.tab_search
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.PlayHistory
import io.github.javiernarvaezz.shura.core.ui.components.ChromeSurface
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion
import io.github.javiernarvaezz.shura.feature.home.HomeScreen
import io.github.javiernarvaezz.shura.feature.home.HomeViewModel
import io.github.javiernarvaezz.shura.feature.player.MiniPlayer
import io.github.javiernarvaezz.shura.feature.player.PlayerOverlay
import io.github.javiernarvaezz.shura.feature.player.PlayerViewModel
import io.github.javiernarvaezz.shura.feature.player.rememberPlayerSheetState
import io.github.javiernarvaezz.shura.feature.search.SearchScreen
import io.github.javiernarvaezz.shura.feature.search.SearchViewModel
import org.jetbrains.compose.resources.stringResource

/** What the shell needs from the app (manual wiring, no DI framework). */
class ShellDependencies(
    val player: AudioPlayer,
    val history: PlayHistory,
    val searchSongs: suspend (String) -> List<Song>,
    val onPlaybackIntent: () -> Unit,
    /** The local hour (0..23), for Home's greeting. */
    val currentHour: () -> Int,
)

enum class Tab { Home, Search }

internal sealed interface Route {
    data object Home : Route

    data object Search : Route
}

/** Navigation state, kept across rotation. Each tab has its own back stack (album and artist pages: E6). */
internal class ShellViewModel : ViewModel() {
    var tab by mutableStateOf(Tab.Home)

    val stacks =
        mapOf(
            Tab.Home to mutableStateListOf<Route>(Route.Home),
            Tab.Search to mutableStateListOf<Route>(Route.Search),
        )
}

/**
 * The app: the current tab's screens above the bottom chrome (mini-player and navigation bar). Screen ViewModels
 * live at this level, so a tab keeps its state (e.g. search results) when the user switches away and back.
 */
@Composable
fun ShuraAppShell(
    deps: ShellDependencies,
    modifier: Modifier = Modifier,
) {
    val shell = viewModel { ShellViewModel() }
    val home = viewModel { HomeViewModel(deps.player, deps.history, deps.currentHour) }
    val search = viewModel { SearchViewModel(deps.searchSongs, deps.player, deps.onPlaybackIntent) }
    val player = viewModel { PlayerViewModel(deps.player) }
    val sheet = rememberPlayerSheetState()
    val scope = rememberCoroutineScope()
    val tabs = rememberSaveableStateHolder()
    // Back closes the player before it navigates.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = sheet.isShown,
        onBackCompleted = { sheet.close(scope) },
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Shura.colors.background)
            .onSizeChanged { sheet.travelPx = it.height * PLAYER_TRAVEL },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                tabs.SaveableStateProvider(shell.tab) {
                    val stack = shell.stacks.getValue(shell.tab)
                    NavDisplay(
                        backStack = stack,
                        onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider =
                            entryProvider {
                                entry<Route.Home> { HomeScreen(home.model, onSearch = { shell.tab = Tab.Search }) }
                                entry<Route.Search> { SearchScreen(search.controller) }
                            },
                    )
                }
            }
            MiniPlayer(player.model, sheet, Modifier.padding(bottom = Shura.spacing.s))
            BottomChrome(selected = shell.tab, onSelect = { shell.tab = it })
        }
        PlayerOverlay(player.model, sheet)
    }
}

@Composable
private fun BottomChrome(
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    val tint = Shura.colors.surface1
    ChromeSurface(tint = { tint }, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(NAV_HEIGHT),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(ShuraIcons.Home, stringResource(Res.string.tab_home), selected == Tab.Home) { onSelect(Tab.Home) }
            NavItem(ShuraIcons.Search, stringResource(Res.string.tab_search), selected == Tab.Search) {
                onSelect(Tab.Search)
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val indicator by animateColorAsState(
        if (selected) Shura.colors.surface3 else Color.Transparent,
        ShuraMotion.quick(),
    )
    Column(
        Modifier.width(ITEM_WIDTH).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(
                    width = INDICATOR_WIDTH,
                    height = INDICATOR_HEIGHT,
                ).clip(Shura.shapes.pill)
                .background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Shura.colors.text else Shura.colors.textMuted)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Shura.colors.text else Shura.colors.textMuted,
        )
    }
}

private val NAV_HEIGHT = 64.dp

/** Share of the screen height that a drag must cover to fully open or close the player. */
private const val PLAYER_TRAVEL = 0.75f
private val ITEM_WIDTH = 96.dp
private val INDICATOR_WIDTH = 56.dp
private val INDICATOR_HEIGHT = 30.dp
