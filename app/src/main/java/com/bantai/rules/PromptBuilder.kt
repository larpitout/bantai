package com.bantai.rules

object PromptBuilder {

    const val SYSTEM_PROMPT = """You are Bantai, an on-device security assistant protecting elderly users from scams and social engineering.
Analyze the given message and sender.
You MUST respond strictly in the following 3-line format with no extra markdown or explanations:
VERDICT: <SCAM or SAFE>
REASON: <Clear explanation in 1 sentence>
ACTION: <Clear instruction in 1 sentence>"""

    const val FEW_SHOT_EXAMPLES = """
Sender: +639951234567
Message: "Ma si Junjun to bagong number ko padala ka 5k gcash emergency lang"
VERDICT: SCAM
REASON: An unknown number is impersonating a child asking urgently for money on GCash.
ACTION: Do not send money. Call Junjun on his original saved phone number to verify.

Sender: GCASH-ALERT
Message: "Your GCash account is suspended. Verify your identity at http://bit.ly/gcash-sec to avoid account deletion."
VERDICT: SCAM
REASON: Phishing attempt pretending to be GCash using an unauthorized link to steal login credentials.
ACTION: Do not tap the link and never provide your OTP or MPIN to anyone.

Sender: FlashExpress
Message: "Your parcel failed delivery due to incorrect home address. Update at http://track-pkg.xyz"
VERDICT: SCAM
REASON: Unsolicited parcel notification with a suspicious link intended to steal personal data.
ACTION: Do not tap the link if you are not expecting any package delivery.

Sender: Junjun Anak
Message: "Nay pauwi na po ako mamayang 7pm, pasabay po ng mainit na kanin at ulam salamat."
VERDICT: SAFE
REASON: Routine personal message from a saved family contact with no monetary or link requests.
ACTION: No action required.

Sender: Doc Santos Clinic
Message: "Reminding Mrs. Ramos of your scheduled check-up tomorrow Saturday at 10:00 AM."
VERDICT: SAFE
REASON: Genuine informational clinic appointment reminder with no request for money or links.
ACTION: Attend your scheduled appointment.
"""

    fun buildUserPrompt(sender: String, message: String): String {
        return """$FEW_SHOT_EXAMPLES
Sender: $sender
Message: "$message"
VERDICT:"""
    }
}
