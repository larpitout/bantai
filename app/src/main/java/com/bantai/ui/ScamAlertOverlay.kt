package com.bantai.ui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences

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
        present(app, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, reason, action, fromAi, message, signals, bottomOffsetDp = 0) { dismiss(app) }
    }

    /** Banner sa loob ng bukas na chat, para sa mensaheng na-flag. */
    fun showFlagged(service: AccessibilityService, flag: Bantai.Flagged) {
        if (current != null && currentFlag === flag && currentFromAi == flag.fromAi) return // nakalabas na
        present(
            service, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flag.reason, flag.action, flag.fromAi, flag.message, flag.signals,
            // Nasa itaas ng text box ng chat, para makapag-type pa rin si Nanay.
            bottomOffsetDp = 72,
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
        bottomOffsetDp: Int,
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
        view.findViewById<View>(R.id.tvAlertAiBadge).visibility = if (fromAi) View.VISIBLE else View.GONE

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

        val apoPhone = GuardianPreferences(app).apoPhone
        view.findViewById<View>(R.id.btnAlertCallApo).apply {
            visibility = if (apoPhone.isBlank()) View.GONE else View.VISIBLE
            setOnClickListener {
                onDismiss()
                // ACTION_DIAL: bubuksan lang ang dialer, si Nanay pa rin ang pipindot ng tawag.
                app.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$apoPhone"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        // "Sabihan si Apo": bubuksan ang SMS kay Apo na may nakahandang text; si Nanay pa rin ang magse-send.
        view.findViewById<TextView>(R.id.btnAlertTellApo).apply {
            val name = GuardianPreferences(app).apoName.ifBlank { null }
            visibility = if (apoPhone.isBlank() || message.isBlank()) View.GONE else View.VISIBLE
            name?.let { text = Bantai.localized(app).getString(R.string.btn_tell_apo_named, it) }
            setOnClickListener {
                onDismiss()
                val body = Bantai.localized(app).getString(R.string.tell_apo_body, message.take(160))
                app.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$apoPhone"))
                        .putExtra("sms_body", body)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Hindi kinukuha ang keyboard focus; gumagana pa rin ang app sa likod.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = (bottomOffsetDp * app.resources.displayMetrics.density).toInt()
        }

        wm.addView(view, params)
        current = view
        currentWm = wm
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
