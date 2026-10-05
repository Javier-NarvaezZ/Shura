package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.InnerTubeLogEvent
import com.metrolist.innertubex.InnerTubeLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceTest {
    private val events = mutableListOf<Pair<String, Map<String, String>>>()
    private val logger = Trace { name, details -> events += name to details }.asInnerTubeLogger()

    private fun log(
        message: String,
        details: Map<String, String> = emptyMap(),
        mediaId: String? = null,
    ) = logger.log(InnerTubeLogEvent(InnerTubeLogLevel.DEBUG, "InnerTubeExtractor", message, mediaId, details))

    @Test
    fun forwardsKnownStageEventsWithAllowedDetailsOnly() {
        log(
            "cipher processing completed",
            mapOf(
                "elapsedMs" to "812",
                "client" to "WEB_REMIX",
                "playerUrl" to "https://www.youtube.com/s/player/x/base.js",
            ),
            mediaId = "7fwUH0oRmkQ",
        )

        assertEquals(
            listOf("innertubex: cipher processing completed" to mapOf("elapsedMs" to "812", "client" to "WEB_REMIX")),
            events,
        )
    }

    @Test
    fun keepsTimingMessagesThatCarryTheirOwnNumbers() {
        log("EJS bootstrap done elapsed=1532ms")

        assertEquals("innertubex: EJS bootstrap done elapsed=1532ms", events.single().first)
    }

    @Test
    fun dropsUnknownMessagesEntirely() {
        log("EJS solve failed player=abc type=IllegalStateException")
        log("something new https://example.com/watch?v=7fwUH0oRmkQ")

        assertTrue(events.isEmpty())
    }

    @Test
    fun neverForwardsTheMediaId() {
        log("stream selected", mapOf("profile" to "VISIONOS_0_1__nopo"), mediaId = "7fwUH0oRmkQ")

        assertTrue(
            events.none { (name, details) -> "7fwUH0oRmkQ" in name || details.values.any { "7fwUH0oRmkQ" in it } },
        )
    }
}
