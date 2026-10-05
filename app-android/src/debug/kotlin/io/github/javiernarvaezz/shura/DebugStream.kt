package io.github.javiernarvaezz.shura

import android.content.Intent
import android.util.Log
import io.github.javiernarvaezz.shura.core.model.VideoId
import io.github.javiernarvaezz.shura.core.stream.AudioQuality
import io.github.javiernarvaezz.shura.core.stream.PoTokenMinter
import io.github.javiernarvaezz.shura.core.stream.PoTokenUnavailableException
import io.github.javiernarvaezz.shura.core.stream.PoTokens
import io.github.javiernarvaezz.shura.core.stream.ResolvedStream
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import kotlin.time.TimeSource

/*
 * Debug builds only (ADR 0001 R2, R7). Logs profile names, stages and timings; never tokens, visitor data,
 * URLs or video ids.
 */

private const val TAG = "ShuraStream"
private const val EXTRA_NO_LEGACY = "shura.debug.noLegacyClients"

// Every variant of the legacy VISIONOS manifests, to force a non-legacy (PoToken) client in device tests.
private val LEGACY_PROFILES =
    listOf("VISIONOS", "VISIONOS_0_1", "VISIONOS_SABR").flatMap { listOf(it, "${it}__po", "${it}__nopo") }.toSet()

@Volatile
private var noLegacyClients = false

/** `adb shell am start -n <app>/.MainActivity --ez shura.debug.noLegacyClients true` after a force-stop. */
internal fun applyDebugLaunchOptions(intent: Intent) {
    if (intent.getBooleanExtra(EXTRA_NO_LEGACY, false)) {
        noLegacyClients = true
        Log.w(TAG, "Legacy VISIONOS clients excluded for this process")
    }
}

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
