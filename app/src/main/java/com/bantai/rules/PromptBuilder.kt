package com.bantai.rules

object PromptBuilder {

    /** Long notifications are cut so the prompt stays inside the pipeline's latency budget. */
    const val MAX_MESSAGE_CHARS = 160

    /**
     * Prompt para sa on-device model (Gemma 3 1B): maikling paliwanag + dalawang few-shot
     * halimbawa (SCAM / SAFE) — sinukat sa eval: 27/30 ang binary SCAM/SAFE; kapag inalok
     * ang SUSPICIOUS bilang verdict, tinatamnan ito ng model (25/29 naging SUSPICIOUS).
     * Ang SUSPICIOUS ay hatol ng app layer (kakilalang humihingi ng pera, cautious fallback),
     * hindi hinihingi sa model.
     * May sender line mula sa [com.bantai.rules.SenderClassifier] at signals mula sa [RuleFilter].
     */
    fun buildScamPrompt(message: String, senderLine: String, signals: List<String> = emptyList(), language: String = "English"): String {
        val cleanMessage = message.replace('"', '\'').replace(Regex("\\s+"), " ").trim().take(MAX_MESSAGE_CHARS)
        val hints = if (signals.isEmpty()) "" else "Signals: ${signals.joinToString(", ")}\n"
        return """You check text messages sent to an elderly person in the Philippines.
A scam asks for money, load, an OTP, PIN or account details, claims a new number, offers a prize, or has a suspicious link.
Official sender IDs can be spoofed: a dangerous link or payment request still warns. Normal family talk, promos and receipts are SAFE.

Sender: "+639171234567" (mobile number, not in contacts)
Message: "Lola bagong number ko to, padala ka 3k sa gcash ngayon na"
VERDICT: SCAM
REASON: Someone claims a new number and urgently asks for money.

Sender: "GCASH" (official sender ID: GCash)
Message: "You received P500.00 from Juan D. Your new balance is P1,250.45. Ref. No. 601234567890."
VERDICT: SAFE
REASON: An official transaction receipt; nothing is being asked.

$senderLine
${hints}Message: "$cleanMessage"
Reply with exactly two lines, VERDICT and REASON. Write the REASON in $language."""
    }

    /**
     * Pass 1 (Option A): hatol lang — isang linya (~5 tokens), ~2-3 s sa Infinix X6811
     * kaysa sa ~8-9 s ng VERDICT+REASON. Ang babala ay lumalabas agad; ang paliwanag
     * ay hinihingi sa [buildReasonPrompt].
     */
    fun buildVerdictPrompt(message: String, senderLine: String, signals: List<String> = emptyList()): String {
        val cleanMessage = message.replace('"', '\'').replace(Regex("\\s+"), " ").trim().take(MAX_MESSAGE_CHARS)
        val hints = if (signals.isEmpty()) "" else "Signals: ${signals.joinToString(", ")}\n"
        return """You check text messages sent to an elderly person in the Philippines.
A scam asks for money, load, an OTP, PIN or account details, claims a new number, offers a prize, or has a suspicious link.
Official sender IDs can be spoofed. Normal family talk, promos and receipts are SAFE.

Sender: "+639171234567" (mobile number, not in contacts)
Message: "Lola bagong number ko to, padala ka 3k sa gcash ngayon na"
VERDICT: SCAM

Sender: "GCASH" (official sender ID: GCash)
Message: "You received P500.00 from Juan D. Your new balance is P1,250.45. Ref. No. 601234567890."
VERDICT: SAFE

$senderLine
${hints}Message: "$cleanMessage"
Reply with one line only: VERDICT: SCAM or VERDICT: SAFE"""
    }

    /**
     * Pass 2 (Option A): tinawag lang kapag SCAM ang pass 1. Isang maikling paliwanag
     * (~15 tokens) para sa babala; lumabas na ang babala habang naghihintay nito.
     */
    fun buildReasonPrompt(message: String, senderLine: String, language: String = "English"): String {
        val cleanMessage = message.replace('"', '\'').replace(Regex("\\s+"), " ").trim().take(MAX_MESSAGE_CHARS)
        return """This message sent to an elderly Filipino was judged a scam. Explain why in one short $language sentence.
$senderLine
Message: "$cleanMessage"
Reply with one line only: REASON: <sentence>"""
    }
}
