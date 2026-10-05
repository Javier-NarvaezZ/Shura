package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.stream.Trace
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackWarmupTest {
    private var runs = 0
    private var enabled = true
    private var powerSave = false
    private var metered = false
    private var dataSaverRestricts = false
    private val events = mutableListOf<String>()

    private fun TestScope.warmup(warmUp: suspend () -> Unit = { runs++ }) =
        PlaybackWarmup(
            warmUp = warmUp,
            scope = backgroundScope,
            isEnabled = { enabled },
            isPowerSaveMode = { powerSave },
            isActiveNetworkMetered = { metered },
            isDataSaverRestricting = { dataSaverRestricts },
            trace = Trace { name, details -> events += "$name ${details["reason"].orEmpty()}".trim() },
        )

    @Test
    fun runsAtMostOncePerInstance() =
        runTest {
            val warmup = warmup()

            repeat(3) { warmup.onPlaybackIntent() }
            runCurrent()

            assertEquals(1, runs)
        }

    @Test
    fun doesNothingWhenDisabled() =
        runTest {
            enabled = false
            warmup().onPlaybackIntent()
            runCurrent()

            assertEquals(0, runs)
            assertEquals(listOf("warmup: skipped disabled"), events)
        }

    @Test
    fun doesNothingInPowerSaveMode() =
        runTest {
            powerSave = true
            warmup().onPlaybackIntent()
            runCurrent()

            assertEquals(0, runs)
            assertEquals(listOf("warmup: skipped powerSave"), events)
        }

    @Test
    fun runsOnAnUnmeteredNetworkWithoutDataSaverRestriction() =
        runTest {
            warmup().onPlaybackIntent()
            runCurrent()

            assertEquals(1, runs)
        }

    @Test
    fun runsOnAMeteredNetworkWhenDataSaverDoesNotRestrictTheApp() =
        runTest {
            metered = true
            warmup().onPlaybackIntent()
            runCurrent()

            assertEquals(1, runs)
        }

    @Test
    fun runsOnAnUnmeteredNetworkEvenIfDataSaverWouldRestrictTheApp() =
        runTest {
            dataSaverRestricts = true
            warmup().onPlaybackIntent()
            runCurrent()

            assertEquals(1, runs)
        }

    @Test
    fun skipsOnAMeteredNetworkWhenDataSaverRestrictsTheApp() =
        runTest {
            metered = true
            dataSaverRestricts = true
            warmup().onPlaybackIntent()
            runCurrent()

            assertEquals(0, runs)
            assertEquals(listOf("warmup: skipped dataSaver"), events)
        }

    @Test
    fun aSkippedIntentDoesNotUseUpTheSingleRun() =
        runTest {
            val warmup = warmup()
            powerSave = true
            warmup.onPlaybackIntent()
            powerSave = false
            warmup.onPlaybackIntent()
            runCurrent()

            assertEquals(1, runs)
        }

    @Test
    fun repeatedSkipsAreReportedOnce() =
        runTest {
            powerSave = true
            val warmup = warmup()
            repeat(5) { warmup.onPlaybackIntent() }

            assertEquals(listOf("warmup: skipped powerSave"), events)
        }

    @Test
    fun aFailedWarmUpIsNotPropagatedNorRetried() =
        runTest {
            val warmup =
                warmup {
                    runs++
                    error("offline")
                }

            warmup.onPlaybackIntent()
            runCurrent()
            warmup.onPlaybackIntent()
            runCurrent()

            assertEquals(1, runs)
        }
}
