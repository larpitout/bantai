package com.bantai.rules

object PromptBuilder {

    /** Long notifications are cut so the prompt stays inside the pipeline's latency budget. */
    const val MAX_MESSAGE_CHARS = 160

    /**
     * Builds the single-string scam prompt for the on-device model.
     * Every token counts: the Infinix X6835B prefills about 12 tokens per second, so a ~225-token
     * prompt spent ~20 s before the first output token (A6). English instructions tokenize shorter
     * than Tagalog; the reply is still asked for in Tagalog, in the format [ScamParser] reads.
     * The message comes first and the format last, with no yes/no question: asked "Is this a scam?"
     * up front, the model answered "Yes, ..." in free text and ignored the format (A6).
     */
    fun buildScamPrompt(message: String, signals: List<String> = emptyList()): String {
        val cleanMessage = message.replace('"', '\'').replace(Regex("\\s+"), " ").trim().take(MAX_MESSAGE_CHARS)
        val hint = if (signals.isEmpty()) "" else "Possible signs: ${signals.joinToString(", ")}\n"

        return """Text: "$cleanMessage"
$hint
Classify the text. Reply in Tagalog with only these 3 lines:
HATOL: SCAM or LIGTAS
DAHILAN: one short sentence
GAWIN: one short tip"""
    }
}
