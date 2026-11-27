package com.example.smartplantcare.data

import android.content.Context
import android.util.Log
import com.example.smartplantcare.data.local.AppDatabase
import com.example.smartplantcare.data.local.TreatmentPlanDao
import com.example.smartplantcare.data.local.TreatmentPlanEntity
import kotlinx.coroutines.flow.first

/**
 * Local offline-first repository for saved plant diagnosis records.
 */
class TreatmentPlanRepository(
    private val context: Context
) {

    private val roomDao: TreatmentPlanDao = AppDatabase.getDatabase(context).treatmentPlanDao()

    suspend fun savePlan(plan: TreatmentPlan): String {
        val id = if (plan.id.isBlank()) generateLocalId() else plan.id
        val entity = TreatmentPlanEntity(
            id = id,
            plantName = plan.plantName,
            diseaseName = plan.diseaseName,
            className = plan.className,
            imageUrl = plan.imageUrl,
            startDateMillis = plan.startDateMillis,
            confidence = plan.confidence,
            isHealthy = plan.isHealthy
        )
        roomDao.insertPlan(entity)
        Log.d("TreatmentPlanRepository", "Saved plant record to local database: $id")
        return id
    }

    suspend fun getAllPlans(): List<TreatmentPlan> {
        val entities = roomDao.getAllPlans().first()
        return entities.map { entity ->
            TreatmentPlan(
                id = entity.id,
                plantName = entity.plantName,
                diseaseName = entity.diseaseName,
                className = entity.className,
                imageUrl = entity.imageUrl,
                startDateMillis = entity.startDateMillis,
                confidence = entity.confidence,
                isHealthy = entity.isHealthy
            )
        }
    }

    suspend fun getPlan(id: String): TreatmentPlan? {
        val entity = roomDao.getPlanById(id) ?: return null
        return TreatmentPlan(
            id = entity.id,
            plantName = entity.plantName,
            diseaseName = entity.diseaseName,
            className = entity.className,
            imageUrl = entity.imageUrl,
            startDateMillis = entity.startDateMillis,
            confidence = entity.confidence,
            isHealthy = entity.isHealthy
        )
    }

    suspend fun deletePlan(id: String) {
        val plan = getPlan(id)
        if (plan != null && plan.imageUrl.isNotBlank()) {
            ImageUtils.deleteImage(plan.imageUrl)
        }
        roomDao.deletePlan(id)
        Log.d("TreatmentPlanRepository", "Saved plant record deleted: $id")
    }

    suspend fun deleteAllPlans() {
        roomDao.deleteAllPlans()
        Log.d("TreatmentPlanRepository", "All saved plant records deleted")
    }

    private fun generateLocalId(): String {
        return "local_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}
