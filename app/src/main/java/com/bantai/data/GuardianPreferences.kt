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
        private const val KEY_APO_NAME = "apo_name"
        private const val KEY_APO_PHONE = "apo_phone"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    var apoName: String
        get() = prefs.getString(KEY_APO_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_APO_NAME, value.trim()).apply()

    var apoPhone: String
        get() = prefs.getString(KEY_APO_PHONE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_APO_PHONE, value.trim()).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    fun isConfigured(): Boolean {
        return apoName.isNotBlank() && apoPhone.isNotBlank()
    }
}
