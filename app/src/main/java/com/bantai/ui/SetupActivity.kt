package com.bantai.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.rules.RuleFilter
import com.bantai.service.Speaker
import com.bantai.util.PermissionHelper

/**
 * Setup and Onboarding Activity for Apo (Guardian).
 *
 * Responsibilities:
 * - Enter and persist Apo contact details (Name and Phone)
 * - Display live status of Notification Access and Accessibility permissions
 * - Provide 1-tap navigation to system settings to grant permissions
 * - Test Scam Alert trigger verifying rule filter & TTS speech
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: GuardianPreferences
    private var speaker: Speaker? = null

    private lateinit var etApoName: EditText
    private lateinit var etApoPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var layoutNotificationPerm: android.view.View
    private lateinit var layoutAccessibilityPerm: android.view.View
    private lateinit var btnTest: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = GuardianPreferences(this)
        speaker = Speaker(this)

        initViews()
        loadPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker?.shutdown()
        speaker = null
    }

    private fun initViews() {
        etApoName = findViewById(R.id.etApoName)
        etApoPhone = findViewById(R.id.etApoPhone)
        btnSave = findViewById(R.id.btnSave)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        layoutNotificationPerm = findViewById(R.id.layoutNotificationPerm)
        layoutAccessibilityPerm = findViewById(R.id.layoutAccessibilityPerm)
        btnTest = findViewById(R.id.btnTest)
    }

    private fun loadPreferences() {
        etApoName.setText(prefs.apoName)
        etApoPhone.setText(prefs.apoPhone)
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            val name = etApoName.text.toString().trim()
            val phone = etApoPhone.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, R.string.setup_toast_fill_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.apoName = name
            prefs.apoPhone = phone
            prefs.isOnboardingCompleted = true

            Toast.makeText(this, R.string.setup_toast_saved, Toast.LENGTH_SHORT).show()
        }

        layoutNotificationPerm.setOnClickListener {
            startActivity(PermissionHelper.getNotificationAccessSettingsIntent())
        }

        layoutAccessibilityPerm.setOnClickListener {
            startActivity(PermissionHelper.getAccessibilitySettingsIntent())
        }

        btnTest.setOnClickListener {
            triggerTestScamAlert()
        }
    }

    /**
     * Checks permission status in real-time and updates the UI indicators.
     */
    fun updatePermissionStatuses() {
        val notifGranted = PermissionHelper.isNotificationAccessGranted(this)
        if (notifGranted) {
            tvNotificationStatus.setText(R.string.status_granted)
            tvNotificationStatus.setTextColor(Color.parseColor("#198754"))
        } else {
            tvNotificationStatus.setText(R.string.status_not_granted)
            tvNotificationStatus.setTextColor(Color.parseColor("#DC3545"))
        }

        val a11yGranted = PermissionHelper.isAccessibilityServiceEnabled(this)
        if (a11yGranted) {
            tvAccessibilityStatus.setText(R.string.status_granted)
            tvAccessibilityStatus.setTextColor(Color.parseColor("#198754"))
        } else {
            tvAccessibilityStatus.setText(R.string.status_not_granted)
            tvAccessibilityStatus.setTextColor(Color.parseColor("#DC3545"))
        }
    }

    /**
     * Triggers a realistic test scam evaluation and alert.
     */
    private fun triggerTestScamAlert() {
        val sampleScamText = "Congratulations! Nanalo ka ng P50,000 sa ayuda promo. I-click ang bit.ly/claim-ayuda agad bago ma-expire!"

        val result = RuleFilter.score(sampleScamText)
        val warning = RuleFilter.createInstantWarning(result)

        val reasonText = warning?.reason ?: getString(R.string.warning_generic_reason)
        val actionText = warning?.action ?: getString(R.string.warning_generic_action)

        // Speak alert using TTS
        val speechText = "$reasonText. $actionText"
        speaker?.speak(speechText)

        // Show confirmation alert dialog
        AlertDialog.Builder(this)
            .setTitle(R.string.warning_title)
            .setMessage("$reasonText\n\n$actionText\n\n(Signals: ${result.signals.joinToString(", ")})")
            .setPositiveButton(R.string.btn_dismiss) { dialog, _ ->
                speaker?.stop()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}
