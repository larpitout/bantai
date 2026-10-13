package com.bantai.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bantai.Bantai
import com.bantai.data.GuardianPreferences
import com.bantai.model.Verdict
import com.bantai.pipeline.VerdictSource
import com.bantai.R
import com.bantai.rules.LinkChecker
import com.bantai.rules.RuleFilter
import com.bantai.ui.ScamAlertOverlay
import com.bantai.util.ContactHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BantaiNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "BantaiNotifListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Bantai.warmUp(this)
    }

    /** Babala sa wika ng app; kapag mapanganib ang link, sinasabi kung bakit (hal. "hindi opisyal na website ng GCash"). */
    private fun warningText(message: String, rule: com.bantai.model.RuleResult): Pair<String, String> {
        val text = Bantai.localized(this)
        val link = LinkChecker.check(message)
        if (link != null) {
            val reason = when (link.kind) {
                LinkChecker.Kind.FAKE_BRAND ->
                    text.getString(R.string.warning_link_fake_brand, LinkChecker.displayBrand(link.brand!!))
                LinkChecker.Kind.SHORTENER -> text.getString(R.string.warning_link_shortener)
                LinkChecker.Kind.RISKY_ENDING -> text.getString(R.string.warning_link_risky_ending)
                LinkChecker.Kind.IP_ADDRESS, LinkChecker.Kind.LOOKALIKE -> text.getString(R.string.warning_link_fake_site)
            }
            return reason to text.getString(R.string.warning_link_action)
        }
        val (reasonRes, actionRes) = RuleFilter.instantWarningRes(rule)
        return text.getString(reasonRes) to text.getString(actionRes)
    }

    /** Uri ng scam para sa dashboard (isang salita/parirala). */
    private fun kindOf(message: String, signals: List<String>): String = Bantai.localized(this).getString(
        when {
            LinkChecker.check(message) != null -> R.string.kind_link
            "New Number / Impersonation" in signals -> R.string.kind_relative
            "Prize / Raffle" in signals -> R.string.kind_prize
            "Account / OTP / Parcel" in signals -> R.string.kind_account
            "Money Request" in signals -> R.string.kind_money
            else -> R.string.kind_other
        }
    )

    /** Mga senyales mula sa rules, sa wika ng babala. */
    private fun signalNames(signals: List<String>): List<String> {
        val text = Bantai.localized(this)
        return signals.mapNotNull { s ->
            when (s) {
                "Money Request" -> R.string.signal_money_request
                "New Number / Impersonation" -> R.string.signal_new_number
                "Suspicious Link" -> R.string.signal_suspicious_link
                "Dangerous Link" -> R.string.signal_dangerous_link
                "Prize / Raffle" -> R.string.signal_prize_raffle
                "Account / OTP / Parcel" -> R.string.signal_account_parcel
                "Urgency / Emergency" -> R.string.signal_urgency
                else -> null
            }?.let(text::getString)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 0. Master protection toggle check
        if (!GuardianPreferences(this).isProtectionEnabled) {
            return
        }

        val packageName = sbn.packageName ?: return

        // 1. Package allowlist check
        // Debug build lang: tanggapin din ang "adb shell cmd notification post" para ma-test nang walang pangalawang phone.
        val debugTest = packageName == "com.android.shell" &&
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!NotificationExtractor.isPackageAllowed(packageName) && !debugTest) {
            return
        }

        // 2. Skip ongoing background/music/call notifications
        if (sbn.isOngoing) {
            return
        }

        // 3. Skip group summaries (e.g. "3 new messages")
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        // 4. Extract sender and message text
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)

        val (sender, message) = NotificationExtractor.extractContent(title, bigText, text)
        if (message.isBlank()) {
            return
        }

        // 5. Deduplicate recent repetitive notifications
        if (NotificationExtractor.isDuplicate(packageName, sender, message)) {
            Log.d(TAG, "Skipping duplicate notification from $packageName")
            return
        }
        com.bantai.data.ScamHistory.countScanned(this) // para sa dashboard: "nasuri ngayon"

        // 6. Layer 1 Rule Scoring
        val ruleResult = RuleFilter.score(message, sender)
        Log.d(TAG, "Notification from [$packageName] ($sender): score=${ruleResult.score}, signals=${ruleResult.signals}")

        // 7. Score 0 = ligtas ayon sa rules: itabi bilang "checked and cleared" (Alerts → SAFE filter).
        //    Score >= 1: rules + AI (ScamPipeline). Hard signal → babala agad habang nag-iisip ang AI;
        //    iba pang may senyales → ang AI ang huhusga. Walang hatol ang AI → maingat na SUSPICIOUS.
        if (ruleResult.score == 0) {
            val loc = Bantai.localized(this)
            com.bantai.data.ScamHistory.add(
                this, sender, message,
                loc.getString(R.string.warning_safe_reason), loc.getString(R.string.kind_safe), 0,
                safe = true,
            )
            return
        }
        if (ruleResult.score >= 1) {
            val openChat = notification.contentIntent // para buksan ang mismong chat mula sa notification ni Bantai
            Bantai.scope.launch {
                // Kakilala ba ang nagpadala? Sa contacts ng phone lang tinitingnan (null kapag walang pahintulot);
                // kailangan ito ng SenderClassifier para sa lahat ng uri ng mensahe, hindi lang sa hingi ng pera.
                val contact = if (sender.isNotBlank()) {
                    withContext(Dispatchers.IO) { ContactHelper.find(this@BantaiNotificationListener, sender) }
                } else null
                var shown = false
                Bantai.scamPipeline(this@BantaiNotificationListener).check(message, sender, senderIsContact = contact != null).collect { check ->
                    Log.d(TAG, "verdict=${check.verdict.level} source=${check.source} final=${check.isFinal}")
                    if (check.verdict.level == Verdict.SAFE) {
                        // May senyales pero ligtas ang huling hatol: itabi bilang "checked and cleared"
                        // para makita sa Alerts → SAFE filter (audit ng AI). Walang babala.
                        if (check.isFinal) {
                            val loc = Bantai.localized(this@BantaiNotificationListener)
                            com.bantai.data.ScamHistory.add(
                                this@BantaiNotificationListener, sender, message,
                                check.verdict.reason.ifBlank { loc.getString(R.string.warning_safe_reason) },
                                loc.getString(R.string.kind_safe), check.rule.score,
                                signals = signalNames(check.rule.signals),
                                aiModel = if (check.source == VerdictSource.LLM) Bantai.modelName() else null,
                                safe = true,
                            )
                        }
                        return@collect
                    }
                    val suspicious = check.verdict.level == Verdict.SUSPICIOUS
                    val fromAi = check.source == VerdictSource.LLM
                    val loc = Bantai.localized(this@BantaiNotificationListener)
                    // Template mula sa rules ang laging ipinapakita; si Gemma ang nagpapasya kung scam.
                    // Kakilala → "humihingi ng pera"; ibang SUSPICIOUS → "hindi nasuri nang buo, mag-ingat".
                    val (ruleReason, action) = when {
                        suspicious && contact != null ->
                            loc.getString(R.string.suspicious_reason, contact.name) to loc.getString(R.string.suspicious_action)
                        suspicious ->
                            loc.getString(R.string.warning_uncertain_reason) to loc.getString(R.string.warning_uncertain_action)
                        else -> warningText(message, check.rule)
                    }
                    // AI ang bida: kapag si Gemma ang nagpasya, ang sarili niyang paliwanag ang ipapakita.
                    // Kakilala: template lang, dahil "scam" ang salita ng model.
                    val reason = check.verdict.reason.takeIf { fromAi && !(suspicious && contact != null) && it.isNotBlank() && it.length < 300 } ?: ruleReason
                    val signals = signalNames(check.rule.signals)
                    val kind = if (suspicious && contact != null) loc.getString(R.string.kind_contact_money) else kindOf(message, check.rule.signals)
                    // Kasaysayan (audit): sa huling hatol, para alam kung AI o rules ang nagpasya.
                    if (check.isFinal) {
                        com.bantai.data.ScamHistory.add(
                            this@BantaiNotificationListener, sender, message, reason, kind,
                            check.rule.score, action, signals, if (fromAi) Bantai.modelName() else null,
                            suspicious, contact?.number.takeIf { suspicious },
                        )
                    }
                    // Naipakita na ang babala ng rules; ang huling hatol ng rules ay para sa kasaysayan lang.
                    if (shown && !fromAi) return@collect
                    if (!shown) {
                        ScamNotifier.notify(
                            this@BantaiNotificationListener, sender, reason, openChat,
                            if (suspicious && contact != null) R.string.notif_title_suspicious else R.string.notif_title,
                        )
                    }
                    val a11y = BantaiAccessibilityService.instance
                    if (a11y != null) {
                        // Walang biglang popup: lalabas ang babala kapag binuksan ni Nanay ang mensahe.
                        Bantai.flag(message, reason, action, fromAi, signals, check.rule.score, check.verdict.level, contact)
                        a11y.recheck()
                    } else {
                        ScamAlertOverlay.show(this@BantaiNotificationListener, reason, action, fromAi, message, signals, check.verdict.level, contact)
                    }
                    shown = true
                }
            }
        }
    }
}
