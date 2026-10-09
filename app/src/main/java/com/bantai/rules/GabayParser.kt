package com.bantai.rules

/**
 * Reads the model's Gabay reply: the names of the buttons to tap, in order.
 * The 1B model does not reliably follow the prompt, so the rules are enforced here:
 * at most [MAX_STEPS] buttons, and only buttons that are on the screen.
 */
object GabayParser {

    const val MAX_STEPS = 3

    private val NUMBERING = Regex("^\\s*(?:\\d+\\s*[.):]|[-•])\\s*")
    private val EDGE_NOISE = charArrayOf('"', '\'', '“', '”', '.', '!', ':', ' ')

    /**
     * Returns the buttons to tap, spelled as in [labels]. Names that are not in [labels] are dropped;
     * empty when nothing in the reply names a button on the screen.
     */
    fun parse(raw: String, labels: List<String>): List<String> {
        val byName = labels.associateBy(::normalize)

        return raw.split('\n', ',')
            .mapNotNull { part ->
                val name = normalize(part.replace("*", "").replace(NUMBERING, "").trim(*EDGE_NOISE))
                // "Tap Voice call" still names a button: fall back to the longest label inside the line.
                byName[name] ?: byName.filterKeys { it in name }.maxByOrNull { it.key.length }?.value
            }
            .distinct()
            .take(MAX_STEPS)
    }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim().lowercase()
}
