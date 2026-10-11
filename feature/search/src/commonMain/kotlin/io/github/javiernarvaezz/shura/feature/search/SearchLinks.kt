package io.github.javiernarvaezz.shura.feature.search

import io.github.javiernarvaezz.shura.core.model.BrowseLink

/** Where search results lead: the album, artist and playlist pages. */
class SearchLinks(
    val onAlbum: (String) -> Unit,
    val onArtist: (String) -> Unit,
    val onPlaylist: (BrowseLink) -> Unit,
)
