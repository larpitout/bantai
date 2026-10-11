package com.bantai.rules

/**
 * Maikling listahan ng mga karaniwang scam scheme sa PH, para sa prompt ng AI.
 * Isang linya lang bawat isa (name → senyales) para manatiling mabilis ang inference.
 */
object ScamSchemes {

    val schemes: List<Pair<String, String>> = listOf(
        "Bagong Number" to "new number + urgent money/load ask",
        "Fake Parcel" to "delivery problem + link or fee",
        "Account Suspension" to "locked account + verify link",
        "Prize/Raffle" to "prize claim + fee or details first",
        "Job/Task" to "paid clicks/tasks + signup link",
        "Investment" to "guaranteed returns + pay first",
        "Loan Fee" to "approved loan + advance fee",
        "Emergency" to "accident/hospital + money now",
        "Romance" to "online sweetheart + money/gifts",
        "Govt/Aid & SIM reg" to "benefits or SIM-registration link",
        "Spoofed Sender" to "official-looking + OTP or odd link",
    )

    /** Bawat scheme ay isang `- Name: hallmark` na linya para sa prompt. */
    fun promptBlock(): String = schemes.joinToString("\n") { (name, hallmark) -> "- $name: $hallmark" }
}
