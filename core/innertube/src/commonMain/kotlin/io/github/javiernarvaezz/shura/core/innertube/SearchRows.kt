package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Filtered search results for albums, artists and playlists; rows of any other kind are skipped. */
internal object SearchRows {
    fun albums(root: JsonElement): List<AlbumSummary> = rows(root).mapNotNull(::album)

    fun artists(root: JsonElement): List<ArtistSummary> = rows(root).mapNotNull(::artist)

    fun playlists(root: JsonElement): List<PlaylistSummary> = rows(root).mapNotNull(::playlist)

    private fun rows(root: JsonElement) = findAll(root, ListItems.RENDERER)

    /** "Sencillo • Juanes • 2007". */
    private fun album(item: JsonObject): AlbumSummary? =
        item.target(PageTypes.ALBUM)?.let {
            val details = ListItems.column(item, 1)
            AlbumSummary(
                id = it.id,
                title = it.title,
                artists = details.linkedArtists(),
                year = yearOf(details),
                typeLabel = details.typeLabel(),
                thumbnailUrl = largestThumbnail(item["thumbnail"]),
                isExplicit = ListItems.isExplicit(item["badges"]),
            )
        }

    /** "Artista • Público mensual: …": the kind word is dropped. */
    private fun artist(item: JsonObject): ArtistSummary? =
        item.target(PageTypes.ARTIST)?.let {
            val fields = ListItems.column(item, 1).fields()
            val subtitle = (if (fields.size > 1) fields.drop(1) else fields).joinToString(" • ").ifEmpty { null }
            ArtistSummary(it.id, it.title, largestThumbnail(item["thumbnail"]), subtitle)
        }

    /** "<author> • 50 canciones". */
    private fun playlist(item: JsonObject): PlaylistSummary? =
        item.target(PageTypes.PLAYLIST)?.let {
            val fields = ListItems.column(item, 1).fields()
            PlaylistSummary(
                id = it.id,
                title = it.title,
                author = fields.firstOrNull(),
                thumbnailUrl = largestThumbnail(item["thumbnail"]),
                subtitle = fields.drop(1).joinToString(" • ").ifEmpty { null },
            )
        }

    private fun JsonObject.target(type: String): LinkTarget? {
        val title =
            ListItems
                .column(this, 0)
                .joinToString("") { it.text }
                .trim()
                .ifEmpty { null }
        return linkTarget(this["navigationEndpoint"], type, title)
    }
}
