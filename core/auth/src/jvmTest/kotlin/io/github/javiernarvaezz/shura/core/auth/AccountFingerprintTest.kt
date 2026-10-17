package io.github.javiernarvaezz.shura.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AccountFingerprintTest {
    private fun session(dataSyncId: String?) = Session(emptyMap(), dataSyncId, null, 0)

    @Test
    fun theSameAccountGivesTheSameFingerprintAndNeverTheIdItself() {
        val a = AccountFingerprint.of(session("UCbrand||user123"))
        assertEquals(a, AccountFingerprint.of(session("UCbrand||user123")))
        assertFalse(a!!.contains("user123"))
        assertFalse(a.contains("UCbrand"))
    }

    @Test
    fun anotherAccountGivesAnotherFingerprint() {
        assertNotEquals(AccountFingerprint.of(session("||user1")), AccountFingerprint.of(session("||user2")))
    }

    @Test
    fun withoutAnIdThereIsNone() {
        assertNull(AccountFingerprint.of(session(null)))
        assertNull(AccountFingerprint.of(session("")))
    }
}
