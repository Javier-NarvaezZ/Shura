package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Turns search responses into [Song]s.
 *
 * Tolerant by design: list items are found anywhere in the tree, and anything that does not look like
 * an album-audio track (`MUSIC_VIDEO_TYPE_ATV`) with a valid id and title is skipped instead of failing.
 */
internal object SearchParser {
    private const val ITEM = "musicResponsiveListItemRenderer"
    private const val ALBUM_AUDIO = "MUSIC_VIDEO_TYPE_ATV"
    private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    private const val EXPLICIT_BADGE = "MUSIC_EXPLICIT_BADGE"
    private val DURATION = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""")

    fun parseSongs(root: JsonElement): List<Song> = findAll(root, ITEM).mapNotNull(::toSong)

    fun parseDuration(text: String): Duration? {
        val match = DURATION.matchEntire(text.trim()) ?: return null
        val (hours, minutes, seconds) = match.destructured
        return (hours.toIntOrNull() ?: 0).hours + minutes.toInt().minutes + seconds.toInt().seconds
    }

    private fun toSong(item: JsonObject): Song? {
        val videoId = videoId(item)?.takeIf { musicVideoType(item) == ALBUM_AUDIO }
        val title = column(item, 0).joinToString("") { it.text }.trim()
        if (videoId == null || title.isBlank()) return null
        val details = column(item, 1)
        return Song(
            videoId = videoId,
            title = title,
            artists = details.filter { it.pageType == PAGE_TYPE_ARTIST }.mapNotNull { it.toArtist() },
            album = details.firstOrNull { it.pageType == PAGE_TYPE_ALBUM }?.toAlbum(),
            duration = details.firstNotNullOfOrNull { parseDuration(it.text) },
            thumbnailUrl = thumbnailUrl(item),
            isExplicit =
                findAll(item["badges"], "musicInlineBadgeRenderer")
                    .any { it.path("icon", "iconType").string() == EXPLICIT_BADGE },
        )
    }

    private fun videoId(item: JsonObject): VideoId? {
        val raw =
            item.path("playlistItemData", "videoId").string()
                ?: column(item, 0).firstNotNullOfOrNull { it.watchVideoId }
                ?: return null
        return runCatching { VideoId(raw) }.getOrNull()
    }

    private fun musicVideoType(item: JsonObject): String? =
        findAll(item, "watchEndpointMusicConfig").firstNotNullOfOrNull { it["musicVideoType"].string() }
            ?: findValues(item, "musicVideoType").firstOrNull()

    private fun thumbnailUrl(item: JsonObject): String? =
        (item.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.maxByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: 0 }
            ?.get("url")
            .string()
            ?.let { if (it.startsWith("//")) "https:$it" else it }

    private fun column(
        item: JsonObject,
        index: Int,
    ): List<Run> =
        (
            (item["flexColumns"] as? JsonArray)
                ?.getOrNull(index)
                ?.path("musicResponsiveListItemFlexColumnRenderer", "text", "runs") as? JsonArray
        )?.mapNotNull { (it as? JsonObject)?.let(::Run) }
            .orEmpty()

    private class Run(
        json: JsonObject,
    ) {
        val text: String = json["text"].string().orEmpty()
        private val browse = json.path("navigationEndpoint", "browseEndpoint")
        val browseId: String? = browse.path("browseId").string()
        val pageType: String? =
            browse
                .path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")
                .string()
        val watchVideoId: String? = json.path("navigationEndpoint", "watchEndpoint", "videoId").string()

        fun toArtist(): Artist? = text.takeIf { it.isNotBlank() }?.let { Artist(it.trim(), browseId) }

        fun toAlbum(): AlbumRef? = text.takeIf { it.isNotBlank() }?.let { AlbumRef(it.trim(), browseId) }
    }

    private fun findAll(
        root: JsonElement?,
        key: String,
    ): List<JsonObject> {
        val found = mutableListOf<JsonObject>()

        fun walk(element: JsonElement?) {
            when (element) {
                is JsonObject -> {
                    element.forEach { (k, v) ->
                        if (k == key && v is JsonObject) found += v
                        walk(v)
                    }
                }

                is JsonArray -> {
                    element.forEach(::walk)
                }

                else -> {}
            }
        }
        walk(root)
        return found
    }

    private fun findValues(
        root: JsonElement?,
        key: String,
    ): List<String> {
        val found = mutableListOf<String>()

        fun walk(element: JsonElement?) {
            when (element) {
                is JsonObject -> {
                    element.forEach { (k, v) ->
                        if (k == key) v.string()?.let(found::add)
                        walk(v)
                    }
                }

                is JsonArray -> {
                    element.forEach(::walk)
                }

                else -> {}
            }
        }
        walk(root)
        return found
    }

    private fun JsonElement?.path(vararg keys: String): JsonElement? =
        keys.fold(this) { current, key -> (current as? JsonObject)?.get(key) }

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
