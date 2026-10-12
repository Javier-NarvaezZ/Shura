package io.github.javiernarvaezz.shura.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.player.AudioPlayer

/** Keeps the search (query, kind, results, status) across tab switches and rotation. */
class SearchViewModel(
    sources: SearchSources,
    player: AudioPlayer,
    onPlaybackIntent: () -> Unit,
) : ViewModel() {
    val controller = SearchController(sources, player, viewModelScope, onPlaybackIntent)
}
