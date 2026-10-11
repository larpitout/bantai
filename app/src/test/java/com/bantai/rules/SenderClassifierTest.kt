package com.bantai.rules

import com.bantai.rules.SenderClassifier.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class SenderClassifierTest {

    @Test
    fun officialIdsHitTheCuratedTable() {
        val cases = mapOf(
            "8080" to "Globe", "GLOBE" to "Globe", "TM" to "Globe TM",
            "SMART" to "Smart", "TNT" to "Smart TNT", "DITO" to "DITO", "GOMO" to "GOMO",
            "GCASH" to "GCash", "2882" to "GCash", "MAYA" to "Maya", "PAYMAYA" to "Maya",
            "BDO" to "BDO", "BDO ALERT" to "BDO", "BDO DEALS" to "BDO", "BDO OTP" to "BDO",
            "225678" to "BDO", "22567888" to "BDO", "22565048" to "BDO",
            "BPI" to "BPI", "METROBANK" to "Metrobank", "UNIONBANK" to "UnionBank",
            "LANDBANK" to "LandBank", "RCBC" to "RCBC",
            "NINJA VAN" to "Ninja Van", "SHOPEE" to "Shopee", "LAZADA" to "Lazada",
        )
        for ((sender, brand) in cases) {
            val info = SenderClassifier.classify(sender, senderIsContact = false)
            assertEquals(sender, Type.OFFICIAL_ID, info.type)
            assertEquals(sender, "official sender ID: $brand", info.label)
            assertEquals(sender, "Sender: \"$sender\" (official sender ID: $brand)", info.promptLine())
        }
        // Case-insensitive
        assertEquals(Type.OFFICIAL_ID, SenderClassifier.classify("gcash", false).type)
    }

    @Test
    fun digitsOnlyShortSendersAreShortcodes() {
        for (sender in listOf("123", "2345", "77777", "888888")) {
            val info = SenderClassifier.classify(sender, senderIsContact = false)
            assertEquals(sender, Type.SHORTCODE, info.type)
            assertEquals(sender, "short code", info.label)
        }
    }

    @Test
    fun phoneNumbersAreMobileNumbers() {
        for (sender in listOf("09171234567", "+639171234567", "0917-123-4567")) {
            assertEquals(sender, Type.MOBILE_NUMBER, SenderClassifier.classify(sender, false).type)
        }
        assertEquals(
            "Sender: \"09171234567\" (mobile number, not in contacts)",
            SenderClassifier.classify("09171234567", false).promptLine()
        )
    }

    @Test
    fun savedContactWinsOverEverything() {
        assertEquals(Type.SAVED_CONTACT, SenderClassifier.classify("Junjun", senderIsContact = true).type)
        // Kahit opisyal ang pangalan, kakilala pa rin ang nagpadala.
        assertEquals(Type.SAVED_CONTACT, SenderClassifier.classify("GCASH", senderIsContact = true).type)
        assertEquals(
            "Sender: \"Junjun\" (saved contact)",
            SenderClassifier.classify("Junjun", true).promptLine()
        )
    }

    @Test
    fun unknownUppercaseIdsAreUnverifiedSenderIds() {
        for (sender in listOf("MERALCO", "CEB PAC", "XYZ PROMO")) {
            val info = SenderClassifier.classify(sender, senderIsContact = false)
            assertEquals(sender, Type.ALPHANUMERIC_ID, info.type)
            assertEquals(sender, "sender ID, unverified", info.label)
        }
    }

    @Test
    fun everythingElseIsUnknown() {
        for (sender in listOf("", "FlashExpress", "GCASH-ALERT", "Ma", "12", "1234567")) {
            assertEquals(sender, Type.UNKNOWN, SenderClassifier.classify(sender, false).type)
        }
    }
}
