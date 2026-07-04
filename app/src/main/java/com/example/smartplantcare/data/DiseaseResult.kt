package com.example.smartplantcare.data

data class DiseaseResult(
    val diseaseName: String,
    val description: String,
    val causes: String,
    val treatment: String,
    val preventionTips: String,
    val sevenDayPlan: List<String>
)