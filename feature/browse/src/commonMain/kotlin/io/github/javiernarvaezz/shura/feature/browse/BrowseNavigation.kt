package io.github.javiernarvaezz.shura.feature.browse

import io.github.javiernarvaezz.shura.core.model.BrowseLink

/** Where a page's links lead; the shell pushes the page on the current tab. */
class BrowseNavigation(
    val onBack: () -> Unit,
    val onAlbum: (String) -> Unit,
    val onArtist: (String) -> Unit,
    val onPlaylist: (BrowseLink) -> Unit,
)
