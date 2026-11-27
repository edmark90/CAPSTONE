package com.example.smartplantcare.ui.theme.screens.homescreen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.data.TreatmentPlanRepository
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

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            val allPlans = withContext(Dispatchers.IO) {
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
