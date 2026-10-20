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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.javiernarvaezz.shura.appshell.resources.Res
import io.github.javiernarvaezz.shura.appshell.resources.tab_home
import io.github.javiernarvaezz.shura.appshell.resources.tab_library
import io.github.javiernarvaezz.shura.appshell.resources.tab_search
import io.github.javiernarvaezz.shura.core.lyrics.LyricsOffsets
import io.github.javiernarvaezz.shura.core.lyrics.LyricsRepository
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.MusicCatalog
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.Library
import io.github.javiernarvaezz.shura.core.player.PlayHistory
import io.github.javiernarvaezz.shura.core.player.PlaybackSettings
import io.github.javiernarvaezz.shura.core.ui.artwork.LocalNetworkRegained
import io.github.javiernarvaezz.shura.core.ui.artwork.NeverRegained
import io.github.javiernarvaezz.shura.core.ui.components.ChromeSurface
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion
import io.github.javiernarvaezz.shura.feature.browse.AlbumScreen
import io.github.javiernarvaezz.shura.feature.browse.AlbumViewModel
import io.github.javiernarvaezz.shura.feature.browse.ArtistScreen
import io.github.javiernarvaezz.shura.feature.browse.ArtistViewModel
import io.github.javiernarvaezz.shura.feature.browse.BrowseNavigation
import io.github.javiernarvaezz.shura.feature.browse.DiscographyScreen
import io.github.javiernarvaezz.shura.feature.browse.DiscographyViewModel
import io.github.javiernarvaezz.shura.feature.browse.PlaylistScreen
import io.github.javiernarvaezz.shura.feature.browse.PlaylistViewModel
import io.github.javiernarvaezz.shura.feature.home.HomeScreen
import io.github.javiernarvaezz.shura.feature.home.HomeViewModel
import io.github.javiernarvaezz.shura.feature.library.AccountLibraryModel
import io.github.javiernarvaezz.shura.feature.library.LibraryLinks
import io.github.javiernarvaezz.shura.feature.library.LibraryScreen
import io.github.javiernarvaezz.shura.feature.library.LibraryViewModel
import io.github.javiernarvaezz.shura.feature.library.LikedSongsScreen
import io.github.javiernarvaezz.shura.feature.library.LikedSongsViewModel
import io.github.javiernarvaezz.shura.feature.lyrics.LyricsModel
import io.github.javiernarvaezz.shura.feature.lyrics.LyricsViewModel
import io.github.javiernarvaezz.shura.feature.lyrics.PlayerLyrics
import io.github.javiernarvaezz.shura.feature.player.MiniPlayer
import io.github.javiernarvaezz.shura.feature.player.PlayerLinks
import io.github.javiernarvaezz.shura.feature.player.PlayerMode
import io.github.javiernarvaezz.shura.feature.player.PlayerOverlay
import io.github.javiernarvaezz.shura.feature.player.PlayerSheetState
import io.github.javiernarvaezz.shura.feature.player.PlayerViewModel
import io.github.javiernarvaezz.shura.feature.player.PreloadUpcomingArtwork
import io.github.javiernarvaezz.shura.feature.player.rememberPlayerSheetState
import io.github.javiernarvaezz.shura.feature.search.SearchLinks
import io.github.javiernarvaezz.shura.feature.search.SearchScreen
import io.github.javiernarvaezz.shura.feature.search.SearchSources
import io.github.javiernarvaezz.shura.feature.search.SearchViewModel
import io.github.javiernarvaezz.shura.feature.settings.AccountModel
import io.github.javiernarvaezz.shura.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

/** What the shell needs from the app (manual wiring, no DI framework). */
class ShellDependencies(
    val player: AudioPlayer,
    val history: PlayHistory,
    val catalog: MusicCatalog,
    val onPlaybackIntent: () -> Unit,
    /** The local hour (0..23), for Home's greeting. */
    val currentHour: () -> Int,
    /** Emits when the device gets a network again, so covers that failed to load try once more. */
    val networkRegained: Flow<Unit> = NeverRegained,
    /** The user's playback settings (autoplay); null where there are none yet (desktop). */
    val settings: PlaybackSettings? = null,
    /** Lyrics for a song (cached); null hides the lyrics (desktop, until it has a database). */
    val lyrics: LyricsRepository? = null,
    /** The user's timing fix for each song's lyrics; null keeps none. */
    val lyricsOffsets: LyricsOffsets? = null,
    /** The user's library on this device; null hides the Library tab and the like and save actions (desktop). */
    val library: Library? = null,
    /** The YouTube Music account; null where there is none yet (desktop). */
    val account: AccountModel? = null,
    /** Opens the platform's sign-in screen. */
    val onSignIn: () -> Unit = {},
    /** The signed-in account's library for the Library tab; null where there is no account (desktop). */
    val accountLibrary: AccountLibraryModel? = null,
    /** The album track a video upload is known to stand for, so a radio starts from songs, not videos. */
    val trackOfVideo: (VideoId) -> VideoId? = { null },
)

