package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song
import kotlin.random.Random

/**
 * How a list of songs (a catalog page, the liked songs) plays: all in order, shuffled, from one of them, or one song
 * next or last.
 */
class TrackPlayback(
    private val player: AudioPlayer,
    private val random: Random,
) {
    fun playAll(tracks: List<Song>) {
        if (tracks.isEmpty()) return
        player.setShuffle(false)
        player.playQueue(tracks, 0)
    }

    /** Shuffled from a random first song; the player draws the rest of the order. */
    fun shuffle(tracks: List<Song>) {
        if (tracks.isEmpty()) return
        player.setShuffle(true)
        player.playQueue(tracks, random.nextInt(tracks.size))
    }

    /** From [index], with the whole list as the queue, keeping the current shuffle setting. */
    fun playFrom(
        tracks: List<Song>,
        index: Int,
    ) {
        if (index in tracks.indices) player.playQueue(tracks, index)
    }

    fun playNext(song: Song) = player.playNext(listOf(song))

    fun enqueue(song: Song) = player.enqueue(listOf(song))
}
