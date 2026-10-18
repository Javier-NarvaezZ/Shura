package io.github.javiernarvaezz.shura.core.ui.artwork

private val sizedArtworkHosts = setOf("lh3.googleusercontent.com", "yt3.googleusercontent.com", "yt3.ggpht.com")

/** The first of [artworkUrls]: the cover at about [sizePx] pixels. */
fun artworkUrl(
    url: String?,
    sizePx: Int,
): String? = url?.let { artworkUrls(it, sizePx).first() }

/**
 * The cover at about [sizePx] pixels, best first, with smaller ones to fall back on when one does not exist.
 *
 * Google's artwork hosts take the size as a `=w…-h…` suffix, so a 60 px thumbnail from search can be shown large in
 * the player without upscaling. A video's frame (`i.ytimg.com`) comes in fixed sizes, not all made for every video:
 * from 1280 × 720 down, the 4:3 ones (with black bars) last; small, the 320 × 180 frame. Other URLs are left alone.
 */
fun artworkUrls(
    url: String,
    sizePx: Int,
): List<String> {
    val frames = VIDEO_FRAME.find(url)?.value
    return when {
        frames == null -> listOf(sizedArtworkUrl(url, sizePx))
        sizePx <= SMALL_FRAME_PX -> listOf("$frames/mqdefault.jpg", "$frames/hqdefault.jpg")
        else -> LARGE_FRAMES.map { "$frames/$it.jpg" }
    }
}

private val VIDEO_FRAME = Regex("""^https://i\.ytimg\.com/vi(?:_webp)?/[A-Za-z0-9_-]{11}""")
private const val SMALL_FRAME_PX = 180
private val LARGE_FRAMES = listOf("maxresdefault", "hq720", "sddefault", "hqdefault")

private fun sizedArtworkUrl(
    url: String,
    sizePx: Int,
): String {
    val host = url.substringAfter("://", "").substringBefore('/')
    return when {
        host !in sizedArtworkHosts || '=' !in url.substringAfterLast('/') -> url
        else -> url.substringBeforeLast('=') + "=w$sizePx-h$sizePx-l90-rj"
    }
}

/** The sizes covers are requested at, so covers shown at nearby sizes share one download and one cache entry. */
private val artworkSteps = intArrayOf(120, 226, 544, 720, 1080, 1440)

/** The smallest step that covers [shownPx] pixels, or the largest one. */
fun artworkStep(shownPx: Int): Int = artworkSteps.firstOrNull { it >= shownPx } ?: artworkSteps.last()
