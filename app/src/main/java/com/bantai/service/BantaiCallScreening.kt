package com.bantai.service

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.ScamHistory
import com.bantai.ui.ScamAlertOverlay

/**
 * Babala sa tawag. Hindi pinakikinggan ang tawag; numero lang ang tinitingnan.
 * Tinatawag lang ng Android ito para sa mga numerong wala sa contacts (kapag si Bantai ang "Caller ID & spam app").
 * Hindi hinaharang ang tawag: si Nanay pa rin ang magpapasya.
 */
class BantaiCallScreening : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        respondToCall(details, CallResponse.Builder().build()) // payagan; babala lang
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return

        val number = details.handle?.schemeSpecificPart.orEmpty()
        val text = Bantai.localized(this)
        // Ang numerong ito ba ay nagpadala na ng na-flag na text?
        val sentScam = ScamHistory.list(this).firstOrNull { sameNumber(it.sender, number) }
        val (reason, action) = if (sentScam != null) {
            text.getString(R.string.call_scam_sender_reason) to text.getString(R.string.call_scam_sender_action)
        } else {
            text.getString(R.string.call_unknown_reason) to text.getString(R.string.call_unknown_action)
        }
        val shown = number.ifBlank { text.getString(R.string.call_hidden_number) }

        Handler(Looper.getMainLooper()).post {
            ScamNotifier.notify(this, shown, reason, null)
            ScamAlertOverlay.show(this, reason, action, fromAi = false)
            if (sentScam != null) {
                ScamHistory.add(this, shown, text.getString(R.string.call_history_message), reason, text.getString(R.string.kind_call), 3)
            }
        }
    }

    /** Huling 10 digit lang (pareho ang +639… at 09…). */
    private fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter(Char::isDigit).takeLast(10)
        val y = b.filter(Char::isDigit).takeLast(10)
        return x.length >= 7 && x == y
    }
}
