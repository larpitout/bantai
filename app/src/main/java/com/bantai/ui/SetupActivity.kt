package com.bantai.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.rules.RuleFilter
import com.bantai.util.PermissionHelper

/**
 * Setup and Onboarding Activity for Apo (Guardian).
 *
 * Responsibilities:
 * - Enter and persist Apo contact details (Name and Phone)
 * - Display live status of Notification Access and overlay permissions
 * - Provide 1-tap navigation to system settings to grant permissions
 * - Test Scam Alert trigger verifying rule filter & TTS speech
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: GuardianPreferences

    private lateinit var etApoName: EditText
    private lateinit var etApoPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var tvNotificationStatus: TextView
    private lateinit var layoutNotificationPerm: android.view.View
    private lateinit var tvOverlayStatus: TextView
    private lateinit var layoutOverlayPerm: android.view.View
    private lateinit var switchProtection: androidx.appcompat.widget.SwitchCompat
    private lateinit var tvProtectionStatus: TextView
    private lateinit var btnTest: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = GuardianPreferences(this)
        Bantai.warmUp(this)

        initViews()
        loadPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun initViews() {
        etApoName = findViewById(R.id.etApoName)
        etApoPhone = findViewById(R.id.etApoPhone)
        btnSave = findViewById(R.id.btnSave)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        layoutNotificationPerm = findViewById(R.id.layoutNotificationPerm)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        layoutOverlayPerm = findViewById(R.id.layoutOverlayPerm)
        switchProtection = findViewById(R.id.switchProtection)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        btnTest = findViewById(R.id.btnTest)
    }

    private fun loadPreferences() {
        etApoName.setText(prefs.apoName)
        etApoPhone.setText(prefs.apoPhone)
        val isEnabled = prefs.isProtectionEnabled
        switchProtection.isChecked = isEnabled
        updateSwitchUi(isEnabled)
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

        layoutOverlayPerm.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.isProtectionEnabled = isChecked
            updateSwitchUi(isChecked)
        }

        btnTest.setOnClickListener {
            triggerTestScamAlert()
        }
    }

    private fun updateSwitchUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvProtectionStatus.setText(R.string.switch_protection_active)
            tvProtectionStatus.setTextColor(Color.parseColor("#198754"))
        } else {
            tvProtectionStatus.setText(R.string.switch_protection_inactive)
            tvProtectionStatus.setTextColor(Color.parseColor("#6C757D"))
        }
    }

    /**
     * Checks permission status in real-time and updates the UI indicators.
     */
    fun updatePermissionStatuses() {
        val overlayGranted = ScamAlertOverlay.canShow(this)
        tvOverlayStatus.setText(if (overlayGranted) R.string.status_granted else R.string.status_not_granted)
        tvOverlayStatus.setTextColor(Color.parseColor(if (overlayGranted) "#198754" else "#DC3545"))

        val notifGranted = PermissionHelper.isNotificationAccessGranted(this)
        if (notifGranted) {
            tvNotificationStatus.setText(R.string.status_granted)
            tvNotificationStatus.setTextColor(Color.parseColor("#198754"))
        } else {
            tvNotificationStatus.setText(R.string.status_not_granted)
            tvNotificationStatus.setTextColor(Color.parseColor("#DC3545"))
        }
    }

    /**
     * Ipinapakita ang totoong overlay na babala gamit ang sample na scam.
     */
    private fun triggerTestScamAlert() {
        if (!ScamAlertOverlay.canShow(this)) {
            Toast.makeText(this, R.string.setup_perm_overlay_desc, Toast.LENGTH_LONG).show()
            return
        }
        val sampleScamText = "Ma si Junjun to bagong number ko padala ka 5k sa gcash emergency lang"
        val (reasonRes, actionRes) = RuleFilter.instantWarningRes(RuleFilter.score(sampleScamText))
        ScamAlertOverlay.show(this, getString(reasonRes), getString(actionRes))
    }
}
