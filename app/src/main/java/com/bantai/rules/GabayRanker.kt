package com.bantai.rules

/**
 * Inuuna ang mga button na may kinalaman sa tanong ni Nanay bago ipakita kay Gemma.
 * Kailangan ito dahil [PromptBuilder.gabayLabels] ay hanggang 12 lang: sa Home screen, ang "Phone"
 * ay pang-15 mula itaas kaya hindi ito nakikita ni Gemma kahit iyon ang tamang sagot.
 * Si Gemma pa rin ang pumipili; inaayos lang ang pagkakasunod.
 */
object GabayRanker {

    // Taglish/English na salita sa tanong → salitang madalas nasa pangalan ng button.
    // ponytail: maliit na listahan para sa demo; dagdagan habang may nakikitang miss sa totoong gamit.
    private val SYNONYMS = mapOf(
        "tawag" to listOf("phone", "call", "dial"),
        "tawagan" to listOf("phone", "call", "dial"),
        "call" to listOf("phone", "call", "dial"),
        "video" to listOf("video"),
        "mensahe" to listOf("message", "messages", "messenger", "chat", "sms"),
        "message" to listOf("message", "messages", "messenger", "chat", "sms"),
        "text" to listOf("message", "messages", "sms"),
        "chat" to listOf("chat", "message", "messenger"),
        "litrato" to listOf("camera", "gallery", "photo", "picture"),
        "picture" to listOf("camera", "gallery", "photo", "picture", "image"),
        "photo" to listOf("camera", "gallery", "photo", "picture", "image"),
        "camera" to listOf("camera"),
        "kamera" to listOf("camera"),
        "hanap" to listOf("search", "find"),
        "hanapin" to listOf("search", "find"),
        "search" to listOf("search", "find"),
        "settings" to listOf("settings", "menu"),
        "bumalik" to listOf("back", "navigate up"),
        "back" to listOf("back", "navigate up"),
    )

    private val WORD = Regex("[a-z0-9]+")

    /**
     * Kapag may button na tumutugma sa tanong, iyon lang (hanggang [MAX_CANDIDATES]) ang ibibigay kay Gemma:
     * sa 26 na button sa Home screen, "Weather" ang pinili niya para sa "Call my grandson".
     * Kapag walang tumugma, lahat ng button (ayos mula itaas pababa).
     */
    fun candidates(question: String, labels: List<String>): List<String> {
        val words = WORD.findAll(question.lowercase()).map { it.value }.filter { it.length > 2 }.toSet()
        val wanted = words + words.flatMap { SYNONYMS[it].orEmpty() }
        val scored = labels.map { label ->
            val labelWords = WORD.findAll(label.lowercase()).map { it.value }.toList()
            val hits = labelWords.count { it in wanted }
            // "Phone" (1/1) bago "Phone Master" (1/2): mas eksaktong tugma ang nauuna.
            label to if (labelWords.isEmpty()) 0.0 else hits.toDouble() / labelWords.size
        }
        val matched = scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        return if (matched.isEmpty()) labels else matched.take(MAX_CANDIDATES)
    }

    const val MAX_CANDIDATES = 5
}
