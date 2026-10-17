package io.github.javiernarvaezz.shura.core.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The account menu's active account header: present only while signed in. Shape taken from YouTube Music's
 * `account/account_menu` (fixtures are synthetic: a real one would carry the account; checked on device).
 */
internal object AccountMenuParser {
    /** The active account's name; "" when signed in without one; null when no account is active. */
    fun accountName(json: JsonElement): String? {
        val header = find(json, "activeAccountHeaderRenderer") as? JsonObject ?: return null
        val runs = (header["accountName"] as? JsonObject)?.get("runs") as? JsonArray
        return runs
            ?.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.content }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun find(
        json: JsonElement,
        key: String,
    ): JsonElement? =
        when (json) {
            is JsonObject -> json[key] ?: json.values.firstNotNullOfOrNull { find(it, key) }
            is JsonArray -> json.firstNotNullOfOrNull { find(it, key) }
            else -> null
        }
}
