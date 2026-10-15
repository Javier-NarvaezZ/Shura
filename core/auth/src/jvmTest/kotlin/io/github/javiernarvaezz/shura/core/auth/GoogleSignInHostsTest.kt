package io.github.javiernarvaezz.shura.core.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleSignInHostsTest {
    @Test
    fun signInMayNavigateToGoogleAccountsInEveryOfficialCountryDomain() {
        listOf("accounts.google.com", "accounts.google.com.co", "accounts.google.co.uk", "accounts.google.de").forEach {
            assertTrue(GoogleSignInHosts.navigationAllowed(it), it)
        }
        listOf("myaccount.google.com", "accounts.youtube.com", "music.youtube.com").forEach {
            assertTrue(GoogleSignInHosts.navigationAllowed(it), it)
        }
    }

    @Test
    fun anythingElseIsRefused() {
        listOf(
            "accounts.google.com.evil.com",
            "evilgoogle.com",
            "accounts.google.zz",
            "accounts.google.xyz",
            "google.com.co.attacker.net",
            "www.youtube.com.evil.com",
            "googleads.g.doubleclick.net",
            "",
        ).forEach { assertFalse(GoogleSignInHosts.navigationAllowed(it), it) }
    }

    @Test
    fun pagesMayLoadGoogleStaticAndImageHostsButNotAdsOrAnalytics() {
        listOf("ssl.gstatic.com", "lh3.googleusercontent.com", "i.ytimg.com", "yt3.ggpht.com", "www.google.com.co")
            .forEach { assertTrue(GoogleSignInHosts.resourceAllowed(it), it) }
        listOf("googleads.g.doubleclick.net", "www.googletagmanager.com", "static.doubleclick.net", "evil.com")
            .forEach { assertFalse(GoogleSignInHosts.resourceAllowed(it), it) }
    }

    @Test
    fun theListIsGooglesOfficialOne() {
        assertTrue(GoogleSignInHosts.officialDomains.size > 150)
        assertTrue("google.com.co" in GoogleSignInHosts.officialDomains)
    }
}
