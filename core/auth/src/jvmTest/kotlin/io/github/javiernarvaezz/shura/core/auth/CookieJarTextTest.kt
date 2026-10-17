package io.github.javiernarvaezz.shura.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CookieJarTextTest {
    @Test
    fun parsesWhatTheWebViewReturns() {
        val cookies = CookieJarText.parse("SID=abc; HSID=x=y; __Secure-3PAPISID=q;  empty=; =broken")
        assertEquals(mapOf("SID" to "abc", "HSID" to "x=y", "__Secure-3PAPISID" to "q", "empty" to ""), cookies)
    }

    @Test
    fun writesTheCookieHeaderBack() {
        assertEquals("SID=abc; HSID=x=y", CookieJarText.header(linkedMapOf("SID" to "abc", "HSID" to "x=y")))
    }

    @Test
    fun rotatedSessionCookiesAreTakenFromResponses() {
        val session = linkedMapOf("SID" to "1", "SIDCC" to "old", "__Secure-1PSIDTS" to "old")
        val merged =
            CookieJarText.merge(
                session,
                listOf(
                    "SIDCC=new; expires=Fri, 15-Oct-2027 10:00:00 GMT; path=/; domain=.youtube.com; priority=high",
                    "__Secure-1PSIDTS=rot; Path=/; Secure; HttpOnly",
                    // Only cookies the session already has are taken; a stranger is ignored.
                    "TRACKER=1; path=/",
                ),
            )
        assertEquals(mapOf("SID" to "1", "SIDCC" to "new", "__Secure-1PSIDTS" to "rot"), merged)
        assertFalse("TRACKER" in merged)
    }

    @Test
    fun aCookieDeletedByTheServerIsDropped() {
        val merged = CookieJarText.merge(linkedMapOf("SID" to "1", "SIDCC" to "x"), listOf("SIDCC=; Max-Age=0; path=/"))
        assertEquals(mapOf("SID" to "1"), merged)
    }
}
