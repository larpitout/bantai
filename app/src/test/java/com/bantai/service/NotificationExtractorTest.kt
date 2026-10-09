package com.bantai.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationExtractorTest {

    @Before
    fun setUp() {
        NotificationExtractor.clearCache()
    }

    @Test
    fun testAllowedPackages() {
        // SMS & Chat packages that must be allowed
        assertTrue(NotificationExtractor.isPackageAllowed("com.google.android.apps.messaging"))
        assertTrue(NotificationExtractor.isPackageAllowed("com.samsung.android.messaging"))
        assertTrue(NotificationExtractor.isPackageAllowed("com.android.mms"))
        assertTrue(NotificationExtractor.isPackageAllowed("com.android.messaging"))
        assertTrue(NotificationExtractor.isPackageAllowed("com.facebook.orca"))
        assertTrue(NotificationExtractor.isPackageAllowed("com.viber.voip"))
        assertTrue(NotificationExtractor.isPackageAllowed("com.whatsapp"))

        // Other apps that must be ignored
        assertFalse(NotificationExtractor.isPackageAllowed("com.spotify.music"))
        assertFalse(NotificationExtractor.isPackageAllowed("com.android.chrome"))
        assertFalse(NotificationExtractor.isPackageAllowed("com.instagram.android"))
    }

    @Test
    fun testExtractContentPrioritizesBigText() {
        val title = "09951234567"
        val text = "Short summary..."
        val bigText = "Ma si Junjun to bagong number ko padala ka 5k gcash"

        val (sender, message) = NotificationExtractor.extractContent(title, bigText, text)
        assertEquals("09951234567", sender)
        assertEquals("Ma si Junjun to bagong number ko padala ka 5k gcash", message)
    }

    @Test
    fun testExtractContentFallsBackToText() {
        val title = "FlashExpress"
        val text = "Your parcel delivery failed. Update details at http://track.xyz"

        val (sender, message) = NotificationExtractor.extractContent(title, null, text)
        assertEquals("FlashExpress", sender)
        assertEquals("Your parcel delivery failed. Update details at http://track.xyz", message)
    }

    @Test
    fun testNotificationDeduplication() {
        val pkg = "com.facebook.orca"
        val sender = "Junjun"
        val text = "Ma padala ka pera"

        // First instance -> Not a duplicate
        val isFirstDup = NotificationExtractor.isDuplicate(pkg, sender, text)
        assertFalse("First time seeing notification should not be duplicate", isFirstDup)

        // Second instance with identical details -> Is a duplicate
        val isSecondDup = NotificationExtractor.isDuplicate(pkg, sender, text)
        assertTrue("Repeated identical notification should be detected as duplicate", isSecondDup)

        // Different text -> Not a duplicate
        val isDiffDup = NotificationExtractor.isDuplicate(pkg, sender, "Ma wag na pala")
        assertFalse("Different message text should not be duplicate", isDiffDup)
    }

    @Test
    fun testBlankTextTreatedAsDuplicate() {
        assertTrue(NotificationExtractor.isDuplicate("com.whatsapp", "Sender", ""))
        assertTrue(NotificationExtractor.isDuplicate("com.whatsapp", "Sender", "   "))
    }
}
