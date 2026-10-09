package com.bantai.rules

import com.bantai.model.RuleResult
import com.bantai.model.ScamVerdict
import java.util.Locale
import java.util.regex.Pattern

object RuleFilter {

    private val MONEY_PATTERNS = listOf(
        "padala", "send", "gcash", "maya", "paymaya", "pautang",
        "utang", "load", "bayad", "transfer", "pera", "deposit",
        "money", "cash", "bank"
    )

    private val NEW_NUMBER_PATTERNS = listOf(
        "bagong number", "new number", "bago kong number", "palit number"
    )

    private val NEW_NUMBER_REGEX = Pattern.compile(
        "\\b(?:si|ako si|eto si)\\s+[a-zA-Z]+\\s+(?:to|'to|ito)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val URL_REGEX = Pattern.compile(
        "(https?://\\S+|www\\.\\S+|bit\\.ly/\\S+|tinyurl\\.com/\\S+|[a-zA-Z0-9.-]+\\.(?:com|ph|net|org|xyz|top|link|site|info)(?:/\\S*)?)",
        Pattern.CASE_INSENSITIVE
    )

    private val PRIZE_PATTERNS = listOf(
        "nanalo", "congratulations", "congrats", "claim", "premyo",
        "panalo", "raffle", "biyaya", "jackpot", "grand prize", "winner", "prize"
    )

    private val ACCOUNT_PARCEL_PATTERNS = listOf(
        "otp", "pin", "verify", "verification", "na-hold", "nahold",
        "suspended", "deactivate", "blocked", "parcel", "delivery", "package"
    )

    private val URGENCY_PATTERNS = listOf(
        "ngayon na", "urgent", "emergency", "naaksidente", "ospital",
        "hospital", "asap", "agad", "oras lang", "bago mag", "now"
    )

    fun score(text: String, sender: String = ""): RuleResult {
        val lowerText = text.lowercase(Locale.ROOT)
        val signals = mutableListOf<String>()

        // 1. Money Request
        if (MONEY_PATTERNS.any { lowerText.contains(it) }) {
            signals.add("Money Request")
        }

        // 2. New Number / Impersonation
        val hasNewNumberText = NEW_NUMBER_PATTERNS.any { lowerText.contains(it) } ||
                NEW_NUMBER_REGEX.matcher(lowerText).find()
        if (hasNewNumberText) {
            signals.add("New Number / Impersonation")
        }

        // 3. Suspicious Link
        if (URL_REGEX.matcher(lowerText).find()) {
            signals.add("Suspicious Link")
        }

        // 4. Prize / Raffle
        if (PRIZE_PATTERNS.any { lowerText.contains(it) }) {
            signals.add("Prize / Raffle")
        }

        // 5. Account, OTP, or Parcel
        if (ACCOUNT_PARCEL_PATTERNS.any { lowerText.contains(it) }) {
            signals.add("Account / OTP / Parcel")
        }

        // 6. Urgency / Emergency
        if (URGENCY_PATTERNS.any { lowerText.contains(it) }) {
            signals.add("Urgency / Emergency")
        }

        return RuleResult(
            score = signals.size,
            signals = signals
        )
    }

    /**
     * Creates an immediate generic warning based purely on rule signals (for Score >= 2)
     * while the on-device LLM is asynchronously generating a detailed explanation.
     */
    fun createInstantWarning(ruleResult: RuleResult): ScamVerdict {
        val reason = when {
            ruleResult.signals.contains("New Number / Impersonation") &&
                    ruleResult.signals.contains("Money Request") ->
                "Someone appears to be impersonating a relative asking for money from an unfamiliar number."

            ruleResult.signals.contains("Account / OTP / Parcel") &&
                    ruleResult.signals.contains("Suspicious Link") ->
                "Suspicious link detected requesting sensitive account information or an OTP."

            ruleResult.signals.contains("Prize / Raffle") ->
                "Unsolicited prize or lottery claim detected, which is commonly a scam."

            ruleResult.signals.contains("Suspicious Link") ->
                "Message contains an unverified external link. Do not tap or open it."

            else ->
                "This message contains multiple common scam indicators."
        }

        val action = when {
            ruleResult.signals.contains("New Number / Impersonation") ->
                "Do not send money. Contact the person directly using their existing verified contact number."

            ruleResult.signals.contains("Account / OTP / Parcel") ->
                "Never share your OTP or PIN, and do not click the link."

            ruleResult.signals.contains("Suspicious Link") ->
                "Do not click the link or provide any personal details."

            else ->
                "Do not send money or sensitive personal information. Verify with a family member first."
        }

        return ScamVerdict(
            isScam = true,
            reason = reason,
            action = action
        )
    }
}
