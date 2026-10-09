package com.bantai.rules

/**
 * Sinusuri ang mga link sa mensahe, offline at walang binubuksang website.
 * Ang mga scam link sa PH ay madalas: pekeng pangalan ng brand sa domain, shortener, o murang domain ending.
 */
object LinkChecker {

    enum class Kind { FAKE_BRAND, SHORTENER, RISKY_ENDING, IP_ADDRESS, LOOKALIKE }

    /** [brand] ay may laman lang kapag [Kind.FAKE_BRAND]. */
    data class Finding(val url: String, val kind: Kind, val brand: String? = null)

    // Brand → mga opisyal na domain. Ang ibang domain na may pangalan ng brand ay peke.
    private val BRANDS = mapOf(
        "gcash" to listOf("gcash.com"),
        "maya" to listOf("maya.ph", "paymaya.com"),
        "bdo" to listOf("bdo.com.ph"),
        "bpi" to listOf("bpi.com.ph"),
        "metrobank" to listOf("metrobank.com.ph"),
        "landbank" to listOf("landbank.com"),
        "unionbank" to listOf("unionbankph.com"),
        "lbc" to listOf("lbcexpress.com"),
        "jnt" to listOf("jtexpress.ph"),
        "jtexpress" to listOf("jtexpress.ph"),
        "shopee" to listOf("shopee.ph"),
        "lazada" to listOf("lazada.com.ph"),
        "sss" to listOf("sss.gov.ph"),
        "philhealth" to listOf("philhealth.gov.ph"),
        "pagibig" to listOf("pagibigfund.gov.ph"),
        "globe" to listOf("globe.com.ph"),
        "smart" to listOf("smart.com.ph"),
        "meralco" to listOf("meralco.com.ph"),
    )

    // Kilalang ligtas at mapagkakatiwalaang mga domain (search, social, video, tech).
    private val SAFE_DOMAINS = setOf(
        "google.com", "google.com.ph",
        "youtube.com", "youtu.be",
        "facebook.com", "fb.com", "fb.me", "messenger.com",
        "instagram.com", "threads.net",
        "twitter.com", "x.com",
        "wikipedia.org",
        "apple.com", "icloud.com",
        "microsoft.com", "live.com", "office.com",
        "yahoo.com",
        "viber.com", "whatsapp.com", "telegram.org", "t.me",
    )

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "s.id", "tiny.cc", "ow.ly", "rebrand.ly",
    )

    private val RISKY_ENDINGS = setOf(
        "xyz", "top", "link", "click", "icu", "site", "online", "live", "shop", "vip", "buzz", "cfd", "rest", "info", "cc", "sbs",
    )

    private val URL = Regex(
        "(?:https?://)?(?:www\\.)?((?:[a-z0-9-]+\\.)+[a-z]{2,}|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(/\\S*)?",
        RegexOption.IGNORE_CASE,
    )

    /** Sinusuri kung ang host ay opisyal na domain ng brand, pamahalaan, o kilalang ligtas na website. */
    fun isSafeDomain(host: String): Boolean {
        var clean = host.lowercase().trim()
        while (clean.startsWith("www.")) {
            clean = clean.removePrefix("www.")
        }
        if (clean.endsWith(".gov.ph") || clean.endsWith(".edu.ph") || clean == "gov.ph" || clean == "edu.ph") return true
        if (SAFE_DOMAINS.any { clean == it || clean.endsWith(".$it") }) return true
        for (officialList in BRANDS.values) {
            if (officialList.any { clean == it || clean.endsWith(".$it") }) return true
        }
        return false
    }

    /** May link ba sa mensahe na hindi kilalang ligtas na domain (unverified o kahina-hinala)? */
    fun hasUnverifiedLink(text: String): Boolean {
        val matches = URL.findAll(text).filter { looksLikeLink(it) }.toList()
        if (matches.isEmpty()) return false
        if (matches.any { judge(it.value, it.groupValues[1].lowercase()) != null }) return true
        return matches.any { !isSafeDomain(it.groupValues[1].lowercase()) }
    }

    /** Tinatanggal ang mga kilalang ligtas na link mula sa teksto para hindi mag-false positive ang keyword matching sa domain name (hal. "gcash.com" na nagti-trigger ng Money Request). */
    fun removeSafeUrls(text: String): String {
        return URL.replace(text) { m ->
            val host = m.groupValues[1].lowercase()
            if (looksLikeLink(m) && isSafeDomain(host) && judge(m.value, host) == null) {
                " "
            } else {
                m.value
            }
        }
    }

    /** Ang pinakamabigat na problema sa mga link ng [text], o null kung walang kahina-hinala. */
    fun check(text: String): Finding? = URL.findAll(text)
        .filter { looksLikeLink(it) }
        .mapNotNull { m -> judge(m.value, m.groupValues[1].lowercase()) }
        .minByOrNull { it.kind.ordinal } // FAKE_BRAND ang pinakamabigat

    // "5k" o "ma.si" ay hindi link: kailangang may kilalang ending o may scheme/path.
    private fun looksLikeLink(m: MatchResult): Boolean {
        val host = m.groupValues[1].lowercase()
        val tld = host.substringAfterLast('.')
        return m.value.contains("://") || m.groupValues[2].isNotEmpty() || tld in KNOWN_ENDINGS || IP.matches(host)
    }

    private fun judge(url: String, host: String): Finding? {
        if (IP.matches(host)) return Finding(url, Kind.IP_ADDRESS)
        if (host.startsWith("xn--") || host.contains(".xn--")) return Finding(url, Kind.LOOKALIKE)

        val compact = host.replace("-", "").replace(".", "")
        for ((brand, official) in BRANDS) {
            val isOfficial = official.any { host == it || host.endsWith(".$it") }
            if (!isOfficial && compact.contains(brand) && brandIsWholeWord(host, brand)) return Finding(url, Kind.FAKE_BRAND, brand)
        }
        if (SHORTENERS.any { host == it }) return Finding(url, Kind.SHORTENER)
        if (host.substringAfterLast('.') in RISKY_ENDINGS) return Finding(url, Kind.RISKY_ENDING)
        return null
    }

    // "gcash-verify.com" oo; "smartphones.com" ay hindi "smart" na brand.
    private fun brandIsWholeWord(host: String, brand: String): Boolean =
        host.split('.', '-').any { it == brand || it.startsWith(brand) && it.length <= brand.length + 3 }

    private val IP = Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")
    private val KNOWN_ENDINGS = RISKY_ENDINGS + setOf("com", "ph", "net", "org", "ly", "co", "me", "io", "gd", "at", "id", "gov", "edu", "be")

    /** Pangalan ng brand na pang-display, hal. "gcash" → "GCash". */
    fun displayBrand(brand: String): String = when (brand) {
        "gcash" -> "GCash"; "bdo" -> "BDO"; "bpi" -> "BPI"; "lbc" -> "LBC"; "jnt", "jtexpress" -> "J&T Express"
        "sss" -> "SSS"; "pagibig" -> "Pag-IBIG"; "philhealth" -> "PhilHealth"
        else -> brand.replaceFirstChar(Char::uppercase)
    }
}
