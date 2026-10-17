package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.TrackPage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The signed-in account's library pages (fixtures: real responses sanitized on the device, Phase 3 E5). Liked
 * songs are the "LM" playlist; playlists and albums come as a grid of two-row items; followed artists as rows.
 * A page without the expected parts gives nothing rather than failing.
 */
internal object AccountLibrary {
    /** The liked-music playlist and the auto-generated "episodes for later": shown apart or not at all. */
    private val NOT_OWN_PLAYLISTS = setOf("VLLM", "VLSE")

    fun likedSongs(root: JsonElement): TrackPage =
        runCatching { PlaylistParser.parse(root, LIKED_SONGS).let { TrackPage(it.tracks, it.continuation) } }
            .getOrElse { TrackPage(emptyList()) }

    fun likedSongsContinuation(root: JsonElement): TrackPage =
        runCatching { PlaylistParser.parseContinuation(root) }.getOrElse { TrackPage(emptyList()) }

    fun playlists(root: JsonElement): List<PlaylistSummary> =
        gridItems(root).mapNotNull(Carousels::playlist).filter { it.id !in NOT_OWN_PLAYLISTS }

    fun albums(root: JsonElement): List<AlbumSummary> = gridItems(root).mapNotNull { Carousels.album(it) }

    fun artists(root: JsonElement): List<ArtistSummary> = SearchRows.artists(root)

    private fun gridItems(root: JsonElement): List<JsonObject> = findAll(root, "musicTwoRowItemRenderer")

    const val LIKED_SONGS = "VLLM"
}
