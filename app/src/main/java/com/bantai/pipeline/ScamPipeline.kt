package com.bantai.pipeline

import com.bantai.model.RuleResult
import com.bantai.model.ScamVerdict
import com.bantai.model.Verdict
import com.bantai.rules.PromptBuilder
import com.bantai.rules.RuleFilter
import com.bantai.rules.ScamParser
import com.bantay.app.core.EngineState
import com.bantay.app.core.LlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

enum class VerdictSource { RULES, LLM }

/**
 * One step of a scam check. [isFinal] is false only for the instant rule warning
 * that is shown while the model is still writing its explanation.
 */
data class ScamCheck(
    val rule: RuleResult,
    val verdict: ScamVerdict,
    val source: VerdictSource,
    val isFinal: Boolean,
)

/**
 * Layer 1 rules + Layer 2 on-device model.
 *
 * - Score 0: safe from rules, the model is not called.
 * - Score 1: the model decides; falls back to the rule warning.
 * - Score 2+: rule warning is emitted at once, then the model's explanation. The model cannot downgrade it to safe.
 * - Saved contact asking for money (no link, OTP or prize): SUSPICIOUS instead of SCAM.
 *
 * Falls back to the rule verdict when the engine is not READY, the call times out, throws,
 * or the reply has no verdict line. The flow never throws to its collector.
 */
class ScamPipeline(
    private val engine: LlmEngine,
    /** Wika ng paliwanag ng AI ("English" o "Filipino"). */
    private val language: String = "English",
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /** Kapag true (tulad sa Check tab), pinapayagan ang AI na magpasya kahit may rule signals. */
    private val allowAiDowngrade: Boolean = false,
) {

    fun check(message: String, sender: String = "", senderIsContact: Boolean = false): Flow<ScamCheck> = flow {
        val rule = RuleFilter.score(message, sender)
        val contactMoney = senderIsContact && RuleFilter.isContactMoneyRequest(rule)
        // Score 2+ mula sa kakilala: SUSPICIOUS anuman ang sabihin ng model, kaya hindi na ito tinatawag.
        if (contactMoney && rule.score >= 2 && !allowAiDowngrade) {
            emit(ScamCheck(rule, SUSPICIOUS_VERDICT, VerdictSource.RULES, isFinal = true))
            return@flow
        }
        if (rule.score == 0 && !allowAiDowngrade) {
            emit(ScamCheck(rule, SAFE_VERDICT, VerdictSource.RULES, isFinal = true))
            return@flow
        }

        // Walang rule signal: ligtas ang fallback kapag hindi sumagot ang model.
        val ruleVerdict = if (rule.score == 0) SAFE_VERDICT else RuleFilter.createInstantWarning(rule)
        val confirmedByRules = rule.score >= 2 && !allowAiDowngrade
        if (confirmedByRules) {
            emit(ScamCheck(rule, ruleVerdict, VerdictSource.RULES, isFinal = false))
        }

        val llmVerdict = askModel(message, rule)
        val final = if (llmVerdict == null || (confirmedByRules && !llmVerdict.isScam)) {
            ScamCheck(rule, ruleVerdict, VerdictSource.RULES, isFinal = true)
        } else {
            ScamCheck(rule, llmVerdict, VerdictSource.LLM, isFinal = true)
        }
        emit(if (contactMoney && final.verdict.isScam) final.copy(verdict = SUSPICIOUS_VERDICT) else final)
    }

    /** Returns null when the model gave no usable verdict. */
    private suspend fun askModel(message: String, rule: RuleResult): ScamVerdict? {
        if (engine.state.value != EngineState.READY) return null

        val raw = try {
            withTimeoutOrNull(timeoutMs) {
                engine.generate(PromptBuilder.buildScamPrompt(message, rule.signals, language))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        } ?: return null

        if (!VERDICT_LINE.containsMatchIn(raw)) return null
        return ScamParser.parse(raw)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 25_000L

        private val VERDICT_LINE = Regex("^\\s*(HATOL|VERDICT)\\s*:", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        val SAFE_VERDICT = ScamVerdict(
            isScam = false,
            reason = "Mukhang ligtas po at karaniwang mensahe lamang ito.",
            action = "Wala pong kailangang gawin.",
        )

        // English tulad ng createInstantWarning; ang babala ay gumagamit ng naka-localize na string.
        val SUSPICIOUS_VERDICT = ScamVerdict(
            isScam = false,
            reason = "Your contact is asking for money. Their account may have been hacked.",
            action = "Call them directly on the phone before sending anything.",
            level = Verdict.SUSPICIOUS,
        )
    }
}
