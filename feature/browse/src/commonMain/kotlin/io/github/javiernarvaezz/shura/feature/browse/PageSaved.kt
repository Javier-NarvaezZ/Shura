package io.github.javiernarvaezz.shura.feature.browse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the loaded page's album, playlist or artist is in the library's [items], matched by the id the page
 * itself reports. False while the page loads, and always without a library.
 */
internal fun <P, T> savedInLibrary(
    scope: CoroutineScope,
    state: StateFlow<PageState<P>>,
    items: StateFlow<List<T>>?,
    pageId: (P) -> String,
    itemId: (T) -> String,
): StateFlow<Boolean> {
    if (items == null) return MutableStateFlow(false)
    return combine(state, items) { page, saved ->
        val id = (page as? PageState.Loaded<P>)?.page?.let(pageId)
        id != null && saved.any { itemId(it) == id }
    }.stateIn(scope, SharingStarted.Eagerly, false)
}
