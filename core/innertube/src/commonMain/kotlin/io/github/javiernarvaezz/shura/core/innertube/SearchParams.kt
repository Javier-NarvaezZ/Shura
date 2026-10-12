package io.github.javiernarvaezz.shura.core.innertube

/** Search filter tokens, taken from the filter chips YouTube Music returns in search responses. */
internal object SearchParams {
    /** "Songs" chip, observed on 2026-10-04. */
    const val SONGS = "EgWKAQIIAWoSEAQQAxAFEAkQEBAOEAoQFRAR"

    /** "Albums", "Artists" and "Featured playlists" chips, observed on 2026-10-11. */
    const val ALBUMS = "EgWKAQIYAWoSEAQQAxAFEAkQEBAOEAoQFRAR"
    const val ARTISTS = "EgWKAQIgAWoSEAQQAxAFEAkQEBAOEAoQFRAR"
    const val FEATURED_PLAYLISTS = "EgeKAQQoADgBahIQBBADEAUQCRAQEA4QChAVEBE%3D"

    fun of(filter: SearchFilter): String =
        when (filter) {
            SearchFilter.Songs -> SONGS
            SearchFilter.Albums -> ALBUMS
            SearchFilter.Artists -> ARTISTS
            SearchFilter.Playlists -> FEATURED_PLAYLISTS
        }
}
