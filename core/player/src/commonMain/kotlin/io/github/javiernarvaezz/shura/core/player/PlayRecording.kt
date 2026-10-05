package io.github.javiernarvaezz.shura.core.player

/** A play counts for the history after this much listening, so skipped songs are not recorded. */
const val PLAY_RECORD_THRESHOLD_MS = 30_000L

/** Records once per play: when it ends, or once it has played for [PLAY_RECORD_THRESHOLD_MS]. */
fun shouldRecordPlay(
    playedMs: Long,
    ended: Boolean,
    alreadyRecorded: Boolean,
): Boolean = !alreadyRecorded && (ended || playedMs >= PLAY_RECORD_THRESHOLD_MS)
