package com.bantai.pipeline

import com.bantai.rules.PromptBuilder
import com.bantai.rules.RuleFilter
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

        val scam = ScamPipeline(StubEngine { SCAM_REPLY }).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(1, scam.size)
        assertTrue(scam[0].verdict.isScam)
        assertEquals(VerdictSource.LLM, scam[0].source)
        assertEquals("Humihingi ng pera.", scam[0].verdict.dahilan)

        val safe = ScamPipeline(StubEngine { SAFE_REPLY }).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(1, safe.size)
        assertFalse(safe[0].verdict.isScam)
        assertEquals(VerdictSource.LLM, safe[0].source)
    }

    @Test
    fun scoreTwoEmitsRuleWarningThenModelExplanation() = runTest {
        assertTrue(RuleFilter.score(SCAM_TEXT).score >= 2)

        val results = ScamPipeline(StubEngine { SCAM_REPLY }).check(SCAM_TEXT).toList()

        assertEquals(2, results.size)
        assertTrue(results[0].verdict.isScam)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertFalse(results[0].isFinal)
        assertTrue(results[1].verdict.isScam)
        assertEquals(VerdictSource.LLM, results[1].source)
        assertTrue(results[1].isFinal)
        assertEquals("Huwag magpadala.", results[1].verdict.gawin)
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
    fun timeoutFallsBackToRuleVerdict() = runTest {
        val engine = FakeEngine(delayMs = 0)
        engine.load()
        val slow = FakeEngine(delayMs = ScamPipeline.DEFAULT_TIMEOUT_MS + 5_000)
        slow.load()

        val fast = ScamPipeline(engine).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(VerdictSource.LLM, fast.last().source)

        val timedOut = ScamPipeline(slow).check(ONE_SIGNAL_TEXT).toList()
        assertEquals(VerdictSource.RULES, timedOut.last().source)
        assertTrue(timedOut.last().verdict.isScam)
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
    fun engineErrorFallsBackToRuleVerdict() = runTest {
        val results = ScamPipeline(StubEngine { error("inference failed") }).check(ONE_SIGNAL_TEXT).toList()

        assertEquals(1, results.size)
        assertEquals(VerdictSource.RULES, results[0].source)
        assertTrue(results[0].verdict.isScam)
    }

    @Test
    fun replyWithoutVerdictLineFallsBackToRuleVerdict() = runTest {
        val results = ScamPipeline(StubEngine { "Hindi ko po alam." }).check(ONE_SIGNAL_TEXT).toList()

        assertEquals(VerdictSource.RULES, results.last().source)
        assertTrue(results.last().verdict.isScam)
    }

    @Test
    fun promptIsShortAndCarriesTheMessage() {
        val prompt = PromptBuilder.buildScamPrompt(SCAM_TEXT, listOf("Money Request"))

        assertTrue(prompt.contains(SCAM_TEXT))
        assertTrue(prompt.contains("Money Request"))
        // Isang buong halimbawa (hindi template na makokopya) at ang format na binabasa ng ScamParser.
        assertTrue(prompt.contains("VERDICT: SCAM"))
        assertTrue(prompt.contains("REASON:"))
        assertTrue(!prompt.contains("SCAM or LIGTAS"))
        assertTrue(PromptBuilder.buildScamPrompt(SCAM_TEXT, language = "Filipino").contains("REASON in Filipino"))

        val long = PromptBuilder.buildScamPrompt("a".repeat(2000))
        assertTrue(!long.contains("a".repeat(PromptBuilder.MAX_MESSAGE_CHARS + 1)))
    }

    private companion object {
        const val SAFE_TEXT = "Lola, happy birthday po! Dadalaw po kami sa Linggo."
        const val ONE_SIGNAL_TEXT = "Padala ka naman ng ulam dito sa bahay."
        const val SCAM_TEXT = "Ma si Junjun to bagong number ko padala ka 5k gcash"

        const val SCAM_REPLY = "HATOL: SCAM\nDAHILAN: Humihingi ng pera.\nGAWIN: Huwag magpadala."
        const val SAFE_REPLY = "HATOL: LIGTAS\nDAHILAN: Karaniwang mensahe lang.\nGAWIN: Wala pong kailangang gawin."
    }
}
