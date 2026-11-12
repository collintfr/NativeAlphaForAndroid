package com.cylonid.nativealpha

import com.cylonid.nativealpha.browser.HostMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMatcherTest {
    @Test fun registrableDomain() {
        assertEquals("example.com", HostMatcher.registrableDomain("a.b.example.com"))
        assertEquals("example.com", HostMatcher.registrableDomain("WWW.Example.COM."))
        assertEquals("bbc.co.uk", HostMatcher.registrableDomain("www.bbc.co.uk"))
        assertEquals("orf.at", HostMatcher.registrableDomain("oe3.orf.at"))
        assertEquals("192.168.1.10", HostMatcher.registrableDomain("192.168.1.10"))
        assertEquals("example.com", HostMatcher.registrableDomain("example.com:8443"))
    }

    @Test fun sameSiteIncludesSubdomainsBothWays() {
        assertTrue(HostMatcher.isSameSite("m.facebook.com", "www.facebook.com", emptyList()))
        assertTrue(HostMatcher.isSameSite("mail.google.com", "google.com", emptyList()))
        assertFalse(HostMatcher.isSameSite("evilgoogle.com", "google.com", emptyList()))
        assertFalse(HostMatcher.isSameSite("google.com.evil.net", "google.com", emptyList()))
        assertFalse(HostMatcher.isSameSite("news.bbc.co.uk.attacker.co.uk", "bbc.co.uk", emptyList()))
    }

    @Test fun allowedHostsMatchSubdomainsOnly() {
        val allowed = listOf("accounts.google.com", "login.microsoftonline.com")
        assertTrue(HostMatcher.isSameSite("accounts.google.com", "outlook.com", allowed))
        assertTrue(HostMatcher.isSameSite("eu.accounts.google.com", "outlook.com", allowed))
        assertFalse(HostMatcher.isSameSite("google.com", "outlook.com", allowed))
        assertFalse(HostMatcher.isSameSite("xaccounts.google.com", "outlook.com", allowed))
    }

    @Test fun idnHostsAreNormalised() {
        assertTrue(HostMatcher.isSameSite("www.bücher.de", "xn--bcher-kva.de", emptyList()))
    }
}
