package io.github.javiernarvaezz.shura.feature.browse

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.DiscographyFilter
import io.github.javiernarvaezz.shura.core.model.DiscographyPage
import io.github.javiernarvaezz.shura.core.testing.FakeMusicCatalog
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscographyModelTest {
    private val catalog = FakeMusicCatalog()
    private val albumsFilter = DiscographyFilter("Álbumes", selected = false, token = "tokAlbums")
    private val singlesFilter = DiscographyFilter("Sencillos y EP", selected = true, token = "tokSingles")
    private val singles =
        DiscographyPage(listOf(AlbumSummary("MPREb_s", "Single")), listOf(albumsFilter, singlesFilter))
    private val albums =
        DiscographyPage(
            listOf(AlbumSummary("MPREb_a", "Album")),
            listOf(albumsFilter.copy(selected = true), singlesFilter.copy(selected = false)),
        )

    private fun TestScope.model(): DiscographyModel {
        val model = DiscographyModel(catalog, BrowseLink("MPAD1", "p"), backgroundScope)
        runCurrent()
        return model
    }

    @Test
    fun loadsTheDiscographyOfTheLink() =
        runTest {
            catalog.discographies["MPAD1"] = singles

            assertEquals(PageState.Loaded(singles), model().state.value)
            assertEquals(listOf("discography:MPAD1"), catalog.calls)
        }

    @Test
    fun anotherFilterLoadsItsReleases() =
        runTest {
            catalog.discographies["MPAD1"] = singles
            catalog.discographies["tokAlbums"] = albums
            val model = model()
            model.select(albumsFilter)
            runCurrent()

            assertEquals(PageState.Loaded(albums), model.state.value)
            assertEquals("discographyFilter:tokAlbums", catalog.calls.last())
        }

    @Test
    fun theShownFilterLoadsNothing() =
        runTest {
            catalog.discographies["MPAD1"] = singles
            val model = model()
            model.select(singlesFilter)
            runCurrent()

            assertEquals(listOf("discography:MPAD1"), catalog.calls)
        }

    @Test
    fun aRetryAfterAFailedFilterAsksForThatFilterAgain() =
        runTest {
            catalog.discographies["MPAD1"] = singles
            val model = model()
            model.select(albumsFilter)
            runCurrent()
            assertEquals(PageState.Failed("IllegalStateException"), model.state.value)

            catalog.discographies["tokAlbums"] = albums
            model.retry()
            runCurrent()
            assertEquals(PageState.Loaded(albums), model.state.value)
        }
}
