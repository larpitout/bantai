package com.bantai.rules

import com.bantai.data.TestDataset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleFilterTest {

    @Test
    fun testAllScamMessagesTriggerAtLeastOneSignal() {
        val scamCases = TestDataset.messages.filter { it.isScam }
        assertEquals(15, scamCases.size)

        var triggeredCount = 0
        for (case in scamCases) {
            val result = RuleFilter.score(case.text, case.sender)
            assertTrue(
                "Expected scam case #${case.id} (${case.category}) to trigger >= 1 signal, got ${result.score}",
                result.score >= 1
            )
            triggeredCount++
        }

        assertEquals(15, triggeredCount)
    }

    @Test
    fun testAllSafeMessagesProduceZeroFalsePositives() {
        val safeCases = TestDataset.messages.filter { !it.isScam }
        assertEquals(15, safeCases.size)

        for (case in safeCases) {
            val result = RuleFilter.score(case.text, case.sender)
            assertEquals(
                "Expected safe case #${case.id} (${case.category}) to have score 0, got ${result.score} with signals ${result.signals}",
                0,
                result.score
            )
        }
    }

    @Test
    fun testInstantWarningCreationForHighRiskImpersonation() {
        val text = "Ma si Junjun to bagong number ko padala ka 5k gcash"
        val result = RuleFilter.score(text)
        assertTrue(result.score >= 2)

        val warning = RuleFilter.createInstantWarning(result)
        assertTrue(warning.isScam)
        assertTrue(warning.reason.contains("impersonating a relative", ignoreCase = true))
        assertTrue(warning.action.contains("Do not send money", ignoreCase = true))
    }

    @Test
    fun testInstantWarningCreationForSuspiciousLink() {
        val text = "Your account is suspended. Verify at http://bit.ly/bank-security"
        val result = RuleFilter.score(text)
        assertTrue(result.score >= 2)

        val warning = RuleFilter.createInstantWarning(result)
        assertTrue(warning.isScam)
        assertTrue(warning.reason.contains("Suspicious link", ignoreCase = true))
        assertTrue(warning.action.contains("Never share your OTP", ignoreCase = true))
    }
}
