package io.github.javiernarvaezz.shura.core.innertube

/**
 * Typed catalog failures. Messages never include the query, URLs, headers or response bodies.
 */
sealed class InnerTubeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Http(
        val status: Int,
    ) : InnerTubeException("InnerTube request failed with HTTP $status")

    class Network(
        cause: Throwable,
    ) : InnerTubeException("InnerTube request failed: ${cause::class.simpleName}", cause)

    class Parse(
        cause: Throwable,
    ) : InnerTubeException("InnerTube response could not be parsed: ${cause::class.simpleName}", cause)
}
