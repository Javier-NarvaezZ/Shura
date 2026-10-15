package io.github.javiernarvaezz.shura.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SapisidHashTest {
    @Test
    fun signsTimestampSecretAndOrigin() {
        // sha1("1700000000 TESTSAPISID/abcDEF https://music.youtube.com"), computed separately.
        assertEquals(
            "SAPISIDHASH 1700000000_d97ce8f36fa4413d31e7400032f38bc98cf7a477",
            SapisidHash.header("TESTSAPISID/abcDEF", "https://music.youtube.com", 1_700_000_000),
        )
    }

    @Test
    fun takesSapisidOrElseTheSecure3pVariant() {
        assertEquals("a", SapisidHash.secret(mapOf("SAPISID" to "a", "__Secure-3PAPISID" to "b")))
        assertEquals("b", SapisidHash.secret(mapOf("__Secure-3PAPISID" to "b")))
        assertNull(SapisidHash.secret(mapOf("SID" to "x")))
    }
}
