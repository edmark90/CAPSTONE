package com.example.smartplantcare.data

import android.content.Context
import android.util.Log
import com.example.smartplantcare.data.local.AppDatabase
import com.example.smartplantcare.data.local.TreatmentPlanDao
import com.example.smartplantcare.data.ImageUtils
import com.example.smartplantcare.data.local.TreatmentPlanEntity
import kotlinx.coroutines.flow.first

/**
 * Repository for managing TreatmentPlan data.
 *
 * All data is saved locally to Room Database (SQLite) as the source of truth.
 * This is an offline-first architecture -- no cloud dependency is required.
 *
 * Future cloud sync (e.g. Firebase Firestore) can be added as a background
 * synchronization layer without changing the UI or business logic.
 */
class TreatmentPlanRepository(
    private val context: Context
) {

    private val roomDao: TreatmentPlanDao = AppDatabase.getDatabase(context).treatmentPlanDao()

    suspend fun savePlan(plan: TreatmentPlan): String {
        val id = if (plan.id.isBlank()) generateLocalId() else plan.id
        Log.d("TreatmentPlanRepository", "Saving plan - sevenDayPlan size: ${plan.sevenDayPlan.size}")
        Log.d("TreatmentPlanRepository", "sevenDayPlan content: ${plan.sevenDayPlan}")
        val entity = TreatmentPlanEntity(
            id = id,
            plantName = plan.plantName,
            diseaseName = plan.diseaseName,
            className = plan.className,
            imageUrl = plan.imageUrl,
            startDateMillis = plan.startDateMillis,
            sevenDayPlan = plan.sevenDayPlan,
            durations = plan.durations,
            completedDays = plan.completedDays,
            isActive = plan.isActive,
            currentDayIndex = plan.currentDayIndex,
            lastNotificationSent = plan.lastNotificationSent,
            nextScheduledNotification = plan.nextScheduledNotification,
            lastOverdueCheck = plan.lastOverdueCheck,
            confidence = plan.confidence,
            isHealthy = plan.isHealthy
        )
        roomDao.insertPlan(entity)
        Log.d("TreatmentPlanRepository", "Plan saved to local database: $id")
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
                sevenDayPlan = entity.sevenDayPlan,
                durations = entity.durations,
                completedDays = entity.completedDays,
                isActive = entity.isActive,
                currentDayIndex = entity.currentDayIndex,
                lastNotificationSent = entity.lastNotificationSent,
                nextScheduledNotification = entity.nextScheduledNotification,
                lastOverdueCheck = entity.lastOverdueCheck,
                confidence = entity.confidence,
                isHealthy = entity.isHealthy
            )
        }
    }

    suspend fun getPlan(id: String): TreatmentPlan? {
        val entity = roomDao.getPlanById(id) ?: return null
        Log.d("TreatmentPlanRepository", "Retrieved plan - sevenDayPlan size: ${entity.sevenDayPlan.size}")
        Log.d("TreatmentPlanRepository", "sevenDayPlan content: ${entity.sevenDayPlan}")
        return TreatmentPlan(
            id = entity.id,
            plantName = entity.plantName,
            diseaseName = entity.diseaseName,
            className = entity.className,
            imageUrl = entity.imageUrl,
            startDateMillis = entity.startDateMillis,
            sevenDayPlan = entity.sevenDayPlan,
            durations = entity.durations,
            completedDays = entity.completedDays,
            isActive = entity.isActive,
            currentDayIndex = entity.currentDayIndex,
            lastNotificationSent = entity.lastNotificationSent,
            nextScheduledNotification = entity.nextScheduledNotification,
            lastOverdueCheck = entity.lastOverdueCheck,
            confidence = entity.confidence,
            isHealthy = entity.isHealthy
        )
    }

    suspend fun markDayComplete(planId: String, dayIndex: Int) {
        android.util.Log.d("TreatmentPlanRepository", "markDayComplete called - planId: $planId, dayIndex: $dayIndex")
        try {
            val entity = roomDao.getPlanById(planId)
            if (entity == null) {
                android.util.Log.e("TreatmentPlanRepository", "Entity is null, returning early")
                return
            }
            android.util.Log.d("TreatmentPlanRepository", "Entity fetched: ${entity.plantName}, completedDays: ${entity.completedDays}")

            val updated = entity.completedDays.toMutableList()
            if (dayIndex in updated.indices) {
                updated[dayIndex] = true
                android.util.Log.d("TreatmentPlanRepository", "Updated day $dayIndex to true")
            } else {
                android.util.Log.e("TreatmentPlanRepository", "Day index $dayIndex out of bounds")
            }

            val nextDayIndex = updated.indexOfFirst { !it }.let { if (it == -1) 6 else it }
            android.util.Log.d("TreatmentPlanRepository", "nextDayIndex: $nextDayIndex")

            val updatedEntity = entity.copy(
                completedDays = updated,
                isActive = updated.any { !it },
                currentDayIndex = nextDayIndex
            )
            android.util.Log.d("TreatmentPlanRepository", "Updating entity in database...")
            roomDao.updatePlan(updatedEntity)
            android.util.Log.d("TreatmentPlanRepository", "Day marked complete in local database successfully")
        } catch (e: Exception) {
            android.util.Log.e("TreatmentPlanRepository", "Error in markDayComplete", e)
        }
    }

    suspend fun undoDayComplete(planId: String, dayIndex: Int) {
        android.util.Log.d("TreatmentPlanRepository", "undoDayComplete called - planId: $planId, dayIndex: $dayIndex")
        try {
            val entity = roomDao.getPlanById(planId)
            if (entity == null) {
                android.util.Log.e("TreatmentPlanRepository", "Entity is null, returning early")
                return
            }
            android.util.Log.d("TreatmentPlanRepository", "Entity fetched: ${entity.plantName}, completedDays: ${entity.completedDays}")

            val updated = entity.completedDays.toMutableList()
            if (dayIndex in updated.indices) {
                updated[dayIndex] = false
                android.util.Log.d("TreatmentPlanRepository", "Updated day $dayIndex to false")
            } else {
                android.util.Log.e("TreatmentPlanRepository", "Day index $dayIndex out of bounds")
            }

            val nextDayIndex = updated.indexOfFirst { !it }.let { if (it == -1) 6 else it }
            android.util.Log.d("TreatmentPlanRepository", "nextDayIndex: $nextDayIndex")

            val updatedEntity = entity.copy(
                completedDays = updated,
                isActive = updated.any { !it },
                currentDayIndex = nextDayIndex
            )
            android.util.Log.d("TreatmentPlanRepository", "Updating entity in database...")
            roomDao.updatePlan(updatedEntity)
            android.util.Log.d("TreatmentPlanRepository", "Day $dayIndex undone in local database successfully")
        } catch (e: Exception) {
            android.util.Log.e("TreatmentPlanRepository", "Error in undoDayComplete", e)
        }
    }

    suspend fun toggleDayComplete(planId: String, dayIndex: Int) {
        val entity = roomDao.getPlanById(planId) ?: return
        val updated = entity.completedDays.toMutableList()
        if (dayIndex in updated.indices) {
            updated[dayIndex] = !updated[dayIndex]
        }
        val nextDayIndex = updated.indexOfFirst { !it }.let { if (it == -1) 6 else it }
        val updatedEntity = entity.copy(
            completedDays = updated,
            isActive = updated.any { !it },
            currentDayIndex = nextDayIndex
        )
        roomDao.updatePlan(updatedEntity)
        Log.d("TreatmentPlanRepository", "Day $dayIndex toggled in local database")
    }

    suspend fun deletePlan(id: String) {
        // Delete the stored image file if it exists
        val plan = getPlan(id)
        if (plan != null && plan.imageUrl.isNotBlank()) {
            ImageUtils.deleteImage(plan.imageUrl)
        }
        roomDao.deletePlan(id)
        Log.d("TreatmentPlanRepository", "Plan deleted from local database: $id")
    }

    suspend fun deleteAllPlans() {
        roomDao.deleteAllPlans()
        Log.d("TreatmentPlanRepository", "All plans deleted from local database")
    }

    /**
     * Clear all existing treatment plans to fix separator issue.
     * This should be called once to clear old data stored with incorrect separator.
     */
    suspend fun clearAndResetAllPlans() {
        val allPlans = getAllPlans()
        allPlans.forEach { plan ->
            deletePlan(plan.id)
        }
        Log.d("TreatmentPlanRepository", "All plans cleared and reset")
    }

    private fun generateLocalId(): String {
        return "local_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}