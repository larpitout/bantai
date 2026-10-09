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

    const val MAX_QUESTION_CHARS = 100
    const val MAX_GABAY_LABELS = 12
    const val MAX_LABEL_CHARS = 24
    const val MAX_GABAY_LABEL_CHARS = 160

    /**
     * The screen labels that go into the Gabay prompt. The reader allows 600 characters of labels,
     * which alone is ~15 s of prefill on the Infinix, so only the first few short ones are kept.
     * The reply is validated against this same list: the model is only held to labels it was shown.
     */
    fun gabayLabels(labels: List<String>): List<String> {
        val kept = mutableListOf<String>()
        var total = 0
        for (label in labels) {
            val clean = label.replace(Regex("[\"\u201C\u201D,]"), " ").replace(Regex("\\s+"), " ").trim()
                .take(MAX_LABEL_CHARS).trim()
            if (clean.isEmpty() || kept.any { it.equals(clean, ignoreCase = true) }) continue
            if (kept.size == MAX_GABAY_LABELS || total + clean.length > MAX_GABAY_LABEL_CHARS) break
            kept += clean
            total += clean.length
        }
        return kept
    }

    /**
     * Builds the Gabay prompt from the senior's question and the labels of [gabayLabels].
     * The model only picks which buttons to tap; the Tagalog sentences are written in code.
     * Asked for full Tagalog steps, the Infinix timed out at 20 s on 3 of 4 screens and the one
     * answer took 18.5 s (A7), so the reply is kept to a button name instead of Tagalog sentences.
     * Only the next button is asked for: told "at most 3", the model always named 3, and the
     * second and third were padding ("Video call" and "Message" after "Voice call").
     */
    fun buildGabayPrompt(question: String, labels: List<String>): String {
        val cleanQuestion = question.replace('"', '\'').replace(Regex("\\s+"), " ").trim().take(MAX_QUESTION_CHARS)

        return """Buttons: ${labels.joinToString(", ")}
Question: "$cleanQuestion"
Which one button should be tapped next? Reply with only its name from the list."""
    }
}
