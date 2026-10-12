package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.VideoId

/** What a search returns: each filter is one of YouTube Music's search chips. */
enum class SearchFilter { Songs, Albums, Artists, Playlists }

/**
 * A catalog request, validated when built: ids and tokens are opaque YouTube values made of URL-safe characters,
 * never free text, so nothing else can reach the request body.
 */
sealed interface InnerTubeRequest {
    /** [query] is trimmed and must not be blank. */
    class Search(
        query: String,
        val filter: SearchFilter,
    ) : InnerTubeRequest {
        val query: String = query.trim()

        init {
            require(this.query.isNotEmpty()) { "Query must not be blank" }
        }
    }

    /** An album (`MPREb_…`), artist (`UC…`) or playlist (`VL…`) page; [params] as given by a link to it. */
    class Browse(
        val browseId: String,
        val params: String? = null,
    ) : InnerTubeRequest {
        init {
            require(ID.matches(browseId)) { "Invalid browse id" }
            require(params == null || TOKEN.matches(params)) { "Invalid browse params" }
        }
    }

    /** The songs YouTube Music would play after [videoId] (its radio), as in the "up next" list. */
    class Next(
        val videoId: VideoId,
    ) : InnerTubeRequest

    /** The next page of a list, from the token its previous page ended with. */
    class Continuation(
        val token: String,
    ) : InnerTubeRequest {
        init {
            require(TOKEN.matches(token)) { "Invalid continuation token" }
        }
    }

    private companion object {
        val ID = Regex("^[A-Za-z0-9_-]{1,200}$")
        val TOKEN = Regex("^[A-Za-z0-9_%=-]{1,2000}$")
    }
}
