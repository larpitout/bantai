package com.bantai.pipeline

import com.bantai.model.RuleResult
import com.bantai.model.ScamVerdict
import com.bantai.model.Verdict
import com.bantai.rules.PromptBuilder
import com.bantai.rules.RuleFilter
import com.bantai.rules.SenderClassifier
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
 * Layer 1 rules + Layer 2 on-device model — the rules advise, the AI judges.
 *
 * - Score 0: safe from rules, the model is not called (the Check tab still asks it).
 * - Hard signals (dangerous link, or new-number + money request) from a sender that is
 *   not a saved contact and not an official sender ID/short code: the rule warning is
 *   emitted at once, then the model explains. It cannot downgrade to SAFE, only to SUSPICIOUS.
 * - Any other message with signals: the model decides SCAM, SUSPICIOUS or SAFE,
 *   with the sender type (official ID, number, contact) as context.
 * - Saved contact asking for money (no link, OTP or prize): SUSPICIOUS instead of SCAM.
 *
 * No usable model verdict (not READY, timeout, error, or no verdict line): a message with
 * signals gets a cautious SUSPICIOUS; the hard-signal path keeps its rule warning.
 * The flow never throws to its collector.
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
        val senderInfo = SenderClassifier.classify(sender, senderIsContact)
        val contactMoney = senderIsContact && RuleFilter.isContactMoneyRequest(rule)

        // Score 0: ligtas ayon sa rules; hindi na tinatawag ang model (sa Check tab, tinatanong pa rin).
        if (rule.score == 0 && !allowAiDowngrade) {
            emit(ScamCheck(rule, SAFE_VERDICT, VerdictSource.RULES, isFinal = true))
            return@flow
        }
        // Score 2+ mula sa kakilala: SUSPICIOUS anuman ang sabihin ng model, kaya hindi na ito tinatawag.
        if (contactMoney && rule.score >= 2 && !allowAiDowngrade) {
            emit(ScamCheck(rule, SUSPICIOUS_VERDICT, VerdictSource.RULES, isFinal = true))
            return@flow
        }

        // Hard signal mula sa hindi kakilala at hindi opisyal na sender: babala agad habang nag-iisip ang AI.
        val hardSignal = "Dangerous Link" in rule.signals ||
            ("New Number / Impersonation" in rule.signals && "Money Request" in rule.signals)
        val trustedSender = senderInfo.type in TRUSTED_SENDER_TYPES
        val confirmedByRules = hardSignal && !trustedSender && !allowAiDowngrade
        val ruleVerdict = RuleFilter.createInstantWarning(rule)
        if (confirmedByRules) {
            emit(ScamCheck(rule, ruleVerdict, VerdictSource.RULES, isFinal = false))
        }

        // Pass 1 (Option A): hatol lang (~5 tokens). Mabilis ang babala kaysa sa VERDICT+REASON.
        val scamByModel = askVerdict(message, senderInfo.promptLine(), rule)
        val final = when {
            // Walang nagamit na hatol ang AI: hard signal → panatilihin ang babala;
            // may senyales → maingat na SUSPICIOUS; wala → ligtas na lang.
            scamByModel == null -> ScamCheck(
                rule,
                if (confirmedByRules) ruleVerdict
                else if (rule.signals.isNotEmpty()) UNCERTAIN_VERDICT
                else SAFE_VERDICT,
                VerdictSource.RULES,
                isFinal = true,
            )
            // Sa hard-signal path hindi maaaring ibaba ng AI sa SAFE.
            confirmedByRules && !scamByModel ->
                ScamCheck(rule, ruleVerdict, VerdictSource.RULES, isFinal = true)
            !scamByModel -> ScamCheck(rule, SAFE_BY_MODEL, VerdictSource.LLM, isFinal = true)
            else -> {
                // SCAM: babala agad gamit ang template na paliwanag, saka ang saliwanag ng AI (pass 2).
                // Kakilalang humihingi ng pera: SUSPICIOUS agad, hindi SCAM, kahit sa mabilis na babala.
                val quick = if (contactMoney) SUSPICIOUS_VERDICT
                    else ScamVerdict(isScam = true, reason = ruleVerdict.reason, action = ruleVerdict.action)
                emit(ScamCheck(rule, quick, VerdictSource.LLM, isFinal = false))
                val reason = askReason(message, senderInfo.promptLine())
                ScamCheck(rule, quick.copy(reason = reason ?: quick.reason), VerdictSource.LLM, isFinal = true)
            }
        }
        emit(if (contactMoney && final.verdict.isScam) final.copy(verdict = SUSPICIOUS_VERDICT) else final)
    }

    /** Pass 1: SCAM/SAFE lang. Null kapag walang nagamit na hatol ang model. */
    private suspend fun askVerdict(message: String, senderLine: String, rule: RuleResult): Boolean? {
        if (engine.state.value != EngineState.READY) return null

        val raw = generate(PromptBuilder.buildVerdictPrompt(message, senderLine, rule.signals)) ?: return null

        val field = VERDICT_LINE.find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
        val scam = VERDICT_WORDS_SCAM.any { field.contains(it, ignoreCase = true) }
        val safe = VERDICT_WORDS_SAFE.any { field.contains(it, ignoreCase = true) }
        return if (scam == safe) null else scam
    }

    /** Pass 2: maikling paliwanag para sa mensaheng SCAM na ang hatol. Null kapag wala. */
    private suspend fun askReason(message: String, senderLine: String): String? {
        if (engine.state.value != EngineState.READY) return null
        val raw = generate(PromptBuilder.buildReasonPrompt(message, senderLine, language)) ?: return null
        return REASON_LINE.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun generate(prompt: String): String? = try {
        withTimeoutOrNull(timeoutMs) { engine.generate(prompt) }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L

        private val VERDICT_LINE = Regex("^\\s*(?:HATOL|VERDICT)\\s*:(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val REASON_LINE = Regex("^\\s*(?:REASON|DAHILAN)\\s*:(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val VERDICT_WORDS_SCAM = listOf("SCAM", "PHISHING", "FRAUD")
        private val VERDICT_WORDS_SAFE = listOf("SAFE", "LIGTAS", "LEGIT")

        // Sa mga sender na ito hindi nagba-babala agad ang rules; ang AI (na may sender context) ang huhusga.
        private val TRUSTED_SENDER_TYPES = setOf(
            SenderClassifier.Type.SAVED_CONTACT,
            SenderClassifier.Type.OFFICIAL_ID,
            SenderClassifier.Type.SHORTCODE,
        )

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

        /** Hatol ng model: ligtas (binary lang ang hinihingi; walang reason). */
        private val SAFE_BY_MODEL = ScamVerdict(
            isScam = false,
            reason = "Looks like an ordinary message.",
            action = "Nothing to do.",
        )

        // May senyales pero walang hatol ang AI: mas ligtas ang mag-ingat. Naka-localize sa
        // warning_uncertain_reason/action ng listener; English dito tulad ng iba pang verdict.
        val UNCERTAIN_VERDICT = ScamVerdict(
            isScam = false,
            reason = "Bantai couldn't fully check this message, but it has signs that need care.",
            action = "Verify with the sender or family before replying or sending anything.",
            level = Verdict.SUSPICIOUS,
        )
    }
}
