package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.AlbumPage
import io.github.javiernarvaezz.shura.core.model.AlbumSummary
import io.github.javiernarvaezz.shura.core.model.ArtistPage
import io.github.javiernarvaezz.shura.core.model.ArtistSummary
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.MusicCatalog
import io.github.javiernarvaezz.shura.core.model.PlaylistPage
import io.github.javiernarvaezz.shura.core.model.PlaylistSummary
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.TrackPage
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Anonymous YouTube Music catalog client. It never sends cookies, authorization or visitor data.
 *
 * The caller owns [httpClient] and its engine.
 */
class InnerTubeClient(
    private val httpClient: HttpClient,
    private val config: InnerTubeConfig = InnerTubeConfig(),
) : MusicCatalog {
    /** Searches album-audio songs. [query] is trimmed and must not be blank. */
    override suspend fun searchSongs(query: String): List<Song> =
        SearchParser.parseSongs(raw(InnerTubeRequest.Search(query, SearchFilter.Songs)))

    override suspend fun searchAlbums(query: String): List<AlbumSummary> =
        SearchRows.albums(raw(InnerTubeRequest.Search(query, SearchFilter.Albums)))

    override suspend fun searchArtists(query: String): List<ArtistSummary> =
        SearchRows.artists(raw(InnerTubeRequest.Search(query, SearchFilter.Artists)))

    /** YouTube Music's featured playlists for [query]. */
    override suspend fun searchPlaylists(query: String): List<PlaylistSummary> =
        SearchRows.playlists(raw(InnerTubeRequest.Search(query, SearchFilter.Playlists)))

    /** An album page (`MPREb_…`). */
    override suspend fun album(id: String): AlbumPage = AlbumParser.parse(raw(InnerTubeRequest.Browse(id)), id)

    /** An artist page (`UC…`). */
    override suspend fun artist(id: String): ArtistPage = ArtistParser.parse(raw(InnerTubeRequest.Browse(id)), id)

    /** A playlist page (`VL…`) with its first tracks; [playlistContinuation] loads the rest. */
    override suspend fun playlist(link: BrowseLink): PlaylistPage =
        PlaylistParser.parse(raw(InnerTubeRequest.Browse(link.id, link.params)), link.id)

    /** The next tracks of a playlist, from the token its previous page ended with. */
    override suspend fun playlistContinuation(token: String): TrackPage =
        PlaylistParser.parseContinuation(raw(InnerTubeRequest.Continuation(token)))

    override suspend fun relatedSongs(videoId: VideoId): List<Song> =
        NextParser.parse(raw(InnerTubeRequest.Next(videoId)), videoId)

    /**
     * Sends [request] exactly as the typed calls do and returns the raw response. Used by them and by diagnostics
     * (fixture recording in `:tools:livecheck`).
     */
    suspend fun raw(request: InnerTubeRequest): JsonElement =
        when (request) {
            is InnerTubeRequest.Search -> {
                post("search") {
                    put("query", request.query)
                    put("params", SearchParams.of(request.filter))
                }
            }

            is InnerTubeRequest.Browse -> {
                post("browse") {
                    put("browseId", request.browseId)
                    request.params?.let { put("params", it) }
                }
            }

            is InnerTubeRequest.Next -> {
                post("next") {
                    put("videoId", request.videoId.value)
                    put("playlistId", RADIO_PREFIX + request.videoId.value)
                    put("isAudioOnly", true)
                    put("enablePersistentPlaylistPanel", true)
                }
            }

            is InnerTubeRequest.Continuation -> {
                post("browse") { put("continuation", request.token) }
            }
        }

    private suspend fun post(
        endpoint: String,
        fields: JsonObjectBuilder.() -> Unit,
    ): JsonElement =
        post(
            endpoint,
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", CLIENT_NAME)
                        put("clientVersion", config.clientVersion)
                        put("hl", config.hl)
                        put("gl", config.gl)
                    }
                }
                fields()
            },
        )

    private suspend fun post(
        endpoint: String,
        body: JsonElement,
    ): JsonElement {
        val response: HttpResponse =
            transport {
                httpClient.post("$API_BASE/$endpoint?prettyPrint=false") {
                    header(HttpHeaders.UserAgent, config.userAgent)
                    header("X-Goog-Api-Format-Version", "1")
                    header("X-YouTube-Client-Name", CLIENT_ID)
                    header("X-YouTube-Client-Version", config.clientVersion)
                    header(HttpHeaders.Origin, ORIGIN)
                    header(HttpHeaders.Referrer, "$ORIGIN/")
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                }
            }
        if (!response.status.isSuccess()) throw InnerTubeException.Http(response.status.value)
        return parseJson(transport { response.bodyAsText() })
    }

    /** Wraps engine-specific transport errors as [InnerTubeException.Network]; cancellation passes through. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> transport(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InnerTubeException.Network(e)
        }

    private fun parseJson(text: String): JsonElement =
        try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw InnerTubeException.Parse(e)
        }

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val API_BASE = "$ORIGIN/youtubei/v1"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"

        /** A song's radio playlist, as YouTube Music builds it. */
        const val RADIO_PREFIX = "RDAMVM"
    }
}
