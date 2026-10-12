package io.github.javiernarvaezz.shura.core.model

import kotlin.time.Duration

/**
 * Catalog pages and the items that lead to them. Ids are YouTube browse ids (`MPREb_…` albums, `UC…` artists,
 * `VL…` playlists). Text such as [AlbumSummary.typeLabel] or [ArtistPage.subscribers] is shown as YouTube Music
 * words it, in the request's language, never parsed.
 */
data class AlbumSummary(
    val id: String,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val year: Int? = null,
    /** "Álbum", "EP", "Sencillo"… */
    val typeLabel: String? = null,
    val thumbnailUrl: String? = null,
    val isExplicit: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Album id must not be blank" }
        require(title.isNotBlank()) { "Album title must not be blank" }
    }

    val artistNames: String get() = artists.joinToString(", ") { it.name }
}

data class ArtistSummary(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    /** E.g. the subscriber count, as shown in search. */
    val subtitle: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Artist id must not be blank" }
        require(name.isNotBlank()) { "Artist name must not be blank" }
    }
}

data class PlaylistSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    /** E.g. the track count or views, as shown in search. */
    val subtitle: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Playlist id must not be blank" }
        require(title.isNotBlank()) { "Playlist title must not be blank" }
    }
}

/** A link to another page, with the params YouTube Music gave it (e.g. an artist's "all songs" playlist). */
data class BrowseLink(
    val id: String,
    val params: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Browse id must not be blank" }
    }
}

/** A titled row of a page ("Otras versiones", "Sencillos y EP"…), in YouTube Music's words and order. */
sealed interface CatalogShelf {
    val title: String
}

data class AlbumShelf(
    override val title: String,
    val albums: List<AlbumSummary>,
) : CatalogShelf

data class ArtistShelf(
    override val title: String,
    val artists: List<ArtistSummary>,
) : CatalogShelf

data class PlaylistShelf(
    override val title: String,
    val playlists: List<PlaylistSummary>,
) : CatalogShelf

data class AlbumPage(
    val album: AlbumSummary,
    val tracks: List<Song>,
    val description: String? = null,
    /** E.g. other versions of the album and releases like it. */
    val shelves: List<CatalogShelf> = emptyList(),
) {
    /** The sum of the tracks' durations, or null when none is known. */
    val knownDuration: Duration?
        get() = tracks.mapNotNull { it.duration }.takeIf { it.isNotEmpty() }?.reduce(Duration::plus)
}

data class ArtistPage(
    val artist: ArtistSummary,
    val description: String? = null,
    val subscribers: String? = null,
    val monthlyListeners: String? = null,
    val topSongs: List<Song> = emptyList(),
    /** The playlist with all of the artist's songs, when offered. */
    val allSongs: BrowseLink? = null,
    /** Albums, singles, playlists and similar artists. */
    val shelves: List<CatalogShelf> = emptyList(),
)

data class PlaylistPage(
    val playlist: PlaylistSummary,
    val tracks: List<Song>,
    val description: String? = null,
    /** E.g. "150 canciones • 44+ horas". */
    val details: String? = null,
    /** Token for the next tracks; null when all are loaded. */
    val continuation: String? = null,
) {
    val hasMore: Boolean get() = continuation != null
}

/** More tracks of a playlist. */
data class TrackPage(
    val tracks: List<Song>,
    val continuation: String? = null,
) {
    val hasMore: Boolean get() = continuation != null
}

/** The catalog as the features see it: no HTTP, no parsing. Failures are exceptions. */
interface MusicCatalog {
    suspend fun searchSongs(query: String): List<Song>

    suspend fun searchAlbums(query: String): List<AlbumSummary>

    suspend fun searchArtists(query: String): List<ArtistSummary>

    suspend fun searchPlaylists(query: String): List<PlaylistSummary>

    suspend fun album(id: String): AlbumPage

    suspend fun artist(id: String): ArtistPage

    suspend fun playlist(link: BrowseLink): PlaylistPage

    suspend fun playlistContinuation(token: String): TrackPage

    /** The songs YouTube Music would play after [videoId] (its radio), without it. */
    suspend fun relatedSongs(videoId: VideoId): List<Song>
}
