package io.github.javiernarvaezz.shura.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTickTest {
    @Test
    fun oneStepIsTheTimeOnePixelOfTheBarRepresents() {
        // A 3-minute song on a 900 px bar moves one pixel every 200 ms.
        assertEquals(200L, progressStepMs(durationMs = 180_000, widthPx = 900f))
    }

    @Test
    fun neverFasterThanAFrame() {
        assertEquals(16L, progressStepMs(durationMs = 1_000, widthPx = 1_000f))
    }

    @Test
    fun atLeastOncePerSecond() {
        assertEquals(1_000L, progressStepMs(durationMs = 3_600_000, widthPx = 100f))
    }

    @Test
    fun unknownDurationOrNoWidthStepsOncePerSecond() {
        assertEquals(1_000L, progressStepMs(durationMs = null, widthPx = 900f))
        assertEquals(1_000L, progressStepMs(durationMs = 180_000, widthPx = 0f))
    }
}
