package io.github.javiernarvaezz.shura.appshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.javiernarvaezz.shura.feature.browse.BrowseNavigation
import io.github.javiernarvaezz.shura.feature.player.PlayerLinks
import io.github.javiernarvaezz.shura.feature.player.PlayerSheetState
import io.github.javiernarvaezz.shura.feature.search.SearchLinks

/** Where the pages' links and Back lead, and the player's links (which bring the player back on Back). */
internal class ShellLinks(
    val navigation: BrowseNavigation,
    val player: PlayerLinks,
) {
    val search = SearchLinks(navigation.onAlbum, navigation.onArtist, navigation.onPlaylist)
}

@Composable
internal fun rememberShellLinks(
    shell: ShellViewModel,
    sheet: PlayerSheetState,
): ShellLinks {
    val scope = rememberCoroutineScope()
    return remember(shell, sheet) {
        // A page opened from the player brings the player back when Back leaves it.
        val goBack: () -> Unit = {
            if ((shell.back() as? BackResult.Popped)?.reopenPlayer == true) sheet.open(scope)
        }
        ShellLinks(
            navigation = browseNavigation(shell, goBack),
            player =
                PlayerLinks(
                    onArtist = { shell.push(Route.Artist(it, shell.nextEntry()), fromPlayer = true) },
                    onAlbum = { shell.push(Route.Album(it, shell.nextEntry()), fromPlayer = true) },
                ),
        )
    }
}

/** Back on the main screen of Search or Library goes to Home; on Home's, it leaves the app (the system's). */
@Composable
internal fun TabRootBack(
    shell: ShellViewModel,
    sheet: PlayerSheetState,
) {
    val onMainScreen =
        shell.nav.stacks
            .getValue(shell.tab)
            .size == 1
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !sheet.isShown && shell.tab != Tab.Home && onMainScreen,
        onBackCompleted = { shell.back() },
    )
}

/** Links between pages push onto the current tab; back pops it. */
internal fun browseNavigation(
    shell: ShellViewModel,
    onBack: () -> Unit,
) = BrowseNavigation(
    onBack = onBack,
    onAlbum = { shell.push(Route.Album(it, shell.nextEntry())) },
    onArtist = { shell.push(Route.Artist(it, shell.nextEntry())) },
    onPlaylist = { shell.push(Route.Playlist(it, shell.nextEntry())) },
    onDiscography = { link, title -> shell.push(Route.Discography(link, title, shell.nextEntry())) },
)
