package io.github.javiernarvaezz.shura.core.innertube

import io.github.javiernarvaezz.shura.core.model.Song
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration

/**
 * Turns search responses into [Song]s.
 *
 * Tolerant by design: list items are found anywhere in the tree, and anything that does not look like
 * an album-audio track (`MUSIC_VIDEO_TYPE_ATV`) with a valid id and title is skipped instead of failing.
 */
internal object SearchParser {
    fun parseSongs(root: JsonElement): List<Song> = findAll(root, ListItems.RENDERER).mapNotNull { ListItems.song(it) }

    fun parseDuration(text: String): Duration? = ListItems.parseDuration(text)
}
