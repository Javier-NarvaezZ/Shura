package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * "Me gusta" for a screen's songs: which ones are liked, and the toggle. A set read by every row, so a list does not
 * query the library row by row. Without a [library] (desktop for now) nothing is liked and toggling does nothing.
 */
class SongLikes(
    private val library: Library?,
    scope: CoroutineScope,
) {
    val likedIds: StateFlow<Set<VideoId>> =
        library
            ?.likedSongs
            ?.map { songs -> songs.mapTo(HashSet()) { it.videoId } }
            ?.stateIn(scope, SharingStarted.Eagerly, emptySet())
            ?: MutableStateFlow(emptySet())

    /** Whether there is a library to like songs in; screens hide the action otherwise. */
    val isAvailable: Boolean get() = library != null

    fun toggle(song: Song) {
        library?.setLiked(song, liked = song.videoId !in likedIds.value)
    }
}
