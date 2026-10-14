package io.github.javiernarvaezz.shura.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A list of strings in one text column (e.g. a song's artist names), as a JSON array. */
internal fun encodeStrings(values: List<String>): String = JsonArray(values.map(::JsonPrimitive)).toString()

internal fun decodeStrings(json: String): List<String> =
    Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.content }

internal fun Boolean.toLong(): Long = if (this) 1L else 0L
