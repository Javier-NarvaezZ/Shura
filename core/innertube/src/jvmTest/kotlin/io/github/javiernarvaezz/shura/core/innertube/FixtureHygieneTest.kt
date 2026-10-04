package io.github.javiernarvaezz.shura.core.innertube

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Recorded fixtures must never carry session, device or tracking identifiers. */
class FixtureHygieneTest {
    private val forbidden =
        listOf(
            Regex("visitorData", RegexOption.IGNORE_CASE),
            Regex("trackingParams", RegexOption.IGNORE_CASE),
            Regex("clickTrackingParams", RegexOption.IGNORE_CASE),
            Regex("serviceTrackingParams", RegexOption.IGNORE_CASE),
            Regex("responseContext", RegexOption.IGNORE_CASE),
            Regex("cookie", RegexOption.IGNORE_CASE),
            Regex("SAPISID", RegexOption.IGNORE_CASE),
            Regex("__Secure-"),
            Regex("datasyncId", RegexOption.IGNORE_CASE),
        )

    @Test
    fun fixturesExist() {
        assertTrue(Fixtures.all().isNotEmpty(), "No fixtures found")
    }

    @Test
    fun fixturesContainNoSessionOrTrackingData() {
        val violations =
            Fixtures.all().flatMap { file ->
                val text = file.readText()
                forbidden.filter { it.containsMatchIn(text) }.map { "${file.name}: ${it.pattern}" }
            }
        if (violations.isNotEmpty()) fail("Fixtures contain forbidden data:\n${violations.joinToString("\n")}")
    }
}
