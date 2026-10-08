package io.github.javiernarvaezz.shura.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.player.AudioPlayer

/** Keeps the search (query, results, status) across tab switches and rotation. */
class SearchViewModel(
    search: suspend (String) -> List<Song>,
    player: AudioPlayer,
    onPlaybackIntent: () -> Unit,
) : ViewModel() {
    val controller = SearchController(search, player, viewModelScope, onPlaybackIntent)
}
