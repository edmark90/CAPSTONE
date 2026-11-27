package com.example.smartplantcare.notification

import android.content.Context
import android.util.Log
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import com.example.smartplantcare.iot.AccessRevocationEvent
import com.example.smartplantcare.iot.RevocationEventCoordinator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Handles high-priority data-only Firebase Cloud Messaging pushes in foreground, background,
 * and killed states:
 * - `ACCESS_REVOKED`: verifies active account, clears cached device data if not superseded,
 *   saves deduplicated in-app NotificationHistory, shows safe system notification, and notifies
 *   any active ViewModel immediately.
 * - IoT device alerts (`onDeviceEventCreated`): displays system alert via [NotificationHelper].
 */
class SmartPlantCareMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        serviceScope.launch {
            FcmTokenManager.registerTokenForUser(applicationContext, uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) return

        val type = data["type"].orEmpty()
        if (type == NotificationHelper.TYPE_ACCESS_REVOKED) {
            handleAccessRevokedPush(data)
        } else if (data.containsKey("eventId") && data.containsKey("deviceId")) {
            handleIotAlertPush(data)
        }
    }

    private fun handleAccessRevokedPush(data: Map<String, String>) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val targetUid = data["targetUid"].orEmpty()
        // Never process or display revocation alerts for a different or signed-out account
        if (currentUid.isNullOrBlank() || (targetUid.isNotBlank() && targetUid != currentUid)) {
            Log.d(TAG, "Ignoring ACCESS_REVOKED push for non-active account.")
            return
        }

        val eventId = data["eventId"].orEmpty()
        val deviceId = data["deviceId"].orEmpty()
        if (eventId.isBlank() || deviceId.isBlank()) return

        val deviceName = data["deviceName"]?.takeIf { it.isNotBlank() } ?: "SmartPlantCare Box"
        val ownerUid = data["ownerUid"].orEmpty()
        val ownerName = data["ownerName"]?.takeIf { it.isNotBlank() } ?: "Device Owner"
        val revokedAt = data["revokedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: "Monitoring Access Removed"
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: "$ownerName has removed your monitoring access to $deviceName."

        val event = AccessRevocationEvent(
            eventId = eventId,
            deviceId = deviceId,
            deviceName = deviceName,
            ownerUid = ownerUid,
            ownerName = ownerName,
            revokedAt = revokedAt,
            acknowledged = false
        )

        // Immediately clear cached snapshot & link if not superseded by newer re-authorization
        clearCachedDeviceIfNotSuperseded(currentUid, deviceId, revokedAt)

        // Emit live event so foreground ViewModel immediately disconnects listeners & shows dialog
        RevocationEventCoordinator.emitLiveEvent(event)

        // Post deduplicated system push notification
        if (RevocationEventCoordinator.tryMarkPushShown(applicationContext, currentUid, eventId)) {
            NotificationHelper.showAccessRevokedNotification(
                context = applicationContext,
                eventId = eventId,
                deviceId = deviceId,
                title = title,
                message = body
            )
        }

        // Persist deduplicated in-app notification history record even when app is closed
        if (RevocationEventCoordinator.tryMarkHistorySaved(applicationContext, currentUid, eventId)) {
            serviceScope.launch {
                runCatching {
                    NotificationHistoryRepository(applicationContext).saveNotification(
                        NotificationHistory(
                            id = RevocationEventCoordinator.notificationHistoryId(eventId),
                            plantId = "",
                            plantName = deviceName,
                            plantImageUrl = "",
                            day = 0,
                            title = title,
                            message = body,
                            timestamp = revokedAt,
                            isRead = false,
                            isCompleted = true
                        )
                    )
                }.onFailure {
                    Log.e(TAG, "Failed to persist ACCESS_REVOKED notification history", it)
                }
            }
        }
    }

    private fun clearCachedDeviceIfNotSuperseded(uid: String, deviceId: String, revokedAt: Long) {
        runCatching {
            val cachePrefs = applicationContext.getSharedPreferences("spc_iot_fast_cache", Context.MODE_PRIVATE)
            val rawLinks = cachePrefs.getString("links_$uid", null)
            if (!rawLinks.isNullOrBlank()) {
                val arr = JSONArray(rawLinks)
                val filtered = JSONArray()
                var superseded = false
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("deviceId", "")
                    if (id == deviceId) {
                        val addedAt = obj.optLong("addedAt", 0L)
                        if (addedAt > revokedAt) {
                            superseded = true
                            filtered.put(obj)
                        }
                    } else {
                        filtered.put(obj)
                    }
                }
                if (superseded) {
                    return
                }
                val editor = cachePrefs.edit()
                    .putString("links_$uid", filtered.toString())
                    .remove("snap_${uid}_$deviceId")
                if (cachePrefs.getString("active_$uid", null) == deviceId) {
                    editor.remove("active_$uid")
                }
                editor.apply()
            } else {
                cachePrefs.edit().remove("snap_${uid}_$deviceId").apply()
            }
        }
    }

    private fun handleIotAlertPush(data: Map<String, String>) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        val eventType = data["type"].orEmpty()
        if (!AlertPreferences.isEventAllowed(applicationContext, eventType)) return
        val eventId = data["eventId"].orEmpty()
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: "Smart Plant Care Alert"
        val body = data["body"].orEmpty()
        val severity = data["severity"].orEmpty()
        if (body.isBlank()) return

        NotificationHelper.showIotDeviceAlert(
            context = applicationContext,
            notificationId = "fcm_iot_$eventId".hashCode(),
            title = title,
            message = body,
            boxName = data["deviceName"].orEmpty(),
            isCritical = severity.equals("critical", ignoreCase = true)
        )
    }

    companion object {
        private const val TAG = "SpcMessagingService"
    }
}
