package com.bantai.rules

import com.bantai.rules.LinkChecker.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkCheckerTest {

    private fun kind(text: String) = LinkChecker.check(text)?.kind

    @Test
    fun fakeBrandDomains() {
        assertEquals(Kind.FAKE_BRAND, kind("Na-hold ang GCash mo. I-verify sa gcash-verify.com agad"))
        assertEquals("gcash", LinkChecker.check("login: http://gcash-ph.xyz/otp")?.brand)
        assertEquals(Kind.FAKE_BRAND, kind("BDO alert: bdo-secure.online/login"))
        assertEquals(Kind.FAKE_BRAND, kind("Parcel mo: jnt-delivery.top/pay"))
    }

    @Test
    fun officialDomainsAreFine() {
        assertNull(kind("Bisitahin ang https://www.gcash.com/help"))
        assertNull(kind("Statement mo: https://online.bdo.com.ph"))
        assertNull(kind("Track: https://www.jtexpress.ph"))
    }

    @Test
    fun shortenersRiskyEndingsAndIp() {
        assertEquals(Kind.SHORTENER, kind("Claim mo na: bit.ly/claim-prize"))
        assertEquals(Kind.RISKY_ENDING, kind("Panalo ka! claim-reward.xyz"))
        assertEquals(Kind.IP_ADDRESS, kind("Login dito http://192.168.4.20/login"))
        assertEquals(Kind.LOOKALIKE, kind("https://xn--gcsh-8ra.com/login"))
    }

    @Test
    fun noLinkOrNormalText() {
        assertNull(kind("Ma si Junjun to padala ka 5k sa gcash"))
        assertNull(kind("Nay uwi ako 7pm, pasabay ng tinapay"))
        assertNull(kind("Tingnan mo to https://www.google.com"))
        assertNull(kind("Bili tayo sa smartphones.com mamaya"))
    }

    @Test
    fun safeDomainRecognition() {
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("google.com"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("www.google.com"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("google.com.ph"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("facebook.com"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("m.facebook.com"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("youtube.com"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("youtu.be"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("gcash.com"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("help.gcash.com"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("bdo.com.ph"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("online.bdo.com.ph"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("doh.gov.ph"))
        org.junit.Assert.assertTrue(LinkChecker.isSafeDomain("up.edu.ph"))

        org.junit.Assert.assertFalse(LinkChecker.isSafeDomain("gcash-verify.com"))
        org.junit.Assert.assertFalse(LinkChecker.isSafeDomain("bdo-secure.online"))
        org.junit.Assert.assertFalse(LinkChecker.isSafeDomain("sim-registration-portal.com"))
        org.junit.Assert.assertFalse(LinkChecker.isSafeDomain("bit.ly"))
    }

    @Test
    fun unverifiedLinkDetection() {
        org.junit.Assert.assertFalse(LinkChecker.hasUnverifiedLink("www.google.com"))
        org.junit.Assert.assertFalse(LinkChecker.hasUnverifiedLink("Bisitahin ang https://www.gcash.com/help"))
        org.junit.Assert.assertFalse(LinkChecker.hasUnverifiedLink("Tingnan mo to https://www.google.com"))
        org.junit.Assert.assertFalse(LinkChecker.hasUnverifiedLink("Panoorin sa https://youtu.be/abc"))

        org.junit.Assert.assertTrue(LinkChecker.hasUnverifiedLink("http://bit.ly/claim-prize"))
        org.junit.Assert.assertTrue(LinkChecker.hasUnverifiedLink("http://sim-registration-portal.com"))
        org.junit.Assert.assertTrue(LinkChecker.hasUnverifiedLink("http://gcash-verify.com/login"))
    }
}
