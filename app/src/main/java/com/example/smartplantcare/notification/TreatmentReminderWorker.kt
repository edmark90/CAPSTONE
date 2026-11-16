package com.example.smartplantcare.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import com.example.smartplantcare.data.TreatmentPlanRepository
import java.util.concurrent.TimeUnit

class TreatmentReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val planId = inputData.getString("plan_id") ?: return Result.failure()
        val dayIndex = inputData.getInt("day_index", 0)
        val diseaseName = inputData.getString("disease_name") ?: "Plant"
        val taskText = inputData.getString("task_text") ?: "Check on your plant"
        val plantName = inputData.getString("plant_name") ?: ""
        val reminderType = inputData.getString("reminder_type") ?: "normal"

        android.util.Log.d("TreatmentReminderWorker", "Treatment reminder triggered: planId=$planId, dayIndex=$dayIndex, type=$reminderType")

        val repository = TreatmentPlanRepository(applicationContext)
        val plan = repository.getPlan(planId)

        // If plan no longer exists, skip
        if (plan == null) {
            android.util.Log.d("TreatmentReminderWorker", "Plan not found: $planId")
            return Result.success()
        }

        android.util.Log.d("TreatmentReminderWorker", "Plan found: ${plan.plantName}, completedDays: ${plan.completedDays}")

        // Check if this day is already completed
        if (plan.completedDays.getOrElse(dayIndex) { false }) {
            android.util.Log.d("TreatmentReminderWorker", "Day $dayIndex already completed, skipping")
            return Result.success()
        }

        // SEQUENTIAL CHECK: Only send notifications for the current active day
        val currentDayIndex = plan.completedDays.indexOfFirst { !it }
        android.util.Log.d("TreatmentReminderWorker", "Current active day: $currentDayIndex, requested day: $dayIndex")
        
        if (currentDayIndex == -1 || dayIndex != currentDayIndex) {
            // This is not the current active day or all days are completed - skip notification
            android.util.Log.d("TreatmentReminderWorker", "Skipping notification - not current active day")
            return Result.success()
        }

        val notificationHistoryRepository = NotificationHistoryRepository(applicationContext)

        when (reminderType) {
            "normal" -> {
                val slot = inputData.getInt("reminder_slot", 1)
                android.util.Log.d("TreatmentReminderWorker", "Normal reminder for slot $slot")
                
                // Show the normal reminder notification

                val pref = applicationContext.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)
                val slotEnabled = when (slot) {
                    1 -> pref.getBoolean("treatment_morning_enabled", true)
                    2 -> pref.getBoolean("treatment_evening_enabled", true)
                    else -> true
                }
                android.util.Log.d("TreatmentReminderWorker", "Slot $slot enabled: $slotEnabled")
                
                if(!slotEnabled){
                    android.util.Log.d("TreatmentReminderWorker", "Slot $slot not enabled, skipping")
                    return Result.success()
                }

                android.util.Log.d("TreatmentReminderWorker", "Showing treatment reminder notification")
                try {
                    NotificationHelper.showReminder(
                        context = applicationContext,
                        notificationId = "${planId}_day${dayIndex}_slot${slot}".hashCode(),
                        title = diseaseName,
                        message = "$plantName — $taskText",
                        dayIndex = dayIndex,
                        plantName = plantName,
                        plantImageUrl = plan.imageUrl,
                        planId = planId
                    )

                    android.util.Log.d("TreatmentReminderWorker", "Treatment reminder notification shown successfully")
                } catch (e: Exception) {
                    android.util.Log.e("TreatmentReminderWorker", "Error showing treatment reminder notification", e)
                }

                // Save to notification history
                val notification = NotificationHistory(
                    plantId = planId,
                    plantName = plantName,
                    plantImageUrl = plan.imageUrl,
                    day = dayIndex + 1,
                    title = diseaseName,
                    message = "$plantName \u2014 $taskText",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    isCompleted = plan.completedDays.getOrElse(dayIndex) { false }
                )
                notificationHistoryRepository.saveNotification(notification)
            }
            "overdue" -> {
                android.util.Log.d("TreatmentReminderWorker", "Overdue reminder for day $dayIndex")

                // Verify the plan is still active before sending overdue notifications
                if (!plan.isActive) {
                    android.util.Log.d("TreatmentReminderWorker", "Plan is no longer active, skipping overdue")
                    return Result.success()
                }

                // Show the overdue notification
                try {
                    NotificationHelper.showReminder(
                        context = applicationContext,
                        notificationId = "${planId}_day${dayIndex}_overdue".hashCode(),
                        title = "\u23F0 Overdue: Day ${dayIndex + 1}",
                        message = "You haven't completed Day ${dayIndex + 1} treatment yet. $plantName — $taskText",
                        dayIndex = dayIndex,
                        plantName = plantName,
                        plantImageUrl = plan.imageUrl,
                        planId = planId
                    )
                    android.util.Log.d("TreatmentReminderWorker", "Overdue notification shown successfully")
                } catch (e: Exception) {
                    android.util.Log.e("TreatmentReminderWorker", "Error showing overdue notification", e)
                }

                // Save to notification history
                val notification = NotificationHistory(
                    plantId = planId,
                    plantName = plantName,
                    plantImageUrl = plan.imageUrl,
                    day = dayIndex + 1,
                    title = "\u23F0 Overdue: Day ${dayIndex + 1}",
                    message = "You haven't completed Day ${dayIndex + 1} treatment yet. $plantName \u2014 $taskText",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    isCompleted = plan.completedDays.getOrElse(dayIndex) { false }
                )
                notificationHistoryRepository.saveNotification(notification)

                // Re-schedule reminders and another overdue check ONLY if:
                // 1. The plan is still active
                // 2. The day is still the current incomplete day
                val updatedPlan = repository.getPlan(planId)
                if (updatedPlan != null && updatedPlan.isActive) {
                    val updatedCurrentDayIndex = updatedPlan.completedDays.indexOfFirst { !it }
                    if (updatedCurrentDayIndex == dayIndex) {
                        NotificationScheduler.scheduleDayReminders(
                            applicationContext,
                            updatedPlan,
                            dayIndex
                        )
                        NotificationScheduler.scheduleOverdueReminder(
                            applicationContext,
                            updatedPlan,
                            dayIndex
                        )
                    } else {
                        android.util.Log.d("TreatmentReminderWorker", "Day $dayIndex was completed or skipped, not re-scheduling overdue")
                    }
                } else {
                    android.util.Log.d("TreatmentReminderWorker", "Plan deleted or inactive, not re-scheduling overdue")
                }
            }
        }

        return Result.success()
    }
}
