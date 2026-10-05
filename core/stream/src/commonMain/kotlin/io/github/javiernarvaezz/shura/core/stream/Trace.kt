package io.github.javiernarvaezz.shura.core.stream

import com.metrolist.innertubex.InnerTubeLogger

/**
 * Diagnostic timing events, consumed by debug builds only (release passes [NONE]). Names and details must never
 * contain URLs, video ids, tokens or visitor data.
 */
fun interface Trace {
    fun event(
        name: String,
        details: Map<String, String>,
    )

    companion object {
        val NONE = Trace { _, _ -> }
    }
}

/** Convenience for events without details. */
fun Trace.event(name: String) = event(name, emptyMap())

/**
 * Forwards InnerTubeX's stage timings to [Trace]. Only known stage messages pass, only allow-listed detail keys are
 * kept, and the event's media id (a video id) is always dropped.
 */
internal fun Trace.asInnerTubeLogger(): InnerTubeLogger =
    if (this === Trace.NONE) {
        InnerTubeLogger.NONE
    } else {
        InnerTubeLogger { event ->
            if (STAGE_MESSAGES.any { event.message.startsWith(it) }) {
                event("innertubex: ${event.message}", event.details.filterKeys { it in SAFE_DETAIL_KEYS })
            }
        }
    }

private val STAGE_MESSAGES =
    listOf(
        "EJS bootstrap done",
        "watch page config ready",
        "embedded config ready",
        "player responses received",
        "player response selected",
        "player response batch completed",
        "player response decoded",
        "token fetch completed",
        "cipher processing completed",
        "cipher fallback completed",
        "direct stream selected",
        "stream selected",
        "prewarm completed",
        "player config prewarm failed",
        "fetchFreshVisitorData success",
        "fetchFreshVisitorData failed",
    )

private val SAFE_DETAIL_KEYS =
    setOf(
        "elapsedMs",
        "client",
        "profile",
        "count",
        "resultCount",
        "httpStatus",
        "tokenPresent",
        "requestedCount",
        "processedCount",
        "streamingPresent",
        "formatCount",
        "direct",
        "boundedRange",
        "fetched",
        "authenticated",
    )
