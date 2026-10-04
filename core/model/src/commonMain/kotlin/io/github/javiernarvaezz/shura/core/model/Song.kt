package io.github.javiernarvaezz.shura.core.model

import kotlin.jvm.JvmInline
import kotlin.time.Duration

/** YouTube video identifier: exactly 11 URL-safe base64 characters. */
@JvmInline
value class VideoId(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "Invalid video id" }
    }

    override fun toString(): String = value

    private companion object {
        val PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
    }
}

data class Artist(
    val name: String,
    val id: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Artist name must not be blank" }
    }
}

data class AlbumRef(
    val title: String,
    val id: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Album title must not be blank" }
    }
}

/**
 * A playable track in the catalog.
 *
 * [isExplicit] mirrors the catalog's lyrics advisory; it must not influence stream resolution.
 */
data class Song(
    val videoId: VideoId,
    val title: String,
    val artists: List<Artist>,
    val album: AlbumRef? = null,
    val duration: Duration? = null,
    val thumbnailUrl: String? = null,
    val isExplicit: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Song title must not be blank" }
    }

    val artistNames: String
        get() = artists.joinToString(", ") { it.name }
}
