package io.github.javiernarvaezz.shura.core.player

/**
 * Listening time of the current queue item, to decide when it enters the play history ([shouldRecordPlay]). Only
 * time spent playing counts. Not thread-safe: use from the player thread. [now] is a monotonic clock in ms.
 */
class PlayTracker(
    private val now: () -> Long,
) {
    private var playedMs = 0L
    private var playingSince: Long? = null
    private var recorded = false

    fun onPlayingChanged(playing: Boolean) {
        if (playing) {
            if (playingSince == null) playingSince = now()
        } else {
            accumulate()
            playingSince = null
        }
    }

    /** True once, when the current item has been listened to long enough to be recorded. */
    fun checkListened(): Boolean = consume(ended = false)

    /**
     * The player moved to another item; [ended] is true when the previous one finished on its own. Returns whether
     * the previous item should be recorded, then starts counting the new one.
     */
    fun onItemChanged(ended: Boolean): Boolean {
        val record = consume(ended)
        val stillPlaying = playingSince != null
        playedMs = 0
        recorded = false
        playingSince = if (stillPlaying) now() else null
        return record
    }

    private fun consume(ended: Boolean): Boolean {
        accumulate()
        playingSince?.let { playingSince = now() }
        if (!shouldRecordPlay(playedMs, ended, recorded)) return false
        recorded = true
        return true
    }

    private fun accumulate() {
        playingSince?.let { playedMs += now() - it }
        playingSince = playingSince?.let { now() }
    }
}
