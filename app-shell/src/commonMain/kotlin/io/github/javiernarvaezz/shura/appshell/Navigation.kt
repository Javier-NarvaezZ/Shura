package io.github.javiernarvaezz.shura.appshell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.javiernarvaezz.shura.core.model.BrowseLink

enum class Tab { Home, Search, Library }

internal sealed interface Route {
    data object Home : Route

    data object Search : Route

    data object Library : Route

    data object LikedSongs : Route

    data object Settings : Route

    /** [entry] tells apart two visits to the same page in one back stack (album → artist → same album). */
    data class Album(
        val id: String,
        val entry: Long,
    ) : Route

    data class Artist(
        val id: String,
        val entry: Long,
    ) : Route

    data class Playlist(
        val link: BrowseLink,
        val entry: Long,
    ) : Route

    data class Discography(
        val link: BrowseLink,
        val title: String,
        val entry: Long,
    ) : Route
}

/** The same page as [other]: an album or artist with the same id (another visit has another entry). */
internal fun Route.isSamePage(other: Route): Boolean =
    when (this) {
        is Route.Album -> other is Route.Album && other.id == id
        is Route.Artist -> other is Route.Artist && other.id == id
        else -> this == other
    }

/** What touching a tab in the bottom bar did. */
internal enum class TabSelection {
    /** Another tab is shown, as the user left it. */
    Switched,

    /** The tab showed pages over its main screen; now its main screen. */
    PoppedToRoot,

    /** The tab was on its main screen: it goes back to the top of its list. */
    ScrollToTop,

    /** Search was on its main screen: its field takes the focus. */
    FocusSearch,
}

/** What Back did. */
internal sealed interface BackResult {
    /** A page was left; [reopenPlayer] when it had been opened from the player, which is shown again. */
    data class Popped(
        val reopenPlayer: Boolean,
    ) : BackResult

    /** From the main screen of Search or Library, to Home (the tab left keeps its state). */
    data object ToHome : BackResult

    /** On Home's main screen: Back leaves the app (the system handles it). */
    data object Exit : BackResult
}

/**
 * The app's navigation, apart from the screens: one back stack per tab, which tab is shown, and the pages opened
 * from the player. Back undoes one step: a page, then from Search or Library to Home, then out of the app. Touching
 * the tab shown goes to its main screen, then to the top of it (or, in Search, to its field).
 */
internal class TabNavigator {
    var tab by mutableStateOf(Tab.Home)
        private set

    val stacks: Map<Tab, SnapshotStateList<Route>> =
        mapOf(
            Tab.Home to mutableStateListOf<Route>(Route.Home),
            Tab.Search to mutableStateListOf<Route>(Route.Search),
            Tab.Library to mutableStateListOf<Route>(Route.Library),
        )

    private val openedFromPlayer = mutableSetOf<Route>()
    private var entries = 0L

    /** The page shown in the current tab. */
    val current: Route get() = stacks.getValue(tab).last()

    /** A number for the next page pushed, unique for the life of this navigator. */
    fun nextEntry(): Long = ++entries

    /**
     * Shows [route] over the current tab's pages; false when the same page is already on top (nothing is pushed).
     * A page [fromPlayer] brings the player back when Back leaves it.
     */
    fun push(
        route: Route,
        fromPlayer: Boolean = false,
    ): Boolean {
        val stack = stacks.getValue(tab)
        if (stack.last().isSamePage(route)) return false
        stack.add(route)
        if (fromPlayer) openedFromPlayer += route
        return true
    }

    fun select(selected: Tab): TabSelection {
        val stack = stacks.getValue(selected)
        return when {
            selected != tab -> {
                tab = selected
                TabSelection.Switched
            }

            stack.size > 1 -> {
                while (stack.size > 1) openedFromPlayer -= stack.removeAt(stack.lastIndex)
                TabSelection.PoppedToRoot
            }

            selected == Tab.Search -> {
                TabSelection.FocusSearch
            }

            else -> {
                TabSelection.ScrollToTop
            }
        }
    }

    fun back(): BackResult {
        val stack = stacks.getValue(tab)
        return when {
            stack.size > 1 -> {
                BackResult.Popped(reopenPlayer = openedFromPlayer.remove(stack.removeAt(stack.lastIndex)))
            }

            tab != Tab.Home -> {
                tab = Tab.Home
                BackResult.ToHome
            }

            else -> {
                BackResult.Exit
            }
        }
    }

    /** Whether Back has something to undo in the app (otherwise it leaves the app). */
    val canGoBack: Boolean get() = tab != Tab.Home || stacks.getValue(tab).size > 1
}
