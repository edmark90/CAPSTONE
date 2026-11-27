package com.example.smartplantcare.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.smartplantcare.MainActivity
import com.example.smartplantcare.R

object NotificationHelper {
    const val IOT_ALERT_CHANNEL_ID = "iot_plant_alerts_channel"
    const val ACCESS_ALERT_CHANNEL_ID = "device_access_alerts_channel"

    const val EXTRA_NOTIFICATION_TYPE = "notif_type"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_DEVICE_ID = "device_id"
    const val TYPE_ACCESS_REVOKED = "ACCESS_REVOKED"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return

            // SmartPlantCare IoT Hardware & Weather Alerts channel (high importance)
            val iotChannel = NotificationChannel(
                IOT_ALERT_CHANNEL_ID, "Smart Garden IoT Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time ESP32 sensor, reservoir, rain suspension, and irrigation alerts"
                enableLights(true)
                lightColor = Color.parseColor("#1D4B34")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            nm.createNotificationChannel(iotChannel)

            // Shared Device Access Alerts channel (high importance)
            val accessChannel = NotificationChannel(
                ACCESS_ALERT_CHANNEL_ID, "Shared Device Access Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when shared ESP32 device monitoring access changes"
                enableLights(true)
                lightColor = Color.parseColor("#1D4B34")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            nm.createNotificationChannel(accessChannel)
        }
    }

    /**
     * Update vibration setting across active notification channels.
     */
    fun updateVibrationSetting(context: Context, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
            val pattern = if (enabled) longArrayOf(0, 300, 200, 300) else longArrayOf(0)

            listOf(IOT_ALERT_CHANNEL_ID, ACCESS_ALERT_CHANNEL_ID).forEach { id ->
                notificationManager.getNotificationChannel(id)?.let { ch ->
                    ch.enableVibration(enabled)
                    ch.vibrationPattern = pattern
                    notificationManager.createNotificationChannel(ch)
                }
            }
        }
    }

    /**
     * Posts a deduplicated Android system notification for a real-time ESP32 IoT event transition.
     */
    fun showIotDeviceAlert(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        boxName: String = "SmartPlantCare Box",
        isCritical: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fullTitle = if (boxName.isNotBlank() && !title.contains(boxName, ignoreCase = true)) {
            "$title • $boxName"
        } else {
            title
        }

        val builder = NotificationCompat.Builder(context, IOT_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.appsicon)
            .setContentTitle(fullTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message).setBigContentTitle(fullTitle))
            .setContentIntent(pendingIntent)
            .setColor(Color.parseColor(if (isCritical) "#B91C1C" else "#1D4B34"))
            .setPriority(
                if (isCritical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(
                if (isCritical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        try {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(notificationId, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error showing IoT alert notification", e)
        }
    }

    /**
     * Posts a notification when a device owner revokes the user's shared monitoring access.
     * Tapping opens MainActivity safely on the Home tab (NEVER opening the restricted device dashboard).
     */
    fun showAccessRevokedNotification(
        context: Context,
        eventId: String,
        deviceId: String,
        title: String,
        message: String
    ) {
        if (!hasNotificationPermission(context)) return
        createChannel(context)

        val notificationId = "access_revoked_$eventId".hashCode()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, TYPE_ACCESS_REVOKED)
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_DEVICE_ID, deviceId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, ACCESS_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.appsicon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message).setBigContentTitle(title))
            .setContentIntent(pendingIntent)
            .setColor(Color.parseColor("#B91C1C"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        try {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(notificationId, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error showing access revoked notification", e)
        }
    }
}
