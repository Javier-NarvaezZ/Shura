package io.github.javiernarvaezz.shura.core.lyrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Real LRCLIB responses under `src/jvmTest/resources/fixtures`, recorded unchanged on 2026-10-12 with the
 * requests [LrcLibClient] sends. Songs with public-domain lyrics or no lyrics at all.
 */
internal object Fixtures {
    private const val ROOT = "/fixtures"

    fun read(path: String): String {
        val url = requireNotNull(Fixtures::class.java.getResource("$ROOT/$path")) { "Missing fixture $path" }
        return url.readText()
    }

    /** One record of a recorded search response, by its LRCLIB id. */
    fun searchRecord(
        path: String,
        id: Long,
    ): JsonObject =
        Json
            .parseToJsonElement(read(path))
            .jsonArray
            .map { it.jsonObject }
            .single { it.getValue("id").jsonPrimitive.long == id }

    fun getRecord(path: String): JsonObject = Json.parseToJsonElement(read(path)).jsonObject
}
