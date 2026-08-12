package com.example.smartplantcare.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartplantcare.data.TreatmentPlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appContext = context.applicationContext

                    // Alarms are cleared on reboot, so re-arm the daily reminders at fixed times (8 AM and 6 PM)
                    DailyAlarmScheduler.schedule(
                        appContext,
                        "morning_reminder",
                        8,
                        0,
                        "\uD83C\uDF05 Good Morning!",
                        "Time to check on your plants and start the day's care routine."
                    )
                    DailyAlarmScheduler.schedule(
                        appContext,
                        "evening_reminder",
                        18,
                        0,
                        "\uD83C\uDF06 Good Evening!",
                        "Don't forget to give your plants evening care before the night."
                    )

                    // Re-schedule all active treatment plan reminders
                    val repository = TreatmentPlanRepository(appContext)
                    val activePlans = repository.getAllPlans().filter { it.isActive }
                    for (plan in activePlans) {
                        val dayIndex = plan.completedDays.indexOfFirst { !it }
                        if (dayIndex != -1) {
                            NotificationScheduler.scheduleDayReminders(appContext, plan, dayIndex)
                            NotificationScheduler.scheduleDayBoundaryOverdueCheck(appContext, plan, dayIndex)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Error re-scheduling notifications on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
