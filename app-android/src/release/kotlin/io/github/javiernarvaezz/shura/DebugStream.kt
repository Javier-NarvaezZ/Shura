package io.github.javiernarvaezz.shura

import android.content.Intent
import io.github.javiernarvaezz.shura.core.stream.PoTokenMinter
import io.github.javiernarvaezz.shura.core.stream.StreamResolver

// Release builds have no stream diagnostics and ignore debug launch options (ADR 0001 R2, R7).

@Suppress("UnusedParameter")
internal fun applyDebugLaunchOptions(intent: Intent) = Unit

internal fun debugExcludedStreamProfiles(): Set<String> = emptySet()

internal fun debugStreamResolver(resolver: StreamResolver): StreamResolver = resolver

internal fun debugPoTokenMinter(minter: PoTokenMinter): PoTokenMinter = minter
