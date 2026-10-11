package com.bantai.rules

import com.bantai.model.ScamVerdict
import com.bantai.model.Verdict
import java.util.Locale

object ScamParser {

    /**
     * Parses the LLM's raw text response into a structured ScamVerdict.
     * Expected format:
     * VERDICT: SCAM (or SUSPICIOUS / SAFE)
     * REASON: <Explanation>
     * ACTION: <Recommended action>
     */
    fun parse(rawOutput: String, fallbackVerdict: ScamVerdict? = null): ScamVerdict {
        val lines = rawOutput.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var verdictStr: String? = null
        var reason: String? = null
        var action: String? = null

        for (line in lines) {
            val upper = line.uppercase(Locale.ROOT)
            when {
                upper.startsWith("VERDICT:") || upper.startsWith("HATOL:") -> {
                    verdictStr = line.substringAfter(":").trim()
                }
                upper.startsWith("REASON:") || upper.startsWith("DAHILAN:") -> {
                    reason = line.substringAfter(":").trim()
                }
                upper.startsWith("ACTION:") || upper.startsWith("GAWIN:") -> {
                    action = line.substringAfter(":").trim()
                }
                upper.startsWith("SCAM:") -> {
                    verdictStr = "SCAM"
                    reason = line.substringAfter(":").trim()
                }
                upper == "SAFE" || upper == "LIGTAS" -> {
                    verdictStr = "SAFE"
                }
            }
        }

        if (verdictStr != null) {
            val v = verdictStr.uppercase(Locale.ROOT).trim()
            val level = when {
                v.startsWith("NOT") || v.contains("NOT SCAM") || v.contains("NOT A SCAM") || v.contains("NO SCAM") -> Verdict.SAFE
                v.contains("SUSPICIOUS") || v.contains("KAHINA-HINALA") || v.contains("MAG-INGAT") || v.contains("MAGINGAT") -> Verdict.SUSPICIOUS
                v.contains("SAFE") || v.contains("LIGTAS") || v.contains("VALID") || v.contains("LEGIT") -> Verdict.SAFE
                v.contains("SCAM") || v.contains("PHISHING") || v.contains("FRAUD") || v.contains("MAPANGANIB") -> Verdict.SCAM
                else -> Verdict.SAFE
            }
            val finalLevel = if (level != Verdict.SAFE && action != null && (action.contains("no action", ignoreCase = true) || action.contains("wala", ignoreCase = true))) {
                Verdict.SAFE
            } else {
                level
            }
            return ScamVerdict(
                isScam = finalLevel == Verdict.SCAM,
                reason = reason ?: when (finalLevel) {
                    Verdict.SCAM -> "This message appears suspicious."
                    Verdict.SUSPICIOUS -> "This message needs verification."
                    Verdict.SAFE -> "This message appears safe."
                },
                action = action ?: when (finalLevel) {
                    Verdict.SCAM -> "Do not send money or click links. Verify with family."
                    Verdict.SUSPICIOUS -> "Verify with the sender or family before replying."
                    Verdict.SAFE -> "No action required."
                },
                level = finalLevel
            )
        }

        // Fallback heuristic if formatting was loose:
        val upperRaw = rawOutput.uppercase(Locale.ROOT)
        val hasNegative = upperRaw.contains("NOT SCAM") || upperRaw.contains("NOT A SCAM") || upperRaw.contains("NO SCAM") ||
                upperRaw.contains("SAFE") || upperRaw.contains("VALID") || upperRaw.contains("LIGTAS") || upperRaw.contains("LEGIT")
        val detectedScam = !hasNegative && (upperRaw.contains("SCAM") ||
                upperRaw.contains("PHISHING") ||
                upperRaw.contains("DO NOT SEND"))
        val detectedSuspicious = !hasNegative && !detectedScam && (upperRaw.contains("SUSPICIOUS") ||
                upperRaw.contains("KAHINA-HINALA") ||
                upperRaw.contains("MAG-INGAT") || upperRaw.contains("MAGINGAT"))
        val level = if (detectedScam) Verdict.SCAM else if (detectedSuspicious) Verdict.SUSPICIOUS else Verdict.SAFE

        if (fallbackVerdict != null) {
            return fallbackVerdict.copy(
                reason = if (rawOutput.isNotBlank() && detectedScam) rawOutput.take(150) else fallbackVerdict.reason
            )
        }

        return ScamVerdict(
            isScam = detectedScam,
            reason = if (level != Verdict.SAFE) "This message appears suspicious." else "This message appears safe.",
            action = if (level != Verdict.SAFE) "Verify with the sender or family before replying." else "No action required.",
            level = level
        )
    }
}
