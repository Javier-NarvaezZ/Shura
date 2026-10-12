package io.github.javiernarvaezz.shura.tools.livecheck

import io.github.javiernarvaezz.shura.core.innertube.InnerTubeClient
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeConfig
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeRequest
import io.github.javiernarvaezz.shura.core.innertube.SearchFilter
import io.github.javiernarvaezz.shura.core.network.ShuraNetwork
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * Records a real InnerTube response as a sanitized test fixture for `:core:innertube` (Spanish, Colombia, like the
 * existing ones). Session, device and tracking fields are removed; the write is refused if any remain.
 *
 * Usage (paths are relative to `core/innertube/src/jvmTest/resources/fixtures`):
 * - `fixture <path> browse <id> [params]`
 * - `fixture <path> search <songs|albums|artists|playlists> <query…>`
 * - `fixture <path> continuation <path of the previous page's fixture>`
 *
 * Output: the written path and its size only.
 */
internal suspend fun recordFixture(args: List<String>) {
    require(args.size >= MIN_ARGS) { "fixture <path> <browse|search|continuation> <…>" }
    val root = fixturesRoot()
    val target = File(root, args[0])
    val request =
        when (args[1]) {
            "browse" -> InnerTubeRequest.Browse(args[2], args.getOrNull(3))
            "search" -> InnerTubeRequest.Search(args.drop(3).joinToString(" "), searchFilter(args[2]))
            "continuation" -> InnerTubeRequest.Continuation(continuationToken(File(root, args[2])))
            else -> error("Unknown request kind")
        }
    val client = InnerTubeClient(ShuraNetwork(emptyList()).ktor, InnerTubeConfig.forDevice("es", "CO"))
    val text = Json.encodeToString(JsonElement.serializer(), sanitize(client.raw(request)))
    FORBIDDEN.firstOrNull { it.containsMatchIn(text) }?.let { error("Sanitizing left ${it.pattern}") }
    target.parentFile.mkdirs()
    target.writeText(text)
    println("wrote ${target.relativeTo(root)} (${text.length / KB} KB)")
}

private fun searchFilter(name: String) =
    SearchFilter.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: error("Unknown search filter")

private fun fixturesRoot(): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
    return File(requireNotNull(dir) { "Repository root not found" }, "core/innertube/src/jvmTest/resources/fixtures")
}

/** The first continuation token in a recorded page. */
private fun continuationToken(page: File): String =
    requireNotNull(
        findFirst(Json.parseToJsonElement(page.readText())) { key, value ->
            if (key == "continuationCommand" || key == "nextContinuationData") {
                (value as? JsonObject)?.let { (it["token"] ?: it["continuation"]) as? JsonPrimitive }?.contentOrNull
            } else {
                null
            }
        },
    ) { "No continuation in ${page.name}" }

private fun findFirst(
    element: JsonElement,
    match: (String, JsonElement) -> String?,
): String? =
    when (element) {
        is JsonObject -> element.entries.firstNotNullOfOrNull { (k, v) -> match(k, v) ?: findFirst(v, match) }
        is JsonArray -> element.firstNotNullOfOrNull { findFirst(it, match) }
        else -> null
    }

private fun sanitize(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> JsonObject(element.filterKeys { !isDropped(it) }.mapValues { sanitize(it.value) })
        is JsonArray -> JsonArray(element.map(::sanitize))
        else -> element
    }

private fun isDropped(key: String) = key.lowercase().endsWith("trackingparams") || key in DROPPED_KEYS

private val DROPPED_KEYS =
    setOf("responseContext", "visitorData", "loggingContext", "loggingDirectives", "frameworkUpdates", "adSignalsInfo")

/** Same patterns as `FixtureHygieneTest`. */
private val FORBIDDEN =
    listOf(
        Regex("visitorData", RegexOption.IGNORE_CASE),
        Regex("trackingParams", RegexOption.IGNORE_CASE),
        Regex("responseContext", RegexOption.IGNORE_CASE),
        Regex("cookie", RegexOption.IGNORE_CASE),
        Regex("SAPISID", RegexOption.IGNORE_CASE),
        Regex("__Secure-"),
        Regex("datasyncId", RegexOption.IGNORE_CASE),
    )

private const val KB = 1024
private const val MIN_ARGS = 3
