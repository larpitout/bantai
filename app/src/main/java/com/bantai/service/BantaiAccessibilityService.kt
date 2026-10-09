package com.bantai.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bantai.Bantai
import com.bantai.ui.ScamAlertOverlay

/**
 * Ipinapakita ang babala kapag BINUKSAN ni Nanay ang na-flag na mensahe, hindi bilang biglang popup.
 * Messaging apps lang ang tinitingnan (accessibility_service_config), at text lang na tugma sa na-flag ang hinahanap.
 */
class BantaiAccessibilityService : AccessibilityService() {

    companion object {
        private const val MAX_NODES = 400

        @Volatile
        var instance: BantaiAccessibilityService? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private val check = Runnable { checkScreen() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Bantai.warmUp(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ScamAlertOverlay.dismiss(this)
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Maraming event habang nag-i-scroll: isang check lang pagkatapos tumahimik.
        main.removeCallbacks(check)
        main.postDelayed(check, 400)
    }

    override fun onInterrupt() {}

    /** Bagong na-flag na mensahe: baka bukas na ito ngayon. */
    fun recheck() {
        main.removeCallbacks(check)
        main.post(check)
    }

    private fun checkScreen() {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        val nodes = if (root != null && pkg in Bantai.MESSAGING_APPS) visibleTexts(root) else emptyList()
        val match = Bantai.flaggedOnScreen(nodes.map { it.first })
        if (match == null) {
            ScamAlertOverlay.dismiss(this)
            return
        }
        // Ang bubble ng scammer: doon mismo sa ilalim ilalagay ang babala (pinakahuli kung marami).
        val bubble = nodes.lastOrNull { Bantai.matches(match, it.first) }?.second
        ScamAlertOverlay.showFlagged(this, match, bubble)
    }

    private fun visibleTexts(root: AccessibilityNodeInfo): List<Pair<String, Rect>> {
        val out = mutableListOf<Pair<String, Rect>>()
        var seen = 0
        fun visit(n: AccessibilityNodeInfo) {
            if (++seen > MAX_NODES) return
            if (n.isVisibleToUser) n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it to Rect().also(n::getBoundsInScreen) }
            for (i in 0 until n.childCount) n.getChild(i)?.let(::visit)
        }
        visit(root)
        return out
    }
}
