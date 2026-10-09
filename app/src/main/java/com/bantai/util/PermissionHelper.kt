package com.bantai.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.bantai.service.BantaiNotificationListener

/**
 * Checks and requests necessary system permissions for Bantai.
 */
object PermissionHelper {

    /**
     * Checks if NotificationListenerService access has been granted to Bantai.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val component = ComponentName.unflattenFromString(name)
                if (component != null && TextUtils.equals(packageName, component.packageName)) {
                    return true
                }
            }
        }
        return false
    }


    /**
     * Creates an Intent to open the Notification Listener settings screen.
     */
    fun getNotificationAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

}
