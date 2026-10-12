package io.github.javiernarvaezz.shura.core.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/** Queue as the UI sees it: items in queue order and their play order (shuffled when shuffle is on). */
internal fun Player.queueState(): QueueState {
    val timeline = currentTimeline
    val window = Timeline.Window()
    val items = (0 until timeline.windowCount).mapNotNull { timeline.getWindow(it, window).mediaItem.toSong() }
    if (items.size != timeline.windowCount) return QueueState()
    val playOrder = mutableListOf<Int>()
    var index = timeline.getFirstWindowIndex(shuffleModeEnabled)
    while (index != C.INDEX_UNSET && playOrder.size < timeline.windowCount) {
        playOrder += index
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    }
    return QueueState(
        items = items,
        currentIndex = if (items.isEmpty()) -1 else currentMediaItemIndex,
        shuffle = shuffleModeEnabled,
        repeat =
            when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                else -> RepeatMode.Off
            },
        playOrder = playOrder,
    )
}
