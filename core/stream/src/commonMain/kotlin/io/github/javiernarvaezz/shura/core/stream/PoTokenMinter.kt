package io.github.javiernarvaezz.shura.core.stream

import io.github.javiernarvaezz.shura.core.model.VideoId

/**
 * Mints proof-of-origin tokens (ADR 0001 R2). Values are sensitive: never log them.
 */
interface PoTokenMinter {
    /**
     * Returns the player token (bound to [visitorData], reused while the attestation is valid) and the streaming
     * token (bound to [videoId]).
     *
     * @throws PoTokenUnavailableException when no token can be minted; callers continue without one.
     */
    suspend fun mint(
        videoId: VideoId,
        visitorData: String,
    ): PoTokens

    /** Drops the current attestation, e.g. after a URL carrying a token was rejected. */
    suspend fun invalidate()

    fun close()
}

/** Minted tokens. [toString] never reveals the values. */
class PoTokens(
    val player: String,
    val streaming: String,
    val visitorData: String,
) {
    override fun toString(): String = "PoTokens(player=redacted, streaming=redacted, visitorData=redacted)"
}

/** Token minting failed at [stage]. Only the stage and the cause type are kept: no third-party content. */
class PoTokenUnavailableException(
    val stage: String,
    val causeType: String?,
) : Exception("PoToken unavailable (stage=$stage, cause=$causeType)")

/**
 * A sandboxed JavaScript environment that only computes: it never needs network access (all requests are made
 * from Kotlin through the app's single HTTP client, ADR 0001 R7). Android: a hardened `WebView`.
 */
interface JsRuntime {
    /** User agent of the environment; HTTP requests for its attestation use the same value. */
    val userAgent: String

    /** Loads [script] into the global scope. */
    suspend fun load(script: String)

    /**
     * Calls the global function [function] as `function(requestId, <argumentsJs>)` and waits for it to report
     * back through the bridge. Returns the reported payload.
     *
     * @throws JsRuntimeException with a short error code when the script reports a failure.
     */
    suspend fun call(
        function: String,
        argumentsJs: String,
    ): String

    fun close()
}

/** A script-reported failure; [code] is a short fixed code, never data. */
class JsRuntimeException(
    val code: String,
) : Exception("JavaScript runtime failure: $code")
