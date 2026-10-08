package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.stream.StreamAttempt
import io.github.javiernarvaezz.shura.core.stream.StreamFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlaybackErrorLogTest {
    private fun record(
        second: Long,
        error: PlaybackError = PlaybackError.Network,
    ) = PlaybackErrorRecord(
        at = Instant.fromEpochSeconds(1_791_000_000 + second),
        appVersion = "0.0.1",
        network = NetworkSnapshot(NetworkTransport.Cellular, validated = true),
        error = error,
        platformCode = "ERROR_CODE_IO_UNSPECIFIED",
    )

    private val streamFailure =
        PlaybackErrorRecord(
            at = Instant.fromEpochSeconds(1_791_000_000),
            appVersion = "0.0.1",
            network = NetworkSnapshot(NetworkTransport.Wifi, validated = false),
            error = PlaybackError.Stream(StreamFailure.Network),
            platformCode = "ERROR_CODE_IO_UNSPECIFIED",
            causeType = "StreamResolveException.NETWORK",
            attempts =
                listOf(
                    StreamAttempt("WEB_REMIX", "selection:GVS PO-token provider unavailable"),
                    StreamAttempt("VISIONOS_0_1__nopo", "request:IllegalStateException"),
                ),
        )

    @Test
    fun newestFirstAndCappedAtFive() {
        var records = emptyList<PlaybackErrorRecord>()
        for (second in 1L..7L) records = PlaybackErrorLog.append(records, record(second))

        assertEquals(PlaybackErrorLog.CAPACITY, records.size)
        assertEquals((7L downTo 3L).map(::record), records)
    }

    @Test
    fun recordsRoundTrip() {
        val records =
            listOf(
                streamFailure,
                record(1, PlaybackError.Http(403)).copy(network = null, platformCode = null),
                record(2, PlaybackError.Decoding),
                record(3, PlaybackError.Unknown).copy(network = NetworkSnapshot(NetworkTransport.None, false)),
            )

        assertEquals(records, PlaybackErrorLog.decode(PlaybackErrorLog.encode(records)))
    }

    @Test
    fun appendedRecordsAreSanitized() {
        val leaky =
            streamFailure.copy(
                causeType = "Exception for dQw4w9WgXcQ",
                attempts =
                    listOf(
                        StreamAttempt(
                            "WEB|REMIX",
                            "request:failed https://rr1---sn-x.googlevideo.com/videoplayback?sig=X",
                        ),
                        StreamAttempt("WEB:CREATOR", "selection:token\tbad\nline"),
                    ),
            )

        val stored = PlaybackErrorLog.append(emptyList(), leaky).single()
        val text = PlaybackErrorLog.encode(listOf(stored)) + PlaybackErrorLog.describe(stored)

        assertFalse("dQw4w9WgXcQ" in text)
        assertFalse("googlevideo" in text)
        assertEquals("Exception for <id>", stored.causeType)
        assertEquals(StreamAttempt("WEB?REMIX", "request:failed <url>"), stored.attempts[0])
        assertEquals(StreamAttempt("WEB?CREATOR", "selection:token?bad?line"), stored.attempts[1])
        assertEquals(listOf(stored), PlaybackErrorLog.decode(PlaybackErrorLog.encode(listOf(stored))))
    }

    @Test
    fun attemptsAreCapped() {
        val many = streamFailure.copy(attempts = List(40) { StreamAttempt("P$it", "request:x") })

        assertEquals(
            PlaybackErrorLog.MAX_ATTEMPTS,
            PlaybackErrorLog
                .append(emptyList(), many)
                .single()
                .attempts.size,
        )
    }

    @Test
    fun corruptOrUnknownLinesAreDropped() {
        val good = PlaybackErrorLog.encode(listOf(record(1)))
        val text =
            listOf(
                "garbage",
                "v0\t1\t0.0.1\tcellular\ttrue\tnetwork\t-\t-\t",
                "v1\tnot-a-number\t0.0.1\tcellular\ttrue\tnetwork\t-\t-\t",
                "v1\t1\t0.0.1",
                good,
                "",
            ).joinToString("\n")

        assertEquals(listOf(record(1)), PlaybackErrorLog.decode(text))
        assertEquals(emptyList(), PlaybackErrorLog.decode(""))
    }

    @Test
    fun describeIsOneReadableLine() {
        val line = PlaybackErrorLog.describe(streamFailure)

        assertEquals(
            "2026-10-03T04:00:00Z app=0.0.1 net=wifi validated=false error=stream:Network " +
                "media3=ERROR_CODE_IO_UNSPECIFIED cause=StreamResolveException.NETWORK " +
                "attempts=WEB_REMIX:selection:GVS PO-token provider unavailable|" +
                "VISIONOS_0_1__nopo:request:IllegalStateException",
            line,
        )
        assertTrue("\n" !in line)
        assertEquals(
            "2026-10-03T04:00:01Z app=0.0.1 net=unknown error=http:403 media3=- cause=- attempts=-",
            PlaybackErrorLog.describe(record(1, PlaybackError.Http(403)).copy(network = null, platformCode = null)),
        )
    }
}
