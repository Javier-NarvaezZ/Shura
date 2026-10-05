package io.github.javiernarvaezz.shura.core.stream

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull

/**
 * Serializes `@Serializable` request bodies to JSON with `Content-Type: application/json`.
 *
 * InnerTubeX sends its request bodies as `@Serializable` objects and expects the caller's client to serialize
 * them; it reads every response itself. This covers **requests only** (ADR 0001): if an InnerTubeX update starts
 * reading responses with `body<T>()`, this must be revisited. Strings, byte arrays and explicit
 * [OutgoingContent] are left to Ktor's defaults.
 */
internal fun serializedRequestBodies(json: Json): ClientPlugin<Unit> =
    createClientPlugin("SerializedRequestBodies") {
        transformRequestBody { _, body, bodyType ->
            if (body is OutgoingContent || body is String || body is ByteArray) return@transformRequestBody null
            val serializer = bodyType?.kotlinType?.let { json.serializersModule.serializerOrNull(it) }
            serializer?.let { TextContent(json.encodeToString(it, body), ContentType.Application.Json) }
        }
    }
