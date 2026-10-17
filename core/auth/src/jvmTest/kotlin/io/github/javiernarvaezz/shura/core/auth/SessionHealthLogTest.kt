package io.github.javiernarvaezz.shura.core.auth

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionHealthLogTest {
    private val file = File(Files.createTempDirectory("health").toFile(), "session-health.txt")
    private var today = LocalDate.of(2026, 10, 17)
    private val log = SessionHealthLog(file) { today }

    @Test
    fun oneLinePerDayAndState() {
        log.record(alive = true)
        log.record(alive = true)
        today = today.plusDays(1)
        log.record(alive = true)
        assertEquals(listOf("2026-10-17 alive", "2026-10-18 alive"), log.lines())
    }

    @Test
    fun aChangeTheSameDayIsKept() {
        log.record(alive = true)
        log.record(alive = false)
        assertEquals(listOf("2026-10-17 alive", "2026-10-17 expired"), log.lines())
    }

    @Test
    fun nothingRecordedIsEmpty() {
        assertEquals(emptyList(), log.lines())
    }
}
