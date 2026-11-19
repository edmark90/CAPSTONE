package com.example.smartplantcare.ui.theme.screens.homescreen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.data.TreatmentPlanRepository
import com.example.smartplantcare.notification.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    fun loadPlans() {
        viewModelScope.launch {
            _isLoading.value = true
            _plans.value = withContext(Dispatchers.IO) { repository.getAllPlans() }
            _isLoading.value = false
        }
    }

    fun loadPlan(id: String) {
        viewModelScope.launch {
            _isDetailLoading.value = true
            _selectedPlan.value = withContext(Dispatchers.IO) { repository.getPlan(id) }
            _isDetailLoading.value = false
        }
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
                val (allPlans, refreshedPlan) = withContext(Dispatchers.IO) {
                    val plan = repository.getPlan(planId) ?: return@withContext null
                    val currentDayIndex = plan.completedDays.indexOfFirst { !it }
                    if (currentDayIndex == -1 || currentDayIndex != dayIndex) {
                        android.util.Log.e("TreatmentPlanViewModel", "Invalid day index - currentDayIndex: $currentDayIndex, dayIndex: $dayIndex")
                        return@withContext null
                    }

                    repository.markDayComplete(planId, dayIndex)
                    NotificationScheduler.cancelDay(context, planId, dayIndex)

                    val updatedPlan = repository.getPlan(planId)
                    if (updatedPlan != null) {
                        val nextDayIndex = dayIndex + 1
                        if (nextDayIndex < 7 && !updatedPlan.completedDays.getOrElse(nextDayIndex) { false }) {
                            NotificationScheduler.scheduleNextDayReminders(
                                context,
                                updatedPlan,
                                nextDayIndex
                            )
                        }
                    }

                    repository.getAllPlans() to repository.getPlan(planId)
                } ?: return@launch

                _plans.value = allPlans
                _selectedPlan.value = refreshedPlan
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
                val (allPlans, refreshedPlan) = withContext(Dispatchers.IO) {
                    val plan = repository.getPlan(planId) ?: return@withContext null
                    val lastCompletedIndex = plan.completedDays.indexOfLast { it }
                    if (lastCompletedIndex != dayIndex) {
                        android.util.Log.e("TreatmentPlanViewModel", "Invalid day index for undo - lastCompletedIndex: $lastCompletedIndex, dayIndex: $dayIndex")
                        return@withContext null
                    }

                    repository.undoDayComplete(planId, dayIndex)

                    val updatedPlan = repository.getPlan(planId)
                    if (updatedPlan != null) {
                        NotificationScheduler.scheduleDayReminders(context, updatedPlan, dayIndex)
                        NotificationScheduler.scheduleDayBoundaryOverdueCheck(context, updatedPlan, dayIndex)

                        val nextDayIndex = dayIndex + 1
                        if (nextDayIndex < 7) {
                            NotificationScheduler.cancelDay(context, planId, nextDayIndex)
                        }
                    }

                    repository.getAllPlans() to repository.getPlan(planId)
                } ?: return@launch

                _plans.value = allPlans
                _selectedPlan.value = refreshedPlan
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
            val wasCompleted = withContext(Dispatchers.IO) {
                val plan = repository.getPlan(planId) ?: return@withContext null
                plan.completedDays.getOrElse(dayIndex) { false }
            } ?: return@launch

            if (!wasCompleted) {
                markDayComplete(planId, dayIndex)
            } else {
                undoDayComplete(planId, dayIndex)
            }
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            val allPlans = withContext(Dispatchers.IO) {
                NotificationScheduler.cancelAll(context, planId)
                repository.deletePlan(planId)
                repository.getAllPlans()
            }
            _plans.value = allPlans
            if (_selectedPlan.value?.id == planId) {
                _selectedPlan.value = null
            }
        }
    }
}
