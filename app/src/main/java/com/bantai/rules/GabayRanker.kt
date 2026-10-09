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

    fun rank(question: String, labels: List<String>): List<String> {
        val words = WORD.findAll(question.lowercase()).map { it.value }.filter { it.length > 2 }.toSet()
        val wanted = words + words.flatMap { SYNONYMS[it].orEmpty() }
        // Stable sort: pareho ang score → nananatili ang ayos mula itaas pababa.
        return labels.sortedByDescending { label ->
            val l = label.lowercase()
            wanted.count { it in l }
        }
    }
}
