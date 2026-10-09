package io.github.javiernarvaezz.shura.core.ui.artwork

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.flow.Flow

/**
 * Loads a cover into the image caches and its colors into the color cache ahead of time (e.g. the next song's),
 * through the app's single image loader, retrying like a shown cover. Returns when both are cached; cancel to stop.
 */
suspend fun preloadArtwork(
    context: PlatformContext,
    url: String,
    sizePx: Int,
    networkRegained: Flow<Unit>,
) {
    val request = ImageRequest.Builder(context).data(artworkUrl(url, sizePx)).build()
    retryingLoad(networkRegained) { SingletonImageLoader.get(context).execute(request) as? SuccessResult }
    cachedArtworkColors(context, url, networkRegained)
}
