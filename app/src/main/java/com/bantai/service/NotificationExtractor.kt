package com.bantai.service

import java.util.concurrent.ConcurrentLinkedDeque

object NotificationExtractor {

    val ALLOWED_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging",
        "com.facebook.orca",
        "com.viber.voip",
        "com.whatsapp"
    )

    private const val MAX_RECENT_HASHES = 50
    private val recentHashes = ConcurrentLinkedDeque<Int>()

    fun isPackageAllowed(packageName: String): Boolean {
        return ALLOWED_PACKAGES.contains(packageName)
    }

    /**
     * Extracts sender title and message content.
     * Prioritizes EXTRA_BIG_TEXT over EXTRA_TEXT to capture long messages.
     */
    fun extractContent(
        title: CharSequence?,
        bigText: CharSequence?,
        text: CharSequence?
    ): Pair<String, String> {
        val sender = title?.toString()?.trim() ?: ""
        val message = (bigText ?: text)?.toString()?.trim() ?: ""
        return Pair(sender, message)
    }

    /**
     * Checks if a notification with identical package, sender, and text was recently processed.
     * Prevents repetitive triggers when messaging apps update active notifications.
     */
    fun isDuplicate(packageName: String, sender: String, text: String): Boolean {
        if (text.isBlank()) return true

        val hash = (packageName + sender + text).hashCode()

        if (recentHashes.contains(hash)) {
            return true
        }

        recentHashes.addFirst(hash)
        while (recentHashes.size > MAX_RECENT_HASHES) {
            recentHashes.removeLast()
        }

        return false
    }

    fun clearCache() {
        recentHashes.clear()
    }
}
