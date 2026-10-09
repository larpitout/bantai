package com.bantai.pipeline

import com.bantai.model.ScreenContext
import com.bantai.rules.GabayParser
import com.bantai.rules.PromptBuilder
import com.bantay.app.core.EngineState
import com.bantay.app.core.LlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class GabaySource { LLM, NO_SCREEN, TIMEOUT, UNAVAILABLE }

/**
 * The answer to one Gabay question. [steps] is empty for every source except [GabaySource.LLM];
 * [spokenText] is what the panel shows and the Speaker reads in all cases.
 */
data class GabayResult(
    val steps: List<String>,
    val spokenText: String,
    val source: GabaySource,
    val offerCallApo: Boolean,
)

/**
 * Screen labels + question -> at most 3 polite steps from the on-device model.
 *
 * - No labels (e.g. a banking app that blocks screen reading): fixed reply, the model is not called.
 * - No reply within the timeout: offers to call Apo.
 * - Engine not READY, inference error, or no step that survives [GabayParser]: offers to call Apo.
 *
 * Never throws to its caller, except for cancellation.
 */
class GabayPipeline(
    private val engine: LlmEngine,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    suspend fun guide(question: String, screen: ScreenContext, apoName: String = ""): GabayResult {
        val labels = PromptBuilder.gabayLabels(screen.labels)
        if (labels.isEmpty()) return fallback(GabaySource.NO_SCREEN, NO_SCREEN_TEXT)
        if (engine.state.value != EngineState.READY) return unavailable(apoName)

        val raw = try {
            withTimeoutOrNull(timeoutMs) {
                engine.generate(PromptBuilder.buildGabayPrompt(question, labels))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return unavailable(apoName)
        } ?: return fallback(GabaySource.TIMEOUT, "$TIMEOUT_TEXT ${callApoOffer(apoName)}")

        val steps = GabayParser.parse(raw, labels)
        if (steps.isEmpty()) return unavailable(apoName)

        val spoken = steps.withIndex().joinToString("\n") { (i, step) -> "${i + 1}. $step" }
        return GabayResult(steps, spoken, GabaySource.LLM, offerCallApo = false)
    }

    private fun unavailable(apoName: String) =
        fallback(GabaySource.UNAVAILABLE, "$UNAVAILABLE_TEXT ${callApoOffer(apoName)}")

    private fun fallback(source: GabaySource, text: String) =
        GabayResult(emptyList(), text, source, offerCallApo = true)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L

        const val NO_SCREEN_TEXT = "Hindi ko po makita ang screen na ito."
        const val TIMEOUT_TEXT = "Natatagalan po ako."
        const val UNAVAILABLE_TEXT = "Hindi ko po kayo matulungan ngayon."

        fun callApoOffer(apoName: String): String =
            "Gusto n'yo po bang tawagan si ${apoName.trim().ifEmpty { "Apo" }}?"
    }
}
