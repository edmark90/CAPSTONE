package com.example.smartplantcare.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import com.example.smartplantcare.data.TreatmentPlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires from AlarmManager at the exact scheduled time for a 7-day treatment
 * reminder. Runs the same validation as TreatmentReminderWorker so stale or
 * disabled reminders never appear.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra("plan_id") ?: return
        val dayIndex = intent.getIntExtra("day_index", 0)
        val slot = intent.getIntExtra("reminder_slot", 1)
        val diseaseName = intent.getStringExtra("disease_name") ?: "Plant"
        val taskText = intent.getStringExtra("task_text") ?: "Check on your plant"
        val plantName = intent.getStringExtra("plant_name") ?: ""

        android.util.Log.d("AlarmReceiver", "Alarm received: planId=$planId, day=$dayIndex, slot=$slot")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val repository = TreatmentPlanRepository(appContext)
                val plan = repository.getPlan(planId)
                if (plan == null) {
                    android.util.Log.d("AlarmReceiver", "Plan not found, skipping")
                    return@launch
                }

                if (plan.completedDays.getOrElse(dayIndex) { false }) {
                    android.util.Log.d("AlarmReceiver", "Day already completed, skipping")
                    return@launch
                }

                val currentDayIndex = plan.completedDays.indexOfFirst { !it }
                if (currentDayIndex == -1 || dayIndex != currentDayIndex) {
                    android.util.Log.d("AlarmReceiver", "Not the current active day, skipping")
                    return@launch
                }

                val prefs = appContext.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)
                val slotEnabled = when (slot) {
                    1 -> prefs.getBoolean("treatment_morning_enabled", true)
                    2 -> prefs.getBoolean("treatment_evening_enabled", true)
                    else -> true
                }
                if (!slotEnabled) {
                    android.util.Log.d("AlarmReceiver", "Slot $slot disabled in settings, skipping")
                    return@launch
                }

                NotificationHelper.showReminder(
                    context = appContext,
                    notificationId = "${planId}_day${dayIndex}_slot${slot}".hashCode(),
                    title = diseaseName,
                    message = "$plantName — $taskText",
                    dayIndex = dayIndex,
                    plantName = plantName,
                    plantImageUrl = plan.imageUrl,
                    planId = planId
                )


                val notification = NotificationHistory(
                    plantId = planId,
                    plantName = plan.plantName,
                    plantImageUrl = plan.imageUrl,
                    day = dayIndex + 1,
                    title = diseaseName,
                    message = "$plantName \u2014 $taskText",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    isCompleted = plan.completedDays.getOrElse(dayIndex) { false }
                )
                NotificationHistoryRepository(appContext).saveNotification(notification)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
