package com.example.smartplantcare.iot

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Immutable
data class AccessRevocationEvent(
    val eventId: String,
    val deviceId: String,
    val deviceName: String,
    val ownerUid: String,
    val ownerName: String,
    val revokedAt: Long,
    val acknowledged: Boolean = false
)

/**
 * Cross-layer coordinator & deduplicator for shared-device access revocation events.
 *
 * Guarantees:
 * - Every revocation event is keyed by its backend-generated unique [eventId] (NOT deviceId),
 *   so repeated legitimate share -> revoke -> re-share -> revoke cycles work cleanly.
 * - Deduplicates push notifications, in-app NotificationHistory entries, and dialog boxes across
 *   FCM, RTDB listeners, REST sync, and ViewModels.
 * - Protects against delayed revocation events overwriting newer authorized access
 *   (`addedAt > revokedAt`).
 */
object RevocationEventCoordinator {

    private const val PREFS_NAME = "spc_revocation_dedupe"
    private const val MAX_STORED_IDS = 100
    private val lock = Any()

    private val _liveEvents = MutableSharedFlow<AccessRevocationEvent>(extraBufferCapacity = 16)
    val liveEvents: SharedFlow<AccessRevocationEvent> = _liveEvents.asSharedFlow()

    fun emitLiveEvent(event: AccessRevocationEvent) {
        _liveEvents.tryEmit(event)
    }

    /**
     * Returns true if the user currently holds a newer active device link for [event.deviceId]
     * whose [UserDeviceLink.addedAt] is strictly later than [event.revokedAt].
     * Prevents delayed revocation events from tearing down newly re-authorized access.
     */
    fun isSupersededByNewerAccess(
        event: AccessRevocationEvent,
        activeLinks: List<UserDeviceLink>
    ): Boolean {
        val currentLink = activeLinks.firstOrNull { it.deviceId == event.deviceId } ?: return false
        return currentLink.addedAt > event.revokedAt
    }

    /**
     * Deterministic NotificationHistory primary key for a revocation event so Room's
     * PRIMARY KEY uniqueness guarantees zero duplicate in-app records even under concurrency.
     */
    fun notificationHistoryId(eventId: String): String = "revocation_$eventId"

    fun tryMarkPushShown(context: Context, uid: String, eventId: String): Boolean {
        if (uid.isBlank() || eventId.isBlank()) return false
        return tryAddId(context, "push_$uid", eventId)
    }

    fun tryMarkHistorySaved(context: Context, uid: String, eventId: String): Boolean {
        if (uid.isBlank() || eventId.isBlank()) return false
        return tryAddId(context, "hist_$uid", eventId)
    }

    fun isDialogAcknowledged(context: Context, uid: String, eventId: String): Boolean {
        if (uid.isBlank() || eventId.isBlank()) return true
        synchronized(lock) {
            return readIdSet(context, "ack_$uid").contains(eventId)
        }
    }

    fun markDialogAcknowledged(context: Context, uid: String, eventId: String) {
        if (uid.isBlank() || eventId.isBlank()) return
        tryAddId(context, "ack_$uid", eventId)
    }

    private fun tryAddId(context: Context, key: String, eventId: String): Boolean {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ordered = readIdSet(context, key)
            if (ordered.contains(eventId)) return false
            ordered.add(eventId)
            while (ordered.size > MAX_STORED_IDS) {
                val first = ordered.firstOrNull() ?: break
                ordered.remove(first)
            }
            prefs.edit().putString(key, ordered.joinToString("\n")).apply()
            return true
        }
    }

    private fun readIdSet(context: Context, key: String): LinkedHashSet<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, "").orEmpty()
        val set = LinkedHashSet<String>()
        if (raw.isNotBlank()) {
            raw.split('\n').forEach { if (it.isNotBlank()) set.add(it) }
        }
        return set
    }
}
