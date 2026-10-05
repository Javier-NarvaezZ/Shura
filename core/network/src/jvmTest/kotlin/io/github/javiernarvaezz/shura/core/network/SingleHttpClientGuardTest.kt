package io.github.javiernarvaezz.shura.core.network

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * ADR 0001 R7: the whole app uses the single OkHttpClient built by [ShuraNetwork].
 *
 * Scans production sources and build files of the repository. It does NOT cover the PoToken `WebView`,
 * which has its own network stack and enforces its allowlist inside the WebView (R2).
 */
class SingleHttpClientGuardTest {
    private val root = File("../..").canonicalFile
    private val networkMain = File(root, "core/network/src").canonicalPath

    private val ktorEngines = "cio|android|java|apache5?|jetty|curl|darwin|winhttp"
    private val media3HttpStacks = "DefaultHttpDataSource|DefaultDataSource|cronet"

    private val forbiddenOutsideNetwork =
        mapOf(
            "creates an OkHttpClient" to Regex("""OkHttpClient\s*(\.Builder\s*)?\("""),
            "creates a Ktor HttpClient" to Regex("""\bHttpClient\s*\("""),
            "uses another Ktor engine" to Regex("""io\.ktor\.client\.engine\.($ktorEngines)"""),
            "uses Media3's default HTTP stack" to Regex("""androidx\.media3\.datasource\.($media3HttpStacks)"""),
            "uses HttpURLConnection" to Regex("""(java\.net\.HttpURLConnection|javax\.net\.ssl\.HttpsURLConnection)"""),
            "uses java.net.http" to Regex("""java\.net\.http\."""),
            "opens a URL connection" to Regex("""\.(openConnection|openStream)\s*\("""),
        )

    // Builder calls are often split across lines ("ExoPlayer" then ".Builder(context)").
    private val exoPlayerBuilder = Regex("""\bExoPlayer\s*\.\s*Builder\s*\(""")
    private val mediaSessionBuilder = Regex("""\b(MediaSession|MediaLibrarySession)\s*\.\s*Builder\s*\(""")

    private val forbiddenInBuildFiles =
        Regex("""ktor-client-($ktorEngines)|media3-datasource-(cronet|rtmp)""")

    /** Drops block and line comments so KDoc mentioning a type is not mistaken for code. */
    private fun code(file: File): String =
        file
            .readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""(^|\s)//[^\n]*""", RegexOption.MULTILINE), " ")

    private fun productionSources(): List<File> =
        root
            .walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                val sourceSet = path.substringAfter("/src/", "").substringBefore('/')
                sourceSet == "main" || sourceSet == "debug" || sourceSet == "release" || sourceSet.endsWith("Main")
            }.toList()

    @Test
    fun scansTheRepository() {
        assertTrue(File(root, "settings.gradle.kts").isFile, "Unexpected working directory: $root")
        assertTrue(productionSources().size > 10, "Too few production sources found")
    }

    @Test
    fun noOtherHttpStackInProductionCode() {
        val violations =
            productionSources()
                .filterNot { it.canonicalPath.startsWith(networkMain) }
                .flatMap { file ->
                    val text = code(file)
                    val found = forbiddenOutsideNetwork.filterValues { it.containsMatchIn(text) }.keys.toMutableList()
                    if (exoPlayerBuilder.containsMatchIn(text) && "setMediaSourceFactory(" !in text) {
                        found += "builds ExoPlayer without an explicit media source factory"
                    }
                    // Without these, Media3 loads session and notification artwork with its own HTTP stack.
                    if (mediaSessionBuilder.containsMatchIn(text) && "setBitmapLoader(" !in text) {
                        found += "builds a media session without our bitmap loader"
                    }
                    if ("DataSourceBitmapLoader" in text && "setDataSourceFactory(" !in text) {
                        found += "builds a DataSourceBitmapLoader without our data source factory"
                    }
                    found.map { "${file.relativeTo(root)}: $it" }
                }
        if (violations.isNotEmpty()) fail("Network code outside :core:network:\n${violations.joinToString("\n")}")
    }

    @Test
    fun networkModuleBuildsItsClientsInOnePlace() {
        val builders =
            productionSources()
                .filter { it.canonicalPath.startsWith(networkMain) }
                .filter { forbiddenOutsideNetwork.getValue("creates an OkHttpClient").containsMatchIn(code(it)) }
                .map { it.name }
        assertTrue(builders == listOf("ShuraNetwork.kt"), "OkHttpClient built outside ShuraNetwork: $builders")
    }

    @Test
    fun noOtherHttpStackInBuildFiles() {
        val violations =
            root
                .walkTopDown()
                .onEnter { it.name != "build" && !it.name.startsWith(".") }
                .filter { it.isFile && (it.name.endsWith(".gradle.kts") || it.name == "libs.versions.toml") }
                .filter { forbiddenInBuildFiles.containsMatchIn(it.readText()) }
                .map { it.relativeTo(root).path }
                .toList()
        if (violations.isNotEmpty()) fail("Other HTTP stacks declared in: $violations")
    }
}
