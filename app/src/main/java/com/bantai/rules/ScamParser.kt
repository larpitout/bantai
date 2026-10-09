package com.bantai.rules

import com.bantai.model.ScamVerdict
import java.util.Locale

object ScamParser {

    /**
     * Parses the LLM's raw text response into a structured ScamVerdict.
     * Expected format:
     * VERDICT: SCAM (or SAFE)
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
            val isScam = verdictStr.uppercase(Locale.ROOT).contains("SCAM")
            return ScamVerdict(
                isScam = isScam,
                reason = reason ?: if (isScam) "This message appears suspicious." else "This message appears safe.",
                action = action ?: if (isScam) "Do not send money or click links. Verify with family." else "No action required."
            )
        }

        // Fallback heuristic if formatting was loose:
        val upperRaw = rawOutput.uppercase(Locale.ROOT)
        val detectedScam = upperRaw.contains("SCAM") ||
                upperRaw.contains("SUSPICIOUS") ||
                upperRaw.contains("PHISHING") ||
                upperRaw.contains("DO NOT SEND")

        if (fallbackVerdict != null) {
            return fallbackVerdict.copy(
                reason = if (rawOutput.isNotBlank() && detectedScam) rawOutput.take(150) else fallbackVerdict.reason
            )
        }

        return ScamVerdict(
            isScam = detectedScam,
            reason = if (detectedScam) "This message appears suspicious." else "This message appears safe.",
            action = if (detectedScam) "Do not send money or click links." else "No action required."
        )
    }
}
