package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What a page knows about its rows when they leave it out, e.g. album tracks without artist or cover. */
internal class TrackDefaults(
    val artists: List<Artist> = emptyList(),
    val album: AlbumRef? = null,
    val thumbnailUrl: String? = null,
)

/** List rows (`musicResponsiveListItemRenderer`) as songs. */
internal object ListItems {
    const val RENDERER = "musicResponsiveListItemRenderer"
    private const val ALBUM_AUDIO = "MUSIC_VIDEO_TYPE_ATV"
    private const val EXPLICIT_BADGE = "MUSIC_EXPLICIT_BADGE"
    private val DURATION = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""")

    /** The rows of a shelf, in order. */
    fun rows(shelf: JsonObject): List<JsonObject> =
        (shelf["contents"] as? JsonArray)?.mapNotNull { it.path(RENDERER) as? JsonObject }.orEmpty()

    /**
     * The row as a song, or null when it has no valid video id or title (e.g. an unavailable track).
     *
     * With [defaults] (a page of tracks), any video counts and rows without linked artists keep their plain
     * names; without them (search), only album-audio tracks with linked artists are taken.
     */
    fun song(
        item: JsonObject,
        defaults: TrackDefaults? = null,
    ): Song? {
        val type = musicVideoType(item)
        val videoId = videoId(item)?.takeIf { defaults != null || type == ALBUM_AUDIO }
        val title = column(item, 0).joinToString("") { it.text }.trim()
        if (videoId == null || title.isBlank()) return null
        val details = (1 until columnCount(item)).flatMap { column(item, it) }
        val linked = details.filter { it.pageType == PageTypes.ARTIST }.map { Artist(it.text.trim(), it.browseId) }
        return Song(
            videoId = videoId,
            title = title,
            artists =
                when {
                    linked.isNotEmpty() || defaults == null -> linked
                    else -> plainArtists(item).ifEmpty { defaults.artists }
                },
            album =
                details.firstOrNull { it.pageType == PageTypes.ALBUM }?.let { AlbumRef(it.text.trim(), it.browseId) }
                    ?: defaults?.album,
            duration = (details + fixedColumns(item)).firstNotNullOfOrNull { parseDuration(it.text) },
            thumbnailUrl = largestThumbnail(item["thumbnail"]) ?: defaults?.thumbnailUrl,
            isExplicit = isExplicit(item["badges"]),
            isVideo = isVideo(type),
        )
    }

    /** Whether a `musicVideoType` names a video upload rather than an album track; unknown counts as a track. */
    fun isVideo(musicVideoType: String?): Boolean = musicVideoType != null && musicVideoType != ALBUM_AUDIO

    fun parseDuration(text: String): Duration? {
        val match = DURATION.matchEntire(text.trim()) ?: return null
        val (hours, minutes, seconds) = match.destructured
        return (hours.toIntOrNull() ?: 0).hours + minutes.toInt().minutes + seconds.toInt().seconds
    }

    fun isExplicit(badges: JsonElement?): Boolean =
        findAll(badges, "musicInlineBadgeRenderer").any { it.path("icon", "iconType").string() == EXPLICIT_BADGE }

    fun column(
        item: JsonObject,
        index: Int,
    ): List<Run> =
        runsOf(
            (item["flexColumns"] as? JsonArray)
                ?.getOrNull(
                    index,
                ).path("musicResponsiveListItemFlexColumnRenderer", "text"),
        )

    private fun columnCount(item: JsonObject) = (item["flexColumns"] as? JsonArray)?.size ?: 0

    private fun fixedColumns(item: JsonObject): List<Run> =
        (item["fixedColumns"] as? JsonArray)
            ?.flatMap { runsOf(it.path("musicResponsiveListItemFixedColumnRenderer", "text")) }
            .orEmpty()

    /** Second-column names that link nowhere or to a channel (uploads in playlists), without ids. */
    private fun plainArtists(item: JsonObject): List<Artist> =
        column(item, 1)
            .filter { !it.isSeparator && it.pageType != PageTypes.ALBUM && parseDuration(it.text) == null }
            .map { Artist(it.text.trim()) }

    private fun videoId(item: JsonObject): VideoId? {
        val raw =
            item.path("playlistItemData", "videoId").string()
                ?: column(item, 0).firstNotNullOfOrNull { it.watchVideoId }
                ?: return null
        return runCatching { VideoId(raw) }.getOrNull()
    }

    fun musicVideoType(item: JsonObject): String? =
        findAll(item, "watchEndpointMusicConfig").firstNotNullOfOrNull { it["musicVideoType"].string() }
            ?: findStrings(item, "musicVideoType").firstOrNull()
}
