package com.bantai.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persisted guardian (Apo) configuration.
 */
class GuardianPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bantai_guardian_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
    }

    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
}
