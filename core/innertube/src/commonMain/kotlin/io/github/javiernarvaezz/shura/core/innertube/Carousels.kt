package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumShelf
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.ArtistShelf
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.CatalogShelf
import io.github.javiernarvaezz.shura.core.model.PlaylistShelf
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Carousels (`musicCarouselShelfRenderer` of `musicTwoRowItemRenderer`) as shelves. */
internal object Carousels {
    const val RENDERER = "musicCarouselShelfRenderer"
    private const val ITEM = "musicTwoRowItemRenderer"

    /**
     * The carousel as a shelf of the kind of its items, or null for carousels of videos or of nothing parseable.
     * [pageArtists] stand in for albums that do not name their artist (an artist's own albums).
     */
    fun of(
        carousel: JsonObject,
        pageArtists: List<Artist> = emptyList(),
    ): CatalogShelf? {
        val title = textOf(carousel.path("header", "musicCarouselShelfBasicHeaderRenderer", "title")) ?: return null
        val items = (carousel["contents"] as? JsonArray)?.mapNotNull { it.path(ITEM) as? JsonObject }.orEmpty()
        val type = items.firstOrNull().path("navigationEndpoint", "browseEndpoint").pageType()
        val shelf =
            when (type) {
                PageTypes.ALBUM -> AlbumShelf(title, items.mapNotNull { album(it, pageArtists) })
                PageTypes.ARTIST -> ArtistShelf(title, items.mapNotNull(::artist))
                PageTypes.PLAYLIST -> PlaylistShelf(title, items.mapNotNull(::playlist))
                else -> null
            }
        return shelf?.takeIf { !it.isEmpty() }
    }

    private fun CatalogShelf.isEmpty(): Boolean =
        when (this) {
            is AlbumShelf -> albums.isEmpty()
            is ArtistShelf -> artists.isEmpty()
            is PlaylistShelf -> playlists.isEmpty()
        }

    private fun album(
        item: JsonObject,
        pageArtists: List<Artist>,
    ): AlbumSummary? {
        val target = item.target(PageTypes.ALBUM) ?: return null
        val subtitle = runsOf(item["subtitle"])
        return AlbumSummary(
            id = target.id,
            title = target.title,
            artists = subtitle.linkedArtists().ifEmpty { pageArtists },
            year = yearOf(subtitle),
            typeLabel = subtitle.typeLabel(),
            thumbnailUrl = largestThumbnail(item["thumbnailRenderer"]),
            isExplicit = ListItems.isExplicit(item["subtitleBadges"]),
        )
    }

    private fun artist(item: JsonObject): ArtistSummary? =
        item.target(PageTypes.ARTIST)?.let {
            ArtistSummary(it.id, it.title, largestThumbnail(item["thumbnailRenderer"]), textOf(item["subtitle"]))
        }

    /** Subtitle "Playlist • <author>". */
    private fun playlist(item: JsonObject): PlaylistSummary? =
        item.target(PageTypes.PLAYLIST)?.let {
            val fields = runsOf(item["subtitle"]).fields()
            PlaylistSummary(it.id, it.title, fields.getOrNull(1), largestThumbnail(item["thumbnailRenderer"]))
        }

    private fun JsonObject.target(type: String) = linkTarget(this["navigationEndpoint"], type, textOf(this["title"]))
}

/** The page an item links to: its browse id and the item's title. */
internal class LinkTarget(
    val id: String,
    val title: String,
)

/** The target of [navigation] when it is a page of [type] and the item has a [title]. */
internal fun linkTarget(
    navigation: JsonElement?,
    type: String,
    title: String?,
): LinkTarget? {
    val browse = navigation.path("browseEndpoint")
    val id = browse.path("browseId").string()
    return if (browse.pageType() == type && id != null && title != null) LinkTarget(id, title) else null
}

internal fun List<Run>.linkedArtists(): List<Artist> =
    filter { it.pageType == PageTypes.ARTIST }.map { Artist(it.text.trim(), it.browseId) }

/** The non-separator texts of a run list, trimmed. */
internal fun List<Run>.fields(): List<String> = filterNot { it.isSeparator }.map { it.text.trim() }

/** The leading plain word ("Álbum", "EP", "Sencillo"), when the first field is neither a link nor a year. */
internal fun List<Run>.typeLabel(): String? =
    firstOrNull { !it.isSeparator }
        ?.takeIf { it.browseId == null && yearOf(listOf(it)) == null }
        ?.text
        ?.trim()
