package com.bantai.rules

/**
 * Tinutukoy kung anong uri ang nagpadala ng mensahe (sender string ng notification),
 * para may konteksto ang AI at ang rules (hal. opisyal na sender ID, numero, o kakilala).
 */
object SenderClassifier {

    enum class Type { OFFICIAL_ID, SHORTCODE, SAVED_CONTACT, MOBILE_NUMBER, ALPHANUMERIC_ID, UNKNOWN }

    data class SenderInfo(
        val type: Type,
        /** Maikling paliwanag para sa prompt ng AI, hal. "official sender ID: Globe". */
        val label: String,
        val sender: String,
    ) {
        /** Isang linya ng konteksto para sa prompt, hal. Sender: "8080" (official sender ID: Globe). */
        fun promptLine() = "Sender: \"$sender\" ($label)"
    }

    /**
     * Mga kilalang PH sender ID → brand. Curated at puwedeng dagdagan:
     * walang pampublikong registry; ang sender IDs ay rehistrado kada telco.
     */
    private val OFFICIAL_IDS = mapOf(
        "8080" to "Globe", "GLOBE" to "Globe", "TM" to "Globe TM",
        "SMART" to "Smart", "TNT" to "Smart TNT", "DITO" to "DITO", "GOMO" to "GOMO",
        "GCASH" to "GCash", "2882" to "GCash",
        "MAYA" to "Maya", "PAYMAYA" to "Maya",
        "BDO" to "BDO", "BDO ALERT" to "BDO", "BDO DEALS" to "BDO", "BDO OTP" to "BDO",
        "225678" to "BDO", "22567888" to "BDO", "22565048" to "BDO",
        "BPI" to "BPI", "METROBANK" to "Metrobank", "UNIONBANK" to "UnionBank",
        "LANDBANK" to "LandBank", "RCBC" to "RCBC",
        "NINJA VAN" to "Ninja Van", "SHOPEE" to "Shopee", "LAZADA" to "Lazada",
    )

    private val MOBILE = Regex("^(?:\\+63|0)9\\d{9}$")

    /** [senderIsContact]: nasa phone contacts ba ang sender (mula sa ContactHelper). */
    fun classify(sender: String, senderIsContact: Boolean): SenderInfo {
        val s = sender.trim()
        if (senderIsContact) return SenderInfo(Type.SAVED_CONTACT, "saved contact", s)
        OFFICIAL_IDS[s.uppercase()]?.let { return SenderInfo(Type.OFFICIAL_ID, "official sender ID: $it", s) }
        // 3-6 na digit: karaniwang lehitimong A2P short code ng telco/bangko.
        if (s.length in 3..6 && s.all(Char::isDigit)) return SenderInfo(Type.SHORTCODE, "short code", s)
        val digits = s.replace(Regex("[\\s-]+"), "")
        if (MOBILE.matches(digits)) return SenderInfo(Type.MOBILE_NUMBER, "mobile number, not in contacts", s)
        // Purong malalaking letra (puwedeng may espasyo/digit), maikli: sender ID na hindi namin kilala.
        if (s.length <= 15 && s.any(Char::isLetter) && s.all { it.isUpperCase() || it.isDigit() || it == ' ' }) {
            return SenderInfo(Type.ALPHANUMERIC_ID, "sender ID, unverified", s)
        }
        // Neutral lang ("hindi naka-save sa contacts"): ang "unknown sender" ay nagtutulak sa model na laging scam.
        return SenderInfo(Type.UNKNOWN, "not in contacts", s)
    }
}
