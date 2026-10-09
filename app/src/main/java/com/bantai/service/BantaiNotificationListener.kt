package com.bantai.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bantai.Bantai
import com.bantai.data.GuardianPreferences
import com.bantai.pipeline.VerdictSource
import com.bantai.R
import com.bantai.rules.LinkChecker
import com.bantai.rules.RuleFilter
import com.bantai.ui.ScamAlertOverlay
import kotlinx.coroutines.launch

class BantaiNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "BantaiNotifListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Bantai.warmUp(this)
    }

    /** Tagalog na babala; kapag mapanganib ang link, sinasabi kung bakit (hal. "hindi opisyal na website ng GCash"). */
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

        // 6. Layer 1 Rule Scoring
        val ruleResult = RuleFilter.score(message, sender)
        Log.d(TAG, "Notification from [$packageName] ($sender): score=${ruleResult.score}, signals=${ruleResult.signals}")

        // 7. Score >= 1: rules + Gemma (ScamPipeline). Lalabas agad ang babala kapag score >= 2;
        //    kapag score 1, si Gemma ang magdedesisyon (rules ang fallback kapag mabagal o pumalya).
        if (ruleResult.score >= 1) {
            Bantai.scope.launch {
                var shown = false
                Bantai.scamPipeline(this@BantaiNotificationListener).check(message, sender).collect { check ->
                    Log.d(TAG, "verdict scam=${check.verdict.isScam} source=${check.source} final=${check.isFinal}")
                    if (!check.verdict.isScam) return@collect
                    val fromAi = check.source == VerdictSource.LLM
                    if (shown && !fromAi) return@collect
                    // Tagalog template mula sa rules ang laging ipinapakita; si Gemma ang nagpapasya kung scam.
                    val (reason, action) = warningText(message, check.rule)
                    val signals = signalNames(check.rule.signals)
                    if (!shown) com.bantai.data.ScamHistory.add(this@BantaiNotificationListener, sender, message, reason)
                    val a11y = BantaiAccessibilityService.instance
                    if (a11y != null) {
                        // Walang biglang popup: lalabas ang babala kapag binuksan ni Nanay ang mensahe.
                        Bantai.flag(message, reason, action, fromAi, signals)
                        a11y.recheck()
                    } else {
                        ScamAlertOverlay.show(this@BantaiNotificationListener, reason, action, fromAi, message, signals)
                    }
                    shown = true
                }
            }
        }
    }
}
