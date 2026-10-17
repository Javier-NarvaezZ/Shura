package io.github.javiernarvaezz.shura

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.javiernarvaezz.shura.core.innertube.InnerTubeRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

private const val TAG = "SunloopDump"
private const val EXTRA_DUMP = "shura.debug.dumpAccountFixtures"

/** The account library pages dumped as fixtures: file name to browse id. */
private val PAGES =
    linkedMapOf(
        "liked_songs" to "VLLM",
        "liked_playlists" to "FEmusic_liked_playlists",
        "liked_albums" to "FEmusic_liked_albums",
        "library_artists" to "FEmusic_library_corpus_artists",
    )

/**
 * Debug builds only (`--ez shura.debug.dumpAccountFixtures true`): fetches the signed-in account's library pages
 * and writes them, **sanitized on the device before anything is written**, to `files/fixture-dump/`, so their real
 * shape can become test fixtures. A page that still holds anything account-specific after sanitizing is not
 * written. Logs only page names and sizes.
 */
internal fun debugDumpAccountFixtures(
    intent: Intent,
    context: Context,
    graph: AppGraph,
) {
    if (!intent.getBooleanExtra(EXTRA_DUMP, false)) return
    Thread {
        runBlocking {
            val session = graph.account.session()
            val credentials = session?.let(graph::credentialsOf)
            if (session == null || credentials == null) {
                Log.w(TAG, "not signed in")
                return@runBlocking
            }
            val secrets =
                listOfNotNull(session.accountName) +
                    session.dataSyncId
                        .orEmpty()
                        .split("||")
                        .filter { it.length > MIN_SECRET } +
                    session.cookies.values.filter { it.length > MIN_SECRET } +
                    session.googleCookies.values.filter { it.length > MIN_SECRET }
            val dir = File(context.filesDir, "fixture-dump").apply { mkdirs() }
            PAGES.forEach { (name, browseId) ->
                val raw = runCatching { graph.accountClient.raw(credentials, InnerTubeRequest.Browse(browseId)).json }
                val json = raw.getOrNull()
                if (json == null) {
                    Log.w(TAG, "$name failed: ${raw.exceptionOrNull()?.let { it::class.simpleName }}")
                    return@forEach
                }
                val clean = AccountDumpSanitizer(secrets, ownPlaylists = name == "liked_playlists").sanitize(json)
                val text = clean.toString()
                val leak = AccountDumpSanitizer.leaks(text, secrets)
                if (leak != null) {
                    Log.w(TAG, "$name NOT written: still matches $leak")
                } else {
                    File(dir, "$name.json").writeText(text)
                    Log.i(TAG, "$name written: ${text.length / KB} KB")
                }
            }
        }
    }.start()
}

private const val MIN_SECRET = 3
private const val KB = 1024

/**
 * Removes what ties a page to the account: tracking and response context, e-mail addresses, the account's name,
 * handle and ids, and session values. With [ownPlaylists], every text and playlist id is replaced too (the
 * account's own playlists cannot be told apart from automatic ones reliably).
 */
private class AccountDumpSanitizer(
    private val secrets: List<String>,
    private val ownPlaylists: Boolean,
) {
    private var texts = 0
    private val ids = HashMap<String, String>()

    fun sanitize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> {
                JsonObject(
                    element
                        .filterKeys { !dropped(it) }
                        .mapValues { (key, value) -> value(key, value) },
                )
            }

            is JsonArray -> {
                JsonArray(element.map(::sanitize))
            }

            else -> {
                element
            }
        }

    private fun value(
        key: String,
        value: JsonElement,
    ): JsonElement {
        val primitive = value as? JsonPrimitive
        if (primitive == null || !primitive.isString) return sanitize(value)
        val text = primitive.content
        return JsonPrimitive(
            when {
                ownPlaylists && key == "text" -> "Text ${++texts}"
                ownPlaylists && (key == "playlistId" || key == "browseId") && isPlaylistId(text) -> fakeId(text)
                else -> scrub(text)
            },
        )
    }

    private fun scrub(text: String): String {
        var out = EMAIL.replace(text, "test@example.invalid").let { HANDLE.replace(it, "@testaccount") }
        secrets.forEach { out = out.replace(it, "redacted") }
        return out
    }

    private fun isPlaylistId(id: String): Boolean =
        id != "VLLM" && id != "LM" && (id.startsWith("VLPL") || id.startsWith("PL") || id.startsWith("VLRD"))

    private fun fakeId(id: String): String =
        ids.getOrPut(id) {
            val prefix = id.takeWhile { it.isUpperCase() }.take(PREFIX)
            prefix + "Fixture" + ids.size.toString().padStart(id.length - prefix.length - FIXTURE, '0')
        }

    companion object {
        private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        private val HANDLE = Regex("""@[A-Za-z0-9._-]{3,}""")
        private const val PREFIX = 4
        private const val FIXTURE = 7

        private val DROPPED =
            setOf(
                "responseContext",
                "visitorData",
                "loggingContext",
                "frameworkUpdates",
                "adSignalsInfo",
                "accountPhoto",
            )

        private val FORBIDDEN =
            listOf(
                Regex("visitorData", RegexOption.IGNORE_CASE),
                Regex("trackingParams", RegexOption.IGNORE_CASE),
                Regex("responseContext", RegexOption.IGNORE_CASE),
                Regex("cookie", RegexOption.IGNORE_CASE),
                Regex("SAPISID", RegexOption.IGNORE_CASE),
                Regex("__Secure-"),
                Regex("datasyncId", RegexOption.IGNORE_CASE),
                // Any address but the placeholder.
                Regex("""[A-Za-z0-9._%+-]+@(?!example\.invalid)[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
            )

        private fun dropped(key: String) = key.lowercase().endsWith("trackingparams") || key in DROPPED

        /** The first rule [text] still breaks, or null when it is clean. */
        fun leaks(
            text: String,
            secrets: List<String>,
        ): String? =
            FORBIDDEN.firstOrNull { it.containsMatchIn(text) }?.pattern
                ?: secrets.firstOrNull { it in text }?.let { "a session value" }
    }
}
