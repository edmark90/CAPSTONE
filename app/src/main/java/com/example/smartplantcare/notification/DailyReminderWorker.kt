package com.example.smartplantcare.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smartplantcare.data.TreatmentPlanRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker that fires at the configured morning/evening time each day.
 *
 * After showing the notification, it re-schedules itself for the next day
 * so the user receives a recurring daily reminder without needing
 * PeriodicWorkRequest (which has a minimum 15-minute interval).
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tag = inputData.getString("tag") ?: return Result.failure()
        val hour = if (tag == "morning_reminder") 8 else 18
        val minute = 0
        val title = inputData.getString("title") ?: if (tag == "morning_reminder") "\uD83C\uDF05 Good Morning!" else "\uD83C\uDF06 Good Evening!"
        val message = inputData.getString("message") ?: if (tag == "morning_reminder") "Time to check on your plants and start the day's care routine." else "Don't forget to give your plants evening care before the night."

        android.util.Log.d("DailyReminderWorker", "Daily reminder triggered: $tag at $hour:$minute")

        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)
        val vibrationEnabled = prefs.getBoolean("vibration_enabled", true)

        // Show notification (always enabled)
        android.util.Log.d("DailyReminderWorker", "Showing daily reminder notification: $tag")
        try {
            NotificationHelper.showDailyReminder(
                context = applicationContext,
                notificationId = tag.hashCode(),
                title = title,
                message = message,
                vibrationEnabled = vibrationEnabled
            )
            android.util.Log.d("DailyReminderWorker", "Daily reminder notification shown successfully")
        } catch (e: Exception) {
            android.util.Log.e("DailyReminderWorker", "Error showing daily reminder notification", e)
        }

        // Re-schedule for tomorrow at the same time
        schedule(
            context = applicationContext,
            tag = tag,
            hour = hour,
            minute = minute,
            title = title,
            message = message
        )

        return Result.success()
    }

    companion object {
        /**
         * Schedule (or re-schedule) a daily reminder.
         * Uses [ExistingWorkPolicy.REPLACE] so changing the time updates the existing schedule.
         */
        fun schedule(
            context: Context,
            tag: String,
            hour: Int = 8,
            minute: Int = 0,
            title: String = if (tag == "morning_reminder") "\uD83C\uDF05 Good Morning!" else "\uD83C\uDF06 Good Evening!",
            message: String = if (tag == "morning_reminder") "Time to check on your plants and start the day's care routine." else "Don't forget to give your plants evening care before the night."
        ) {
            val fixedHour = if (tag == "morning_reminder") 8 else 18
            val fixedMinute = 0
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, fixedHour)
                set(Calendar.MINUTE, fixedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If the time has already passed today, schedule for tomorrow
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delayMs = calendar.timeInMillis - now

            val inputData = workDataOf(
                "title" to title,
                "message" to message,
                "tag" to tag,
                "hour" to fixedHour,
                "minute" to fixedMinute
            )

            val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(tag)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Cancel a daily reminder by its tag.
         */
        fun cancel(context: Context, tag: String) {
            WorkManager.getInstance(context).cancelUniqueWork(tag)
        }
    }
}
