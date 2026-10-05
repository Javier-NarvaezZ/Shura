package io.github.javiernarvaezz.shura

import android.content.Intent
import android.util.Log
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.PoTokenMinter
import io.github.javiernarvaezz.shura.core.stream.PoTokenUnavailableException
import io.github.javiernarvaezz.shura.core.stream.PoTokens
import io.github.javiernarvaezz.shura.core.stream.PreprocessedPlayerStore
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import io.github.javiernarvaezz.shura.core.stream.Trace
import kotlin.time.TimeSource

/*
 * Debug builds only (ADR 0001 R2, R7). Logs profile names, stages and timings; never tokens, visitor data,
 * URLs or video ids.
 */

private const val TAG = "ShuraStream"
private const val EXTRA_NO_LEGACY = "shura.debug.noLegacyClients"
private const val EXTRA_PREWARM = "shura.debug.prewarm"
private const val EXTRA_WARMUP = "shura.debug.warmup"

// Every variant of the legacy VISIONOS manifests, to force a non-legacy (PoToken) client in device tests.
private val LEGACY_PROFILES =
    listOf("VISIONOS", "VISIONOS_0_1", "VISIONOS_SABR").flatMap { listOf(it, "${it}__po", "${it}__nopo") }.toSet()

@Volatile
private var noLegacyClients = false

@Volatile
internal var prewarmRequested = false

@Volatile
private var warmupEnabled = false

/** `adb shell am start -n <app>/.MainActivity --ez shura.debug.noLegacyClients true` after a force-stop. */
internal fun applyDebugLaunchOptions(intent: Intent) {
    if (intent.getBooleanExtra(EXTRA_NO_LEGACY, false)) {
        noLegacyClients = true
        Log.w(TAG, "Legacy VISIONOS clients excluded for this process")
    }
    if (intent.getBooleanExtra(EXTRA_PREWARM, false)) prewarmRequested = true
    warmupEnabled = intent.getBooleanExtra(EXTRA_WARMUP, false)
}

/** Debug: off unless launched with `--ez shura.debug.warmup true`, so cold-start measurements stay comparable. */
internal fun playbackWarmupEnabled(): Boolean = warmupEnabled

internal fun debugExcludedStreamProfiles(): Set<String> = if (noLegacyClients) LEGACY_PROFILES else emptySet()

internal fun debugStreamResolver(resolver: StreamResolver): StreamResolver =
    object : StreamResolver {
        override suspend fun resolve(
            videoId: VideoId,
            quality: AudioQuality,
        ): ResolvedStream {
            val started = TimeSource.Monotonic.markNow()
            return resolver.resolve(videoId, quality).also {
                Log.d(TAG, "Resolved profile=${it.clientProfile} in ${started.elapsedNow().inWholeMilliseconds}ms")
            }
        }
    }

internal fun debugPoTokenMinter(minter: PoTokenMinter): PoTokenMinter =
    object : PoTokenMinter by minter {
        override suspend fun mint(
            videoId: VideoId,
            visitorData: String,
        ): PoTokens {
            val started = TimeSource.Monotonic.markNow()
            try {
                return minter.mint(videoId, visitorData).also {
                    Log.d(TAG, "PoToken minted in ${started.elapsedNow().inWholeMilliseconds}ms")
                }
            } catch (e: PoTokenUnavailableException) {
                val elapsed = started.elapsedNow().inWholeMilliseconds
                Log.w(TAG, "PoToken failed stage=${e.stage} cause=${e.causeType} after ${elapsed}ms")
                throw e
            }
        }
    }

/** Reports store hits, misses and sizes; never the key (it derives from the player script URL). */
internal fun debugPreprocessedPlayerStore(
    store: PreprocessedPlayerStore,
    trace: Trace,
): PreprocessedPlayerStore =
    object : PreprocessedPlayerStore {
        override suspend fun read(key: String): String? {
            val started = TimeSource.Monotonic.markNow()
            return store.read(key).also {
                val ms = started.elapsedNow().inWholeMilliseconds.toString()
                val result = if (it == null) "miss" else "hit"
                trace.event(
                    "ejs-store: read",
                    mapOf(
                        "result" to result,
                        "ms" to ms,
                        "chars" to (it?.length ?: 0).toString(),
                    ),
                )
            }
        }

        override suspend fun write(
            key: String,
            value: String?,
        ) {
            val started = TimeSource.Monotonic.markNow()
            store.write(key, value)
            val ms = started.elapsedNow().inWholeMilliseconds.toString()
            trace.event("ejs-store: write", mapOf("chars" to (value?.length ?: -1).toString(), "ms" to ms))
        }
    }
