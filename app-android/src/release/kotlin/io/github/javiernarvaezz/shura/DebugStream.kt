package io.github.javiernarvaezz.shura

import android.content.Intent
import io.github.javiernarvaezz.shura.core.stream.InnerTubeXStreamResolver
import io.github.javiernarvaezz.shura.core.stream.PoTokenMinter
import io.github.javiernarvaezz.shura.core.stream.PreprocessedPlayerStore
import io.github.javiernarvaezz.shura.core.stream.StreamResolver
import io.github.javiernarvaezz.shura.core.stream.Trace
import okhttp3.EventListener

// Release builds have no stream diagnostics and ignore debug launch options (ADR 0001 R2, R7).

@Suppress("UnusedParameter")
internal fun applyDebugLaunchOptions(intent: Intent) = Unit

internal fun debugExcludedStreamProfiles(): Set<String> = emptySet()

// Measured on device (ADR 0001, start-up measurements): ~1.5 KB once per process, ~0.4 s off the first play.
@Suppress("FunctionOnlyReturningConstant")
internal fun playbackWarmupEnabled(): Boolean = true

internal fun debugWarmUp(
    warmUp: suspend () -> Unit,
    @Suppress("UnusedParameter") trace: Trace,
): suspend () -> Unit = warmUp

internal fun debugStreamResolver(resolver: StreamResolver): StreamResolver = resolver

internal fun debugPoTokenMinter(minter: PoTokenMinter): PoTokenMinter = minter

@Suppress("UnusedParameter")
internal fun debugPreprocessedPlayerStore(
    store: PreprocessedPlayerStore,
    trace: Trace,
): PreprocessedPlayerStore = store

internal fun debugTrace(): Trace = Trace.NONE

@Suppress("FunctionOnlyReturningConstant") // Same signature as the debug source set's factory.
internal fun debugEventListenerFactory(): EventListener.Factory? = null

@Suppress("UnusedParameter")
internal fun debugMaybePrewarm(
    resolver: InnerTubeXStreamResolver,
    trace: Trace,
) = Unit
