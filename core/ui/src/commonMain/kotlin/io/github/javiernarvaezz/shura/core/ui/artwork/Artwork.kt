package io.github.javiernarvaezz.shura.core.ui.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.Image
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import io.github.javiernarvaezz.shura.core.ui.icons.ShuraIcons
import io.github.javiernarvaezz.shura.core.ui.theme.Shura
import io.github.javiernarvaezz.shura.core.ui.theme.ShuraMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A cover at about [sizePx] pixels (see [artworkUrl]), on a neutral placeholder that also stands in when there is
 * no cover or it fails to load. Loaded by the app's single image loader (ADR 0001 R7).
 */
@Composable
fun Artwork(
    url: String?,
    sizePx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = Shura.shapes.thumbnail,
) {
    Box(modifier.clip(shape).background(Shura.colors.surface2), contentAlignment = Alignment.Center) {
        Icon(
            ShuraIcons.MusicNote,
            contentDescription = null,
            tint = Shura.colors.textMuted,
            modifier = Modifier.fillMaxWidth(PLACEHOLDER_ICON_FRACTION),
        )
        if (url != null) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(artworkUrl(url, sizePx))
                        .crossfade(ShuraMotion.QUICK_MS)
                        .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The colors of a cover, computed once per URL from a 64 px copy and shared by everyone who asks (an LRU of 32).
 * Null until computed, or when the cover cannot be loaded: callers fall back to the neutral tokens.
 */
@Composable
fun rememberArtworkColors(url: String?): ArtworkColors? {
    val context = LocalPlatformContext.current
    var colors by remember(url) { mutableStateOf(url?.let { ArtworkColorCache[it] }) }
    LaunchedEffect(url) {
        if (url == null || colors != null) return@LaunchedEffect
        colors = loadArtworkColors(context, url)?.also { ArtworkColorCache[url] = it }
    }
    return colors
}

private object ArtworkColorCache {
    private val cache = LruCache<String, ArtworkColors>(capacity = 32)

    operator fun get(url: String): ArtworkColors? = cache[url]

    operator fun set(
        url: String,
        colors: ArtworkColors,
    ) {
        cache[url] = colors
    }
}

private suspend fun loadArtworkColors(
    context: PlatformContext,
    url: String,
): ArtworkColors? {
    val request =
        ImageRequest
            .Builder(context)
            .data(artworkUrl(url, SAMPLE_PX))
            .size(SAMPLE_PX)
            .readablePixels()
            .build()
    val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return null
    return withContext(Dispatchers.Default) {
        val bitmap = image.toComposeBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        ArtworkPalette.from(pixels, bitmap.width, bitmap.height)
    }
}

/** Asks for a bitmap whose pixels can be read (Android's default hardware bitmaps cannot). */
internal expect fun ImageRequest.Builder.readablePixels(): ImageRequest.Builder

internal expect fun Image.toComposeBitmap(): ImageBitmap

private const val SAMPLE_PX = 64
private const val PLACEHOLDER_ICON_FRACTION = 0.4f
