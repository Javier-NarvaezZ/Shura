package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.Artist
import io.github.javiernarvaezz.shura.core.model.ArtistPage
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** An artist page (`UC…`): header, top songs with the link to all of them, and carousels (videos skipped). */
internal object ArtistParser {
    fun parse(
        root: JsonElement,
        id: String,
    ): ArtistPage {
        val header = findAll(root, "musicImmersiveHeaderRenderer").firstOrNull() ?: missing("artist header")
        val name = textOf(header["title"]) ?: missing("artist name")
        val artist = Artist(name, id)
        val songs = findAll(root, "musicShelfRenderer").firstOrNull()
        return ArtistPage(
            artist = ArtistSummary(id, name, largestThumbnail(header["thumbnail"])),
            description = textOf(header["description"]),
            subscribers = textOf(header.path("subscriptionButton", "subscribeButtonRenderer", "subscriberCountText")),
            monthlyListeners = textOf(header["monthlyListenerCount"]),
            topSongs =
                songs
                    ?.let(ListItems::rows)
                    ?.mapNotNull { ListItems.song(it, TrackDefaults(listOf(artist))) }
                    .orEmpty(),
            allSongs = songs?.let(::allSongsLink),
            shelves = findAll(root, Carousels.RENDERER).mapNotNull { Carousels.of(it, listOf(artist)) },
        )
    }

    /** The "show all" playlist of the top songs shelf: its bottom link, or the link on its title. */
    private fun allSongsLink(shelf: JsonObject): BrowseLink? {
        val browse =
            shelf.path("bottomEndpoint", "browseEndpoint") ?: findAll(shelf["title"], "browseEndpoint").firstOrNull()
        return browse.path("browseId").string()?.let { BrowseLink(it, browse.path("params").string()) }
    }
}
