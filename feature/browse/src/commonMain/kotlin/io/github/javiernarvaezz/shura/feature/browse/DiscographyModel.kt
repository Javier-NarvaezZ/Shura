package io.github.javiernarvaezz.shura.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.javiernarvaezz.shura.core.model.BrowseLink
import io.github.javiernarvaezz.shura.core.model.DiscographyFilter
import io.github.javiernarvaezz.shura.core.model.DiscographyPage
import io.github.javiernarvaezz.shura.core.model.MusicCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** An artist's whole discography ("Ver todo" of an album shelf) and its filters; plain so it can be tested. */
class DiscographyModel(
    catalog: MusicCatalog,
    link: BrowseLink,
    scope: CoroutineScope,
) {
    /** The filter shown, once the user picks one; until then, the link's own page. */
    private var filterToken: String? = null

    private val loader =
        PageLoader(scope) { filterToken?.let { catalog.discographyFilter(it) } ?: catalog.discography(link) }

    val state: StateFlow<PageState<DiscographyPage>> = loader.state

    /** Shows the releases of [filter]; the filter already shown loads nothing. */
    fun select(filter: DiscographyFilter) {
        val token = filter.token
        val shown = if (filterToken == null) filter.selected else token == filterToken
        if (token == null || shown) return
        filterToken = token
        loader.retry()
    }

    fun retry() = loader.retry()
}

class DiscographyViewModel(
    catalog: MusicCatalog,
    link: BrowseLink,
) : ViewModel() {
    val model = DiscographyModel(catalog, link, viewModelScope)
}
