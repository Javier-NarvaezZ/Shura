package io.github.javiernarvaezz.shura.core.stream

/**
 * Persistent storage for InnerTubeX's EJS preprocessed players. They are generated on the device from YouTube's
 * own player script, never fetched from a third party (ADR 0001 R3). Keys come from the library (40 hex chars).
 * Failures must not throw: a miss only costs a slower first resolution.
 */
interface PreprocessedPlayerStore {
    suspend fun read(key: String): String?

    /** Stores [value] under [key]; a null [value] deletes the entry. */
    suspend fun write(
        key: String,
        value: String?,
    )
}
