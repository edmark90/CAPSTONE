package com.example.smartplantcare.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmScheduler {
    companion object {
        fun scheduleReminder(context: Context, planId: String, dayIndex: Int, slot: Int, triggerAtMillis: Long, diseaseName: String = "", taskText: String = "", plantName: String = "") {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("plan_id", planId)
                putExtra("day_index", dayIndex)
                putExtra("reminder_slot", slot)
                putExtra("disease_name", diseaseName)
                putExtra("task_text", taskText)
                putExtra("plant_name", plantName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                "${planId}_day${dayIndex}_slot${slot}".hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            try {
                // Android 12+ requires SCHEDULE_EXACT_ALARM for exact alarms.
                // If the permission is missing/revoked, fall back to an inexact
                // but still Doze-aware alarm so the reminder is never lost.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                android.util.Log.d("AlarmScheduler", "Alarm set for $triggerAtMillis (plan=$planId day=$dayIndex slot=$slot)")
            } catch (e: Exception) {
                // Never let a scheduling failure silently kill the reminder chain.
                android.util.Log.e("AlarmScheduler", "Failed to schedule alarm, falling back to inexact", e)
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } catch (e2: Exception) {
                    android.util.Log.e("AlarmScheduler", "Fallback alarm also failed", e2)
                }
            }
        }

        fun cancelReminder(context: Context, planId: String, dayIndex: Int, slot: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                "${planId}_day${dayIndex}_slot${slot}".hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }
}
