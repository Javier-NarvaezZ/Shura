package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.model.Song
import io.github.javiernarvaezz.shura.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueStoreContractTest {
    private val songs =
        listOf(
            "7fwUH0oRmkQ",
            "dQw4w9WgXcQ",
            "kJQP7kiw5Fk",
        ).map { Song(VideoId(it), "T $it", emptyList()) }

    private fun snapshot(
        items: List<Song> = songs,
        current: Int = 1,
        position: Long = 1000,
        order: List<Int> = listOf(1, 2, 0),
    ) = QueueSnapshot(items, current, position, shuffle = true, repeat = RepeatMode.All, playOrder = order)

    @Test
    fun aConsistentSnapshotIsRestorable() {
        assertTrue(snapshot().isRestorable)
    }

    @Test
    fun inconsistentSnapshotsAreNotRestorable() {
        assertFalse(snapshot(items = emptyList(), order = emptyList(), current = 0).isRestorable)
        assertFalse(snapshot(current = 3).isRestorable)
        assertFalse(snapshot(position = -1).isRestorable)
        assertFalse(snapshot(order = listOf(0, 0, 1)).isRestorable)
        assertFalse(snapshot(order = listOf(0, 1)).isRestorable)
    }

    @Test
    fun playsAreRecordedOnceAfterThirtySecondsOrAtTheEnd() {
        assertFalse(shouldRecordPlay(playedMs = 29_999, ended = false, alreadyRecorded = false))
        assertTrue(shouldRecordPlay(playedMs = 30_000, ended = false, alreadyRecorded = false))
        assertTrue(shouldRecordPlay(playedMs = 5_000, ended = true, alreadyRecorded = false))
        assertFalse(shouldRecordPlay(playedMs = 90_000, ended = true, alreadyRecorded = true))
    }
}
