package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A song's radio (the `next` endpoint's "up next" list) as songs, in order. The list starts with the seed itself,
 * which is left out, as is anything without a valid id or title. An empty or unknown response gives no songs.
 */
internal object NextParser {
    fun parse(
        root: JsonElement,
        seed: VideoId,
    ): List<Song> =
        findAll(root, "playlistPanelVideoRenderer")
            .mapNotNull(::song)
            .filter { it.videoId != seed }
            .distinctBy { it.videoId }

    private fun song(item: JsonObject): Song? {
        val videoId = item["videoId"].string()?.let { runCatching { VideoId(it) }.getOrNull() }
        val title = textOf(item["title"])
        if (videoId == null || title == null) return null
        // "Juanes • Mi Sangre • 2004": the linked artists and album.
        val byline = runsOf(item["longBylineText"])
        return Song(
            videoId = videoId,
            title = title,
            artists = byline.linkedArtists().ifEmpty { byline.fields().take(1).map { Artist(it) } },
            album =
                byline.firstOrNull { it.pageType == PageTypes.ALBUM }?.let {
                    AlbumRef(
                        it.text.trim(),
                        it.browseId,
                    )
                },
            duration = textOf(item["lengthText"])?.let(ListItems::parseDuration),
            thumbnailUrl = largestThumbnail(item["thumbnail"]),
            isExplicit = ListItems.isExplicit(item["badges"]),
            isVideo = ListItems.isVideo(ListItems.musicVideoType(item)),
        )
    }
}
