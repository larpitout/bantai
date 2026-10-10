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

        // 7. Score >= 1: rules + Gemma (ScamPipeline). Lalabas agad ang babala kapag score >= 2;
        //    kapag score 1, si Gemma ang magdedesisyon (rules ang fallback kapag mabagal o pumalya).
        if (ruleResult.score >= 1) {
            val openChat = notification.contentIntent // para buksan ang mismong chat mula sa notification ni Bantai
            Bantai.scope.launch {
                // Kakilala ba ang humihingi ng pera? Sa contacts ng phone lang tinitingnan (null kapag walang pahintulot).
                val contact = if ("Money Request" in ruleResult.signals) {
                    withContext(Dispatchers.IO) { ContactHelper.find(this@BantaiNotificationListener, sender) }
                } else null
                var shown = false
                Bantai.scamPipeline(this@BantaiNotificationListener).check(message, sender, senderIsContact = contact != null).collect { check ->
                    Log.d(TAG, "verdict=${check.verdict.level} source=${check.source} final=${check.isFinal}")
                    if (check.verdict.level == Verdict.SAFE) return@collect
                    val suspicious = check.verdict.level == Verdict.SUSPICIOUS
                    val fromAi = check.source == VerdictSource.LLM
                    val loc = Bantai.localized(this@BantaiNotificationListener)
                    // Template mula sa rules ang laging ipinapakita; si Gemma ang nagpapasya kung scam.
                    val (ruleReason, action) = if (suspicious) {
                        loc.getString(R.string.suspicious_reason, contact?.name ?: sender) to loc.getString(R.string.suspicious_action)
                    } else warningText(message, check.rule)
                    // AI ang bida: kapag si Qwen/Gemma ang nagpasya, ang sarili niyang paliwanag ang ipapakita.
                    // Kakilala: template lang, dahil "scam" ang salita ng model.
                    val reason = check.verdict.reason.takeIf { !suspicious && fromAi && it.isNotBlank() && it.length < 300 } ?: ruleReason
                    val signals = signalNames(check.rule.signals)
                    val kind = if (suspicious) loc.getString(R.string.kind_contact_money) else kindOf(message, check.rule.signals)
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
                            if (suspicious) R.string.notif_title_suspicious else R.string.notif_title,
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
