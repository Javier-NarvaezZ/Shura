package io.github.javiernarvaezz.shura.core.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.random.Random

/**
 * The player given to the media session: ExoPlayer with native shuffle and repeat, so the notification, Bluetooth
 * and every controller see the real state, plus two queue rules:
 * - turning shuffle on (or loading a new queue while shuffled) draws a fresh order with the current item first;
 * - items added right after the current one ("play next") also play next in the shuffled order.
 */
@OptIn(UnstableApi::class)
internal class ShuraPlayer(
    private val exo: ExoPlayer,
    private val random: Random = Random.Default,
) : ForwardingPlayer(exo) {
    init {
        exo.setShuffleOrder(QueueShuffleOrder(PlayOrder.identity(exo.mediaItemCount)))
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled) reshuffle()
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        super.setMediaItems(mediaItems)
        reshuffleIfEnabled()
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        resetPosition: Boolean,
    ) {
        super.setMediaItems(mediaItems, resetPosition)
        reshuffleIfEnabled()
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        reshuffleIfEnabled()
    }

    override fun addMediaItem(
        index: Int,
        mediaItem: MediaItem,
    ) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ) {
        val current = exo.currentMediaItemIndex
        val before = playOrder()
        val playNext = exo.shuffleModeEnabled && exo.mediaItemCount > 0 && index == current + 1
        super.addMediaItems(index, mediaItems)
        if (playNext) {
            exo.setShuffleOrder(
                QueueShuffleOrder(before.insert(index, mediaItems.size, atPosition = before.positionOf(current) + 1)),
            )
        }
    }

    /**
     * Loads a saved queue without preparing it (nothing is resolved or downloaded until play), keeping its saved
     * shuffled order instead of drawing a new one.
     */
    fun restore(
        items: List<MediaItem>,
        snapshot: QueueSnapshot,
    ) {
        exo.setMediaItems(items, snapshot.currentIndex, snapshot.positionMs)
        restoreModes(snapshot)
    }

    /** Applies a saved shuffled order, shuffle and repeat to the loaded queue, if it is the same size. */
    fun restoreModes(snapshot: QueueSnapshot) {
        if (exo.mediaItemCount != snapshot.items.size) return
        exo.setShuffleOrder(QueueShuffleOrder(PlayOrder.fromList(snapshot.playOrder)))
        exo.shuffleModeEnabled = snapshot.shuffle
        exo.repeatMode =
            when (snapshot.repeat) {
                RepeatMode.Off -> REPEAT_MODE_OFF
                RepeatMode.All -> REPEAT_MODE_ALL
                RepeatMode.One -> REPEAT_MODE_ONE
            }
    }

    fun playOrder(): PlayOrder =
        (exo.shuffleOrder as? QueueShuffleOrder)?.playOrder ?: PlayOrder.identity(exo.mediaItemCount)

    private fun reshuffleIfEnabled() {
        if (exo.shuffleModeEnabled) reshuffle()
    }

    private fun reshuffle() {
        val count = exo.mediaItemCount
        val order =
            if (count ==
                0
            ) {
                PlayOrder.identity(0)
            } else {
                PlayOrder.shuffled(count, exo.currentMediaItemIndex.coerceIn(0, count - 1), random)
            }
        exo.setShuffleOrder(QueueShuffleOrder(order))
    }
}
