package com.example.smartplantcare.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartplantcare.MainActivity
import com.example.smartplantcare.R
import com.example.smartplantcare.data.ImageUtils

object NotificationHelper {
    const val CHANNEL_ID = "treatment_reminder_channel"
    const val DAILY_CHANNEL_ID = "daily_reminder_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Treatment reminders channel (high importance)
            val channel = NotificationChannel(
                CHANNEL_ID, "Plant Treatment Reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily reminders for your plant\'s recovery plan"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#1D4B34")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

            // Daily reminder channel (default importance)
            val dailyChannel = NotificationChannel(
                DAILY_CHANNEL_ID, "Daily Care Reminders", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily morning and evening plant care reminders"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#1D4B34")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(dailyChannel)
        }
    }

    /**
     * Update vibration setting on both notification channels.
     * When vibration is disabled, remove the vibration pattern.
     */
    fun updateVibrationSetting(context: Context, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            val treatmentChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (treatmentChannel != null) {
                treatmentChannel.enableVibration(enabled)
                if (enabled) {
                    treatmentChannel.vibrationPattern = longArrayOf(0, 300, 200, 300)
                } else {
                    treatmentChannel.vibrationPattern = longArrayOf(0)
                }
                notificationManager.createNotificationChannel(treatmentChannel)
            }

            val dailyChannel = notificationManager.getNotificationChannel(DAILY_CHANNEL_ID)
            if (dailyChannel != null) {
                dailyChannel.enableVibration(enabled)
                if (enabled) {
                    dailyChannel.vibrationPattern = longArrayOf(0, 300, 200, 300)
                } else {
                    dailyChannel.vibrationPattern = longArrayOf(0)
                }
                notificationManager.createNotificationChannel(dailyChannel)
            }
        }
    }

    /**
     * Show a daily recurring reminder (morning or evening).
     * Respects the vibration preference from settings.
     */
    fun showDailyReminder(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        vibrationEnabled: Boolean = true
    ) {
        android.util.Log.d("NotificationHelper", "showDailyReminder called: id=$notificationId, title=$title")
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, DAILY_CHANNEL_ID)
            .setSmallIcon(R.drawable.appsicon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setColor(Color.parseColor("#1D4B34"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        // Only set vibration defaults if vibration is enabled
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // On API < 26, use defaults
            if (vibrationEnabled) {
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            } else {
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
            }
        }

        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(notificationId, builder.build())
            android.util.Log.d("NotificationHelper", "Daily reminder notification shown successfully")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error showing daily reminder notification", e)
        }
    }

    /**
     * @param dayIndex 0-based day index (0..6)
     * @param totalDays total length of the treatment plan, default 7
     */
    fun showReminder(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        dayIndex: Int = 0,
        totalDays: Int = 7,
        plantName: String = "",
        plantImageUrl: String = "",
        planId: String = ""
    ) {
        android.util.Log.d("NotificationHelper", "showReminder called: id=$notificationId, title=$title, day=$dayIndex, image=$plantImageUrl")

        val day = dayIndex + 1
        val daysLeft = totalDays - day

        // Large icon — falls back gracefully if the drawable isn't present.
        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        }.getOrNull()

        val plantLabel = plantName.ifBlank { "Plant Care" }
        val bigTitle = "🌱 $plantLabel — Day $day"

        // Create a concise summary for the collapsed notification state to avoid text overflow
        val collapsedText = run {
            val sentenceBoundary = message.indexOf('.')
            if (sentenceBoundary in 1..100) {
                message.substring(0, sentenceBoundary + 1)
            } else if (message.length > 80) {
                message.take(80).trim() + "..."
            } else {
                message
            }
        }

        val statusLine = when {
            daysLeft <= 0 -> "🎉 Final day of recovery plan!"
            daysLeft == 1 -> "🌱 1 day remaining in the treatment plan."
            else          -> "🌱 $daysLeft days remaining in the treatment plan."
        }

        val expandedBody = buildString {
            appendLine("📋 Diagnosis: $title")
            appendLine()
            appendLine("🌿 Action Plan:")
            appendLine(message)
            appendLine()
            append(statusLine)
        }

        val tickerText = "$plantLabel — Day $day: $collapsedText"

        // Create intent to open app when notification is tapped, including the plan_id
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (planId.isNotBlank()) {
                putExtra("plan_id", planId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Load scaled plant image for BigPictureStyle (800x400 fits standard 2:1 aspect ratio)
        val plantBitmap = if (plantImageUrl.isNotBlank()) {
            ImageUtils.loadResizedBitmap(context, plantImageUrl, 800, 400)
        } else {
            null
        }

        // Use BigPictureStyle if image loaded successfully, else fall back to BigTextStyle
        val style = if (plantBitmap != null) {
            NotificationCompat.BigPictureStyle()
                .bigPicture(plantBitmap)
                .bigLargeIcon(null as Bitmap?) // Removes large icon when expanded for a cleaner layout
                .setBigContentTitle(bigTitle)
                .setSummaryText(statusLine)
        } else {
            NotificationCompat.BigTextStyle()
                .bigText(expandedBody)
                .setBigContentTitle(bigTitle)
                .setSummaryText("Treatment Plan Progress")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.appsicon)
            .setContentTitle(bigTitle)
            .setContentText(collapsedText)
            .setSubText("Day $day of $totalDays")
            .setTicker(tickerText)
            .setStyle(style)
            .setLargeIcon(largeIcon)
            .setContentIntent(pendingIntent)
            .setColor(android.graphics.Color.parseColor("#1D4B34"))
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(notificationId, builder.build())
            android.util.Log.d("NotificationHelper", "Treatment reminder notification shown successfully")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error showing treatment reminder notification", e)
        }
    }

}