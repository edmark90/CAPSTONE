package com.example.smartplantcare.notification

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Manages FCM device token registration and cleanup in Firebase Realtime Database
 * under `/users/{uid}/fcmTokens/{tokenId}` and keeps `/users/{uid}/profile` synced
 * so backend notifications always have the owner's actual display name.
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"
    private const val RTDB_URL =
        "https://smartplantcare-7c3e5-default-rtdb.asia-southeast1.firebasedatabase.app"
    private const val PREFS_NAME = "spc_fcm_prefs"
    private const val KEY_LAST_TOKEN_ID = "last_token_id"
    private const val KEY_LAST_TOKEN_UID = "last_token_uid"

    private fun dbRef() = FirebaseDatabase.getInstance(RTDB_URL).reference

    fun tokenIdFor(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    suspend fun syncCurrentUserTokenAndProfile(
        context: Context,
        uid: String,
        displayName: String = "",
        email: String = ""
    ) {
        if (uid.isBlank()) return
        val appContext = context.applicationContext

        // Keep RTDB /users/{uid}/profile synchronized so backend always resolves real owner display name
        val cleanName = displayName.trim()
        val cleanEmail = email.trim()
        if (cleanName.isNotBlank() || cleanEmail.isNotBlank()) {
            runCatching {
                val profileUpdates = mutableMapOf<String, Any>(
                    "updatedAt" to System.currentTimeMillis()
                )
                if (cleanName.isNotBlank()) {
                    profileUpdates["displayName"] = cleanName
                    profileUpdates["name"] = cleanName
                }
                if (cleanEmail.isNotBlank()) {
                    profileUpdates["email"] = cleanEmail
                }
                dbRef().child("users").child(uid).child("profile")
                    .updateChildren(profileUpdates)
                    .await()
            }.onFailure {
                Log.w(TAG, "Profile sync to RTDB failed (non-fatal): ${it.message}")
            }
        }

        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.length >= 20) {
                registerTokenForUser(appContext, uid, token)
            }
        }.onFailure {
            Log.w(TAG, "FCM token fetch failed (non-fatal): ${it.message}")
        }
    }

    suspend fun registerTokenForUser(context: Context, uid: String, token: String) {
        if (uid.isBlank() || token.length < 20) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newTokenId = tokenIdFor(token)
        val prevTokenId = prefs.getString(KEY_LAST_TOKEN_ID, null)
        val prevUid = prefs.getString(KEY_LAST_TOKEN_UID, null)

        // If the token rotated for the same user, prune the old tokenId
        if (!prevTokenId.isNullOrBlank() && prevUid == uid && prevTokenId != newTokenId) {
            runCatching {
                dbRef().child("users").child(uid).child("fcmTokens").child(prevTokenId)
                    .removeValue().await()
            }
        }

        val payload = mapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to System.currentTimeMillis()
        )
        runCatching {
            dbRef().child("users").child(uid).child("fcmTokens").child(newTokenId)
                .setValue(payload)
                .await()
            prefs.edit()
                .putString(KEY_LAST_TOKEN_ID, newTokenId)
                .putString(KEY_LAST_TOKEN_UID, uid)
                .apply()
            Log.d(TAG, "Registered FCM token for uid=$uid")
        }.onFailure {
            Log.w(TAG, "Failed to register FCM token: ${it.message}")
        }
    }

    /**
     * MUST be invoked BEFORE FirebaseAuth.signOut() while the user's auth token is still valid,
     * so the signed-out device never receives push notifications intended for the previous account.
     */
    suspend fun unregisterCurrentDeviceToken(context: Context) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTokenId = prefs.getString(KEY_LAST_TOKEN_ID, null)

        val tokenIdToRemove = if (!savedTokenId.isNullOrBlank()) {
            savedTokenId
        } else {
            runCatching {
                val tok = FirebaseMessaging.getInstance().token.await()
                if (tok.length >= 20) tokenIdFor(tok) else null
            }.getOrNull()
        }

        if (!tokenIdToRemove.isNullOrBlank()) {
            runCatching {
                dbRef().child("users").child(currentUid).child("fcmTokens").child(tokenIdToRemove)
                    .removeValue()
                    .await()
                Log.d(TAG, "Removed FCM token on sign-out for uid=$currentUid")
            }.onFailure {
                Log.w(TAG, "Failed to remove FCM token on sign-out: ${it.message}")
            }
        }
        prefs.edit().remove(KEY_LAST_TOKEN_ID).remove(KEY_LAST_TOKEN_UID).apply()
    }
}
