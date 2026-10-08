package io.github.javiernarvaezz.shura.core.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ClockTest {
    @Test
    fun minutesAndSeconds() {
        assertEquals("0:00", Duration.ZERO.toClock())
        assertEquals("0:07", 7.seconds.toClock())
        assertEquals("3:26", (3.minutes + 26.seconds).toClock())
        assertEquals("59:59", (59.minutes + 59.seconds + 999.milliseconds).toClock())
    }

    @Test
    fun hoursWhenNeeded() {
        assertEquals("1:02:03", (1.hours + 2.minutes + 3.seconds).toClock())
    }

    @Test
    fun negativeShowsZero() {
        assertEquals("0:00", (-5).seconds.toClock())
    }
}
