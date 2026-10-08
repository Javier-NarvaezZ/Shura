package io.github.javiernarvaezz.shura.core.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticTextTest {
    @Test
    fun ordinaryLabelsAreKept() {
        val label = "selection:player+GVS PO-token provider unavailable"
        assertEquals(label, DiagnosticText.sanitize(label))
        assertEquals("request:IllegalStateException", DiagnosticText.sanitize("request:IllegalStateException"))
        assertEquals("VISIONOS_0_1__nopo", DiagnosticText.sanitize("VISIONOS_0_1__nopo"))
    }

    @Test
    fun aLooseVideoIdIsMasked() {
        assertEquals("playback of <id> failed", DiagnosticText.sanitize("playback of dQw4w9WgXcQ failed"))
        assertEquals("<id>", DiagnosticText.sanitize("kJQP7kiw5Fk"))
        assertEquals("request:<id>", DiagnosticText.sanitize("request:7fwUH0oRmkQ"))
    }

    @Test
    fun knownLabelsOfElevenCharactersAreNeverMasked() {
        for (label in STAGE_LABELS + PROFILE_NAMES_OF_ELEVEN) {
            assertEquals(label, DiagnosticText.sanitize(label), label)
            assertEquals("x:$label y", DiagnosticText.sanitize("x:$label y"), label)
        }
    }

    @Test
    fun longerOrShorterTokensAreNotMasked() {
        assertEquals("abcdefghij abcdefghijkl", DiagnosticText.sanitize("abcdefghij abcdefghijkl"))
    }

    @Test
    fun urlsAreRemoved() {
        val text = "request failed https://rr1---sn-x.googlevideo.com/videoplayback?id=abc&sig=XYZ now"
        val sanitized = DiagnosticText.sanitize(text)
        assertEquals("request failed <url> now", sanitized)
        assertFalse("googlevideo" in sanitized)
    }

    @Test
    fun sanitizingTwiceChangesNothing() {
        val once = DiagnosticText.sanitize("id dQw4w9WgXcQ at https://example.com/x?y=1 and <b>")

        assertEquals("id <id> at <url> and ?b?", once)
        assertEquals(once, DiagnosticText.sanitize(once))
    }

    @Test
    fun unsafeCharactersAreReplaced() {
        assertEquals("a?b?c?d", DiagnosticText.sanitize("a\tb|c\nd"))
        assertEquals("key?value", DiagnosticText.sanitize("key=value"))
    }

    @Test
    fun textIsCappedInLength() {
        val sanitized = DiagnosticText.sanitize("x".repeat(500))
        assertEquals(DiagnosticText.MAX_LENGTH, sanitized.length)
        assertTrue(DiagnosticText.sanitize("ab", maxLength = 1) == "a")
    }

    private companion object {
        // Stage and outcome words seen in InnerTubeX attempt labels and in our own codes, 11 characters long.
        val STAGE_LABELS =
            listOf(
                "selection",
                "request",
                "playability",
                "token",
                "unavailable",
                "Unavailable",
                "unsupported",
                "IOException",
            )
        val PROFILE_NAMES_OF_ELEVEN = listOf("WEB_CREATOR")
    }
}