/** Navigation state ([TabNavigator]), kept across rotation, and the signals a tab's main screen reacts to. */
internal class ShellViewModel : ViewModel() {
    val nav = TabNavigator()

    val tab: Tab get() = nav.tab

    private val reselected = MutableSharedFlow<Pair<Tab, TabSelection>>(extraBufferCapacity = 1)

    /** Home's or Library's tab touched while on its main screen: back to the top of the list. */
    val homeToTop: Flow<Unit> = reselected.filter { it == (Tab.Home to TabSelection.ScrollToTop) }.map {}
    val libraryToTop: Flow<Unit> = reselected.filter { it == (Tab.Library to TabSelection.ScrollToTop) }.map {}

    /** Search's tab touched while on its main screen: its field takes the focus. */
    val searchFocus: Flow<Unit> = reselected.filter { it.second == TabSelection.FocusSearch }.map {}

    fun select(tab: Tab) {
        val result = nav.select(tab)
        if (result == TabSelection.ScrollToTop || result == TabSelection.FocusSearch) reselected.tryEmit(tab to result)
    }

    fun nextEntry(): Long = nav.nextEntry()

    fun push(
        route: Route,
        fromPlayer: Boolean = false,
    ) = nav.push(route, fromPlayer)

    fun back(): BackResult = nav.back()
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
    val home = homeViewModel(deps)
    val search =
        viewModel { SearchViewModel(SearchSources.of(deps.catalog), deps.player, deps.onPlaybackIntent, deps.library) }
    val player = viewModel { PlayerViewModel(deps.player, deps.settings, deps.library) }
    // At shell level, so the lyrics survive closing and opening the player.
    val lyrics =
        deps.lyrics?.let { repository ->
            viewModel { LyricsViewModel(deps.player, repository::lyrics, deps.lyricsOffsets) }
        }
    val sheet = rememberPlayerSheetState()
    val tabs = rememberSaveableStateHolder()
    val links = rememberShellLinks(shell, sheet)
    PlayerModes(sheet, lyrics?.model)
    TabRootBack(shell, sheet)
    CompositionLocalProvider(LocalNetworkRegained provides deps.networkRegained) {
        Box(
            modifier
                .fillMaxSize()
                .background(Shura.colors.background)
                .onSizeChanged { sheet.travelPx = it.height * PLAYER_TRAVEL },
        ) {
            // Not drawn while the open player covers it (a layer with alpha 0 is skipped), but still composed, so
            // scroll positions and typed text survive the player.
            Column(Modifier.fillMaxSize().graphicsLayer { alpha = if (sheet.isOpen) 0f else 1f }) {
                Box(Modifier.weight(1f)) {
                    tabs.SaveableStateProvider(shell.tab) {
                        val stack = shell.nav.stacks.getValue(shell.tab)
                        NavDisplay(
                            backStack = stack,
                            onBack = links.navigation.onBack,
                            // Each entry (e.g. an album page) gets its own ViewModels, cleared when it is popped.
                            entryDecorators =
                                listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                            entryProvider =
                                entryProvider {
                                    homePages(home, shell, deps, links.navigation.onBack)
                                    entry<Route.Search> {
                                        SearchScreen(search.controller, links.search, focusField = shell.searchFocus)
                                    }
                                    pages(deps, links.navigation)
                                    deps.library?.let { libraryPages(it, deps, shell, links.navigation) }
                                },
                        )
                    }
                }
                MiniPlayer(player.model, sheet, Modifier.padding(bottom = Shura.spacing.s))
                BottomChrome(shell.tab, hasLibrary = deps.library != null, onSelect = shell::select)
            }
            PlayerOverlay(
                player.model,
                sheet,
                links = links.player,
                lyrics = lyrics?.let { { modifier -> PlayerLyrics(it.model, modifier) } },
            )
            PreloadUpcomingArtwork(player.model)
        }
    }
}

