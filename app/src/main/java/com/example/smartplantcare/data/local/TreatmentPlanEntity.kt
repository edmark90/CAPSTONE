package com.example.smartplantcare.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatment_plans")
data class TreatmentPlanEntity(
    @PrimaryKey
    val id: String,
    val plantName: String,
    val diseaseName: String,
    val className: String,
    val imageUrl: String,
    val startDateMillis: Long,
    val confidence: Float = 0.0f,
    val isHealthy: Boolean = false
)
