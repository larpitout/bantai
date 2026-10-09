package com.bantai.ui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.bantai.Bantai
import com.bantai.R

/**
 * Ang babala, sa dalawang paraan:
 * - [showFlagged]: banner sa ibaba ng chat kapag binuksan ni Nanay ang na-flag na mensahe (accessibility overlay).
 * - [show]: popup sa ibabaw ng kahit anong app, kapag hindi naka-on ang Accessibility (fallback).
 * Isang babala lang sabay-sabay. Tawagin sa main thread.
 */
object ScamAlertOverlay {

    private const val TAG = "ScamAlertOverlay"

    private var current: View? = null
    private var currentWm: WindowManager? = null
    private var currentFlag: Bantai.Flagged? = null
    private var currentFromAi = false

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    /** Popup agad (fallback kapag walang Accessibility). */
    fun show(context: Context, reason: String, action: String, fromAi: Boolean = false, message: String = "", signals: List<String> = emptyList()) {
        val app = context.applicationContext
        if (!canShow(app)) {
            Log.w(TAG, "Walang 'Display over other apps' permission, hindi maipakita ang babala")
            return
        }
        present(app, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, reason, action, fromAi, message, signals, score = 0, bottomOffsetDp = 0) { dismiss(app) }
    }

    /** Banner sa loob ng bukas na chat, para sa mensaheng na-flag. */
    fun showFlagged(service: AccessibilityService, flag: Bantai.Flagged, bubble: Rect? = null) {
        val view = current
        if (view != null && currentFlag === flag && currentFromAi == flag.fromAi) {
            // Nakalabas na: sundan lang ang bubble kapag nag-scroll si Nanay.
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            val h = if (view.height > 0) view.height else view.measuredHeight
            if (placeUnder(service, params, bubble, h, bottomOffsetDp = 72)) {
                runCatching { currentWm?.updateViewLayout(view, params) }
            }
            return
        }
        present(
            service, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flag.reason, flag.action, flag.fromAi, flag.message, flag.signals, flag.score,
            // Nasa itaas ng text box ng chat, para makapag-type pa rin si Nanay.
            bottomOffsetDp = 72,
            anchor = bubble,
        ) {
            flag.dismissed = true // "Sige po": hindi na lalabas ulit para sa mensaheng ito
            dismiss(service)
        }
        currentFlag = flag
        currentFromAi = flag.fromAi
    }

    @SuppressLint("InflateParams")
    private fun present(
        ctx: Context,
        type: Int,
        reason: String,
        action: String,
        fromAi: Boolean,
        message: String,
        signals: List<String>,
        score: Int,
        bottomOffsetDp: Int,
        anchor: Rect? = null,
        onDismiss: () -> Unit,
    ) {
        dismiss(ctx)
        val app = ctx.applicationContext
        // Ang accessibility overlay ay kailangang idagdag gamit ang WindowManager ng service mismo.
        val wm = ctx.getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(Bantai.localized(app)).inflate(R.layout.overlay_scam_alert, null)
        val tts = Bantai.speaker(app)

        view.findViewById<TextView>(R.id.tvAlertReason).text = reason
        view.findViewById<TextView>(R.id.tvAlertAction).text = action
        // Audit: gaano kapanganib at sino ang nagpasya (rules o AI sa phone).
        val loc = Bantai.localized(app)
        val hasRisk = score > 0
        view.findViewById<TextView>(R.id.tvAlertRisk).apply {
            visibility = if (hasRisk) View.VISIBLE else View.GONE
            val level = loc.getString(
                when { score >= 3 -> R.string.risk_high; score == 2 -> R.string.risk_medium; else -> R.string.risk_low }
            )
            text = loc.getString(R.string.risk_line, level, loc.getString(if (fromAi) R.string.decided_by_ai else R.string.decided_by_rules))
        }
        // Ipakita lang ang hiwalay na badge kung walang risk line para hindi doble ang "checked by AI"
        view.findViewById<View>(R.id.tvAlertAiBadge).visibility = if (fromAi && !hasRisk) View.VISIBLE else View.GONE

        // "Bakit na-flag?": ang mga nakitang senyales, para may paliwanag at hindi lang "scam".
        val why = view.findViewById<TextView>(R.id.tvAlertWhy)
        why.text = signals.joinToString("\n") { "• $it" }
        view.findViewById<View>(R.id.btnAlertWhy).apply {
            visibility = if (signals.isEmpty()) View.GONE else View.VISIBLE
            setOnClickListener { why.visibility = if (why.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }

        view.findViewById<View>(R.id.btnAlertListen).setOnClickListener {
            tts.speak("${Bantai.localized(app).getString(R.string.warning_title)}. $reason $action")
        }
        view.findViewById<View>(R.id.btnAlertDismiss).setOnClickListener { onDismiss() }

        // Sukatin ang view para makuha ang totoong pixel height bago ipuwesto
        val metrics = app.resources.displayMetrics
        view.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST)
        )
        val measuredHeight = view.measuredHeight
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Hindi kinukuha ang keyboard focus; layout in screen para tugma sa getBoundsInScreen coordinates.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            placeUnder(app, this, anchor, measuredHeight, bottomOffsetDp)
        }

