package com.bantai.ui

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
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.service.Speaker

/**
 * Babala card na lumalabas sa ibabaw ng kahit anong app (SYSTEM_ALERT_WINDOW).
 * Isang card lang sabay-sabay; pinapalitan ng bagong babala ang luma.
 * Tawagin sa main thread.
 */
object ScamAlertOverlay {

    private const val TAG = "ScamAlertOverlay"

    private var current: View? = null
    private var speaker: Speaker? = null

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    @SuppressLint("InflateParams")
    fun show(context: Context, reason: String, action: String, fromAi: Boolean = false) {
        val app = context.applicationContext
        if (!canShow(app)) {
            Log.w(TAG, "Walang 'Display over other apps' permission, hindi maipakita ang babala")
            return
        }
        dismiss(app)

        val wm = app.getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(app).inflate(R.layout.overlay_scam_alert, null)
        val tts = speaker ?: Speaker(app).also { speaker = it }

        view.findViewById<TextView>(R.id.tvAlertReason).text = reason
        view.findViewById<TextView>(R.id.tvAlertAction).text = action
        view.findViewById<View>(R.id.tvAlertAiBadge).visibility = if (fromAi) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.btnAlertListen).setOnClickListener {
            tts.speak("${app.getString(R.string.warning_title)}. $reason $action")
        }
        view.findViewById<View>(R.id.btnAlertDismiss).setOnClickListener { dismiss(app) }

        val apoPhone = GuardianPreferences(app).apoPhone
        view.findViewById<View>(R.id.btnAlertCallApo).apply {
            visibility = if (apoPhone.isBlank()) View.GONE else View.VISIBLE
            setOnClickListener {
                dismiss(app)
                // ACTION_DIAL: bubuksan lang ang dialer, si Nanay pa rin ang pipindot ng tawag.
                app.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$apoPhone"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Hindi kinukuha ang keyboard focus; gumagana pa rin ang app sa likod.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        wm.addView(view, params)
        current = view
    }

    fun dismiss(context: Context) {
        val view = current ?: return
        speaker?.stop()
        context.applicationContext.getSystemService(WindowManager::class.java).removeView(view)
        current = null
    }
}
