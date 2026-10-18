package io.github.javiernarvaezz.shura.feature.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FormatOffsetTest {
    @Test
    fun showsTenthsWithASignAndADecimalComma() {
        assertEquals("0", formatOffset(Duration.ZERO))
        assertEquals("+0,5", formatOffset(500.milliseconds))
        assertEquals("−1,5", formatOffset((-1_500).milliseconds))
        assertEquals("+12,0", formatOffset(12.seconds))
    }
}
