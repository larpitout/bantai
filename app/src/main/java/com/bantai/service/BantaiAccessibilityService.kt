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
        if (root == null || pkg !in Bantai.MESSAGING_APPS || !isConversationOpen(root)) {
            ScamAlertOverlay.dismiss(this)
            return
        }
        val nodes = visibleTexts(root)
        val match = Bantai.flaggedOnScreen(nodes.map { it.first })
        if (match == null) {
            ScamAlertOverlay.dismiss(this)
            return
        }
        // Ang bubble ng scammer: doon ilalagay ang babala (pinakahuli kung marami).
        val bubble = nodes.lastOrNull { Bantai.matches(match, it.first) }?.second
        ScamAlertOverlay.showFlagged(this, match, bubble)
    }

    /** Bukas lang ang chat kapag may text box (composer/EditText) para sa pag-type. Iwas lumabas sa inbox list. */
    private fun isConversationOpen(root: AccessibilityNodeInfo): Boolean {
        var seen = 0
        fun findEditable(n: AccessibilityNodeInfo): Boolean {
            if (++seen > 150) return false
            if (n.isEditable || n.className?.contains("EditText", ignoreCase = true) == true) return true
            for (i in 0 until n.childCount) {
                if (n.getChild(i)?.let(::findEditable) == true) return true
            }
            return false
        }
        return findEditable(root)
    }

    private fun visibleTexts(root: AccessibilityNodeInfo): List<Pair<String, Rect>> {
        val out = mutableListOf<Pair<String, Rect>>()
        var seen = 0
        fun visit(n: AccessibilityNodeInfo) {
            if (++seen > MAX_NODES) return
            if (n.isVisibleToUser) {
                val text = n.text?.toString()
                if (!text.isNullOrBlank()) {
                    val rect = Rect().also(n::getBoundsInScreen)
                    // Kunin ang parent bounds kung mas kumpleto ang bubble container
                    val parent = n.parent
                    val bubbleRect = if (parent != null) {
                        val pRect = Rect().also(parent::getBoundsInScreen)
                        if (pRect.height() in rect.height()..(rect.height() * 3) && pRect.bottom >= rect.bottom) {
                            pRect
                        } else rect
                    } else rect
                    out += text to bubbleRect
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(::visit)
        }
        visit(root)
        return out
    }
}
