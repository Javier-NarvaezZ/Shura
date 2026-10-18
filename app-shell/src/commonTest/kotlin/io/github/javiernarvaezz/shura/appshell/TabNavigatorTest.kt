package io.github.javiernarvaezz.shura.appshell

import io.github.javiernarvaezz.shura.core.model.BrowseLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabNavigatorTest {
    private val nav = TabNavigator()

    private fun album(id: String = "MPREb_1") = Route.Album(id, nav.nextEntry())

    private fun artist(id: String = "UC1") = Route.Artist(id, nav.nextEntry())

    private fun playlist() = Route.Playlist(BrowseLink("VLPL1"), nav.nextEntry())

    private fun discography() = Route.Discography(BrowseLink("MPADUC1", "p"), "Sencillos y EP", nav.nextEntry())

    private fun stack(tab: Tab = nav.tab) = nav.stacks.getValue(tab).toList()

    @Test
    fun eachTabStartsOnItsMainScreen() {
        assertEquals(listOf<Route>(Route.Home), stack(Tab.Home))
        assertEquals(listOf<Route>(Route.Search), stack(Tab.Search))
        assertEquals(listOf<Route>(Route.Library), stack(Tab.Library))
        assertEquals(Tab.Home, nav.tab)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun everyPageKindIsPushedOnTheCurrentTabAndBackUndoesEachInTurn() {
        nav.select(Tab.Library)
        val pages = listOf(Route.LikedSongs, album(), artist(), playlist(), discography())
        pages.forEach { assertTrue(nav.push(it)) }
        assertEquals(listOf<Route>(Route.Library) + pages, stack())

        pages.reversed().forEach { page ->
            assertEquals(page, nav.current)
            assertEquals(BackResult.Popped(reopenPlayer = false), nav.back())
        }
        assertEquals(listOf<Route>(Route.Library), stack())
    }

    @Test
    fun settingsOpenFromHomeAndBackReturnsToIt() {
        nav.push(Route.Settings)
        assertEquals(BackResult.Popped(reopenPlayer = false), nav.back())
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun tabsKeepTheirOwnPagesWhenSwitching() {
        nav.select(Tab.Library)
        val saved = album()
        nav.push(saved)
        nav.select(Tab.Search)
        nav.push(artist())

        assertEquals(TabSelection.Switched, nav.select(Tab.Library))
        assertEquals(saved, nav.current)
        nav.select(Tab.Search)
        assertEquals(2, stack().size)
        assertEquals(listOf<Route>(Route.Home), stack(Tab.Home))
    }

    @Test
    fun theSameAlbumVisitedTwiceIsTwoStepsBack() {
        val first = album()
        nav.push(first)
        nav.push(artist())
        val again = album()
        assertTrue(nav.push(again))

        nav.back()
        nav.back()
        assertEquals(first, nav.current)
    }

    @Test
    fun touchingTheTabShownGoesToItsMainScreenThenToItsTop() {
        nav.select(Tab.Library)
        nav.push(playlist())
        nav.push(album())

        assertEquals(TabSelection.PoppedToRoot, nav.select(Tab.Library))
        assertEquals(listOf<Route>(Route.Library), stack())
        assertEquals(TabSelection.ScrollToTop, nav.select(Tab.Library))
        assertEquals(TabSelection.ScrollToTop, nav.select(Tab.Home).let { nav.select(Tab.Home) })
    }

    @Test
    fun touchingSearchAgainFocusesItsField() {
        nav.select(Tab.Search)
        nav.push(artist())

        assertEquals(TabSelection.PoppedToRoot, nav.select(Tab.Search))
        assertEquals(TabSelection.FocusSearch, nav.select(Tab.Search))
    }

    @Test
    fun backOnTheMainScreenOfSearchOrLibraryGoesHomeAndKeepsThatTab() {
        nav.select(Tab.Search)
        assertTrue(nav.canGoBack)
        assertEquals(BackResult.ToHome, nav.back())
        assertEquals(Tab.Home, nav.tab)

        nav.select(Tab.Library)
        nav.push(album())
        nav.back()
        assertEquals(BackResult.ToHome, nav.back())
        assertEquals(listOf<Route>(Route.Library), stack(Tab.Library))
    }

    @Test
    fun backOnHomesMainScreenLeavesTheApp() {
        assertEquals(BackResult.Exit, nav.back())
        assertEquals(listOf<Route>(Route.Home), stack())
    }

    @Test
    fun aPageOpenedFromThePlayerBringsThePlayerBack() {
        nav.push(artist(), fromPlayer = true)
        nav.push(album())

        assertEquals(BackResult.Popped(reopenPlayer = false), nav.back())
        assertEquals(BackResult.Popped(reopenPlayer = true), nav.back())
    }

    @Test
    fun thePageAlreadyShownIsNotPushedAgain() {
        nav.push(album("MPREb_9"))
        assertFalse(nav.push(album("MPREb_9"), fromPlayer = true))
        assertEquals(2, stack().size)
        // Nothing was pushed, so leaving the page does not bring the player back.
        assertEquals(BackResult.Popped(reopenPlayer = false), nav.back())
    }

    @Test
    fun goingToTheMainScreenForgetsPagesOpenedFromThePlayer() {
        nav.push(artist(), fromPlayer = true)
        nav.select(Tab.Home)
        val again = artist()
        nav.push(again)
        assertEquals(BackResult.Popped(reopenPlayer = false), nav.back())
    }

    @Test
    fun theMainScreenIsNeverPopped() {
        nav.select(Tab.Library)
        repeat(3) { nav.select(Tab.Library) }
        nav.back()
        nav.select(Tab.Library)
        assertEquals(listOf<Route>(Route.Library), stack())
    }
}