/** Home, whose recently played songs start their radio. */
@Composable
private fun homeViewModel(deps: ShellDependencies) =
    viewModel {
        HomeViewModel(deps.player, deps.history, deps.currentHour, deps.catalog::relatedSongs, deps.trackOfVideo)
    }

/**
 * Back leaves the queue or the lyrics, then closes the player, before it navigates. Lyrics are looked up only
 * while they are on screen.
 */
@Composable
private fun PlayerModes(
    sheet: PlayerSheetState,
    lyrics: LyricsModel?,
) {
    val scope = rememberCoroutineScope()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = sheet.isShown,
        onBackCompleted = { if (sheet.mode != PlayerMode.Cover) sheet.mode = PlayerMode.Cover else sheet.close(scope) },
    )
    if (lyrics != null) {
        val showsLyrics = sheet.isShown && sheet.mode == PlayerMode.Lyrics
        LaunchedEffect(showsLyrics) { lyrics.setVisible(showsLyrics) }
    }
}

@Composable
private fun BottomChrome(
    selected: Tab,
    hasLibrary: Boolean,
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
            if (hasLibrary) {
                NavItem(ShuraIcons.Library, stringResource(Res.string.tab_library), selected == Tab.Library) {
                    onSelect(Tab.Library)
                }
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

/** The catalog pages, each with its own ViewModel (cleared when the page is popped). */
private fun EntryProviderScope<Route>.pages(
    deps: ShellDependencies,
    navigation: BrowseNavigation,
) {
    entry<Route.Album> { route ->
        AlbumScreen(viewModel { AlbumViewModel(deps.catalog, deps.player, route.id, deps.library) }.model, navigation)
    }
    entry<Route.Artist> { route ->
        ArtistScreen(viewModel { ArtistViewModel(deps.catalog, deps.player, route.id, deps.library) }.model, navigation)
    }
    entry<Route.Playlist> { route ->
        PlaylistScreen(
            viewModel { PlaylistViewModel(deps.catalog, deps.player, route.link, deps.library) }.model,
            navigation,
        )
    }
    entry<Route.Discography> { route ->
        DiscographyScreen(viewModel { DiscographyViewModel(deps.catalog, route.link) }.model, route.title, navigation)
    }
}

/** The Library tab and the liked songs; its links push catalog pages on the same tab. */
private fun EntryProviderScope<Route>.libraryPages(
    library: Library,
    deps: ShellDependencies,
    shell: ShellViewModel,
    navigation: BrowseNavigation,
) {
    val account = deps.accountLibrary
    val links =
        LibraryLinks(
            onLikedSongs = { shell.push(Route.LikedSongs) },
            onAlbum = navigation.onAlbum,
            onArtist = navigation.onArtist,
            onPlaylist = { id -> navigation.onPlaylist(BrowseLink(id)) },
        )
    entry<Route.Library> {
        val model = viewModel { LibraryViewModel(library, account?.likedSongs) }.model
        LibraryScreen(model, links, account = account, onSignIn = deps.onSignIn, scrollToTop = shell.libraryToTop)
    }
    entry<Route.LikedSongs> {
        val model = viewModel { LikedSongsViewModel(library, deps.player, account?.likedSongs) }.model
        LikedSongsScreen(model, links, onBack = navigation.onBack)
    }
}

/** Home, and Settings opened from it. */
private fun EntryProviderScope<Route>.homePages(
    home: HomeViewModel,
    shell: ShellViewModel,
    deps: ShellDependencies,
    onBack: () -> Unit,
) {
    entry<Route.Home> {
        HomeScreen(
            home.model,
            onSearch = { shell.select(Tab.Search) },
            onSettings = { shell.push(Route.Settings) },
            scrollToTop = shell.homeToTop,
        )
    }
    entry<Route.Settings> { Settings(deps, onBack) }
}

/** Settings: the account (where there is one) and playback. */
@Composable
private fun Settings(
    deps: ShellDependencies,
    onBack: () -> Unit,
) = SettingsScreen(
    account = deps.account,
    autoplay = deps.settings?.autoplay,
    onAutoplayChange = { deps.settings?.setAutoplay(it) },
    onSignIn = deps.onSignIn,
    onBack = onBack,
)
