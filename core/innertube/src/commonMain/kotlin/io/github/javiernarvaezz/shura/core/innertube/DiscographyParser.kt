package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.DiscographyFilter
import io.github.javiernarvaezz.shura.core.model.DiscographyPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * An artist's discography (`MPAD…`): a grid of releases and its filter chips. A chip shows the same page with that
 * filter (a reload, see [InnerTubeRequest.Reload]), which this also reads.
 */
internal object DiscographyParser {
    fun parse(root: JsonElement): DiscographyPage {
        val grid = findAll(root, "gridRenderer").firstOrNull() ?: missing("discography grid")
        val items = (grid["items"] as? JsonArray)?.mapNotNull { it.path("musicTwoRowItemRenderer") as? JsonObject }
        return DiscographyPage(
            albums = items.orEmpty().mapNotNull { Carousels.album(it) },
            filters = findAll(root, "chipCloudChipRenderer").mapNotNull(::filter),
        )
    }

    private fun filter(chip: JsonObject): DiscographyFilter? {
        val label = textOf(chip["text"]) ?: return null
        return DiscographyFilter(
            label = label,
            selected = (chip["isSelected"] as? JsonPrimitive)?.booleanOrNull ?: false,
            token =
                chip
                    .path(
                        "navigationEndpoint",
                        "browseSectionListReloadEndpoint",
                        "continuation",
                        "reloadContinuationData",
                    ).path("continuation")
                    .string(),
        )
    }
}
