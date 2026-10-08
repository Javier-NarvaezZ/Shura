package io.github.javiernarvaezz.shura.core.player

import io.github.javiernarvaezz.shura.core.stream.DiagnosticText
import io.github.javiernarvaezz.shura.core.stream.StreamAttempt
import kotlin.time.Instant

/** How the device was connected when playback failed. */
enum class NetworkTransport { Wifi, Cellular, Ethernet, Other, None }

/** [validated] is whether the system had confirmed the network reaches the internet. */
data class NetworkSnapshot(
    val transport: NetworkTransport,
    val validated: Boolean,
)

/**
 * One classified playback failure, kept so it can be read after the log is gone (R4). Holds codes, type names and
 * sanitized library labels only: never URLs, tokens, headers, video ids or titles.
 *
 * @param network null when the network state could not be read.
 * @param platformCode the player's own error code name (Media3 on Android).
 */
data class PlaybackErrorRecord(
    val at: Instant,
    val appVersion: String,
    val network: NetworkSnapshot?,
    val error: PlaybackError,
    val platformCode: String?,
    val causeType: String? = null,
    val attempts: List<StreamAttempt> = emptyList(),
)

/**
 * The last [CAPACITY] playback failures, newest first, as a pure list plus its text format (one record per line).
 * Every record is sanitized when appended and when read back, so a tampered file cannot reintroduce unsafe text.
 */
object PlaybackErrorLog {
    const val CAPACITY = 5
    const val MAX_ATTEMPTS = 24

    private const val VERSION = "v1"
    private const val FIELDS = 9
    private const val TIME_FIELD = 1
    private const val APP_FIELD = 2
    private const val TRANSPORT_FIELD = 3
    private const val VALIDATED_FIELD = 4
    private const val ERROR_FIELD = 5
    private const val PLATFORM_FIELD = 6
    private const val CAUSE_FIELD = 7
    private const val ATTEMPTS_FIELD = 8
    private const val NONE = "-"
    private const val FIELD_SEPARATOR = "\t"
    private const val ATTEMPT_SEPARATOR = "|"
    private const val PROFILE_SEPARATOR = ":"

    fun append(
        records: List<PlaybackErrorRecord>,
        record: PlaybackErrorRecord,
    ): List<PlaybackErrorRecord> = (listOf(record.sanitized()) + records).take(CAPACITY)

    fun encode(records: List<PlaybackErrorRecord>): String = records.joinToString("\n") { it.sanitized().encode() }

    fun decode(text: String): List<PlaybackErrorRecord> =
        text
            .lineSequence()
            .mapNotNull(::decodeLine)
            .take(CAPACITY)
            .toList()

    /** A single human-readable line, for `dumpsys`. */
    fun describe(record: PlaybackErrorRecord): String {
        val network = record.network?.let { "net=${it.transport.code} validated=${it.validated}" } ?: "net=unknown"
        val attempts =
            record.attempts
                .takeIf { it.isNotEmpty() }
                ?.joinToString(ATTEMPT_SEPARATOR) { it.profile + PROFILE_SEPARATOR + it.outcome }
                ?: NONE
        return "${Instant.fromEpochSeconds(record.at.epochSeconds)} app=${record.appVersion} $network " +
            "error=${PlaybackErrorCodec.encode(record.error)} media3=${record.platformCode ?: NONE} " +
            "cause=${record.causeType ?: NONE} attempts=$attempts"
    }

    private fun PlaybackErrorRecord.sanitized() =
        copy(
            appVersion = clean(appVersion),
            platformCode = platformCode?.let(::clean),
            causeType = causeType?.let(::clean),
            attempts =
                attempts.take(MAX_ATTEMPTS).map {
                    StreamAttempt(clean(it.profile).replace(PROFILE_SEPARATOR, "?"), clean(it.outcome))
                },
        )

    private fun clean(text: String) = DiagnosticText.sanitize(text).replace(ATTEMPT_SEPARATOR, "?")

    private fun PlaybackErrorRecord.encode(): String =
        listOf(
            VERSION,
            at.toEpochMilliseconds().toString(),
            appVersion,
            network?.transport?.code ?: NONE,
            network?.validated?.toString() ?: NONE,
            PlaybackErrorCodec.encode(error),
            platformCode ?: NONE,
            causeType ?: NONE,
            attempts.joinToString(ATTEMPT_SEPARATOR) { it.profile + PROFILE_SEPARATOR + it.outcome },
        ).joinToString(FIELD_SEPARATOR)

    private fun decodeLine(line: String): PlaybackErrorRecord? =
        line
            .split(FIELD_SEPARATOR)
            .takeIf { it.size == FIELDS && it.first() == VERSION }
            ?.let(::recordOf)
            ?.sanitized()

    /** Null when a field is malformed; an unknown network is stored as two [NONE] fields. */
    private fun recordOf(fields: List<String>): PlaybackErrorRecord? {
        val at = fields[TIME_FIELD].toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        val transport = fields[TRANSPORT_FIELD]
        val validated = fields[VALIDATED_FIELD]
        val network =
            NetworkTransport.entries
                .firstOrNull { it.code == transport }
                ?.let { found -> validated.toBooleanStrictOrNull()?.let { NetworkSnapshot(found, it) } }
        val networkReadable = network != null || (transport == NONE && validated == NONE)
        return if (at == null || !networkReadable) {
            null
        } else {
            PlaybackErrorRecord(
                at = at,
                appVersion = fields[APP_FIELD],
                network = network,
                error = PlaybackErrorCodec.decode(fields[ERROR_FIELD]),
                platformCode = fields[PLATFORM_FIELD].takeUnless { it == NONE },
                causeType = fields[CAUSE_FIELD].takeUnless { it == NONE },
                attempts =
                    fields[ATTEMPTS_FIELD]
                        .split(ATTEMPT_SEPARATOR)
                        .filter { it.isNotEmpty() }
                        .map {
                            StreamAttempt(
                                it.substringBefore(PROFILE_SEPARATOR),
                                it.substringAfter(PROFILE_SEPARATOR, ""),
                            )
                        },
            )
        }
    }

    private val NetworkTransport.code: String get() = name.lowercase()
}
