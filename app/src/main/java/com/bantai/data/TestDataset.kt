package com.bantai.data

data class TestCase(
    val id: Int,
    val sender: String,
    val text: String,
    val isScam: Boolean,
    val category: String,
    val expectedMinScore: Int
)

object TestDataset {

    val messages: List<TestCase> = listOf(
        // ========================
        // 15 SCAM MESSAGES
        // ========================
        TestCase(
            id = 1,
            sender = "+639951234567",
            text = "Ma si Junjun to bagong number ko nasira phone ko padala ka 5k sa gcash emergency lang",
            isScam = true,
            category = "Impersonation / Relative",
            expectedMinScore = 2
        ),
        TestCase(
            id = 2,
            sender = "GCASH-ALERT",
            text = "Your GCash account has been suspended due to unverified details. Verify immediately at http://bit.ly/gcash-verify-sec to prevent permanent deactivation.",
            isScam = true,
            category = "Phishing Link / Account Suspension",
            expectedMinScore = 2
        ),
        TestCase(
            id = 3,
            sender = "+639178889999",
            text = "Congratulations! Ang iyong cellphone number ay nanalo ng Php 750,000 mula sa Philippine Charity Sweepstakes. Upang ma-claim ang premyo mag-reply ng OTP at pangalan.",
            isScam = true,
            category = "Fake Prize / Lottery",
            expectedMinScore = 2
        ),
        TestCase(
            id = 4,
            sender = "FlashExpress",
            text = "Your parcel PH-99214 failed delivery due to invalid home address. Update details now at http://flashexpress-delivery.xyz/tracking to avoid return.",
            isScam = true,
            category = "Fake Parcel Delivery",
            expectedMinScore = 2
        ),
        TestCase(
            id = 5,
            sender = "+639081239876",
            text = "Nay naaksidente po si Kuya Carlo nasa ospital kami ngayon kailangan ng 10000 pambayad sa gamot send mo agad sa gcash ko ngayon na",
            isScam = true,
            category = "Urgent Hospital Accident",
            expectedMinScore = 2
        ),
        TestCase(
            id = 6,
            sender = "+639194443322",
            text = "BDO Security: Your online banking account has suspicious activity. Unlock your account here: http://bdo-online-auth.top/login",
            isScam = true,
            category = "Bank Phishing Link",
            expectedMinScore = 1
        ),
        TestCase(
            id = 7,
            sender = "+639223334455",
            text = "Hi po! Approved na po ang pautang ninyo na 50,000 pesos. Mag-deposit lang po ng 2,500 advance fee sa Maya bago makuha ang pera.",
            isScam = true,
            category = "Fake Loan Advance Fee",
            expectedMinScore = 2
        ),
        TestCase(
            id = 8,
            sender = "+639451112233",
            text = "Pa, eto bago kong number padala ka 500 load pautang muna kailangan ko lang pantawag",
            isScam = true,
            category = "Impersonation Load Request",
            expectedMinScore = 2
        ),
        TestCase(
            id = 9,
            sender = "MERALCO-BILL",
            text = "Notice of Disconnection: Overdue bill balance detected. Settle within 24 hours at http://tinyurl.com/meralco-pay to prevent disconnection.",
            isScam = true,
            category = "Utility Bill Phishing",
            expectedMinScore = 2
        ),
        TestCase(
            id = 10,
            sender = "+639156667788",
            text = "Shopee 10.10 Grand Winner! You won a brand new iPhone 16 Pro Max. Claim your reward at http://shopee-promos.xyz/winner",
            isScam = true,
            category = "Fake Shopee Voucher",
            expectedMinScore = 2
        ),
        TestCase(
            id = 11,
            sender = "TELCO-SIM",
            text = "Your SIM card is scheduled for immediate deactivation. Verify your SIM registration identity at http://sim-registration-portal.com now.",
            isScam = true,
            category = "SIM Registration Phishing",
            expectedMinScore = 2
        ),
        TestCase(
            id = 12,
            sender = "+639352223344",
            text = "Invest 1k earn 5k daily with guaranteed return. Send money to our Maya wallet today and double your cash in 3 hours.",
            isScam = true,
            category = "Investment Scam",
            expectedMinScore = 2
        ),
        TestCase(
            id = 13,
            sender = "+639775554433",
            text = "Tita ako si Mark ito bago kong number na-hold ang transfer ko padala ka muna 3k gcash bayaran ko mamaya",
            isScam = true,
            category = "Relative Impersonation Hold",
            expectedMinScore = 2
        ),
        TestCase(
            id = 14,
            sender = "BANK-NOTICE",
            text = "DO NOT SHARE: Your one-time PIN (OTP) is 849201. If you did not initiate this online bank transfer, verify immediately.",
            isScam = true,
            category = "OTP Interception Phishing",
            expectedMinScore = 1
        ),
        TestCase(
            id = 15,
            sender = "+639667778899",
            text = "Good day maam part-time job online earn 3000 daily by liking products. Visit http://easy-earning-tasks.link to start.",
            isScam = true,
            category = "Job Task Phishing Link",
            expectedMinScore = 1
        ),

        // ========================
        // 15 SAFE MESSAGES
        // ========================
        TestCase(
            id = 16,
            sender = "Junjun Anak",
            text = "Nay, pauwi na po ako mamayang 7pm. Pasabay na po ng mainit na kanin at ulam salamat.",
            isScam = false,
            category = "Family / Routine",
            expectedMinScore = 0
        ),
        TestCase(
            id = 17,
            sender = "Maria Kapatid",
            text = "Ate kumusta na po pakiramdam mo? Pumunta kami sa palengke kanina naalala ka namin.",
            isScam = false,
            category = "Family Greeting",
            expectedMinScore = 0
        ),
        TestCase(
            id = 18,
            sender = "Doc Santos Clinic",
            text = "Reminding Mrs. Ramos of your scheduled check-up tomorrow Saturday at 10:00 AM at St. Luke's room 302.",
            isScam = false,
            category = "Genuine Clinic Reminder",
            expectedMinScore = 0
        ),
        TestCase(
            id = 19,
            sender = "Kapitbahay Tessie",
            text = "Mars pwede makahingi ng talbos ng kamote sa bakuran niyo mamaya paglabas ko?",
            isScam = false,
            category = "Neighbor Chat",
            expectedMinScore = 0
        ),
        TestCase(
            id = 20,
            sender = "Apo Kyle",
            text = "Lola tapos na po class namin kanina, nakauwi na po ako sa bahay kasama si mama.",
            isScam = false,
            category = "Grandchild Chat",
            expectedMinScore = 0
        ),
        TestCase(
            id = 21,
            sender = "PAGASA Weather",
            text = "Weather advisory: Cloudy skies with scattered rains expected in Metro Manila today due to southwest monsoon.",
            isScam = false,
            category = "Genuine Public Advisory",
            expectedMinScore = 0
        ),
        TestCase(
            id = 22,
            sender = "Kuya Berting",
            text = "Happy birthday Nay! Wishing you good health and long life po. Kitakits tayo sa Linggo.",
            isScam = false,
            category = "Birthday Greeting",
            expectedMinScore = 0
        ),
        TestCase(
            id = 23,
            sender = "Ate Grace",
            text = "Nay nasend ko na po yung picture ng mga bata sa Messenger pacheck na lang po kapag may time ka.",
            isScam = false,
            category = "Photo Share Chat",
            expectedMinScore = 0
        ),
        TestCase(
            id = 24,
            sender = "Barangay Health Center",
            text = "Pabatid: May libreng flu vaccination sa barangay covered court sa darating na Lunes mula 8am hanggang 12nn.",
            isScam = false,
            category = "Community Health Notice",
            expectedMinScore = 0
        ),
        TestCase(
            id = 25,
            sender = "Tita Elena",
            text = "Anong oras po ang simula ng misa bukas ng umaga? Pasundo na lang po kami kay Ricky.",
            isScam = false,
            category = "Church / Activity",
            expectedMinScore = 0
        ),
        TestCase(
            id = 26,
            sender = "Mercury Drug",
            text = "Thank you for purchasing at Mercury Drug EDSA branch. Official Receipt No. 892341.",
            isScam = false,
            category = "Genuine Purchase Receipt",
            expectedMinScore = 0
        ),
        TestCase(
            id = 27,
            sender = "Junjun Anak",
            text = "Nay nandoon po sa ref yung binili kong prutas kanina, kainin niyo po habang sariwa pa.",
            isScam = false,
            category = "Family Reminder",
            expectedMinScore = 0
        ),
        TestCase(
            id = 28,
            sender = "Kumare Lita",
            text = "Kumusta po ang blood pressure niyo ngayon? Nakainom na po ba kayo ng pampababa ng presyon?",
            isScam = false,
            category = "Health Checkup Chat",
            expectedMinScore = 0
        ),
        TestCase(
            id = 29,
            sender = "Apo Bea",
            text = "Lola punta po kami dyan bukas dalhan ko po kayo ng paborito mong bibingka.",
            isScam = false,
            category = "Family Visit Chat",
            expectedMinScore = 0
        ),
        TestCase(
            id = 30,
            sender = "Carla Kapatid",
            text = "Ingat po kayo sa ulan kanina, medyo madulas ang daan papunta sa terminal.",
            isScam = false,
            category = "Care Reminder",
            expectedMinScore = 0
        )
    )
}
