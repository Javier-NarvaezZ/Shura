package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.DiscographyFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiscographyParserTest {
    private fun page(fixture: String) = DiscographyParser.parse(Json.parseToJsonElement(Fixtures.read(fixture)))

    @Test
    fun readsTheGridOfReleasesInOrder() {
        val singles = page("discography/discography_kendrick_singles.json")

        assertEquals(23, singles.albums.size)
        val first = singles.albums.first()
        assertEquals("Not Like Us", first.title)
        assertEquals("Sencillo", first.typeLabel)
        assertEquals(2024, first.year)
        assertTrue(first.id.startsWith("MPREb_"))
        assertTrue(first.thumbnailUrl.orEmpty().startsWith("https://"))
    }

    @Test
    fun readsTheFiltersAndWhichIsShown() {
        val filters = page("discography/discography_kendrick_singles.json").filters

        assertEquals(listOf("Álbumes", "Sencillos y EP"), filters.map(DiscographyFilter::label))
        assertEquals(listOf(false, true), filters.map(DiscographyFilter::selected))
        assertTrue(filters.all { !it.token.isNullOrEmpty() })
    }

    @Test
    fun anotherFilterComesAsAReloadOfTheSameGrid() {
        val albums = page("discography/discography_kendrick_albums.json")

        assertEquals(9, albums.albums.size)
        assertEquals("GNX", albums.albums.first().title)
        assertEquals("Álbum", albums.albums.first().typeLabel)
        assertEquals(listOf(true, false), albums.filters.map(DiscographyFilter::selected))
    }

    @Test
    fun aResponseWithoutAGridIsAParseFailure() {
        assertFailsWith<InnerTubeException.Parse> { DiscographyParser.parse(JsonObject(emptyMap())) }
    }
}
