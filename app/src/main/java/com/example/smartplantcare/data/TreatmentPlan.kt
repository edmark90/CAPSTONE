package com.example.smartplantcare.data

data class TreatmentPlan(
    val id: String = "",
    val plantName: String = "",
    val diseaseName: String = "",
    val className: String = "",
    val imageUrl: String = "",
    val startDateMillis: Long = System.currentTimeMillis(),
    val confidence: Float = 0.0f,
    val isHealthy: Boolean = false
)
