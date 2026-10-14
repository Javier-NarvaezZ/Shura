package io.github.javiernarvaezz.shura.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.AlbumPage
import io.github.javiernarvaezz.shura.core.model.MusicCatalog
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer
import io.github.javiernarvaezz.shura.core.player.Library
import io.github.javiernarvaezz.shura.core.player.QueueState
import io.github.javiernarvaezz.shura.core.player.SongLikes
import io.github.javiernarvaezz.shura.core.player.TrackPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** An album page's state and actions; plain so it can be tested without Android. */
class AlbumModel(
    catalog: MusicCatalog,
    player: AudioPlayer,
    id: String,
    scope: CoroutineScope,
    random: Random = Random.Default,
    private val library: Library? = null,
) {
    private val loader = PageLoader(scope) { catalog.album(id) }
    private val playback = TrackPlayback(player, random)

    val state: StateFlow<PageState<AlbumPage>> = loader.state

    val likes = SongLikes(library, scope)

    /** Whether the album is saved in the library; there is no save action without a library. */
    val saved: StateFlow<Boolean> = savedInLibrary(scope, state, library?.albums, { it.album.id }, { it.id })

    val canSave: Boolean get() = library != null

    fun toggleSaved() {
        val album = loader.page?.album ?: return
        library?.setSaved(album, !saved.value)
    }

    /** The queue, to mark the song that is playing. */
    val queue: StateFlow<QueueState> = player.queue

    fun retry() = loader.retry()

    fun play() = playback.playAll(tracks())

    fun shuffle() = playback.shuffle(tracks())

    fun playTrack(index: Int) = playback.playFrom(tracks(), index)

    fun playNext(song: Song) = playback.playNext(song)

    fun enqueue(song: Song) = playback.enqueue(song)

    private fun tracks() = loader.page?.tracks.orEmpty()
}

class AlbumViewModel(
    catalog: MusicCatalog,
    player: AudioPlayer,
    id: String,
    library: Library? = null,
) : ViewModel() {
    val model = AlbumModel(catalog, player, id, viewModelScope, library = library)
}
