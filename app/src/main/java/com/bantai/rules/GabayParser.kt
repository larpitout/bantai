package com.bantai.rules

/**
 * Turns the model's Gabay reply into steps that are safe to show a senior.
 * The 1B model does not reliably follow the prompt, so the rules are enforced here:
 * at most [MAX_STEPS] steps, every quoted button must be on the screen, and every step says "po".
 */
object GabayParser {

    const val MAX_STEPS = 3

    private val STEP_LINE = Regex("^\\s*(?:\\d+\\s*[.):]|[-•])\\s*(.+)$")
    private val QUOTED = Regex("[\"“”]([^\"“”]+)[\"“”]")
    private val PO = Regex("\\bpo\\b", RegexOption.IGNORE_CASE)

    /**
     * Returns the usable steps, without their numbers. Empty when the reply has no numbered steps
     * or every step names a button that is not in [labels].
     */
    fun parse(raw: String, labels: List<String>): List<String> {
        val allowed = labels.map(::normalize).toSet()

        return raw.lines()
            .mapNotNull { STEP_LINE.find(it.replace("*", ""))?.groupValues?.get(1)?.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_STEPS)
            .filter { step -> QUOTED.findAll(step).all { normalize(it.groupValues[1]) in allowed } }
            .map(::polite)
    }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim().lowercase()

    private fun polite(step: String): String =
        if (PO.containsMatchIn(step)) step else step.trimEnd('.', '!', ' ') + " po."
}
