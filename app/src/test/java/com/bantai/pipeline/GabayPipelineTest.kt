package com.bantai.pipeline

import com.bantai.model.ScreenContext
import com.bantai.rules.PromptBuilder
import com.bantay.app.core.EngineState
import com.bantay.app.core.FakeEngine
import com.bantay.app.core.LlmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GabayPipelineTest {

    private class StubEngine(
        initial: EngineState = EngineState.READY,
        private val answer: () -> String = { GOOD_REPLY },
    ) : LlmEngine {
        var calls = 0
        var lastPrompt = ""
        override val state: StateFlow<EngineState> = MutableStateFlow(initial)
        override suspend fun load() {}
        override suspend fun generate(prompt: String): String {
            calls++
            lastPrompt = prompt
            return answer()
        }
        override fun close() {}
    }

    @Test
    fun noLabelsGivesTheFixedReplyWithoutCallingTheModel() = runTest {
        val engine = StubEngine()

        for (labels in listOf(emptyList(), listOf(" ", ""))) {
            val result = GabayPipeline(engine).guide(QUESTION, ScreenContext("com.bank.app", labels))

            assertEquals(GabaySource.NO_SCREEN, result.source)
            assertEquals("Hindi ko po makita ang screen na ito.", result.spokenText)
            assertTrue(result.steps.isEmpty())
            assertTrue(result.offerCallApo)
        }
        assertEquals(0, engine.calls)
    }

    @Test
    fun modelStepsAreReturnedAndNumbered() = runTest {
        val engine = StubEngine()

        val result = GabayPipeline(engine).guide(QUESTION, SCREEN)

        assertEquals(GabaySource.LLM, result.source)
        assertEquals(listOf("Pindutin po ang \"Gallery\".", "Pindutin po ang \"Send\"."), result.steps)
        assertEquals("1. Pindutin po ang \"Gallery\".\n2. Pindutin po ang \"Send\".", result.spokenText)
        assertFalse(result.offerCallApo)
        assertTrue(engine.lastPrompt.contains(QUESTION))
        assertTrue(engine.lastPrompt.contains("\"Gallery\""))
    }

    @Test
    fun timeoutOffersToCallApoByName() = runTest {
        val slow = FakeEngine(delayMs = 30_000)
        slow.load()

        val result = GabayPipeline(slow).guide(QUESTION, SCREEN, apoName = "Junjun")

        assertEquals(GabaySource.TIMEOUT, result.source)
        assertTrue(result.offerCallApo)
        assertTrue(result.steps.isEmpty())
        assertEquals("Natatagalan po ako. Gusto n'yo po bang tawagan si Junjun?", result.spokenText)
    }

    @Test
    fun engineNotReadyOffersToCallApo() = runTest {
        for (state in listOf(EngineState.IDLE, EngineState.LOADING, EngineState.FAILED)) {
            val engine = StubEngine(initial = state)

            val result = GabayPipeline(engine).guide(QUESTION, SCREEN)

            assertEquals(GabaySource.UNAVAILABLE, result.source)
            assertTrue(result.offerCallApo)
            assertTrue(result.spokenText.endsWith("tawagan si Apo?"))
            assertEquals(0, engine.calls)
        }
    }

    @Test
    fun engineErrorOffersToCallApo() = runTest {
        val result = GabayPipeline(StubEngine { error("inference failed") }).guide(QUESTION, SCREEN)

        assertEquals(GabaySource.UNAVAILABLE, result.source)
        assertTrue(result.offerCallApo)
    }

    @Test
    fun replyWithNoUsableStepOffersToCallApo() = runTest {
        val hallucinated = GabayPipeline(StubEngine { "1. Pindutin po ang \"Attach\"." }).guide(QUESTION, SCREEN)
        assertEquals(GabaySource.UNAVAILABLE, hallucinated.source)

        val freeText = GabayPipeline(StubEngine { "Hindi ko po alam." }).guide(QUESTION, SCREEN)
        assertEquals(GabaySource.UNAVAILABLE, freeText.source)
        assertTrue(freeText.offerCallApo)
    }

    @Test
    fun promptStaysShortOnABusyScreen() {
        val busy = (1..30).map { "Napakahabang label ng button numero $it sa screen" }

        val labels = PromptBuilder.gabayLabels(busy)
        val prompt = PromptBuilder.buildGabayPrompt("a".repeat(2000), labels)

        assertTrue(labels.size <= PromptBuilder.MAX_GABAY_LABELS)
        assertTrue(labels.all { it.length <= PromptBuilder.MAX_LABEL_CHARS })
        assertTrue(labels.sumOf { it.length } <= PromptBuilder.MAX_GABAY_LABEL_CHARS)
        assertTrue("Prompt is ${prompt.length} chars", prompt.length < 600)
    }

    @Test
    fun duplicateAndBlankLabelsAreLeftOutOfThePrompt() {
        val labels = PromptBuilder.gabayLabels(listOf("Send", " ", "send", "Say \"hi\"", "Camera"))

        assertEquals(listOf("Send", "Say 'hi'", "Camera"), labels)
    }

    private companion object {
        const val QUESTION = "Paano mag-send ng picture?"
        val SCREEN = ScreenContext("com.facebook.orca", listOf("Camera", "Gallery", "Send"))

        const val GOOD_REPLY = "1. Pindutin po ang \"Gallery\".\n2. Pindutin po ang \"Send\"."
    }
}
