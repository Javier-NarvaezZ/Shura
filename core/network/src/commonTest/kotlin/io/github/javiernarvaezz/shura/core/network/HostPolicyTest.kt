package io.github.javiernarvaezz.shura.core.stream

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamHostPolicyTest {
    @Test
    fun allowsYouTubeAndMediaHosts() {
        listOf(
            "youtube.com",
            "www.youtube.com",
            "music.youtube.com",
            "rr3---sn-ja5gvjv-c59z.googlevideo.com",
        ).forEach { assertTrue(StreamHostPolicy.isAllowed(it), it) }
    }

    @Test
    fun blocksRemoteSolverConfigHostsAndLookalikes() {
        listOf(
            "raw.githubusercontent.com",
            "github.com",
            "cdn.jsdelivr.net",
            "evilyoutube.com",
            "youtube.com.evil.net",
            "googlevideo.com.evil.net",
            "",
        ).forEach { assertFalse(StreamHostPolicy.isAllowed(it), it) }
    }

    @Test
    fun hostMatchingIsCaseInsensitive() {
        assertTrue(StreamHostPolicy.isAllowed("Music.YouTube.com"))
    }

    @Test
    fun clientPluginBlocksDisallowedHostBeforeTheEngine() =
        runTest {
            var engineCalls = 0
            val client =
                HttpClient(
                    MockEngine {
                        engineCalls++
                        respondOk()
                    },
                ) { install(StreamHostAllowlist) }

            val error =
                assertFailsWith<BlockedHostException> {
                    client.get("https://raw.githubusercontent.com/some/config.json")
                }
            assertEquals("raw.githubusercontent.com", error.host)
            assertEquals(0, engineCalls)

            client.get("https://music.youtube.com/youtubei/v1/player")
            assertEquals(1, engineCalls)
        }
}
