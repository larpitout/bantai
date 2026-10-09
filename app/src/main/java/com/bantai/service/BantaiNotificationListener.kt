package com.bantai.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bantai.Bantai
import com.bantai.pipeline.VerdictSource
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 0. Master protection toggle check
        if (!GuardianPreferences(this).isProtectionEnabled) {
            return
        }

        val packageName = sbn.packageName ?: return

        // 1. Package allowlist check
        if (!NotificationExtractor.isPackageAllowed(packageName)) {
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
                    val (reasonRes, actionRes) = RuleFilter.instantWarningRes(check.rule)
                    ScamAlertOverlay.show(this@BantaiNotificationListener, getString(reasonRes), getString(actionRes), fromAi)
                    shown = true
                }
            }
        }
    }
}
