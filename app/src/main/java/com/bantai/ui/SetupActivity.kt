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
import com.bantai.util.ContactHelper
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

    // Iisang wika sa buong app: Tagalog kung walang pinili (pareho ng babala).
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Bantai.localized(newBase))
    }

    private lateinit var prefs: GuardianPreferences

    private lateinit var btnSave: View
    private lateinit var switchNotification: SwitchCompat
    private lateinit var switchAccessibility: SwitchCompat
    private lateinit var switchOverlay: SwitchCompat
    private lateinit var layoutNotificationPerm: View
    private lateinit var layoutAccessibilityPerm: View
    private lateinit var layoutOverlayPerm: View
    private lateinit var switchBattery: SwitchCompat
    private lateinit var layoutBatteryPerm: View
    private lateinit var switchCall: SwitchCompat
    private lateinit var layoutCallPerm: View
    private lateinit var switchContacts: SwitchCompat
    private lateinit var layoutContactsPerm: View
    private lateinit var switchProtection: SwitchCompat
    private lateinit var tvProtectionStatus: TextView
    private lateinit var tvProtectionDesc: TextView

    /** Pag-on/off mula sa code (hindi sa user): huwag ipakita ang confirm dialog. */
    private var suppressProtectionListener = false

    /** Pag-off ng proteksyon: may kumpirmasyon muna; pag-on: agad-agad. */
    private val protectionListener = android.widget.CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (suppressProtectionListener) return@OnCheckedChangeListener
        if (isChecked) {
            prefs.isProtectionEnabled = true
            updateSwitchUi(true)
        } else {
            ProtectionToggle.confirmPause(
                activity = this,
                onConfirm = {
                    prefs.isProtectionEnabled = false
                    updateSwitchUi(false)
                },
                onCancel = {
                    // Binalik sa ON nang hindi inuulit ang dialog.
                    suppressProtectionListener = true
                    switchProtection.isChecked = true
                    suppressProtectionListener = false
                },
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = GuardianPreferences(this)
        Bantai.warmUp(this)
        // Android 13+: kailangan ng pahintulot para sa "Posibleng scam" na notification.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

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
        switchBattery = findViewById(R.id.switchBattery)
        layoutBatteryPerm = findViewById(R.id.layoutBatteryPerm)
        switchCall = findViewById(R.id.switchCall)
        layoutCallPerm = findViewById(R.id.layoutCallPerm)
        switchContacts = findViewById(R.id.switchContacts)
        layoutContactsPerm = findViewById(R.id.layoutContactsPerm)
        switchProtection = findViewById(R.id.switchProtection)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        tvProtectionDesc = findViewById(R.id.tvProtectionDesc)

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
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }

        layoutNotificationPerm.setOnClickListener {
            startActivity(PermissionHelper.getNotificationAccessSettingsIntent())
        }

        layoutAccessibilityPerm.setOnClickListener {
            startActivity(PermissionHelper.getAccessibilitySettingsIntent())
        }

        layoutBatteryPerm.setOnClickListener {
            // Para hindi patayin ng phone (OPPO, TECNO…) si Bantai sa background.
            startActivity(PermissionHelper.getBatteryOptimizationIntent(this))
        }

        layoutCallPerm.setOnClickListener {
            // Android 10+: gawing "Caller ID & spam app" si Bantai para makapagbabala sa tawag.
            PermissionHelper.openCallScreening(this)
        }

        layoutContactsPerm.setOnClickListener {
            // Opsyonal: kapag tinanggihan, karaniwang babala pa rin (walang "kakilala").
            PermissionHelper.requestContacts(this)
        }

        layoutOverlayPerm.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        switchProtection.setOnCheckedChangeListener(protectionListener)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.onContactsResult(this, requestCode)
        updatePermissionStatuses()
    }

    private fun updateSwitchUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvProtectionStatus.setText(R.string.switch_protection_active)
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.bantai_primary))
            tvProtectionDesc.setText(R.string.switch_protection_desc_on)
        } else {
            tvProtectionStatus.setText(R.string.switch_protection_inactive)
            tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.bantai_text_muted))
            tvProtectionDesc.setText(R.string.switch_protection_desc_off)
        }
    }

    /**
     * Checks permission status in real-time and updates the UI indicators.
     */
    fun updatePermissionStatuses() {
        switchOverlay.isChecked = ScamAlertOverlay.canShow(this)
        switchNotification.isChecked = PermissionHelper.isNotificationAccessGranted(this)
        switchAccessibility.isChecked = PermissionHelper.isAccessibilityServiceEnabled(this)
        switchBattery.isChecked = PermissionHelper.isIgnoringBatteryOptimizations(this)
        switchContacts.isChecked = ContactHelper.hasPermission(this)
        switchCall.isChecked = PermissionHelper.isCallScreeningRole(this)
    }
}
