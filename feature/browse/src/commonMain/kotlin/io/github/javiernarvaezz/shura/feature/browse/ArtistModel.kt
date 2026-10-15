package io.github.javiernarvaezz.shura.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.ArtistPage
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

/** An artist page's state and actions: play and shuffle act on the top songs. */
class ArtistModel(
    catalog: MusicCatalog,
    player: AudioPlayer,
    id: String,
    scope: CoroutineScope,
    random: Random = Random.Default,
    private val library: Library? = null,
) {
    private val loader = PageLoader(scope) { catalog.artist(id) }
    private val playback = TrackPlayback(player, random)

    val state: StateFlow<PageState<ArtistPage>> = loader.state

    val likes = SongLikes(library, scope)

    /** Whether the artist is followed; there is no follow action without a library. */
    val followed: StateFlow<Boolean> = savedInLibrary(scope, state, library?.artists, { it.artist.id }, { it.id })

    val canFollow: Boolean get() = library != null

    fun toggleFollowed() {
        val artist = loader.page?.artist ?: return
        library?.setFollowed(artist, !followed.value)
    }

    val queue: StateFlow<QueueState> = player.queue

    fun retry() = loader.retry()

    fun play() = playback.playAll(topSongs())

    fun shuffle() = playback.shuffle(topSongs())

    fun playTrack(index: Int) = playback.playFrom(topSongs(), index)

    fun playNext(song: Song) = playback.playNext(song)

    fun enqueue(song: Song) = playback.enqueue(song)

    private fun topSongs() = loader.page?.topSongs.orEmpty()
}

class ArtistViewModel(
    catalog: MusicCatalog,
    player: AudioPlayer,
    id: String,
    library: Library? = null,
) : ViewModel() {
    val model = ArtistModel(catalog, player, id, viewModelScope, library = library)
}
