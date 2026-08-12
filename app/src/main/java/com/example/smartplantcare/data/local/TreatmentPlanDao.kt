package com.example.smartplantcare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentPlanDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TreatmentPlanEntity)
    
    @Query("SELECT * FROM treatment_plans ORDER BY startDateMillis DESC")
    fun getAllPlans(): Flow<List<TreatmentPlanEntity>>
    
    @Query("SELECT * FROM treatment_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: String): TreatmentPlanEntity?
    
    @Update
    suspend fun updatePlan(plan: TreatmentPlanEntity)
    
    @Query("DELETE FROM treatment_plans WHERE id = :id")
    suspend fun deletePlan(id: String)
    
    @Query("DELETE FROM treatment_plans")
    suspend fun deleteAllPlans()
}
