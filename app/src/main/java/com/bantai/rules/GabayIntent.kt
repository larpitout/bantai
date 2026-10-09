package com.bantai.rules

/**
 * Ang naintindihan sa sinabi ni Nanay: anong gagawin, kanino, at saang app.
 * Rules ito, hindi Gemma: kailangang laging tama ang "sino" at "ano" para hindi maligaw ang buong gabay.
 */
data class GabayIntent(
    val action: Action,
    /** Pangalan ng tatawagan/ime-message, o null kung walang binanggit. */
    val person: String?,
    /** Binanggit ang "apo", "anak", atbp. pero walang pangalan: gamitin ang Trusted Contact o itanong. */
    val needsTrustedContact: Boolean,
    /** "messenger", "phone", "viber"… kung binanggit; null kung wala. */
    val app: String?,
) {
    enum class Action { CALL, VIDEO_CALL, MESSAGE, OPEN, OTHER }

    companion object {
        private val VIDEO = Regex("\\bvideo\\b")
        private val CALL = Regex("\\b(call|tawag|tawagan|tumawag|dial|ring)\\b")
        private val MESSAGE = Regex("\\b(message|mensahe|text|chat|i-message|imessage|mag-message)\\b")
        private val OPEN = Regex("\\b(open|go to|buksan|punta|pumunta|launch)\\b")

        private val FAMILY = Regex(
            "\\b(apo|grandson|granddaughter|grandchild|anak|son|daughter|trusted contact|my (grand)?(son|daughter|child))\\b"
        )

        // Mga app na may sariling paraan ng pagtawag/pag-message.
        private val APPS = mapOf(
            "messenger" to "messenger", "viber" to "viber", "whatsapp" to "whatsapp",
            "phone" to "phone", "telepono" to "phone",
        )

        // Mga salitang hindi pangalan ng tao.
        private val NOT_A_NAME = setOf(
            "call", "video", "message", "text", "chat", "on", "in", "sa", "using", "via", "the", "my", "me", "him", "her",
            "please", "now", "po", "nga", "mo", "ko", "si", "kay", "yung", "ang", "to", "with", "and", "app", "phone",
            "messenger", "viber", "whatsapp", "tawagan", "tawag", "pakitawagan", "naman",
        )

        // "call Junjun", "tawagan si Junjun", "video call kay Maria", "message Junjun on Messenger"
        private val NAMED = Regex(
            "\\b(?:call|tawagan|tawag|message|text|chat|video call|si|kay|ni|with)\\s+(?:si\\s+|kay\\s+)?([a-z][a-z'-]+(?:\\s+[a-z][a-z'-]+)?)"
        )

        fun parse(spoken: String): GabayIntent {
            val s = spoken.lowercase().replace(Regex("[^a-z' -]"), " ").replace(Regex("\\s+"), " ").trim()
            val action = when {
                VIDEO.containsMatchIn(s) -> Action.VIDEO_CALL
                CALL.containsMatchIn(s) -> Action.CALL
                MESSAGE.containsMatchIn(s) -> Action.MESSAGE
                OPEN.containsMatchIn(s) -> Action.OPEN
                else -> Action.OTHER
            }
            val app = APPS.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(s) }?.value
            val family = FAMILY.containsMatchIn(s)

            val person = if (action == Action.OPEN || action == Action.OTHER) null else NAMED.findAll(s)
                .map { m -> m.groupValues[1].split(' ').takeWhile { it !in NOT_A_NAME }.joinToString(" ") }
                .firstOrNull { it.isNotBlank() && !FAMILY.matches(it) }
                ?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }

            return GabayIntent(action, person, needsTrustedContact = person == null && family, app = app)
        }
    }
}
