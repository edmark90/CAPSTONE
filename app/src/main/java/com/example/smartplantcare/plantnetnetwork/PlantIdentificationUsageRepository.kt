package com.example.smartplantcare.plantnetnetwork

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Manages the per-user daily Plant Identification quota in Firestore.
 *
 * Firestore path:  users/{uid}/plantIdentificationUsage/daily
 *   identifyCount       : Long  — successful identifications today
 *   lastResetTimestamp  : Long  — epoch-ms of the last 8:00 AM reset
 */
class PlantIdentificationUsageRepository {

    private val auth      = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        const val DAILY_LIMIT = 10
        private const val TAG = "PlantIDUsage"
    }

    // Returns the epoch-ms of the most-recent 8:00 AM on or before [nowMs]
    private fun last8AMBeforeNow(nowMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE,      0)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > nowMs) cal.add(Calendar.DAY_OF_YEAR, -1)
        return cal.timeInMillis
    }

    private fun usageDocRef(uid: String) =
        firestore.collection("users")
            .document(uid)
            .collection("plantIdentificationUsage")
            .document("daily")

    /**
     * Reads (and auto-resets if needed) the current usage.
     * Returns null if the user is not authenticated.
     */
    suspend fun getUsage(): PlantIdentificationUsage? {
        val uid = auth.currentUser?.uid ?: return null
        val ref = usageDocRef(uid)
        val snapshot = ref.get().await()

        val now       = System.currentTimeMillis()
        val threshold = last8AMBeforeNow(now)

        return if (!snapshot.exists()) {
            Log.d(TAG, "No usage document found. Creating initial usage for user: $uid")
            val initial = mapOf(
                "identifyCount"      to 0L,
                "lastResetTimestamp" to threshold
            )
            ref.set(initial).await()
            val usage = PlantIdentificationUsage(identifyCount = 0, lastResetTimestamp = threshold)
            Log.d(TAG, "Initial usage created - Count: ${usage.identifyCount}, Remaining: ${usage.remaining}/${DAILY_LIMIT}")
            usage
        } else {
            val storedCount = snapshot.getLong("identifyCount")      ?: 0L
            val storedReset = snapshot.getLong("lastResetTimestamp") ?: 0L

            if (storedReset < threshold) {
                // Past 8 AM and last reset is before today's 8 AM — reset
                Log.d(TAG, "Daily reset triggered - Previous count: $storedCount, Previous reset: $storedReset, New reset: $threshold")
                ref.set(mapOf(
                    "identifyCount"      to 0L,
                    "lastResetTimestamp" to threshold
                )).await()
                val usage = PlantIdentificationUsage(identifyCount = 0, lastResetTimestamp = threshold)
                Log.d(TAG, "Usage reset - Count: ${usage.identifyCount}, Remaining: ${usage.remaining}/${DAILY_LIMIT}")
                usage
            } else {
                val usage = PlantIdentificationUsage(
                    identifyCount      = storedCount,
                    lastResetTimestamp = storedReset
                )
                Log.d(TAG, "Current usage - Count: ${usage.identifyCount}, Remaining: ${usage.remaining}/${DAILY_LIMIT}, Limit reached: ${usage.isLimitReached}")
                usage
            }
        }
    }

    /**
     * Atomically increments identifyCount by 1.
     * Call ONLY after a confirmed successful PlantNet result.
     * Returns null if unauthenticated.
     */
    suspend fun incrementCount(): PlantIdentificationUsage? {
        val uid = auth.currentUser?.uid ?: return null
        val ref = usageDocRef(uid)
        Log.d(TAG, "Incrementing count for user: $uid")
        ref.update("identifyCount", FieldValue.increment(1)).await()
        val usage = getUsage()
        Log.d(TAG, "Count incremented - New count: ${usage?.identifyCount}, Remaining: ${usage?.remaining}/${DAILY_LIMIT}, Limit reached: ${usage?.isLimitReached}")
        return usage
    }
}

data class PlantIdentificationUsage(
    val identifyCount      : Long,
    val lastResetTimestamp : Long
) {
    val remaining     : Int     get() = (PlantIdentificationUsageRepository.DAILY_LIMIT - identifyCount.toInt()).coerceAtLeast(0)
    val isLimitReached: Boolean get() = identifyCount >= PlantIdentificationUsageRepository.DAILY_LIMIT
}




























