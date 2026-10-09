package com.bantai.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
        // Debug build lang: adb shell am broadcast -a com.bantai.DEBUG_GOAL --es goal "Go to Facebook"
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            registerReceiver(debugGoal, android.content.IntentFilter("com.bantai.DEBUG_GOAL"), RECEIVER_EXPORTED)
        }
    }

    private val debugGoal = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            intent.getStringExtra("goal")?.let(GabayOverlay::debugStart)
            if (intent.hasExtra("dump")) dumpTree()
        }
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
        val active = rootInActiveWindow ?: return Screen("", emptyList(), scrollable = false)
        val app = active.packageName?.toString().orEmpty()
        // Lahat ng window ng app na nasa harap: ang listahan ng apps ng TECNO launcher ay hiwalay na window
        // sa "Search for apps", kaya ang active window lang ay kulang (Facebook kita sa screen pero hindi nababasa).
        val roots = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == app }
            .ifEmpty { listOf(active) }
        val out = mutableListOf<ScreenButton>()
        var scrollable = false

        fun visit(node: AccessibilityNodeInfo) {
            if (node.isScrollable && node.isVisibleToUser) scrollable = true
            if (out.size >= MAX_BUTTONS) return
            // Ang clickable na lalagyan na may mga clickable sa loob (hal. ang grid ng apps sa launcher) ay hindi button:
            // pasukin ito. Dati, ang buong grid ng 28 apps ay naging isang button na "TikTok".
            if (node.isClickable && node.isVisibleToUser && !hasClickableInside(node)) {
                val label = labelOf(node)
                if (label != null) {
                    out += ScreenButton(label, Rect().also(node::getBoundsInScreen))
                    return // ang mga anak ng button ay bahagi na ng pangalan nito
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        roots.forEach(::visit)
        return Screen(app, out.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })), scrollable)
    }

    private fun hasClickableInside(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable || hasClickableInside(child)) return true
        }
        return false
    }

    private fun dumpTree() {
        fun d(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12) return
            android.util.Log.e("BantaiDump", "${" ".repeat(depth)}${n.className?.toString()?.substringAfterLast('.')} t=${n.text} d=${n.contentDescription} c=${n.isClickable} v=${n.isVisibleToUser} k=${n.childCount}")
            for (i in 0 until n.childCount) n.getChild(i)?.let { d(it, depth + 1) }
        }
        windows.forEach { w -> android.util.Log.e("BantaiDump", "WINDOW type=${w.type} title=${w.title}"); w.root?.let { d(it, 0) } }
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
