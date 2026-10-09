package com.bantai.rules

object PromptBuilder {

    /** Long notifications are cut so the prompt stays inside the pipeline's latency budget. */
    const val MAX_MESSAGE_CHARS = 160

    /**
     * Prompt para sa on-device model (Qwen3.5-2B o Gemma).
     * May isang buong halimbawa (mensahe → sagot) para gayahin ng model ang FORMAT: ang lumang prompt na may
     * "HATOL: SCAM or LIGTAS" ay kinopya ni Qwen nang literal sa Infinix (sagot: "SCAM or LIGTAS|DAHILAN: ...").
     * Dalawang linya lang ang hinihingi para mabilis (VERDICT + REASON); ang gagawin ay template ng app.
     */
    fun buildScamPrompt(message: String, signals: List<String> = emptyList(), language: String = "English"): String {
        val cleanMessage = message.replace('"', '\'').replace(Regex("\\s+"), " ").trim().take(MAX_MESSAGE_CHARS)
        val hint = if (signals.isEmpty()) "" else "Hints from a keyword filter: ${signals.joinToString(", ")}\n"
        return """You check text messages sent to an elderly person in the Philippines.
A scam asks for money, load, an OTP, PIN or account details, claims a new number, offers a prize, or has a suspicious link.
A message that only says money was already sent or paid, or normal family talk, is SAFE.

Message: "Lola bagong number ko to, padala ka 3k sa gcash ngayon na"
VERDICT: SCAM
REASON: Someone claims a new number and urgently asks for money.

Message: "$cleanMessage"
$hint
Reply with exactly two lines, VERDICT and REASON. Write the REASON in $language."""
    }
}
