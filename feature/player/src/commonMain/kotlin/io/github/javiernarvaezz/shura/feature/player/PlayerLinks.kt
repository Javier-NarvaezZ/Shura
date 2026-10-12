package io.github.javiernarvaezz.shura.feature.player

/** Where the player's artist and album lines lead; the player closes first. */
class PlayerLinks(
    val onArtist: (String) -> Unit,
    val onAlbum: (String) -> Unit,
) {
    companion object {
        val None = PlayerLinks({}, {})
    }
}
