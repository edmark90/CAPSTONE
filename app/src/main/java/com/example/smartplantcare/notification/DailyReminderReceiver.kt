package com.example.smartplantcare.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires at the exact morning/evening time. Shows the daily plant care reminder
 * and re-arms the alarm for the next day.
 */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("tag") ?: return
        val hour = if (tag == "morning_reminder") 8 else 18
        val minute = 0
        val title = intent.getStringExtra("title") ?: if (tag == "morning_reminder") "\uD83C\uDF05 Good Morning!" else "\uD83C\uDF06 Good Evening!"
        val message = intent.getStringExtra("message") ?: if (tag == "morning_reminder") "Time to check on your plants and start the day's care routine." else "Don't forget to give your plants evening care before the night."

        android.util.Log.d("DailyReminderReceiver", "Daily alarm received: $tag at $hour:$minute")

        val prefs = context.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)

        // Always show daily reminder notification regardless of settings on/off
        NotificationHelper.showDailyReminder(
            context = context,
            notificationId = tag.hashCode(),
            title = title,
            message = message,
            vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        )

        // Always reschedule for next day at 8 AM / 6 PM
        DailyAlarmScheduler.scheduleNext(context, tag, hour, minute, title, message)
    }
}
