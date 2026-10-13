package com.bantai.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamParserTest {

    @Test
    fun testParseStandardScamOutput() {
        val llmOutput = """
            VERDICT: SCAM
            REASON: Unknown sender impersonating family member demanding instant fund transfer.
            ACTION: Do not send money and confirm with the sender directly.
        """.trimIndent()

        val verdict = ScamParser.parse(llmOutput)
        assertTrue(verdict.isScam)
        assertEquals("Unknown sender impersonating family member demanding instant fund transfer.", verdict.reason)
        assertEquals("Do not send money and confirm with the sender directly.", verdict.action)
    }

    @Test
    fun testParseStandardSafeOutput() {
        val llmOutput = """
            VERDICT: SAFE
            REASON: Casual routine message between family members.
            ACTION: No action required.
        """.trimIndent()

        val verdict = ScamParser.parse(llmOutput)
        assertFalse(verdict.isScam)
        assertEquals("Casual routine message between family members.", verdict.reason)
        assertEquals("No action required.", verdict.action)
    }

    @Test
    fun testParseTagalogTokensCompatibility() {
        val llmOutput = """
            HATOL: SCAM
            DAHILAN: May nagpapanggap na kamag-anak.
            GAWIN: Tawagan sa lumang numero.
        """.trimIndent()

        val verdict = ScamParser.parse(llmOutput)
        assertTrue(verdict.isScam)
        assertEquals("May nagpapanggap na kamag-anak.", verdict.reason)
        assertEquals("Tawagan sa lumang numero.", verdict.action)
    }

    @Test
    fun testParseSingleLineFormat() {
        val llmOutput = "SCAM: Impersonation of relative"
        val verdict = ScamParser.parse(llmOutput)
        assertTrue(verdict.isScam)
        assertEquals("Impersonation of relative", verdict.reason)
    }

    @Test
    fun testParseUnstructuredFallback() {
        val llmOutput = "This is clearly a suspicious phishing message, do not send money."
        val verdict = ScamParser.parse(llmOutput)
        assertTrue(verdict.isScam)
    }

    @Test
    fun testParseValidAndNotScamVerdicts() {
        val out1 = """
            VERDICT: VALID
            REASON: Official Google search domain.
            ACTION: No action required.
        """.trimIndent()
        val res1 = ScamParser.parse(out1)
        assertFalse(res1.isScam)
        assertEquals("Official Google search domain.", res1.reason)
        assertEquals("No action required.", res1.action)

        val out2 = """
            VERDICT: NOT SCAM
            REASON: Message is safe and legitimate.
        """.trimIndent()
        val res2 = ScamParser.parse(out2)
        assertFalse(res2.isScam)

        val out3 = """
            VERDICT: NOT A SCAM
            REASON: Normal message from family.
        """.trimIndent()
        val res3 = ScamParser.parse(out3)
        assertFalse(res3.isScam)
    }

    @Test
    fun testParseSafeActionOverridesScamVerdict() {
        val out = """
            VERDICT: SCAM
            REASON: Legitimate Google website.
            ACTION: No action required.
        """.trimIndent()
        val res = ScamParser.parse(out)
        assertFalse(res.isScam)
    }

    @Test
    fun testParseSuspiciousVerdict() {
        val out = """
            VERDICT: SUSPICIOUS
            REASON: It asks for money but may be a real request from family.
            ACTION: Verify first.
        """.trimIndent()
        val res = ScamParser.parse(out)
        assertFalse(res.isScam)
        assertEquals(com.bantai.model.Verdict.SUSPICIOUS, res.level)
        assertEquals("It asks for money but may be a real request from family.", res.reason)
    }

    @Test
    fun testParseSuspiciousTagalogTokens() {
        for (hatol in listOf("KAHINA-HINALA", "MAG-INGAT")) {
            val res = ScamParser.parse("HATOL: $hatol\nDAHILAN: Baka totoo, baka hindi.")
            assertFalse(hatol, res.isScam)
            assertEquals(hatol, com.bantai.model.Verdict.SUSPICIOUS, res.level)
        }
    }

    @Test
    fun testParseLooseSuspiciousFallback() {
        val res = ScamParser.parse("This message looks suspicious, please verify with the sender.")
        assertFalse(res.isScam)
        assertEquals(com.bantai.model.Verdict.SUSPICIOUS, res.level)
    }
}
