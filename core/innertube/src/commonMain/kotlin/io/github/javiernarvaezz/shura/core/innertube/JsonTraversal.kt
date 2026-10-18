package io.github.javiernarvaezz.shura.core.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Tolerant reading of InnerTube JSON: renderers are found by name anywhere below a node, and missing or
 * mistyped fields read as null instead of failing.
 */
internal fun JsonElement?.path(vararg keys: String): JsonElement? =
    keys.fold(this) { current, key -> (current as? JsonObject)?.get(key) }

internal fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Every object stored under [key] at any depth, in document order. */
internal fun findAll(
    root: JsonElement?,
    key: String,
): List<JsonObject> {
    val found = mutableListOf<JsonObject>()

    fun walk(element: JsonElement?) {
        when (element) {
            is JsonObject -> {
                element.forEach { (k, v) ->
                    if (k == key && v is JsonObject) found += v
                    walk(v)
                }
            }

            is JsonArray -> {
                element.forEach(::walk)
            }

            else -> {}
        }
    }
    walk(root)
    return found
}

/** Every string stored under [key] at any depth, in document order. */
internal fun findStrings(
    root: JsonElement?,
    key: String,
): List<String> {
    val found = mutableListOf<String>()

    fun walk(element: JsonElement?) {
        when (element) {
            is JsonObject -> {
                element.forEach { (k, v) ->
                    if (k == key) v.string()?.let(found::add)
                    walk(v)
                }
            }

            is JsonArray -> {
                element.forEach(::walk)
            }

            else -> {}
        }
    }
    walk(root)
    return found
}

/** The runs of a text object (`{"runs": [...]}`). */
internal fun runsOf(text: JsonElement?): List<Run> =
    (text.path("runs") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::Run) }.orEmpty()

/** The plain text of a text object, or null when empty. */
internal fun textOf(text: JsonElement?): String? =
    runsOf(text).joinToString("") { it.text }.trim().takeIf { it.isNotEmpty() }

/** The URL of the widest image in the first `thumbnails` list below [root], always with a scheme. */
internal fun largestThumbnail(root: JsonElement?): String? =
    findThumbnails(root)
        ?.mapNotNull { it as? JsonObject }
        ?.maxByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: 0 }
        ?.get("url")
        .string()
        ?.let { if (it.startsWith("//")) "https:$it" else it }

private fun findThumbnails(root: JsonElement?): JsonArray? =
    when (root) {
        is JsonObject -> (root["thumbnails"] as? JsonArray) ?: root.values.firstNotNullOfOrNull(::findThumbnails)
        is JsonArray -> root.firstNotNullOfOrNull(::findThumbnails)
        else -> null
    }

/** One run of text, with the page it links to, if any. */
internal class Run(
    json: JsonObject,
) {
    val text: String = json["text"].string().orEmpty()
    private val browse = json.path("navigationEndpoint", "browseEndpoint")
    val browseId: String? = browse.path("browseId").string()
    val pageType: String? = browse.pageType()
    val watchVideoId: String? = json.path("navigationEndpoint", "watchEndpoint", "videoId").string()

    /** A " • " or similar separator between fields. */
    val isSeparator: Boolean get() = text.isBlank() || text.trim() == "•"
}

/** The page type of a browse endpoint (`MUSIC_PAGE_TYPE_ALBUM`…). */
internal fun JsonElement?.pageType(): String? =
    path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType").string()

internal object PageTypes {
    const val ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    const val ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    const val PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
    const val DISCOGRAPHY = "MUSIC_PAGE_TYPE_ARTIST_DISCOGRAPHY"
}

private val YEAR = Regex("""^\d{4}$""")

/** A four-digit year among [runs], if any. */
internal fun yearOf(runs: List<Run>): Int? =
    runs.firstNotNullOfOrNull { run ->
        run.text
            .trim()
            .takeIf(YEAR::matches)
            ?.toInt()
    }
