package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.PlaylistPage
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.TrackPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A playlist page (`VL…`) and its continuations. Tracks come 100 per page; repeats are kept, since a playlist
 * may hold a song twice on purpose.
 */
internal object PlaylistParser {
    private const val SHELF = "musicPlaylistShelfRenderer"

    fun parse(
        root: JsonElement,
        id: String,
    ): PlaylistPage {
        val shelf = findAll(root, SHELF).firstOrNull() ?: missing("playlist tracks")
        val header =
            findAll(root, "musicResponsiveHeaderRenderer").firstOrNull()
                ?: findAll(root, "musicHeaderRenderer").firstOrNull()
        val title =
            textOf(header?.get("title"))
                ?: findAll(root, "microformatDataRenderer").firstNotNullOfOrNull { it["title"].string() }
                ?: missing("playlist title")
        return PlaylistPage(
            playlist =
                PlaylistSummary(
                    id = id,
                    title = title,
                    author = author(header),
                    thumbnailUrl = largestThumbnail(header?.get("thumbnail")),
                ),
            tracks = tracks(shelf["contents"]),
            description = textOf(findAll(header, "musicDescriptionShelfRenderer").firstOrNull()?.get("description")),
            details = textOf(header?.get("secondSubtitle")),
            continuation = continuation(shelf["contents"]),
        )
    }

    fun parseContinuation(root: JsonElement): TrackPage {
        val items =
            findAll(root, "appendContinuationItemsAction").firstOrNull()?.get("continuationItems")
                ?: findAll(root, "musicPlaylistShelfContinuation").firstOrNull()?.get("contents")
                ?: missing("continuation items")
        return TrackPage(tracks(items), continuation(items))
    }

    private fun tracks(items: JsonElement?): List<Song> =
        (items as? JsonArray)
            ?.mapNotNull { it.path(ListItems.RENDERER) as? JsonObject }
            ?.mapNotNull { ListItems.song(it, TrackDefaults()) }
            .orEmpty()

    /** The token of the trailing "load more" item, if any. */
    private fun continuation(items: JsonElement?): String? =
        (items as? JsonArray)
            ?.lastOrNull()
            .path("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token")
            .string()

    /** The owner shown under the title: a linked name, or the avatar stack's text. */
    private fun author(header: JsonObject?): String? =
        textOf(header?.get("straplineTextOne"))
            ?: header
                .path("facepile", "avatarStackViewModel", "text", "content")
                .string()
                ?.trim()
                ?.ifEmpty { null }
}
