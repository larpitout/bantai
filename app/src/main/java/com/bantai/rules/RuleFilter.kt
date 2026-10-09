package com.bantai.rules

import com.bantai.model.RuleResult
import com.bantai.R
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
        if (MONEY_PATTERNS.any { lowerText.hasWord(it) }) {
            signals.add("Money Request")
        }

        // 2. New Number / Impersonation
        val hasNewNumberText = NEW_NUMBER_PATTERNS.any { lowerText.hasWord(it) } ||
                NEW_NUMBER_REGEX.matcher(lowerText).find()
        if (hasNewNumberText) {
            signals.add("New Number / Impersonation")
        }

        // 3. Suspicious Link
        if (URL_REGEX.matcher(lowerText).find()) {
            signals.add("Suspicious Link")
        }

        // 4. Prize / Raffle
        if (PRIZE_PATTERNS.any { lowerText.hasWord(it) }) {
            signals.add("Prize / Raffle")
        }

        // 5. Account, OTP, or Parcel
        if (ACCOUNT_PARCEL_PATTERNS.any { lowerText.hasWord(it) }) {
            signals.add("Account / OTP / Parcel")
        }

        // 6. Urgency / Emergency
        if (URGENCY_PATTERNS.any { lowerText.hasWord(it) }) {
            signals.add("Urgency / Emergency")
        }

        return RuleResult(
            score = signals.size,
            signals = signals
        )
    }

    /**
     * Babala base lang sa rule signals, bilang (reason, action) string resource ids,
     * para naka-localize (values / values-tl) at hindi na kailangan ng LLM.
     */
    fun instantWarningRes(ruleResult: RuleResult): Pair<Int, Int> {
        val s = ruleResult.signals
        return when {
            "New Number / Impersonation" in s && "Money Request" in s ->
                R.string.warning_impersonation_reason to R.string.warning_impersonation_action
            "Account / OTP / Parcel" in s && "Suspicious Link" in s ->
                R.string.warning_account_otp_reason to R.string.warning_account_otp_action
            "Prize / Raffle" in s ->
                R.string.warning_prize_reason to R.string.warning_prize_action
            "Suspicious Link" in s ->
                R.string.warning_link_reason to R.string.warning_link_action
            "New Number / Impersonation" in s ->
                R.string.warning_impersonation_reason to R.string.warning_impersonation_action
            "Account / OTP / Parcel" in s ->
                R.string.warning_account_otp_reason to R.string.warning_account_otp_action
            else ->
                R.string.warning_generic_reason to R.string.warning_generic_action
        }
    }
}

// Buong salita lang, para hindi tumama ang "maya" sa "mamaya" o ang "pin" sa "pinsan".
private fun String.hasWord(word: String) =
    Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(this)
