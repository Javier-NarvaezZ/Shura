package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumPage
import io.github.javiernarvaezz.shura.core.model.AlbumRef
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** An album page (`MPREb_…`): header, tracks (one page) and its carousels. */
internal object AlbumParser {
    fun parse(
        root: JsonElement,
        id: String,
    ): AlbumPage {
        val header = findAll(root, "musicResponsiveHeaderRenderer").firstOrNull() ?: missing("album header")
        val title = textOf(header["title"]) ?: missing("album title")
        val subtitle = runsOf(header["subtitle"])
        val artists = headerArtists(runsOf(header["straplineTextOne"]))
        val album =
            AlbumSummary(
                id = id,
                title = title,
                artists = artists,
                year = yearOf(subtitle),
                typeLabel = subtitle.firstOrNull { !it.isSeparator && yearOf(listOf(it)) == null }?.text?.trim(),
                thumbnailUrl = largestThumbnail(header["thumbnail"]),
                isExplicit = ListItems.isExplicit(header["subtitleBadge"]),
            )
        val defaults = TrackDefaults(artists, AlbumRef(title, id), album.thumbnailUrl)
        return AlbumPage(
            album = album,
            tracks =
                findAll(root, "musicShelfRenderer")
                    .flatMap(ListItems::rows)
                    .mapNotNull { row -> ListItems.song(row, defaults)?.let { it.asAlbumTrack(row) } }
                    .distinctBy { it.videoId },
            description = textOf(findAll(header, "musicDescriptionShelfRenderer").firstOrNull()?.get("description")),
            shelves = findAll(root, Carousels.RENDERER).mapNotNull { Carousels.of(it, artists) },
        )
    }
}

/**
 * The album's own track for a row. Album rows link the artist's video upload when there is one (a music video or an
 * "Official Audio" upload, which can be a different cut of the song), but their song-credits entry (`MPTC` + id)
 * names the album track, whose length is the one the row shows. Rows without it keep their video.
 */
private fun Song.asAlbumTrack(row: JsonObject): Song {
    val track =
        findStrings(row["menu"], "browseId")
            .firstOrNull { it.startsWith(SONG_CREDITS) }
            ?.removePrefix(SONG_CREDITS)
            ?.let { runCatching { VideoId(it) }.getOrNull() }
    return if (track == null) this else copy(videoId = track)
}

private const val SONG_CREDITS = "MPTC"

/**
 * The artists of a header line ("Dani Martin y Juanes"): the linked names, so joining words are left out, or the
 * whole line as one name when nothing is linked.
 */
internal fun headerArtists(runs: List<Run>): List<Artist> {
    val linked = runs.filter { it.browseId != null && !it.isSeparator }.map { Artist(it.text.trim(), it.browseId) }
    val plain = runs.joinToString("") { it.text }.trim()
    return linked.ifEmpty { if (plain.isEmpty()) emptyList() else listOf(Artist(plain)) }
}

/** A response without a part every page of its kind has: YouTube changed the format or sent something else. */
internal fun missing(part: String): Nothing = throw InnerTubeException.Parse(IllegalStateException("No $part"))
