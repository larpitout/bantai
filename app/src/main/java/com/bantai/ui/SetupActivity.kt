package com.bantai.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bantai.Bantai
import com.bantai.R
import com.bantai.data.GuardianPreferences
import com.bantai.util.PermissionHelper

/**
 * Setup and Onboarding Activity. Disenyo: "Setup" ng bantai_ui.
 *
 * Responsibilities:
 * - Pause or resume Bantai protection
 * - Display live status of Notification Access, Overlay and Accessibility permissions
 * - Provide 1-tap navigation to system settings to grant permissions
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: GuardianPreferences

    private lateinit var btnSave: View
    private lateinit var switchNotification: SwitchCompat
    private lateinit var switchAccessibility: SwitchCompat
    private lateinit var switchOverlay: SwitchCompat
    private lateinit var layoutNotificationPerm: View
    private lateinit var layoutAccessibilityPerm: View
    private lateinit var layoutOverlayPerm: View
    private lateinit var switchProtection: SwitchCompat
    private lateinit var tvProtectionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = GuardianPreferences(this)
        Bantai.warmUp(this)

        initViews()
        padForSystemBars()
        loadPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun initViews() {
        btnSave = findViewById(R.id.btnSave)
        switchNotification = findViewById(R.id.switchNotification)
        switchAccessibility = findViewById(R.id.switchAccessibility)
        switchOverlay = findViewById(R.id.switchOverlay)
        layoutNotificationPerm = findViewById(R.id.layoutNotificationPerm)
        layoutAccessibilityPerm = findViewById(R.id.layoutAccessibilityPerm)
        layoutOverlayPerm = findViewById(R.id.layoutOverlayPerm)
        switchProtection = findViewById(R.id.switchProtection)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)

        // XML na clipToOutline ay API 31+ pa; minSdk natin ay 26.
        findViewById<View>(R.id.logoFrame).clipToOutline = true
    }

    /** Edge-to-edge sa Android 15+: huwag matakpan ng status at navigation bar. */
    private fun padForSystemBars() {
        val content = findViewById<View>(R.id.setupContent)
        val baseTop = content.paddingTop
        val baseBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }
    }

    private fun loadPreferences() {
        val isEnabled = prefs.isProtectionEnabled
        switchProtection.isChecked = isEnabled
        updateSwitchUi(isEnabled)
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            prefs.isOnboardingCompleted = true
            Toast.makeText(this, R.string.setup_toast_done, Toast.LENGTH_SHORT).show()
            finish()
        }

        layoutNotificationPerm.setOnClickListener {
            startActivity(PermissionHelper.getNotificationAccessSettingsIntent())
        }

        layoutAccessibilityPerm.setOnClickListener {
            startActivity(PermissionHelper.getAccessibilitySettingsIntent())
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
    }

    private fun updateSwitchUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvProtectionStatus.setText(R.string.switch_protection_active)
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.bantai_primary))
        } else {
            tvProtectionStatus.setText(R.string.switch_protection_inactive)
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.bantai_text_muted))
        }
    }

    /**
     * Checks permission status in real-time and updates the UI indicators.
     */
    fun updatePermissionStatuses() {
        switchOverlay.isChecked = ScamAlertOverlay.canShow(this)
        switchNotification.isChecked = PermissionHelper.isNotificationAccessGranted(this)
        switchAccessibility.isChecked = PermissionHelper.isAccessibilityServiceEnabled(this)
    }
}
