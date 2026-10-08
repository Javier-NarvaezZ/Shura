package io.github.javiernarvaezz.shura.core.innertube

import kotlin.test.Test
import kotlin.test.assertEquals

class InnerTubeLocaleTest {
    private fun config(
        language: String?,
        region: String?,
    ) = InnerTubeConfig.forDevice(language, region)

    @Test
    fun `plain language and region pass through`() {
        val config = config("de", "DE")
        assertEquals("de", config.hl)
        assertEquals("DE", config.gl)
    }

    @Test
    fun `missing locale falls back to es-419 and CO`() {
        val config = config(null, null)
        assertEquals("es-419", config.hl)
        assertEquals("CO", config.gl)
        assertEquals(InnerTubeConfig(), config)
    }

    @Test
    fun `malformed values fall back independently`() {
        assertEquals("es-419", config("", "MX").hl)
        assertEquals("MX", config("", "MX").gl)
        assertEquals("es-419", config("english", "US").hl)
        assertEquals("CO", config("en", "419").gl)
        assertEquals("CO", config("en", "").gl)
        assertEquals("en", config("en", "419").hl)
    }

    @Test
    fun `case and whitespace are normalized`() {
        val config = config(" EN ", " gb ")
        assertEquals("en-GB", config.hl)
        assertEquals("GB", config.gl)
    }

    @Test
    fun `spanish maps to its regional variants`() {
        assertEquals("es", config("es", "ES").hl)
        assertEquals("es-US", config("es", "US").hl)
        assertEquals("es-419", config("es", "AR").hl)
        assertEquals("es-419", config("es", null).hl)
    }

    @Test
    fun `other regional variants`() {
        assertEquals("en", config("en", "US").hl)
        assertEquals("en-IN", config("en", "IN").hl)
        assertEquals("fr-CA", config("fr", "CA").hl)
        assertEquals("fr", config("fr", "FR").hl)
        assertEquals("pt-PT", config("pt", "PT").hl)
        assertEquals("pt", config("pt", "BR").hl)
        assertEquals("zh-TW", config("zh", "TW").hl)
        assertEquals("zh-HK", config("zh", "HK").hl)
        assertEquals("zh-CN", config("zh", "SG").hl)
    }

    @Test
    fun `legacy language codes are modernized`() {
        assertEquals("id", config("in", "ID").hl)
        assertEquals("yi", config("ji", null).hl)
    }
}
