package com.bantai.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bantai.Bantai
import com.bantai.ui.GabayOverlay

/** Isang pinipindot na button sa screen: pangalan at kung nasaan ito. */
data class ScreenButton(val label: String, val bounds: Rect)

/** Ang app na bukas, ang mga button nito, at kung may maisi-scroll pa (para maituro ang pag-scroll). */
data class Screen(val app: String, val buttons: List<ScreenButton>, val scrollable: Boolean)

/**
 * Para sa Gabay: binabasa ang mga BUTTON sa screen (hindi ang laman ng chats) para maituro kay Nanay.
 * Hindi ito pumipindot para sa kanya; ang chathead at highlight lang ang ipinapakita (TYPE_ACCESSIBILITY_OVERLAY).
 */
class BantaiAccessibilityService : AccessibilityService() {

    companion object {
        private const val MAX_BUTTONS = 80

        @Volatile
        var instance: BantaiAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Bantai.warmUp(this)
        GabayOverlay.showBubble(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        GabayOverlay.hideAll()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pumindot si Nanay o nagbago ang screen: tapos na ang itinurong hakbang.
        if (event?.packageName == packageName) return
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            GabayOverlay.onScreenChanged()
        }
    }

    override fun onInterrupt() {}

    /** Ang app na bukas at ang mga nakikitang button nito sa ayos mula itaas pababa. */
    fun readButtons(): Screen {
        val root = rootInActiveWindow ?: return Screen("", emptyList(), scrollable = false)
        val app = root.packageName?.toString().orEmpty()
        val out = mutableListOf<ScreenButton>()
        var scrollable = false

        fun visit(node: AccessibilityNodeInfo) {
            if (node.isScrollable && node.isVisibleToUser) scrollable = true
            if (out.size >= MAX_BUTTONS) return
            if (node.isClickable && node.isVisibleToUser) {
                val label = labelOf(node)
                if (label != null) {
                    out += ScreenButton(label, Rect().also(node::getBoundsInScreen))
                    return // ang mga anak ng button ay bahagi na ng pangalan nito
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        visit(root)
        return Screen(app, out.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })), scrollable)
    }

    /** Sariling text o paglalarawan ng button; kung wala, ang unang text sa loob nito (hal. icon + label). */
    private fun labelOf(node: AccessibilityNodeInfo): String? {
        // Text muna ("Messenger"), saka paglalarawan ("Messenger has 2 notifications") para sa mga icon na walang text.
        val own = node.text?.toString()?.trim()?.ifEmpty { null } ?: node.contentDescription?.toString()?.trim()
        if (!own.isNullOrEmpty()) return own
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            labelOf(child)?.let { return it }
        }
        return null
    }
}
