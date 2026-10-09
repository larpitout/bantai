package com.bantai.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bantai.model.ScreenContext

/**
 * Android Accessibility Service for reading on-screen context and performing
 * senior-assisting global actions (Back, Home).
 */
class BantaiAccessibilityService : AccessibilityService() {

    private val reader = ScreenContextReader()

    companion object {
        @Volatile
        var instance: BantaiAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used for window content change callbacks or real-time inspection
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }

    /**
     * Reads the current screen labels and app context from the active window.
     */
    fun readCurrentScreen(): ScreenContext {
        val root: AccessibilityNodeInfo? = rootInActiveWindow
        return try {
            reader.extractScreenContext(root)
        } finally {
            root?.recycle()
        }
    }

    /**
     * Performs a global BACK navigation action.
     * @return true if action was successfully sent to the system.
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Performs a global HOME navigation action.
     * @return true if action was successfully sent to the system.
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