        wm.addView(view, params)
        current = view
        currentWm = wm
    }

    /**
     * Ilagay ang babala kaugnay sa bubble ng scammer nang hindi tinatakpan ang mensahe:
     * - Kung kasya sa ibaba: sa mismong ilalim ng bubble (bubble.bottom + margin).
     * - Kung nasa ibaba na ng screen ang bubble (malapit sa text box ng chat): ilagay sa ITAAS ng bubble
     *   (bubble.top - height - margin) para manatiling kitang-kita ni Nanay ang mensahe at hindi matakpan!
     * - Kapag nag-scroll si Nanay at umusad ang bubble, susunod ang babala.
     */
    private fun placeUnder(
        ctx: Context,
        params: WindowManager.LayoutParams,
        bubble: Rect?,
        measuredHeight: Int = 0,
        bottomOffsetDp: Int = 0,
    ): Boolean {
        val metrics = ctx.resources.displayMetrics
        val density = metrics.density
        val margin = (6 * density).toInt()
        val composerOffset = ((if (bottomOffsetDp > 0) bottomOffsetDp else 72) * density).toInt()
        val statusBarOffset = (36 * density).toInt()
        val height = if (measuredHeight > 0) measuredHeight else (260 * density).toInt()

        val (gravity, y) = if (bubble != null) {
            val spaceBelow = metrics.heightPixels - bubble.bottom - composerOffset
            val spaceAbove = bubble.top - statusBarOffset

            if (spaceBelow >= height) {
                // May sapat na espasyo sa ibaba ng bubble: sa mismong ilalim ilagay
                (Gravity.TOP or Gravity.START) to (bubble.bottom + margin)
            } else if (spaceAbove >= height) {
                // Nasa ibaba ng screen ang bubble: ilagay sa ITAAS ng bubble para hindi matakpan ang message!
                (Gravity.TOP or Gravity.START) to (bubble.top - height - margin).coerceAtLeast(statusBarOffset)
            } else {
                // Kung mas malaki ang espasyo sa itaas kaysa sa ibaba
                if (spaceAbove >= spaceBelow) {
                    (Gravity.TOP or Gravity.START) to (bubble.top - height - margin).coerceAtLeast(statusBarOffset)
                } else {
                    (Gravity.TOP or Gravity.START) to (bubble.bottom + margin)
                }
            }
        } else {
            Gravity.BOTTOM to composerOffset
        }

        if (params.gravity == gravity && params.y == y) return false
        params.gravity = gravity
        params.y = y
        return true
    }

    fun dismiss(context: Context) {
        val view = current ?: return
        Bantai.speaker(context).stop()
        runCatching { currentWm?.removeView(view) }
        current = null
        currentWm = null
        currentFlag = null
    }
}
