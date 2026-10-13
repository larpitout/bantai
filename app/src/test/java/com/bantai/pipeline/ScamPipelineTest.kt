package com.bantai.pipeline

import com.bantai.model.Verdict
import com.bantai.rules.PromptBuilder
import com.bantai.rules.RuleFilter
import com.bantai.rules.SenderClassifier
import com.bantay.app.core.EngineState
import com.bantay.app.core.FakeEngine
import com.bantay.app.core.LlmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamPipelineTest {

    private class StubEngine(
        initial: EngineState = EngineState.READY,
        private val answer: () -> String = { SCAM_REPLY },
    ) : LlmEngine {
        var calls = 0
        override val state: StateFlow<EngineState> = MutableStateFlow(initial)
        override suspend fun load() {}
        override suspend fun generate(prompt: String): String {
            calls++
            return answer()
        }
        override fun close() {}
    }

    @Test
    fun scoreZeroIsSafeWithoutCallingTheModel() = runTest {
        assertEquals(0, RuleFilter.score(SAFE_TEXT).score)
        val engine = StubEngine()

        val results = ScamPipeline(engine).check(SAFE_TEXT).toList()

        assertEquals(1, results.size)
        assertFalse(results[0].verdict.isScam)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertTrue(results[0].isFinal)
        assertEquals(0, engine.calls)
    }

    @Test
    fun scoreOneUsesTheModelVerdict() = runTest {
        assertEquals(1, RuleFilter.score(ONE_SIGNAL_TEXT).score)

        // SCAM: mabilis na babala muna (non-final), saka ang huling hatol na may paliwanag.
        val scam = ScamPipeline(StubEngine { SCAM_REPLY }).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(2, scam.size)
        assertTrue(scam[0].verdict.isScam)
        assertFalse(scam[0].isFinal)
        assertEquals(VerdictSource.LLM, scam[0].source)
        assertTrue(scam[1].verdict.isScam)
        assertTrue(scam[1].isFinal)
        assertEquals(VerdictSource.LLM, scam[1].source)
        assertEquals("Humihingi ng pera.", scam[1].verdict.dahilan)

        val safe = ScamPipeline(StubEngine { SAFE_REPLY }).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(1, safe.size)
        assertFalse(safe[0].verdict.isScam)
        assertEquals(VerdictSource.LLM, safe[0].source)
    }

    @Test
    fun scoreTwoEmitsRuleWarningThenModelExplanation() = runTest {
        assertTrue(RuleFilter.score(SCAM_TEXT).score >= 2)

        val results = ScamPipeline(StubEngine { SCAM_REPLY }).check(SCAM_TEXT).toList()

        // Instant rule warning + mabilis na SCAM ng model + huling hatol na may paliwanag.
        assertEquals(3, results.size)
        assertTrue(results[0].verdict.isScam)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertFalse(results[0].isFinal)
        assertTrue(results[1].verdict.isScam)
        assertEquals(VerdictSource.LLM, results[1].source)
        assertFalse(results[1].isFinal)
        assertTrue(results[2].verdict.isScam)
        assertEquals(VerdictSource.LLM, results[2].source)
        assertTrue(results[2].isFinal)
        assertEquals("Humihingi ng pera.", results[2].verdict.dahilan)
    }

    @Test
    fun modelCannotDowngradeScoreTwoToSafe() = runTest {
        val results = ScamPipeline(StubEngine { SAFE_REPLY }).check(SCAM_TEXT).toList()

        assertEquals(2, results.size)
        assertTrue(results[1].verdict.isScam)
        assertEquals(VerdictSource.RULES, results[1].source)
        assertTrue(results[1].isFinal)
    }

    @Test
    fun scoreOneTimeoutMeansCautiousSuspicious() = runTest {
        val engine = FakeEngine(delayMs = 0)
        engine.load()
        val slow = FakeEngine(delayMs = ScamPipeline.DEFAULT_TIMEOUT_MS + 5_000)
        slow.load()

        val fast = ScamPipeline(engine).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(VerdictSource.LLM, fast.last().source)

        // May senyales pero walang hatol ang AI: maingat na SUSPICIOUS, hindi SAFE.
        val timedOut = ScamPipeline(slow).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(1, timedOut.size)
        assertEquals(VerdictSource.RULES, timedOut.last().source)
        assertEquals(Verdict.SUSPICIOUS, timedOut.last().verdict.level)
        assertFalse(timedOut.last().verdict.isScam)
        assertTrue(timedOut.last().isFinal)
    }

    @Test
    fun engineNotReadyFallsBackToRuleVerdict() = runTest {
        for (state in listOf(EngineState.IDLE, EngineState.LOADING, EngineState.FAILED)) {
            val engine = StubEngine(initial = state)

            val results = ScamPipeline(engine).check(SCAM_TEXT).toList()

            assertEquals(VerdictSource.RULES, results.last().source)
            assertTrue(results.last().verdict.isScam)
            assertTrue(results.last().isFinal)
            assertEquals(0, engine.calls)
        }
    }

    @Test
    fun scoreZeroStaysSafeWhenTheModelDoesNotAnswer() = runTest {
        val engine = StubEngine(initial = EngineState.IDLE)
        val results = ScamPipeline(engine, allowAiDowngrade = true).check(SAFE_TEXT).toList()

        assertEquals(1, results.size)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertFalse(results[0].verdict.isScam)
    }

    @Test
    fun engineErrorLeavesScoreOneSuspicious() = runTest {
        val results = ScamPipeline(StubEngine { error("inference failed") }).check(ONE_SIGNAL_TEXT).toList()

        assertEquals(1, results.size)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertEquals(Verdict.SUSPICIOUS, results[0].verdict.level)
        assertFalse(results[0].verdict.isScam)
    }

    @Test
    fun scoreOneNotReadyEmitsSingleSuspiciousVerdict() = runTest {
        for (state in listOf(EngineState.IDLE, EngineState.LOADING, EngineState.FAILED)) {
            val results = ScamPipeline(StubEngine(initial = state)).check(ONE_SIGNAL_TEXT).toList()

            assertEquals(1, results.size)
            assertEquals(VerdictSource.RULES, results[0].source)
            assertEquals(Verdict.SUSPICIOUS, results[0].verdict.level)
            assertFalse(results[0].verdict.isScam)
            assertTrue(results[0].isFinal)
        }
    }

    @Test
    fun replyWithoutVerdictLineLeavesScoreOneSuspicious() = runTest {
        val results = ScamPipeline(StubEngine { "Hindi ko po alam." }).check(ONE_SIGNAL_TEXT).toList()

        assertEquals(1, results.size)
        assertEquals(VerdictSource.RULES, results.last().source)
        assertEquals(Verdict.SUSPICIOUS, results.last().verdict.level)
        assertFalse(results.last().verdict.isScam)
    }

    @Test
    fun checkTabGetsSuspiciousFallbackWhenEngineNotReady() = runTest {
        val results = ScamPipeline(StubEngine(initial = EngineState.IDLE), allowAiDowngrade = true)
            .check(ONE_SIGNAL_TEXT).toList()

        assertEquals(1, results.size)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertEquals(Verdict.SUSPICIOUS, results[0].verdict.level)
        assertFalse(results[0].verdict.isScam)
        assertTrue(results[0].isFinal)
    }

    @Test
    fun contactAskingForMoneyIsSuspiciousWithoutCallingTheModel() = runTest {
        assertTrue(RuleFilter.score(CONTACT_MONEY_TEXT).score >= 2)
        val engine = StubEngine()

        val results = ScamPipeline(engine).check(CONTACT_MONEY_TEXT, "Junjun", senderIsContact = true).toList()

        assertEquals(1, results.size)
        assertEquals(Verdict.SUSPICIOUS, results[0].verdict.level)
        assertFalse(results[0].verdict.isScam)
        assertTrue(results[0].isFinal)
        assertEquals(0, engine.calls)
    }

    @Test
    fun unknownSenderAskingForMoneyIsStillScam() = runTest {
        val results = ScamPipeline(StubEngine()).check(CONTACT_MONEY_TEXT, "09171234567").toList()

        assertEquals(Verdict.SCAM, results.last().verdict.level)
    }

    @Test
    fun contactWithOneSignalLetsTheModelDecide() = runTest {
        val flagged = ScamPipeline(StubEngine { SCAM_REPLY }).check(ONE_SIGNAL_TEXT, "Junjun", senderIsContact = true).toList()
        assertEquals(Verdict.SUSPICIOUS, flagged.last().verdict.level)
        assertEquals(VerdictSource.LLM, flagged.last().source)

        val safe = ScamPipeline(StubEngine { SAFE_REPLY }).check(ONE_SIGNAL_TEXT, "Junjun", senderIsContact = true).toList()
        assertEquals(Verdict.SAFE, safe.last().verdict.level)

        val noModel = ScamPipeline(StubEngine(initial = EngineState.IDLE)).check(ONE_SIGNAL_TEXT, "Junjun", senderIsContact = true).toList()
        assertEquals(Verdict.SUSPICIOUS, noModel.last().verdict.level)
    }

    @Test
    fun linkOrOtpFromContactIsStillScam() = runTest {
        for (text in listOf(CONTACT_LINK_TEXT, CONTACT_OTP_TEXT)) {
            val results = ScamPipeline(StubEngine()).check(text, "Junjun", senderIsContact = true).toList()

            assertEquals(text, Verdict.SCAM, results.last().verdict.level)
            assertTrue(results.last().verdict.isScam)
        }
    }

    @Test
    fun suspiciousModelReplyCountsAsNoVerdict() = runTest {
        // Binary lang ang hinihingi sa model; ang "VERDICT: SUSPICIOUS" ay parang walang hatol
        // kaya maingat na SUSPICIOUS mula sa rules ang lumalabas.
        val results = ScamPipeline(StubEngine { SUSPICIOUS_REPLY }).check(ONE_SIGNAL_TEXT).toList()

        assertEquals(1, results.size)
        assertEquals(Verdict.SUSPICIOUS, results[0].verdict.level)
        assertFalse(results[0].verdict.isScam)
        assertEquals(VerdictSource.RULES, results[0].source)
    }

    @Test
    fun hardSignalStaysScamWhateverTheModelSays() = runTest {
        // Sa hard signal hindi na kayang ibaba ng AI ang babala (binary verdict: SAFE = walang effecto).
        for (reply in listOf(SUSPICIOUS_REPLY, SAFE_REPLY)) {
            val results = ScamPipeline(StubEngine { reply }).check(SCAM_TEXT).toList()
            assertTrue(results[0].verdict.isScam)
            assertEquals(VerdictSource.RULES, results[0].source)
            assertFalse(results[0].isFinal)
            assertTrue(results.last().verdict.isScam)
            assertEquals(VerdictSource.RULES, results.last().source)
            assertTrue(results.last().isFinal)
        }
    }

    @Test
    fun hardSignalFromOfficialSenderHasNoInstantWarning() = runTest {
        // Maaaring spoof ang opisyal na sender ID, kaya walang instant warning: ang AI (na may sender context) ang huhusga.
        val scam = ScamPipeline(StubEngine { SCAM_REPLY }).check(OFFICIAL_LINK_TEXT, "GCASH").toList()
        assertEquals(2, scam.size)
        assertEquals(VerdictSource.LLM, scam[0].source)
        assertTrue(scam[0].verdict.isScam)
        assertEquals(VerdictSource.LLM, scam[1].source)
        assertTrue(scam[1].isFinal)

        // Walang hatol ang AI → maingat na SUSPICIOUS, hindi SCAM.
        val noModel = ScamPipeline(StubEngine(initial = EngineState.IDLE)).check(OFFICIAL_LINK_TEXT, "GCASH").toList()
        assertEquals(1, noModel.size)
        assertEquals(Verdict.SUSPICIOUS, noModel[0].verdict.level)
        assertEquals(VerdictSource.RULES, noModel[0].source)
    }

    @Test
    fun dangerousLinkFromUnknownNumberWarnsAtOnce() = runTest {
        assertTrue("Dangerous Link" in RuleFilter.score(OFFICIAL_LINK_TEXT).signals)

        val results = ScamPipeline(StubEngine { SCAM_REPLY }).check(OFFICIAL_LINK_TEXT, "09171234567").toList()

        // Instant rule warning + mabilis na SCAM ng model + huling hatol.
        assertEquals(3, results.size)
        assertTrue(results[0].verdict.isScam)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertFalse(results[0].isFinal)
    }

    @Test
    fun promptIsShortAndCarriesTheMessage() {
        val prompt = PromptBuilder.buildScamPrompt(
            SCAM_TEXT,
            SenderClassifier.classify("+639171234567", senderIsContact = false).promptLine(),
            listOf("Money Request"),
        )

        assertTrue(prompt.contains(SCAM_TEXT))
        assertTrue(prompt.contains("Money Request"))
        // Sender context, few-shot example, at ang format na binabasa ng ScamParser.
        assertTrue(prompt.contains("Sender: \"+639171234567\" (mobile number, not in contacts)"))
        assertTrue(prompt.contains("VERDICT: SCAM"))
        assertTrue(prompt.contains("Reply with exactly two lines, VERDICT and REASON"))
        assertTrue(prompt.contains("REASON:"))
        assertTrue(!prompt.contains("SCAM or LIGTAS"))
        assertTrue(
            PromptBuilder.buildScamPrompt(SCAM_TEXT, "Sender: \"8080\" (official sender ID: Globe)", language = "Filipino")
                .contains("in Filipino")
        )

        val long = PromptBuilder.buildScamPrompt("a".repeat(2000), "")
        assertTrue(!long.contains("a".repeat(PromptBuilder.MAX_MESSAGE_CHARS + 1)))
    }

    private companion object {
        const val SAFE_TEXT = "Lola, happy birthday po! Dadalaw po kami sa Linggo."
        const val ONE_SIGNAL_TEXT = "Padala ka naman ng ulam dito sa bahay."
        const val SCAM_TEXT = "Ma si Junjun to bagong number ko padala ka 5k gcash"

        const val CONTACT_MONEY_TEXT = "Pautang naman 2k, padala mo sa gcash ko ngayon na"
        const val CONTACT_LINK_TEXT = "Padala ka sa gcash, dito: http://gcash-verify.xyz/login"
        const val CONTACT_OTP_TEXT = "Padala mo sa akin yung OTP ng gcash mo ngayon na"
        const val OFFICIAL_LINK_TEXT = "GCash Alert: Verify your account at http://gcash-verify.xyz/secure"

        const val SCAM_REPLY = "HATOL: SCAM\nDAHILAN: Humihingi ng pera.\nGAWIN: Huwag magpadala."
        const val SUSPICIOUS_REPLY = "VERDICT: SUSPICIOUS\nREASON: It asks for money but may be legitimate.\nACTION: Verify first."
        const val SAFE_REPLY = "HATOL: LIGTAS\nDAHILAN: Karaniwang mensahe lang.\nGAWIN: Wala pong kailangang gawin."
    }
}
