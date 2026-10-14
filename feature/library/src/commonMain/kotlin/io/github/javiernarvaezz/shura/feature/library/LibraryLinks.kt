package io.github.javiernarvaezz.shura.feature.library

/** Where the library's items lead; the shell pushes the page on the Library tab. */
class LibraryLinks(
    val onLikedSongs: () -> Unit,
    val onAlbum: (String) -> Unit,
    val onArtist: (String) -> Unit,
    val onPlaylist: (String) -> Unit,
)
