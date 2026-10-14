package io.github.javiernarvaezz.shura.core.ui.components

/** The words of [SongMenu], from the calling feature's resources. */
class SongMenuLabels(
    val more: String,
    val playNext: String,
    val addToQueue: String,
    val goToAlbum: String,
    val goToArtist: String,
    /** "Me gusta" / "Quitar de Me gusta"; needed only where songs can be liked. */
    val like: String = "",
    val unlike: String = "",
)

/** Whether a song has "Me gusta", and how to change it, for [SongMenu]. */
class LikeAction(
    val liked: Boolean,
    val onToggle: () -> Unit,
)
