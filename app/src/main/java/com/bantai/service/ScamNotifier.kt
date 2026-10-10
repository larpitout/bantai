package com.bantai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bantai.Bantai
import com.bantai.R
import com.bantai.ui.DashboardActivity

/**
 * Sariling notification ni Bantai: "Posibleng scam mula sa …".
 * Pagpindot, binubuksan ang mismong chat (ang link ng orihinal na notification), kaya doon makikita ang babala.
 */
object ScamNotifier {

    private const val CHANNEL = "scam_alerts"

    fun notify(context: Context, sender: String, reason: String, openChat: PendingIntent?, titleRes: Int = R.string.notif_title) {
        val app = context.applicationContext
        val text = Bantai.localized(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, text.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        // Walang link ng chat (hal. test): ang dashboard ni Bantai.
        val open = openChat ?: PendingIntent.getActivity(
            app, 0, Intent(app, DashboardActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(text.getString(titleRes, sender))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\n${text.getString(R.string.notif_tap_hint)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setColor(app.getColor(R.color.caution_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // ponytail: walang POST_NOTIFICATIONS → tahimik na hindi lalabas; ang babala sa chat ay gumagana pa rin.
        runCatching { nm.notify(sender.hashCode(), notification) }
    }
}
