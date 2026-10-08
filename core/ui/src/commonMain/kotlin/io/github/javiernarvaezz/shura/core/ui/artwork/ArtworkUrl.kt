package io.github.javiernarvaezz.shura.core.ui.artwork

private val sizedArtworkHosts = setOf("lh3.googleusercontent.com", "yt3.googleusercontent.com", "yt3.ggpht.com")

/**
 * The cover at about [sizePx] pixels. Google's artwork hosts take the size as a `=w…-h…` suffix, so a 60 px
 * thumbnail from search can be shown large in the player without upscaling. Other URLs are returned unchanged.
 */
fun artworkUrl(
    url: String?,
    sizePx: Int,
): String? {
    val host = url?.substringAfter("://", "")?.substringBefore('/')
    return when {
        url == null -> null
        host !in sizedArtworkHosts || '=' !in url.substringAfterLast('/') -> url
        else -> url.substringBeforeLast('=') + "=w$sizePx-h$sizePx-l90-rj"
    }
}
