package com.bantai.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bantai.model.RuleResult
import com.bantai.rules.RuleFilter

class BantaiNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "BantaiNotifListener"

        var scamCandidateListener: ScamCandidateListener? = null

        fun interface ScamCandidateListener {
            fun onScamCandidate(
                packageName: String,
                sender: String,
                message: String,
                ruleResult: RuleResult
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

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

        // 7. Dispatch if score >= 1 (suspicious or confirmed scam)
        if (ruleResult.score >= 1) {
            scamCandidateListener?.onScamCandidate(packageName, sender, message, ruleResult)
        }
    }
}
