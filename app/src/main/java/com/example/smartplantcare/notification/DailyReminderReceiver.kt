package com.example.smartplantcare.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires at the exact morning/evening time. Shows the daily plant care reminder
 * and re-arms the alarm for the next day if still enabled in settings.
 */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("tag") ?: return
        val prefs = context.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)

        val isMorning = tag == "morning_reminder"
        val isEnabled = prefs.getBoolean(tag, true)
        if (!isEnabled) {
            android.util.Log.d("DailyReminderReceiver", "Daily alarm $tag is disabled in settings, skipping")
            return
        }

        val defaultHour = if (isMorning) 8 else 18
        val hour = prefs.getInt(
            if (isMorning) "morning_hour" else "evening_hour",
            intent.getIntExtra("hour", defaultHour)
        )
        val minute = prefs.getInt(
            if (isMorning) "morning_minute" else "evening_minute",
            intent.getIntExtra("minute", 0)
        )
        val title = intent.getStringExtra("title")
            ?: if (isMorning) "\uD83C\uDF05 Good Morning!" else "\uD83C\uDF06 Good Evening!"
        val message = intent.getStringExtra("message")
            ?: if (isMorning) "Time to check on your plants and start the day's care routine."
            else "Don't forget to give your plants evening care before the night."

        android.util.Log.d("DailyReminderReceiver", "Daily alarm received: $tag at $hour:$minute")

        NotificationHelper.showDailyReminder(
            context = context,
            notificationId = tag.hashCode(),
            title = title,
            message = message,
            vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        )

        // Reschedule for the next day at the user's configured time
        DailyAlarmScheduler.scheduleNext(context, tag, hour, minute, title, message)

        // Record in NotificationHistoryRepository so it appears in NotificationHistoryScreen
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notification = NotificationHistory(
                    plantId = "",
                    plantName = title,
                    plantImageUrl = "",
                    day = 1,
                    title = title,
                    message = message,
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    isCompleted = true
                )
                NotificationHistoryRepository(appContext).saveNotification(notification)
            } catch (e: Exception) {
                android.util.Log.e("DailyReminderReceiver", "Failed to save daily reminder to history", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
