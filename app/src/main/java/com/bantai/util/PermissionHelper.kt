package com.bantai.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.bantai.service.BantaiAccessibilityService
import com.bantai.service.BantaiNotificationListener

/**
 * Checks and requests necessary system permissions for Bantai.
 */
object PermissionHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Diretsong tanong na "Allow"; kung hindi suportado ng phone, ang listahan ng battery settings. */
    fun getBatteryOptimizationIntent(context: Context): Intent {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))
        return if (direct.resolveActivity(context.packageManager) != null) direct
        else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    const val REQUEST_CONTACTS = 3

    /** Contacts: tanong ng Android; kapag naka-on na, ang settings ng app (doon ito pinapatay). */
    fun requestContacts(activity: android.app.Activity) {
        if (ContactHelper.hasPermission(activity)) activity.startActivity(appSettingsIntent(activity))
        else activity.requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
    }

    /** Tawagin sa onRequestPermissionsResult: kapag hindi na nagtatanong ang Android ("Don't allow" nang dalawang beses), sa settings ng app. */
    fun onContactsResult(activity: android.app.Activity, requestCode: Int) {
        if (requestCode == REQUEST_CONTACTS && !ContactHelper.hasPermission(activity) &&
            !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CONTACTS)
        ) {
            activity.startActivity(appSettingsIntent(activity))
        }
    }

    private fun appSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))

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
     * Checks if BantaiAccessibilityService has been enabled in Accessibility settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = ComponentName(context, BantaiAccessibilityService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (!flat.isNullOrEmpty()) {
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(flat)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledService = ComponentName.unflattenFromString(componentNameString)
                if (enabledService != null && enabledService == expectedService) {
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

    /**
     * Creates an Intent to open the Accessibility settings screen.
     */
    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}
