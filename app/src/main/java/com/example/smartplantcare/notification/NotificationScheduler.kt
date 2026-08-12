package com.example.smartplantcare.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.data.TreatmentPlanRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val TAG_PREFIX = "treatment_"
    private const val MIN_DELAY_MS = 1_000L
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val OVERDUE_CHECK_DELAY_HOURS = 12L

    // ─── Read settings-based times ───────────────────────────────────────────

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences("smartplantcare_settings", Context.MODE_PRIVATE)

    fun getMorningHour(context: Context): Int = getPrefs(context).getInt("morning_hour", 8)
    fun getMorningMinute(context: Context): Int = getPrefs(context).getInt("morning_minute", 0)
    fun getEveningHour(context: Context): Int = getPrefs(context).getInt("evening_hour", 18)
    fun getEveningMinute(context: Context): Int = getPrefs(context).getInt("evening_minute", 0)

    // ─── Treatment-specific time getters ─────────────────────────────────
    fun getTreatmentMorningHour(context: Context): Int = getPrefs(context).getInt("treatment_morning_hour", 8)
    fun getTreatmentMorningMinute(context: Context): Int = getPrefs(context).getInt("treatment_morning_minute", 0)
    fun getTreatmentEveningHour(context: Context): Int = getPrefs(context).getInt("treatment_evening_hour", 18)
    fun getTreatmentEveningMinute(context: Context): Int = getPrefs(context).getInt("treatment_evening_minute", 0)

    /**
     * Reschedule all active treatment plans using the current settings times.
     * Called when the user changes morning/evening reminder times in Settings.
     */
    suspend fun rescheduleAllActivePlans(context: Context) {
        try {
            val repository = TreatmentPlanRepository(context)
            val allPlans = repository.getAllPlans()
            val activePlans = allPlans.filter { it.isActive }

            for (plan in activePlans) {
                val dayIndex = plan.completedDays.indexOfFirst { !it }
                if (dayIndex != -1 && dayIndex < 7) {
                    // Cancel existing schedules for this plan
                    cancelAll(context, plan.id)
                    // Re-schedule with new times
                    scheduleDayReminders(context, plan, dayIndex)
                    scheduleDayBoundaryOverdueCheck(context, plan, dayIndex)

                    for(futureDay in (dayIndex + 1 ) until 7 ){
                        cancelDay(context, plan.id,futureDay)
                    }
                }
            }
            android.util.Log.d("NotificationScheduler", "Rescheduled ${activePlans.size} active plans with new times")
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "Error rescheduling all active plans", e)
        }
    }

    // ─── Scheduling ──────────────────────────────────────────────────────────

    fun scheduleInitialDay1(context: Context, plan: TreatmentPlan) {
        cancelAll(context, plan.id)
        scheduleDayReminders(context, plan, dayIndex = 0)
        scheduleDayBoundaryOverdueCheck(context, plan, dayIndex = 0)
    }

    private fun getStartOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun scheduleDayReminders(context: Context, plan: TreatmentPlan, dayIndex: Int) {
        android.util.Log.d("NotificationScheduler", "scheduleDayReminders called - planId: ${plan.id}, dayIndex: $dayIndex")
        try {
            val now = System.currentTimeMillis()
            val dayStartMs = maxOf(plan.startDateMillis + (dayIndex * DAY_MS), getStartOfToday())
            val dayTask = plan.sevenDayPlan.getOrNull(dayIndex) ?: plan.sevenDayPlan.firstOrNull() ?: "Continue caring for your plant."
            val taskText = dayTask.substringAfter(":").trim().ifBlank { dayTask }

            val perf = getPrefs(context)
            val morningEnable = perf.getBoolean("treatment_morning_enabled", true)
            val eveningEnable = perf.getBoolean("treatment_evening_enabled", true)

            val morningHour = getTreatmentMorningHour(context)
            val morningMinute = getTreatmentMorningMinute(context)
            val eveningHour = getTreatmentEveningHour(context)
            val eveningMinute = getTreatmentEveningMinute(context)

            if (morningEnable) {
                val mt = getTimeForHourMinute(dayStartMs, morningHour, morningMinute)
                val targetTime = if (mt > now) mt else getNextCalendarDayTime(morningHour, morningMinute)
                val delayMs = targetTime - now
                if (delayMs > 0) {
                    scheduleSingleReminder(
                        context,
                        plan,
                        dayIndex,
                        taskText,
                        delayMs.coerceAtLeast(MIN_DELAY_MS),
                        1
                    )
                }
            }
            if (eveningEnable) {
                val et = getTimeForHourMinute(dayStartMs, eveningHour, eveningMinute)
                val targetTime = if (et > now) et else getNextCalendarDayTime(eveningHour, eveningMinute)
                val delayMs = targetTime - now
                if (delayMs > 0) {
                    scheduleSingleReminder(
                        context,
                        plan,
                        dayIndex,
                        taskText,
                        delayMs.coerceAtLeast(MIN_DELAY_MS),
                        2
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "Error in scheduleDayReminders", e)
        }
    }

    fun scheduleNextDayReminders(context: Context, plan: TreatmentPlan, dayIndex: Int) {
        android.util.Log.d("NotificationScheduler", "scheduleNextDayReminders called - planId: ${plan.id}, dayIndex: $dayIndex")
        try {
            val now = System.currentTimeMillis()
            val nm = getStartOfNextCalendarDay()
            val dt = plan.sevenDayPlan.getOrNull(dayIndex) ?: plan.sevenDayPlan.firstOrNull() ?: "Continue caring for your plant."
            val tt = dt.substringAfter(":").trim().ifBlank { dt }

            val morningHour = getTreatmentMorningHour(context)
            val morningMinute = getTreatmentMorningMinute(context)
            val eveningHour = getTreatmentEveningHour(context)
            val eveningMinute = getTreatmentEveningMinute(context)

            val pref = getPrefs(context)
            val morningEnable = pref.getBoolean("treatment_morning_enabled", true)
            val eveningEnable = pref.getBoolean("treatment_evening_enabled", true)

            if(morningEnable) {
                scheduleSingleReminder(
                    context, plan, dayIndex, tt,
                    (nm + morningHour * 3600_000L + morningMinute * 60_000L - now).coerceAtLeast(1000), 1
                )
            }

            if(eveningEnable) {
                scheduleSingleReminder(
                    context, plan, dayIndex, tt,
                    (nm + eveningHour * 3600_000L + eveningMinute * 60_000L - now).coerceAtLeast(1000), 2
                )
            }
            scheduleDayBoundaryOverdueCheck(context, plan, dayIndex, dayStartMs = nm)
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "Error in scheduleNextDayReminders", e)
        }
    }

    private fun getTimeForHourMinute(ds: Long, hour: Int, minute: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ds
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun getNextCalendarDayTime(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = System.currentTimeMillis()
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun getStartOfNextCalendarDay(): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun scheduleDayBoundaryOverdueCheck(context: Context, plan: TreatmentPlan, dayIndex: Int, dayStartMs: Long = plan.startDateMillis + (dayIndex * DAY_MS)) {
        val now = System.currentTimeMillis()
        val boundary = dayStartMs + DAY_MS
        val delay = boundary - now
        // If the end-of-day boundary has already passed, do NOT schedule an
        // overdue check. Scheduling it would make it fire immediately
        // (coerced to 1s), spamming "Overdue" every time a setting is changed.
        if (delay <= 0) {
            android.util.Log.d("NotificationScheduler", "Boundary already passed for day $dayIndex, skipping overdue check")
            return
        }
        val dt = plan.sevenDayPlan.getOrNull(dayIndex) ?: plan.sevenDayPlan.firstOrNull() ?: "Continue caring."
        val tt = dt.substringAfter(":").trim().ifBlank { dt }
        val data = workDataOf("plan_id" to plan.id, "day_index" to dayIndex, "disease_name" to plan.diseaseName, "task_text" to tt, "plant_name" to plan.plantName, "reminder_type" to "overdue")
        val req = OneTimeWorkRequestBuilder<TreatmentReminderWorker>().setInputData(data).setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag(TAG_PREFIX + plan.id).addTag(dayTag(plan.id, dayIndex)).addTag(boundaryTag(plan.id, dayIndex)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(boundaryWorkName(plan.id, dayIndex), ExistingWorkPolicy.REPLACE, req)
    }

    fun scheduleOverdueReminder(context: Context, plan: TreatmentPlan, dayIndex: Int) {
        val dt = plan.sevenDayPlan.getOrNull(dayIndex) ?: plan.sevenDayPlan.firstOrNull() ?: "Continue caring."
        val tt = dt.substringAfter(":").trim().ifBlank { dt }
        val data = workDataOf("plan_id" to plan.id, "day_index" to dayIndex, "disease_name" to plan.diseaseName, "task_text" to tt, "plant_name" to plan.plantName, "reminder_type" to "overdue")
        val delayMs = OVERDUE_CHECK_DELAY_HOURS * 60 * 60 * 1000
        val req = OneTimeWorkRequestBuilder<TreatmentReminderWorker>().setInputData(data).setInitialDelay(delayMs, TimeUnit.MILLISECONDS).addTag(TAG_PREFIX + plan.id).addTag(dayTag(plan.id, dayIndex)).addTag(overdueTag(plan.id, dayIndex)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(overdueWorkName(plan.id, dayIndex), ExistingWorkPolicy.REPLACE, req)
    }

    private fun scheduleSingleReminder(context: Context, plan: TreatmentPlan, dayIndex: Int, tt: String, dm: Long, slot: Int) {
        android.util.Log.d("NotificationScheduler", "Scheduling single reminder: planId=${plan.id}, day=$dayIndex, slot=$slot, delay=${dm}ms")

        if (dm >= MIN_DELAY_MS) {
            val triggerAt = System.currentTimeMillis() + dm
            AlarmScheduler.scheduleReminder(context, plan.id, dayIndex, slot, triggerAt, plan.diseaseName, tt, plan.plantName)
            android.util.Log.d("NotificationScheduler", "Alarm scheduled for ${java.util.Date(triggerAt)}")
        } else {
            android.util.Log.d("NotificationScheduler", "Delay too short, skipping alarm")
        }
    }

    fun cancelDay(context: Context, planId: String, dayIndex: Int) {
        AlarmScheduler.cancelReminder(context, planId, dayIndex, 1)
        AlarmScheduler.cancelReminder(context, planId, dayIndex, 2)
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(overdueWorkName(planId, dayIndex))
        wm.cancelUniqueWork(boundaryWorkName(planId, dayIndex))
    }

    fun cancelAll(context: Context, planId: String) {
        for (day in 0 until 7) {
            AlarmScheduler.cancelReminder(context, planId, day, 1)
            AlarmScheduler.cancelReminder(context, planId, day, 2)
        }
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PREFIX + planId)
    }

    private fun overdueWorkName(pid: String, di: Int): String = pid + "_day" + di + "_overdue"
    private fun boundaryWorkName(pid: String, di: Int): String = pid + "_day" + di + "_boundary"
    private fun dayTag(pid: String, di: Int): String = TAG_PREFIX + pid + "_day" + di
    private fun overdueTag(pid: String, di: Int): String = TAG_PREFIX + pid + "_day" + di + "_overdue"
    private fun boundaryTag(pid: String, di: Int): String = TAG_PREFIX + pid + "_day" + di + "_boundary"
}
