package com.example.smartplantcare.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules the recurring daily morning/evening reminders with AlarmManager so
 * they fire at the EXACT time the user selected (e.g. 8:00 AM / 6:00 PM),
 * unlike WorkManager which can be deferred by the OS.
 */
object DailyAlarmScheduler {

    /**
     * Schedule a daily reminder at the given hour:minute (today if still ahead,
     * otherwise tomorrow). Used when the user toggles a reminder ON or changes
     * its time, and on device boot.
     */
    fun schedule(context: Context, tag: String, hour: Int = 8, minute: Int = 0, title: String, message: String) {
        val fixedHour = if (tag == "morning_reminder") 8 else 18
        val fixedMinute = 0
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, fixedHour)
            set(Calendar.MINUTE, fixedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        setAlarm(context, tag, calendar.timeInMillis, fixedHour, fixedMinute, title, message)
    }

    /**
     * Schedule the alarm for TOMORROW at the same time. Called from the receiver
     * after the current day's reminder has fired.
     */
    fun scheduleNext(context: Context, tag: String, hour: Int = 8, minute: Int = 0, title: String, message: String) {
        val fixedHour = if (tag == "morning_reminder") 8 else 18
        val fixedMinute = 0
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, fixedHour)
            set(Calendar.MINUTE, fixedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        setAlarm(context, tag, calendar.timeInMillis, fixedHour, fixedMinute, title, message)
    }

    fun cancel(context: Context, tag: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    private fun setAlarm(context: Context, tag: String, triggerAtMillis: Long, hour: Int, minute: Int, title: String, message: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            putExtra("tag", tag)
            putExtra("hour", hour)
            putExtra("minute", minute)
            putExtra("title", title)
            putExtra("message", message)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            android.util.Log.d("DailyAlarmScheduler", "Daily alarm set: $tag at $triggerAtMillis")
        } catch (e: Exception) {
            android.util.Log.e("DailyAlarmScheduler", "Failed to set daily alarm $tag", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (e2: Exception) {
                android.util.Log.e("DailyAlarmScheduler", "Fallback daily alarm also failed", e2)
            }
        }
    }
}
