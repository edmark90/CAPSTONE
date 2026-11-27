package com.example.smartplantcare.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import com.example.smartplantcare.data.TreatmentPlanRepository

/**
 * One-time upgrade cleanup for users migrating from older app versions that had:
 * 1) 7-day treatment plan AlarmManager PendingIntents, WorkManager jobs, channel, and prefs.
 * 2) Legacy daily morning/evening AlarmManager PendingIntents, channel, and prefs.
 *
 * Strictly preserves active IoT sensor/irrigation alerts and shared device access notifications.
 */
object LegacyTreatmentCleanup {

    private const val TAG = "LegacyTreatmentCleanup"
    private const val PREFS_NAME = "smartplantcare_settings"
    private const val KEY_CLEANUP_DONE = "legacy_treatment_cleanup_v1_done"
    private const val KEY_DAILY_CLEANUP_DONE = "legacy_daily_reminder_cleanup_v1_done"
    private const val LEGACY_TREATMENT_CHANNEL_ID = "treatment_reminder_channel"
    private const val LEGACY_DAILY_CHANNEL_ID = "daily_reminder_channel"
    private const val LEGACY_ALARM_RECEIVER_CLASS =
        "com.example.smartplantcare.notification.AlarmReceiver"
    private const val LEGACY_DAILY_RECEIVER_CLASS =
        "com.example.smartplantcare.notification.DailyReminderReceiver"
    private const val LEGACY_TAG_PREFIX = "treatment_"

    suspend fun runOnceIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_CLEANUP_DONE, false)) {
            cancelLegacyTreatmentReminders(appContext)
            prefs.edit()
                .remove("treatment_morning_enabled")
                .remove("treatment_evening_enabled")
                .remove("treatment_morning_hour")
                .remove("treatment_morning_minute")
                .remove("treatment_evening_hour")
                .remove("treatment_evening_minute")
                .putBoolean(KEY_CLEANUP_DONE, true)
                .apply()
            Log.d(TAG, "Legacy 7-day treatment reminders, alarms, channel, and prefs cleaned up.")
        }

        if (!prefs.getBoolean(KEY_DAILY_CLEANUP_DONE, false)) {
            cancelLegacyDailyReminders(appContext)
            prefs.edit()
                .remove("morning_reminder")
                .remove("evening_reminder")
                .remove("morning_hour")
                .remove("morning_minute")
                .remove("evening_hour")
                .remove("evening_minute")
                .putBoolean(KEY_DAILY_CLEANUP_DONE, true)
                .apply()
            Log.d(TAG, "Legacy morning/evening daily reminder alarms, channel, and prefs cleaned up.")
        }
    }

    fun cancelLegacyDailyReminders(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        for (tag in listOf("morning_reminder", "evening_reminder")) {
            runCatching {
                val legacyIntent = Intent().setComponent(
                    ComponentName(appContext.packageName, LEGACY_DAILY_RECEIVER_CLASS)
                )
                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    tag.hashCode(),
                    legacyIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                )
                if (pendingIntent != null) {
                    alarmManager?.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = appContext.getSystemService(NotificationManager::class.java)
                nm?.deleteNotificationChannel(LEGACY_DAILY_CHANNEL_ID)
            }
        }
    }

    suspend fun cancelLegacyTreatmentReminders(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull()

        // 1. Cancel legacy per-plan AlarmManager PendingIntents & WorkManager jobs for all saved plants
        val savedPlans = runCatching {
            TreatmentPlanRepository(appContext).getAllPlans()
        }.getOrDefault(emptyList())

        for (plan in savedPlans) {
            val planId = plan.id
            if (planId.isBlank()) continue

            for (dayIndex in 0 until 7) {
                for (slot in 1..2) {
                    runCatching {
                        val legacyIntent = Intent().setComponent(
                            ComponentName(appContext.packageName, LEGACY_ALARM_RECEIVER_CLASS)
                        )
                        val requestCode = "${planId}_day${dayIndex}_slot${slot}".hashCode()
                        val pendingIntent = PendingIntent.getBroadcast(
                            appContext,
                            requestCode,
                            legacyIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                        )
                        if (pendingIntent != null) {
                            alarmManager?.cancel(pendingIntent)
                            pendingIntent.cancel()
                        }
                    }
                }
                runCatching {
                    workManager?.cancelUniqueWork("${planId}_day${dayIndex}_overdue")
                    workManager?.cancelUniqueWork("${planId}_day${dayIndex}_boundary")
                }
            }

            runCatching {
                workManager?.cancelAllWorkByTag(LEGACY_TAG_PREFIX + planId)
            }
        }

        // 2. Delete legacy treatment notification channel on Android 8+ (preserving daily & IoT channels)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = appContext.getSystemService(NotificationManager::class.java)
                nm?.deleteNotificationChannel(LEGACY_TREATMENT_CHANNEL_ID)
            }
        }
    }
}
