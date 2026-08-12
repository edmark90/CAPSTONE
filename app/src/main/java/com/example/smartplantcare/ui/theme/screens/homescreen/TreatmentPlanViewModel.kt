package com.example.smartplantcare.ui.theme.screens.homescreen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.data.TreatmentPlanRepository
import com.example.smartplantcare.notification.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TreatmentPlanViewModel(
    private val context: Context
) : ViewModel() {

    private val repository = TreatmentPlanRepository(context)

    private val _plans = MutableStateFlow<List<TreatmentPlan>>(emptyList())
    val plans: StateFlow<List<TreatmentPlan>> = _plans.asStateFlow()

    private val _selectedPlan = MutableStateFlow<TreatmentPlan?>(null)
    val selectedPlan: StateFlow<TreatmentPlan?> = _selectedPlan.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadPlans() {
        viewModelScope.launch {
            _isLoading.value = true
            _plans.value = repository.getAllPlans()
            _isLoading.value = false
        }
    }

    fun loadPlan(id: String) {
        viewModelScope.launch { _selectedPlan.value = repository.getPlan(id) }
    }

    /**
     * Mark a day as complete, enforcing sequential day completion.
     * Only the current incomplete day can be marked complete.
     * Once marked, cancels remaining reminders for that day and
     * schedules the next day's reminders.
     */
    fun markDayComplete(planId: String, dayIndex: Int) {
        android.util.Log.d("TreatmentPlanViewModel", "markDayComplete called - planId: $planId, dayIndex: $dayIndex")
        viewModelScope.launch {
            try {
                android.util.Log.d("TreatmentPlanViewModel", "Fetching plan from repository...")
                val plan = repository.getPlan(planId)
                if (plan == null) {
                    android.util.Log.e("TreatmentPlanViewModel", "Plan is null, returning early")
                    return@launch
                }
                android.util.Log.d("TreatmentPlanViewModel", "Plan fetched successfully: ${plan.plantName}")

                // ENFORCE: Only the current incomplete day (first false) can be completed
                val currentDayIndex = plan.completedDays.indexOfFirst { !it }
                android.util.Log.d("TreatmentPlanViewModel", "currentDayIndex: $currentDayIndex, requested dayIndex: $dayIndex")
                if (currentDayIndex == -1 || currentDayIndex != dayIndex) {
                    android.util.Log.e("TreatmentPlanViewModel", "Invalid day index - currentDayIndex: $currentDayIndex, dayIndex: $dayIndex")
                    return@launch
                }

                // Mark this day complete
                android.util.Log.d("TreatmentPlanViewModel", "Calling repository.markDayComplete...")
                repository.markDayComplete(planId, dayIndex)
                android.util.Log.d("TreatmentPlanViewModel", "Day marked complete successfully")

                // Cancel remaining reminders + any overdue for this day
                android.util.Log.d("TreatmentPlanViewModel", "Cancelling notifications for day $dayIndex")
                NotificationScheduler.cancelDay(context, planId, dayIndex)
                android.util.Log.d("TreatmentPlanViewModel", "Notifications cancelled successfully")

                // Get updated plan after marking complete
                android.util.Log.d("TreatmentPlanViewModel", "Fetching updated plan...")
                val updatedPlan = repository.getPlan(planId)
                if (updatedPlan != null) {
                    android.util.Log.d("TreatmentPlanViewModel", "Updated plan fetched successfully")
                    // Check if there's a next day to schedule
                    val nextDayIndex = dayIndex + 1
                    android.util.Log.d("TreatmentPlanViewModel", "Checking next day: nextDayIndex: $nextDayIndex, completedDays: ${updatedPlan.completedDays}")
                    if (nextDayIndex < 7 && !updatedPlan.completedDays.getOrElse(nextDayIndex) { false }) {
                        // Schedule 2 reminders for the next day (starting on next calendar day)
                        android.util.Log.d("TreatmentPlanViewModel", "Scheduling notifications for next day $nextDayIndex")
                        NotificationScheduler.scheduleNextDayReminders(
                            context,
                            updatedPlan,
                            nextDayIndex
                        )
                        android.util.Log.d("TreatmentPlanViewModel", "Next day notifications scheduled successfully")
                    } else {
                        android.util.Log.d("TreatmentPlanViewModel", "No next day to schedule or next day already completed")
                    }
                } else {
                    android.util.Log.e("TreatmentPlanViewModel", "Updated plan is null")
                }

                // Refresh data
                android.util.Log.d("TreatmentPlanViewModel", "Refreshing plans...")
                _plans.value = repository.getAllPlans()
                android.util.Log.d("TreatmentPlanViewModel", "Refreshing selected plan...")
                _selectedPlan.value = repository.getPlan(planId)
                android.util.Log.d("TreatmentPlanViewModel", "markDayComplete completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("TreatmentPlanViewModel", "Error in markDayComplete", e)
            }
        }
    }

    /**
     * Undo day completion status.
     * Only allows undoing the most recently completed day.
     * Restores the day to incomplete and resumes notifications for that day.
     */
    fun undoDayComplete(planId: String, dayIndex: Int) {
        android.util.Log.d("TreatmentPlanViewModel", "undoDayComplete called - planId: $planId, dayIndex: $dayIndex")
        viewModelScope.launch {
            try {
                android.util.Log.d("TreatmentPlanViewModel", "Fetching plan from repository...")
                val plan = repository.getPlan(planId)
                if (plan == null) {
                    android.util.Log.e("TreatmentPlanViewModel", "Plan is null, returning early")
                    return@launch
                }
                android.util.Log.d("TreatmentPlanViewModel", "Plan fetched successfully: ${plan.plantName}")

                // UNDO: Only allow undoing the most recently completed day
                val lastCompletedIndex = plan.completedDays.indexOfLast { it }
                android.util.Log.d("TreatmentPlanViewModel", "lastCompletedIndex: $lastCompletedIndex, requested dayIndex: $dayIndex")
                if (lastCompletedIndex != dayIndex) {
                    android.util.Log.e("TreatmentPlanViewModel", "Invalid day index for undo - lastCompletedIndex: $lastCompletedIndex, dayIndex: $dayIndex")
                    return@launch
                }

                // Mark this day as incomplete
                android.util.Log.d("TreatmentPlanViewModel", "Calling repository.undoDayComplete...")
                repository.undoDayComplete(planId, dayIndex)
                android.util.Log.d("TreatmentPlanViewModel", "Day undone successfully")

                // Re-schedule reminders for this day (since it's now incomplete again)
                android.util.Log.d("TreatmentPlanViewModel", "Fetching updated plan...")
                val updatedPlan = repository.getPlan(planId)
                if (updatedPlan != null) {
                    android.util.Log.d("TreatmentPlanViewModel", "Updated plan fetched successfully")
                    android.util.Log.d("TreatmentPlanViewModel", "Scheduling notifications for day $dayIndex")
                    NotificationScheduler.scheduleDayReminders(
                        context,
                        updatedPlan,
                        dayIndex
                    )
                    NotificationScheduler.scheduleDayBoundaryOverdueCheck(
                        context,
                        updatedPlan,
                        dayIndex
                    )
                    android.util.Log.d("TreatmentPlanViewModel", "Day notifications scheduled successfully")

                    // Cancel any reminders that were scheduled for the next day
                    val nextDayIndex = dayIndex + 1
                    android.util.Log.d("TreatmentPlanViewModel", "Cancelling notifications for next day $nextDayIndex")
                    if (nextDayIndex < 7) {
                        NotificationScheduler.cancelDay(context, planId, nextDayIndex)
                        android.util.Log.d("TreatmentPlanViewModel", "Next day notifications cancelled successfully")
                    }
                } else {
                    android.util.Log.e("TreatmentPlanViewModel", "Updated plan is null")
                }

                // Refresh data
                android.util.Log.d("TreatmentPlanViewModel", "Refreshing plans...")
                _plans.value = repository.getAllPlans()
                android.util.Log.d("TreatmentPlanViewModel", "Refreshing selected plan...")
                _selectedPlan.value = repository.getPlan(planId)
                android.util.Log.d("TreatmentPlanViewModel", "undoDayComplete completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("TreatmentPlanViewModel", "Error in undoDayComplete", e)
            }
        }
    }

    /**
     * Toggle day completion status (legacy method for UI compatibility).
     * Delegates to markDayComplete or undoDayComplete based on current state.
     */
    fun toggleDayComplete(planId: String, dayIndex: Int) {
        viewModelScope.launch {
            val plan = repository.getPlan(planId) ?: return@launch
            val wasCompleted = plan.completedDays.getOrElse(dayIndex) { false }

            if (!wasCompleted) {
                markDayComplete(planId, dayIndex)
            } else {
                undoDayComplete(planId, dayIndex)
            }
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            NotificationScheduler.cancelAll(context, planId)
            repository.deletePlan(planId)
            _plans.value = repository.getAllPlans()
            _selectedPlan.value = null
        }
    }
}
