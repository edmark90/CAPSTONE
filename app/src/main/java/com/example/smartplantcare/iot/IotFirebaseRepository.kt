package com.example.smartplantcare.iot

import android.os.SystemClock
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Firebase Realtime Database repository for Smart Plant Care V3.1 IoT devices.
 * Works seamlessly on both Free Spark Plan and Blaze Plan while preserving strict Firebase Auth security.
 */
class IotFirebaseRepository {

    companion object {
        private const val TAG = "SpcSyncPerf"
        private const val RTDB_URL =
            "https://smartplantcare-7c3e5-default-rtdb.asia-southeast1.firebasedatabase.app"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(RTDB_URL)
    private val rootRef = db.reference

    // IPv4-preferred DNS eliminates broken-IPv6 stalls on residential Wi-Fi routers while preserving IPv6 fallback
    private val ipv4PreferredDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            return Dns.SYSTEM.lookup(hostname).sortedBy { addr -> if (addr is Inet4Address) 0 else 1 }
        }
    }

    // Direct HTTPS/2 REST client with connection pooling and bounded timeouts
    private val restClient: OkHttpClient = OkHttpClient.Builder()
        .dns(ipv4PreferredDns)
        .connectionPool(ConnectionPool(6, 2, TimeUnit.MINUTES))
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory cached Firebase ID token (valid ~55m) for zero-wait authenticated REST requests
    private val tokenMutex = Mutex()
    @Volatile private var cachedTokenUid: String? = null
    @Volatile private var cachedIdToken: String? = null
    @Volatile private var cachedTokenExpiresAtMs: Long = 0L

    private suspend fun getAuthenticatedIdToken(forceRefresh: Boolean = false): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            cachedTokenUid = null
            cachedIdToken = null
            cachedTokenExpiresAtMs = 0L
            return null
        }
        val now = System.currentTimeMillis()
        if (!forceRefresh &&
            cachedTokenUid == user.uid &&
            !cachedIdToken.isNullOrBlank() &&
            now + 300_000L < cachedTokenExpiresAtMs
        ) {
            return cachedIdToken
        }
        return tokenMutex.withLock {
            val recheckNow = System.currentTimeMillis()
            if (!forceRefresh &&
                cachedTokenUid == user.uid &&
                !cachedIdToken.isNullOrBlank() &&
                recheckNow + 300_000L < cachedTokenExpiresAtMs
            ) {
                return@withLock cachedIdToken
            }
            runCatching {
                val res = user.getIdToken(forceRefresh).await()
                val tok = res.token
                val expMs = res.expirationTimestamp * 1000L
                if (!tok.isNullOrBlank()) {
                    cachedTokenUid = user.uid
                    cachedIdToken = tok
                    cachedTokenExpiresAtMs = if (expMs > recheckNow) expMs else (recheckNow + 3_000_000L)
                }
                tok
            }.getOrNull()
        }
    }

    private data class RestHttpResponse(
        val code: Int,
        val body: String,
        val durationMs: Long
    )

    private suspend fun executeAuthenticatedRest(
        method: String,
        pathWithJsonAndOptionalQuery: String,
        jsonBody: String? = null,
        requireAuth: Boolean = false
    ): RestHttpResponse = withContext(Dispatchers.IO) {
        val startedElapsed = SystemClock.elapsedRealtime()
        var token = getAuthenticatedIdToken(forceRefresh = false)
        if (requireAuth && token.isNullOrBlank()) {
            token = getAuthenticatedIdToken(forceRefresh = true)
        }

        fun buildUrl(tok: String?): String {
            val base = "$RTDB_URL$pathWithJsonAndOptionalQuery"
            return if (!tok.isNullOrBlank()) {
                val sep = if (pathWithJsonAndOptionalQuery.contains('?')) "&" else "?"
                "$base${sep}auth=$tok"
            } else {
                base
            }
        }

        fun sendOnce(targetUrl: String): Pair<Int, String> {
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")

            val reqBody = jsonBody?.toRequestBody(JSON_MEDIA)
            when (method.uppercase()) {
                "GET" -> reqBuilder.get()
                "PUT" -> reqBuilder.put((jsonBody ?: "{}").toRequestBody(JSON_MEDIA))
                "PATCH" -> reqBuilder.patch((jsonBody ?: "{}").toRequestBody(JSON_MEDIA))
                "POST" -> reqBuilder.post((jsonBody ?: "{}").toRequestBody(JSON_MEDIA))
                "DELETE" -> if (reqBody != null) reqBuilder.delete(reqBody) else reqBuilder.delete()
                else -> reqBuilder.get()
            }
            restClient.newCall(reqBuilder.build()).execute().use { resp ->
                return resp.code to resp.body?.string().orEmpty()
            }
        }

        var (code, body) = sendOnce(buildUrl(token))
        if (code == 401) {
            val refreshed = getAuthenticatedIdToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank() && refreshed != token) {
                val retry = sendOnce(buildUrl(refreshed))
                code = retry.first
                body = retry.second
            } else if (!requireAuth && !token.isNullOrBlank()) {
                val retry = sendOnce(buildUrl(null))
                code = retry.first
                body = retry.second
            }
        }
        val elapsedMs = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(1L)
        RestHttpResponse(code = code, body = body, durationMs = elapsedMs)
    }

    /**
     * Fast authenticated REST discovery of `/users/{uid}/devices` so cold launch does not
     * wait 5-20s for Firebase SDK WebSocket initialization on residential Wi-Fi.
     */
    suspend fun fetchUserDevicesViaRest(uid: String): Result<List<UserDeviceLink>> = runCatching {
        val resp = executeAuthenticatedRest(
            method = "GET",
            pathWithJsonAndOptionalQuery = "/users/$uid/devices.json",
            requireAuth = true
        )
        if (resp.code !in 200..299) {
            throw IllegalStateException("User devices REST returned HTTP ${resp.code}")
        }
        val trimmed = resp.body.trim()
        if (trimmed.isEmpty() || trimmed == "null") {
            emptyList()
        } else {
            val obj = JSONObject(trimmed)
            val links = mutableListOf<UserDeviceLink>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val devId = keys.next()
                val child = obj.optJSONObject(devId) ?: continue
                links.add(
                    UserDeviceLink(
                        deviceId = devId,
                        role = DeviceRole.fromSlug(child.str("role", "viewer")),
                        ownershipEpoch = child.lng("ownershipEpoch", 1L),
                        addedAt = child.lng("addedAt", 0L)
                    )
                )
            }
            Log.d(TAG, "fetchUserDevicesViaRest(uid=${uid.take(6)}): ${links.size} device(s) in ${resp.durationMs}ms")
            links.sortedByDescending { it.addedAt }
        }
    }

    fun observeUserDevices(uid: String): Flow<List<UserDeviceLink>> = callbackFlow {
        runCatching { db.goOnline() }
        val ref = rootRef.child("users").child(uid).child("devices")
        runCatching { ref.keepSynced(true) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val deviceId = child.key ?: return@mapNotNull null
                    UserDeviceLink(
                        deviceId = deviceId,
                        role = DeviceRole.fromSlug(child.child("role").getValue(String::class.java)),
                        ownershipEpoch = child.child("ownershipEpoch").asLong(1L),
                        addedAt = child.child("addedAt").asLong(0L)
                    )
                }.sortedByDescending { it.addedAt }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun fetchUserRevocationsViaRest(uid: String): Result<List<AccessRevocationEvent>> = runCatching {
        val resp = executeAuthenticatedRest(
            method = "GET",
            pathWithJsonAndOptionalQuery = "/users/$uid/revocations.json",
            requireAuth = true
        )
        if (resp.code !in 200..299) {
            throw IllegalStateException("User revocations REST returned HTTP ${resp.code}")
        }
        val trimmed = resp.body.trim()
        if (trimmed.isEmpty() || trimmed == "null") {
            emptyList()
        } else {
            val obj = JSONObject(trimmed)
            val list = mutableListOf<AccessRevocationEvent>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val child = obj.optJSONObject(key) ?: continue
                val eventId = child.str("eventId", key).ifBlank { key }
                val deviceId = child.str("deviceId", "")
                if (deviceId.isBlank()) continue
                list.add(
                    AccessRevocationEvent(
                        eventId = eventId,
                        deviceId = deviceId,
                        deviceName = child.str("deviceName", "SmartPlantCare Box").ifBlank { "SmartPlantCare Box" },
                        ownerUid = child.str("ownerUid", ""),
                        ownerName = child.str("ownerName", "Device Owner").ifBlank { "Device Owner" },
                        revokedAt = child.lng("revokedAt", 0L),
                        acknowledged = child.optBoolean("acknowledged", false)
                    )
                )
            }
            list.sortedByDescending { it.revokedAt }
        }
    }

    fun observeUserRevocations(uid: String): Flow<List<AccessRevocationEvent>> = callbackFlow {
        runCatching { db.goOnline() }
        val ref = rootRef.child("users").child(uid).child("revocations")
        runCatching { ref.keepSynced(true) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val eventId = child.child("eventId").getValue(String::class.java)?.takeIf { it.isNotBlank() } ?: key
                    val deviceId = child.child("deviceId").getValue(String::class.java).orEmpty()
                    if (deviceId.isBlank()) return@mapNotNull null
                    AccessRevocationEvent(
                        eventId = eventId,
                        deviceId = deviceId,
                        deviceName = child.child("deviceName").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() } ?: "SmartPlantCare Box",
                        ownerUid = child.child("ownerUid").getValue(String::class.java).orEmpty(),
                        ownerName = child.child("ownerName").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() } ?: "Device Owner",
                        revokedAt = child.child("revokedAt").asLong(0L),
                        acknowledged = child.child("acknowledged").asBool(false)
                    )
                }.sortedByDescending { it.revokedAt }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeUserRevocations cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun acknowledgeRevocation(uid: String, eventId: String): Result<Unit> = runCatching {
        if (uid.isBlank() || eventId.isBlank()) return@runCatching
        val now = System.currentTimeMillis()
        val ref = rootRef.child("users").child(uid).child("revocations").child(eventId)
        val payload = JSONObject().apply {
            put("acknowledged", true)
            put("acknowledgedAt", now)
        }.toString()
        val restRes = runCatching {
            executeAuthenticatedRest(
                method = "PATCH",
                pathWithJsonAndOptionalQuery = "/users/$uid/revocations/$eventId.json",
                jsonBody = payload,
                requireAuth = true
            )
        }.getOrNull()
        if (restRes == null || restRes.code !in 200..299) {
            ref.updateChildren(
                mapOf(
                    "acknowledged" to true,
                    "acknowledgedAt" to now
                )
            ).await()
        }
    }

    fun observeDevice(deviceId: String): Flow<LiveDeviceSnapshot?> = callbackFlow {
        runCatching { db.goOnline() }
        val ref = rootRef.child("devices").child(deviceId)
        runCatching { ref.keepSynced(true) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                trySend(parseLiveDeviceSnapshot(deviceId, snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Forces a real Firebase SDK WebSocket reconnect (goOffline -> brief pause -> goOnline).
     * Unlike calling goOnline() alone (which is a no-op when SDK thinks it is online),
     * this tears down any zombie socket left behind after SoftAP->Wi-Fi transitions or phone sleep.
     */
    suspend fun reconnectRealtimeSocket() {
        runCatching { db.goOffline() }
        delay(200L)
        runCatching { db.goOnline() }
    }

    /**
     * Authenticated HTTPS/2 REST read from Firebase RTDB using cached Firebase Auth token.
     * Supports slim parallel delta reads when [existingSnapshot] is available so routine syncs
     * avoid re-downloading large historical `/events` and `/commandResults` trees.
     */
    suspend fun fetchDeviceSnapshotViaRest(
        deviceId: String,
        existingSnapshot: LiveDeviceSnapshot? = null,
        slimDelta: Boolean = false,
        pendingCommandId: String? = null
    ): Result<LiveDeviceSnapshot?> = withContext(Dispatchers.IO) {
        runCatching {
            val t0 = SystemClock.elapsedRealtime()
            if (slimDelta && existingSnapshot != null && !existingSnapshot.isFromLocalCache) {
                coroutineScope {
                    val statusDef = async { executeAuthenticatedRest("GET", "/devices/$deviceId/status.json") }
                    val telDef = async { executeAuthenticatedRest("GET", "/devices/$deviceId/telemetry.json") }
                    val weatherDef = async { executeAuthenticatedRest("GET", "/devices/$deviceId/weather.json") }
                    val configDef = async { executeAuthenticatedRest("GET", "/devices/$deviceId/config.json") }
                    val metaDef = async { executeAuthenticatedRest("GET", "/devices/$deviceId/meta.json") }
                    val cmdDef = if (!pendingCommandId.isNullOrBlank()) {
                        async { executeAuthenticatedRest("GET", "/devices/$deviceId/commandResults/$pendingCommandId.json") }
                    } else null

                    val stResp = statusDef.await()
                    val telResp = telDef.await()
                    val wxResp = weatherDef.await()
                    val cfgResp = configDef.await()
                    val metaResp = metaDef.await()
                    val cmdResp = cmdDef?.await()

                    if (metaResp.code == 200 && (metaResp.body.trim().isEmpty() || metaResp.body.trim() == "null") &&
                        stResp.code == 200 && (stResp.body.trim().isEmpty() || stResp.body.trim() == "null")
                    ) {
                        return@coroutineScope null
                    }
                    if (stResp.code in 200..299 && telResp.code in 200..299 && metaResp.code in 200..299) {
                        val mergedRoot = JSONObject().apply {
                            metaResp.body.trim().takeIf { it.startsWith("{") }?.let { put("meta", JSONObject(it)) }
                            stResp.body.trim().takeIf { it.startsWith("{") }?.let { put("status", JSONObject(it)) }
                            telResp.body.trim().takeIf { it.startsWith("{") }?.let { put("telemetry", JSONObject(it)) }
                            wxResp.body.trim().takeIf { it.startsWith("{") }?.let { put("weather", JSONObject(it)) }
                            cfgResp.body.trim().takeIf { it.startsWith("{") }?.let { put("config", JSONObject(it)) }
                        }
                        val parsed = parseLiveDeviceSnapshotFromJson(deviceId, mergedRoot)
                        val updatedCmdResults = if (cmdResp != null && cmdResp.code in 200..299 && cmdResp.body.trim().startsWith("{")) {
                            val cj = JSONObject(cmdResp.body.trim())
                            val cid = cj.str("commandId", pendingCommandId.orEmpty())
                            existingSnapshot.commandResults + (cid to CommandResult(
                                commandId = cid,
                                state = cj.str("state", ""),
                                reason = cj.str("reason", ""),
                                updatedAt = cj.lng("updatedAt", 0L)
                            ))
                        } else {
                            existingSnapshot.commandResults
                        }
                        val elapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
                        Log.d(TAG, "fetchDeviceSnapshotViaRest(slim, id=$deviceId): completed in ${elapsed}ms")
                        return@coroutineScope parsed.copy(
                            members = existingSnapshot.members,
                            activeInvites = existingSnapshot.activeInvites,
                            events = existingSnapshot.events,
                            commandResults = updatedCmdResults,
                            isFromLocalCache = false,
                            syncOrigin = SyncDataOrigin.LIVE_REST,
                            lastSyncLatencyMs = elapsed
                        )
                    }
                    null
                }?.let { return@runCatching it }
            }

            val resp = executeAuthenticatedRest("GET", "/devices/$deviceId.json")
            if (resp.code !in 200..299) {
                throw IllegalStateException("Firebase REST returned HTTP ${resp.code}")
            }
            val trimmed = resp.body.trim()
            if (trimmed.isEmpty() || trimmed == "null") {
                null
            } else {
                val parsed = parseLiveDeviceSnapshotFromJson(deviceId, JSONObject(trimmed)).copy(
                    isFromLocalCache = false,
                    syncOrigin = SyncDataOrigin.LIVE_REST,
                    lastSyncLatencyMs = resp.durationMs
                )
                Log.d(TAG, "fetchDeviceSnapshotViaRest(full, id=$deviceId): completed in ${resp.durationMs}ms (${trimmed.length} bytes)")
                parsed
            }
        }
    }

    /**
     * Fetches the live server JSON directly via authenticated HTTPS/2 REST (bypassing local SDK cache),
     * ONLY tearing down the RTDB WebSocket when [reconnectSocket] is explicitly true (e.g. genuinely stalled
     * or after leaving ESP32 SoftAP). Falls back to SDK `ref.get()` only if REST fails.
     */
    suspend fun forceRefreshDevice(
        deviceId: String,
        reconnectSocket: Boolean = false,
        existingSnapshot: LiveDeviceSnapshot? = null,
        pendingCommandId: String? = null
    ): Result<LiveDeviceSnapshot?> {
        if (reconnectSocket) {
            reconnectRealtimeSocket()
        } else {
            runCatching { db.goOnline() }
        }
        val ref = rootRef.child("devices").child(deviceId)
        runCatching { ref.keepSynced(true) }

        val restResult = fetchDeviceSnapshotViaRest(
            deviceId = deviceId,
            existingSnapshot = existingSnapshot,
            slimDelta = existingSnapshot != null && !existingSnapshot.isFromLocalCache,
            pendingCommandId = pendingCommandId
        )
        if (restResult.isSuccess) {
            return restResult
        }

        val t0 = SystemClock.elapsedRealtime()
        return runCatching {
            val snap = ref.get().await()
            val elapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
            if (!snap.exists()) null else parseLiveDeviceSnapshot(deviceId, snap).copy(
                isFromLocalCache = false,
                syncOrigin = SyncDataOrigin.LIVE_WEBSOCKET,
                lastSyncLatencyMs = elapsed
            )
        }
    }

    /**
     * Fast authenticated HTTPS/2 REST check (avoiding post-SoftAP WebSocket stalls) to determine
     * whether the ESP32 unit has verified the router Wi-Fi password and checked in online to Firebase.
     * Supports both:
     * 1) Two-phase `CLAIM_PENDING` non-owning stamp (`wifiVerifiedEpoch >= expectedEpoch`), and
     * 2) Legacy firmware check-in (`ownershipState == "OWNED"` without another owner, or fresh `/status`).
     */
    suspend fun isDeviceProvisionedOnline(
        deviceId: String,
        expectedEpoch: Long,
        minEpochTimestampMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val metaResp = executeAuthenticatedRest(
                method = "GET",
                pathWithJsonAndOptionalQuery = "/devices/$deviceId/meta.json",
                requireAuth = true
            )
            if (metaResp.code in 200..299) {
                val trimmed = metaResp.body.trim()
                if (trimmed.startsWith("{")) {
                    val metaJson = JSONObject(trimmed)
                    val wifiVerifiedEpoch = metaJson.lng("wifiVerifiedEpoch", 0L)
                    val wifiVerifiedAt = metaJson.lng("wifiVerifiedAt", 0L)
                    if (wifiVerifiedEpoch >= expectedEpoch &&
                        (wifiVerifiedAt == 0L || wifiVerifiedAt >= minEpochTimestampMs)
                    ) {
                        return@runCatching true
                    }

                    val state = metaJson.str("ownershipState", "")
                    val metaOwner = metaJson.str("ownerUid", "")
                    val metaEpoch = metaJson.lng("ownershipEpoch", 0L)
                    val claimedAt = metaJson.lng("claimedAt", 0L)
                    val ownerCompatible = metaOwner.isBlank() || metaOwner == currentUid
                    if (state.equals("OWNED", ignoreCase = true) &&
                        ownerCompatible &&
                        metaEpoch >= expectedEpoch &&
                        (claimedAt >= minEpochTimestampMs || claimedAt in 1L..999_999_999L)
                    ) {
                        return@runCatching true
                    }
                }
            }

            val statusResp = executeAuthenticatedRest(
                method = "GET",
                pathWithJsonAndOptionalQuery = "/devices/$deviceId/status.json",
                requireAuth = true
            )
            if (statusResp.code in 200..299 && statusResp.body.trim().startsWith("{")) {
                val stJson = JSONObject(statusResp.body.trim())
                val online = stJson.optBoolean("online", false)
                val stEpoch = stJson.lng("ownershipEpoch", 0L)
                val lastSeenAt = stJson.lng("lastSeenAt", 0L)
                if (stEpoch >= expectedEpoch && (online || lastSeenAt >= minEpochTimestampMs)) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Registers the logged-in user as Owner of [deviceId] after ESP32 provisioning succeeds,
     * saving their custom Plant Box Name ([displayName]) and ensuring default /config exists.
     */
    suspend fun completeDeviceClaim(
        deviceId: String,
        ownerUid: String,
        ownerEmail: String,
        ownerName: String,
        displayName: String,
        ownershipEpoch: Long,
        lat: Double = 14.5995,
        lon: Double = 120.9842
    ): Result<Unit> = runCatching {
        val t0 = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val cleanName = displayName.trim().ifEmpty { "SmartPlantCare Box" }

        // Fast parallel authenticated REST reads over HTTP/2 (avoids dead pre-SoftAP WebSocket hangs!)
        val (cfgCheck, metaCheck, aclCheck) = coroutineScope {
            val c = async { runCatching { executeAuthenticatedRest("GET", "/devices/$deviceId/config.json", requireAuth = true) }.getOrNull() }
            val m = async { runCatching { executeAuthenticatedRest("GET", "/devices/$deviceId/meta.json", requireAuth = true) }.getOrNull() }
            val a = async { runCatching { executeAuthenticatedRest("GET", "/devices/$deviceId/acl/ownerUid.json", requireAuth = true) }.getOrNull() }
            Triple(c.await(), m.await(), a.await())
        }

        val configExists = cfgCheck != null && cfgCheck.code in 200..299 &&
            cfgCheck.body.trim().let { it.isNotEmpty() && it != "null" }
        val metaExistingJson = metaCheck?.takeIf { it.code in 200..299 && it.body.trim().startsWith("{") }
            ?.let { runCatching { JSONObject(it.body.trim()) }.getOrNull() }
        val existingCloudEpoch = metaExistingJson?.lng("ownershipEpoch", 0L) ?: 0L
        val existingMetaState = metaExistingJson?.str("ownershipState", "UNCLAIMED").orEmpty()
        val existingMetaOwner = metaExistingJson?.str("ownerUid", "").orEmpty()
        val finalEpoch = maxOf(ownershipEpoch, existingCloudEpoch + 1L)
        val existingAclOwner = aclCheck?.takeIf { it.code in 200..299 }
            ?.body?.trim()?.removeSurrounding("\"")?.takeIf { it != "null" }.orEmpty()

        val resolvedOwnerName = ownerName.ifBlank { ownerEmail.substringBefore("@") }

        // Fast authenticated REST claim commit (preserving exact database.rules.json write ordering)
        val restCommitted = runCatching {
            val needsClaimPendingStep =
                (existingAclOwner.isNotBlank() && existingAclOwner != ownerUid) ||
                    (existingMetaState.equals("OWNED", ignoreCase = true) && existingMetaOwner != ownerUid)
            if (needsClaimPendingStep) {
                executeAuthenticatedRest(
                    "PUT",
                    "/devices/$deviceId/meta/ownershipState.json",
                    "\"CLAIM_PENDING\"",
                    requireAuth = true
                )
            }

            val aclOwnerResp = executeAuthenticatedRest(
                "PUT",
                "/devices/$deviceId/acl/ownerUid.json",
                JSONObject.quote(ownerUid),
                requireAuth = true
            )
            if (aclOwnerResp.code !in 200..299) error("ACL owner write HTTP ${aclOwnerResp.code}")

            val metaJson = JSONObject().apply {
                put("deviceId", deviceId)
                put("displayName", cleanName)
                put("model", "SmartPlantCare-3Zone")
                put("hardwareRevision", "ESP32-WROOM32-V3.1-I2C")
                put("firmwareVersion", "3.1.0-i2c")
                put("ownershipState", "OWNED")
                put("ownerUid", ownerUid)
                put("ownershipEpoch", finalEpoch)
                put("resetRequired", false)
                put("claimedAt", now)
                put("wifiVerifiedEpoch", JSONObject.NULL)
                put("wifiVerifiedBootId", JSONObject.NULL)
                put("wifiVerifiedAt", JSONObject.NULL)
                put("location", JSONObject().apply {
                    put("lat", lat)
                    put("lon", lon)
                    put("timezone", "Asia/Manila")
                })
            }
            val metaResp = executeAuthenticatedRest(
                "PATCH",
                "/devices/$deviceId/meta.json",
                metaJson.toString(),
                requireAuth = true
            )
            if (metaResp.code !in 200..299) error("Meta claim write HTTP ${metaResp.code}")

            coroutineScope {
                val memberJob = async {
                    val memJson = JSONObject().apply {
                        put("role", DeviceRole.OWNER.slug)
                        put("email", ownerEmail)
                        put("displayName", resolvedOwnerName)
                        put("addedAt", now)
                        put("addedBy", "physical_claim")
                    }
                    executeAuthenticatedRest(
                        "PUT",
                        "/devices/$deviceId/acl/members/$ownerUid.json",
                        memJson.toString(),
                        requireAuth = true
                    )
                }
                val cfgJob = if (!configExists) {
                    async {
                        executeAuthenticatedRest(
                            "PUT",
                            "/devices/$deviceId/config.json",
                            JSONObject(defaultConfigMap()).toString(),
                            requireAuth = true
                        )
                    }
                } else null
                val userLinkJob = async {
                    val linkJson = JSONObject().apply {
                        put("role", DeviceRole.OWNER.slug)
                        put("ownershipEpoch", finalEpoch)
                        put("addedAt", now)
                    }
                    executeAuthenticatedRest(
                        "PUT",
                        "/users/$ownerUid/devices/$deviceId.json",
                        linkJson.toString(),
                        requireAuth = true
                    )
                }
                val memRes = memberJob.await()
                cfgJob?.await()
                val linkRes = userLinkJob.await()
                if (memRes.code !in 200..299 || linkRes.code !in 200..299) {
                    error("Member/link claim HTTP ${memRes.code}/${linkRes.code}")
                }
            }
            true
        }.getOrDefault(false)

        if (restCommitted) {
            Log.d(TAG, "completeDeviceClaim(REST, id=$deviceId): completed in ${SystemClock.elapsedRealtime() - t0}ms")
            return@runCatching
        }

        // SDK fallback if REST encountered transient failure
        reconnectRealtimeSocket()
        val deviceRef = rootRef.child("devices").child(deviceId)
        val metaMap = mapOf(
            "deviceId" to deviceId,
            "displayName" to cleanName,
            "model" to "SmartPlantCare-3Zone",
            "hardwareRevision" to "ESP32-WROOM32-V3.1-I2C",
            "firmwareVersion" to "3.1.0-i2c",
            "ownershipState" to "OWNED",
            "ownerUid" to ownerUid,
            "ownershipEpoch" to finalEpoch,
            "resetRequired" to false,
            "claimedAt" to now,
            "location" to mapOf(
                "lat" to lat,
                "lon" to lon,
                "timezone" to "Asia/Manila"
            )
        )
        if (existingAclOwner.isNotBlank() && existingAclOwner != ownerUid) {
            deviceRef.child("meta").child("ownershipState").setValue("CLAIM_PENDING").await()
        }
        val aclRef = deviceRef.child("acl")
        aclRef.child("ownerUid").setValue(ownerUid).await()
        deviceRef.child("meta").updateChildren(metaMap).await()
        aclRef.child("members").child(ownerUid).setValue(
            mapOf(
                "role" to DeviceRole.OWNER.slug,
                "email" to ownerEmail,
                "displayName" to resolvedOwnerName,
                "addedAt" to now,
                "addedBy" to "physical_claim"
            )
        ).await()
        if (!configExists) {
            deviceRef.child("config").setValue(defaultConfigMap()).await()
        }
        rootRef.child("users").child(ownerUid).child("devices").child(deviceId).setValue(
            mapOf(
                "role" to DeviceRole.OWNER.slug,
                "ownershipEpoch" to finalEpoch,
                "addedAt" to now
            )
        ).await()
    }

    suspend fun renameDevice(deviceId: String, newDisplayName: String): Result<Unit> = runCatching {
        val clean = newDisplayName.trim()
        require(clean.isNotEmpty()) { "Plant box name cannot be empty." }
        rootRef.child("devices").child(deviceId).child("meta").child("displayName")
            .setValue(clean.take(35))
            .await()
    }

    /**
     * Synchronizes `/devices/{deviceId}/meta/ownershipEpoch` with the ESP32's live NVS
     * `status.ownershipEpoch` using the authenticated Android Owner session (`auth != null`).
     * Prevents RTDB 401 Permission Denied on `/events` and `Ownership epoch mismatch` on commands.
     */
    suspend fun reconcileMetaOwnershipEpoch(
        deviceId: String,
        espOwnershipEpoch: Long,
        ownerUid: String
    ): Result<Unit> = runCatching {
        if (espOwnershipEpoch <= 0L) return@runCatching
        rootRef.child("devices").child(deviceId).child("meta").updateChildren(
            mapOf(
                "ownershipState" to "OWNED",
                "ownershipEpoch" to espOwnershipEpoch,
                "resetRequired" to false
            )
        ).await()
        if (ownerUid.isNotBlank()) {
            rootRef.child("users").child(ownerUid).child("devices").child(deviceId)
                .child("ownershipEpoch")
                .setValue(espOwnershipEpoch)
                .await()
        }
    }

    suspend fun setDeviceMode(deviceId: String, mode: String): Result<Unit> = runCatching {
        val normalized = if (mode.equals("INDOOR", ignoreCase = true)) "INDOOR" else "OUTDOOR"
        val restOk = runCatching {
            executeAuthenticatedRest(
                "PUT",
                "/devices/$deviceId/config/mode.json",
                JSONObject.quote(normalized),
                requireAuth = true
            ).code in 200..299
        }.getOrDefault(false)
        if (!restOk) {
            rootRef.child("devices").child(deviceId).child("config").child("mode")
                .setValue(normalized)
                .await()
        }
    }

    suspend fun setAutoIrrigationEnabled(deviceId: String, enabled: Boolean): Result<Unit> = runCatching {
        val restOk = runCatching {
            executeAuthenticatedRest(
                "PUT",
                "/devices/$deviceId/config/autoIrrigationEnabled.json",
                enabled.toString(),
                requireAuth = true
            ).code in 200..299
        }.getOrDefault(false)
        if (!restOk) {
            rootRef.child("devices").child(deviceId).child("config").child("autoIrrigationEnabled")
                .setValue(enabled)
                .await()
        }
    }

    suspend fun setPhysicalLoadsEnabled(deviceId: String, enabled: Boolean): Result<Unit> = runCatching {
        val restOk = runCatching {
            executeAuthenticatedRest(
                "PUT",
                "/devices/$deviceId/config/physicalLoadsEnabled.json",
                enabled.toString(),
                requireAuth = true
            ).code in 200..299
        }.getOrDefault(false)
        if (!restOk) {
            rootRef.child("devices").child(deviceId).child("config").child("physicalLoadsEnabled")
                .setValue(enabled)
                .await()
        }
    }

    /**
     * OWNER-ONLY: Saves the plant box's accurate installation GPS coordinates to /meta/location
     * AND updates the live 3-hour Open-Meteo rain forecast in /weather.
     */
    suspend fun updateOwnerLocationAndWeather(
        deviceId: String,
        snapshot: WifiWeatherSnapshot
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val validUntil = now + 3_600_000L
        val weatherMap = mapOf(
            "provider" to snapshot.provider,
            "valid" to true,
            "rainProbabilityPct" to snapshot.rainProbabilityPct.coerceIn(0, 100),
            "windowHours" to 3,
            "windowLabel" to "NEXT 3 H",
            "locationName" to snapshot.locationName,
            "lat" to snapshot.lat,
            "lon" to snapshot.lon,
            "fetchedAt" to now,
            "validUntil" to validUntil
        )
        rootRef.child("devices").child(deviceId).child("weather").setValue(weatherMap).await()
        val locMap = mapOf(
            "lat" to snapshot.lat,
            "lon" to snapshot.lon,
            "name" to snapshot.locationName,
            "source" to snapshot.locationSource,
            "updatedAt" to now
        )
        rootRef.child("devices").child(deviceId).child("meta").child("location").updateChildren(locMap).await()
    }

    /**
     * Refreshes ONLY /weather using the Owner's already-saved box coordinates (never touches /meta/location).
     */
    suspend fun updateWeatherForecastOnly(
        deviceId: String,
        snapshot: WifiWeatherSnapshot
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val validUntil = now + 3_600_000L
        val weatherMap = mapOf(
            "provider" to snapshot.provider,
            "valid" to true,
            "rainProbabilityPct" to snapshot.rainProbabilityPct.coerceIn(0, 100),
            "windowHours" to 3,
            "windowLabel" to "NEXT 3 H",
            "locationName" to snapshot.locationName,
            "lat" to snapshot.lat,
            "lon" to snapshot.lon,
            "fetchedAt" to now,
            "validUntil" to validUntil
        )
        rootRef.child("devices").child(deviceId).child("weather").setValue(weatherMap).await()
    }

    /**
     * Submits a remote irrigation/safety command with strict idempotency & duplicate-execution protection:
     * 1. Pre-generates ONE deterministic [cmdId] (<=36 chars for ESP32 NVS ring buffer).
     * 2. Attempts fast authenticated REST PUT first.
     * 3. On transport uncertainty, verifies `/commandResults/{cmdId}` & `/commandRequests/{cmdId}` BEFORE
     *    attempting SDK fallback on the exact same [cmdId], so a command can NEVER execute twice!
     */
    suspend fun sendCommand(
        deviceId: String,
        type: String,
        requestedByUid: String,
        ownershipEpoch: Long,
        cropSlug: String? = null,
        durationMs: Long? = null
    ): Result<String> = runCatching {
        val now = System.currentTimeMillis()
        val expiresAt = now + IotDomainLogic.COMMAND_TTL_MS
        val cmdId = IotDomainLogic.generateDeterministicCommandId(
            uid = requestedByUid,
            nowMs = now,
            randomNonce = SecureRandom().nextInt(0x10000)
        )
        val clampedDur = durationMs?.let { IotDomainLogic.clampPulseDurationMs(it) }

        val jsonPayload = JSONObject().apply {
            put("type", type)
            put("state", "PENDING")
            put("requestedByUid", requestedByUid)
            put("ownershipEpoch", ownershipEpoch)
            put("createdAt", now)
            put("expiresAt", expiresAt)
            if (!cropSlug.isNullOrBlank()) put("crop", cropSlug)
            if (clampedDur != null) put("durationMs", clampedDur)
        }

        val restDelivered = runCatching {
            val resp = executeAuthenticatedRest(
                method = "PUT",
                pathWithJsonAndOptionalQuery = "/devices/$deviceId/commandRequests/$cmdId.json",
                jsonBody = jsonPayload.toString(),
                requireAuth = true
            )
            Log.d(TAG, "sendCommand(REST, type=$type, id=$cmdId): HTTP ${resp.code} in ${resp.durationMs}ms")
            resp.code in 200..299
        }.getOrDefault(false)

        if (restDelivered) {
            return@runCatching cmdId
        }

        // Transport uncertain (e.g. read timeout after server commit): check if server already recorded it!
        val alreadyOnServer = runCatching {
            val checkRes = executeAuthenticatedRest("GET", "/devices/$deviceId/commandResults/$cmdId.json")
            val hasRes = checkRes.code in 200..299 && checkRes.body.trim().let { it.isNotEmpty() && it != "null" }
            if (hasRes) true else {
                val checkReq = executeAuthenticatedRest("GET", "/devices/$deviceId/commandRequests/$cmdId.json")
                checkReq.code in 200..299 && checkReq.body.trim().let { it.isNotEmpty() && it != "null" }
            }
        }.getOrDefault(false)

        if (alreadyOnServer) {
            return@runCatching cmdId
        }

        // Safe fallback using the EXACT SAME deterministic cmdId
        val payload = mutableMapOf<String, Any>(
            "type" to type,
            "state" to "PENDING",
            "requestedByUid" to requestedByUid,
            "ownershipEpoch" to ownershipEpoch,
            "createdAt" to now,
            "expiresAt" to expiresAt
        )
        if (!cropSlug.isNullOrBlank()) payload["crop"] = cropSlug
        if (clampedDur != null) payload["durationMs"] = clampedDur

        rootRef.child("devices").child(deviceId).child("commandRequests").child(cmdId)
            .setValue(payload)
            .await()
        cmdId
    }

    suspend fun updateDeviceConfig(deviceId: String, config: DeviceConfig): Result<Unit> = runCatching {
        val fullGap = config.reservoirFullGapIn.coerceIn(0.6f, config.reservoirEmptyGapIn - 0.5f)
        val emptyGap = config.reservoirEmptyGapIn.coerceIn(3.0f, 12.0f)

        val map = mapOf(
            "mode" to if (config.mode.equals("INDOOR", true)) "INDOOR" else "OUTDOOR",
            "autoIrrigationEnabled" to config.autoIrrigationEnabled,
            "rainThresholdPct" to config.rainThresholdPct.coerceIn(10, 100),
            "wetConfirmMs" to config.wetConfirmMs.coerceIn(5000L, 120000L),
            "dryRecoverMs" to config.dryRecoverMs.coerceIn(15000L, 600000L),
            "reservoirFullGapIn" to fullGap,
            "reservoirEmptyGapIn" to emptyGap,
            "reservoirLowPct" to config.reservoirLowPct.coerceIn(12f, 50f),
            "reservoirCriticalPct" to config.reservoirCriticalPct.coerceIn(5f, 20f),
            "tempHighC" to config.tempHighC.coerceIn(20f, 50f),
            "humLowRh" to config.humLowRh.coerceIn(15f, 70f),
            "lightHighPct" to config.lightHighPct.coerceIn(50f, 100f),
            "lightLowPct" to config.lightLowPct.coerceIn(0f, 50f),
            "buzzerMuted" to config.buzzerMuted,
            "physicalLoadsEnabled" to config.physicalLoadsEnabled,
            "crops" to mapOf(
                "tomato" to cropConfigToMap(config.tomato),
                "chili" to cropConfigToMap(config.chili),
                "eggplant" to cropConfigToMap(config.eggplant)
            )
        )
        rootRef.child("devices").child(deviceId).child("config").updateChildren(map).await()
    }

    suspend fun generateShareInvite(
        deviceId: String,
        role: DeviceRole,
        ownerUid: String,
        ownershipEpoch: Long
    ): Result<ShareInvite> = runCatching {
        require(role == DeviceRole.OPERATOR || role == DeviceRole.VIEWER) {
            "Invite role must be Operator or Viewer."
        }
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val code = buildString {
            repeat(8) { append(chars[random.nextInt(chars.length)]) }
        }
        val now = System.currentTimeMillis()
        val expiresAt = now + 24L * 60L * 60L * 1000L

        val inviteMap = mapOf(
            "inviteCode" to code,
            "deviceId" to deviceId,
            "role" to role.slug,
            "createdByUid" to ownerUid,
            "ownershipEpoch" to ownershipEpoch,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "status" to "PENDING"
        )
        // Write to device ACL invitations AND root /sharedInvites for fast O(1) multi-account redemption
        rootRef.child("devices").child(deviceId).child("acl").child("invitations").child(code)
            .setValue(inviteMap)
            .await()
        runCatching {
            rootRef.child("sharedInvites").child(code).setValue(inviteMap).await()
        }

        ShareInvite(
            inviteCode = code,
            deviceId = deviceId,
            role = role,
            status = "PENDING",
            createdAt = now,
            expiresAt = expiresAt
        )
    }

    /**
     * Redeems a Share Invite Code (`CODE` or `deviceId:CODE`) on both Free Spark & Blaze plans.
     */
    suspend fun redeemShareInvite(
        rawCodeInput: String,
        uid: String,
        userEmail: String,
        userName: String
    ): Result<String> = runCatching {
        val cleaned = rawCodeInput.trim()
        require(cleaned.isNotEmpty()) { "Please enter an invite code." }

        val parts = cleaned.split(":")
        val explicitDeviceId = if (parts.size == 2) parts[0].trim() else null
        val inviteCode = (if (parts.size == 2) parts[1] else parts[0]).trim().uppercase()

        // 0. Prefer trusted backend Cloud Function for atomic single-use invite redemption
        val callableAttempt = runCatching {
            val payload = mutableMapOf<String, Any>("inviteCode" to inviteCode)
            if (!explicitDeviceId.isNullOrBlank()) payload["deviceId"] = explicitDeviceId
            val res = FirebaseFunctions.getInstance()
                .getHttpsCallable("acceptShareInvite")
                .call(payload)
                .await()
            val map = res.data as? Map<*, *>
            val devId = map?.get("deviceId") as? String
            devId?.takeIf { it.isNotBlank() }
        }.getOrNull()
        if (!callableAttempt.isNullOrBlank()) {
            return@runCatching callableAttempt
        }

        var targetDeviceId: String? = explicitDeviceId
        var inviteSnap: DataSnapshot? = null

        // 1. Fast O(1) direct sharedInvites lookup (never scans /devices collection)
        val sharedSnap = runCatching {
            rootRef.child("sharedInvites").child(inviteCode).get().await()
        }.getOrNull()

        if (sharedSnap != null && sharedSnap.exists()) {
            val devId = sharedSnap.child("deviceId").getValue(String::class.java)
            if (!devId.isNullOrBlank()) {
                targetDeviceId = devId
                inviteSnap = sharedSnap
            }
        }

        // 2. Direct device fallback if explicitDeviceId was provided
        if (inviteSnap == null && targetDeviceId != null) {
            val cand = runCatching {
                rootRef.child("devices").child(targetDeviceId!!)
                    .child("acl").child("invitations").child(inviteCode).get().await()
            }.getOrNull()
            if (cand != null && cand.exists()) {
                inviteSnap = cand
            }
        }

        val resolvedDevId = targetDeviceId ?: error("Invite code not found. Double-check the code.")
        val resolvedSnap = inviteSnap ?: error("Invite code not found.")

        val status = resolvedSnap.child("status").getValue(String::class.java) ?: ""
        val expiresAt = resolvedSnap.child("expiresAt").asLong(0L)
        val role = DeviceRole.fromSlug(resolvedSnap.child("role").getValue(String::class.java))
        val createdBy = resolvedSnap.child("createdByUid").getValue(String::class.java) ?: ""
        val ownershipEpoch = resolvedSnap.child("ownershipEpoch").asLong(1L)
        val now = System.currentTimeMillis()

        if (status != "PENDING") {
            error("This invite code is no longer valid.")
        }
        if (expiresAt > 0L && now > expiresAt) {
            error("This invite code has expired. Ask the owner for a new code.")
        }

        // 1. Mark invite accepted in /sharedInvites AND in /devices/$resolvedDevId/acl/invitations
        runCatching {
            rootRef.child("sharedInvites").child(inviteCode).updateChildren(
                mapOf(
                    "status" to "ACCEPTED",
                    "acceptedByUid" to uid,
                    "acceptedAt" to now
                )
            ).await()
        }
        val invRef = rootRef.child("devices").child(resolvedDevId)
            .child("acl").child("invitations").child(inviteCode)
        invRef.updateChildren(
            mapOf(
                "status" to "ACCEPTED",
                "acceptedByUid" to uid,
                "acceptedAt" to now
            )
        ).await()

        // 2. Add member entry to device ACL (validated against accepted invitation by security rules)
        rootRef.child("devices").child(resolvedDevId).child("acl").child("members").child(uid)
            .setValue(
                mapOf(
                    "role" to role.slug,
                    "email" to userEmail,
                    "displayName" to userName.ifBlank { userEmail.substringBefore("@") },
                    "addedAt" to now,
                    "addedBy" to createdBy,
                    "inviteCode" to inviteCode
                )
            ).await()

        // 3. Link device under member's /users/{uid}/devices/{deviceId}
        rootRef.child("users").child(uid).child("devices").child(resolvedDevId)
            .setValue(
                mapOf(
                    "role" to role.slug,
                    "ownershipEpoch" to ownershipEpoch,
                    "addedAt" to now
                )
            ).await()

        resolvedDevId
    }

    /**
     * Revokes shared member access or lets a shared member leave on their own.
     * Works 100% natively on Free Firebase Spark Plan (and Blaze) via direct Realtime Database
     * security-rule-enforced multi-path updates:
     * - Self-leave (`targetUid == currentAuthUid`): removes member from device ACL & own user link.
     * - Owner revocation: atomically removes member ACL + user device link, marks their invite codes
     *   REVOKED, writes the immutable `ACCESS_REVOKED` event to `/users/$targetUid/revocations/$eventId`,
     *   and cleans up any remaining `/sharedInvites/$code` entry safely.
     */
    suspend fun revokeMember(deviceId: String, targetUid: String): Result<Unit> = runCatching {
        runCatching { db.goOnline() }
        val currentAuth = FirebaseAuth.getInstance().currentUser
        val currentAuthUid = currentAuth?.uid
            ?: throw IllegalStateException("Not authenticated")

        // Case 1: Shared member leaving the plant box on their own
        if (targetUid == currentAuthUid) {
            val selfLeaveUpdates = hashMapOf<String, Any?>(
                "devices/$deviceId/acl/members/$currentAuthUid" to null,
                "users/$currentAuthUid/devices/$deviceId" to null
            )
            rootRef.updateChildren(selfLeaveUpdates).await()
            Log.i(TAG, "Member $currentAuthUid left shared device $deviceId")
            return@runCatching
        }

        // Case 2: Device Owner revoking a shared member (Viewer / Operator)
        val aclOwnerUid = runCatching {
            rootRef.child("devices").child(deviceId).child("acl").child("ownerUid").get().await()
                .getValue(String::class.java)
        }.getOrNull()
        val metaSnap = runCatching {
            rootRef.child("devices").child(deviceId).child("meta").get().await()
        }.getOrNull()
        val metaOwnerUid = metaSnap?.child("ownerUid")?.getValue(String::class.java)

        if (aclOwnerUid != currentAuthUid && metaOwnerUid != currentAuthUid) {
            throw IllegalStateException("Only the device owner can remove shared members")
        }

        val deviceName = metaSnap?.child("displayName")?.getValue(String::class.java)
            ?.takeIf { it.isNotBlank() } ?: deviceId
        val ownerName = currentAuth.displayName?.takeIf { it.isNotBlank() }
            ?: currentAuth.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Device Owner"

        val eventId = "rev_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        val revocationPayload = mapOf(
            "eventId" to eventId,
            "type" to "ACCESS_REVOKED",
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "ownerUid" to currentAuthUid,
            "ownerName" to ownerName,
            "revokedAt" to now,
            "acknowledged" to false
        )

        val matchingInviteCodes = mutableListOf<String>()
        runCatching {
            val invitationsSnap = rootRef.child("devices").child(deviceId)
                .child("acl").child("invitations").get().await()
            for (invChild in invitationsSnap.children) {
                val acceptedBy = invChild.child("acceptedByUid").getValue(String::class.java)
                if (acceptedBy == targetUid) {
                    invChild.key?.takeIf { it.isNotBlank() }?.let { matchingInviteCodes.add(it) }
                }
            }
        }

        // Core atomic revocation (no leading slashes; never coupled to optional /sharedInvites cleanup)
        val coreUpdates = hashMapOf<String, Any?>(
            "devices/$deviceId/acl/members/$targetUid" to null,
            "users/$targetUid/devices/$deviceId" to null,
            "users/$targetUid/revocations/$eventId" to revocationPayload
        )
        for (code in matchingInviteCodes) {
            coreUpdates["devices/$deviceId/acl/invitations/$code/status"] = "REVOKED"
        }

        rootRef.updateChildren(coreUpdates).await()

        // Best-effort cleanup of /sharedInvites/$code (skipped safely if already absent)
        for (code in matchingInviteCodes) {
            runCatching {
                val sharedRef = rootRef.child("sharedInvites").child(code)
                if (sharedRef.get().await().exists()) {
                    sharedRef.removeValue().await()
                }
            }
        }

        Log.i(TAG, "Spark Plan member revocation committed atomically for $targetUid on $deviceId")
    }

    /**
     * Owner Unclaim & Factory Reset:
     * Transitions device to RESET_PENDING (ESP32 stops pump/valves, wipes Wi-Fi from NVS,
     * preserves calibration, and re-opens SPC-SETUP-*), removes all shared users & events.
     */
    suspend fun unclaimAndResetDevice(
        deviceId: String,
        currentEpoch: Long,
        memberUids: List<String>,
        ownerUid: String
    ): Result<Unit> = runCatching {
        val nextEpoch = currentEpoch + 1L
        val now = System.currentTimeMillis()
        val sharedMemberUids = memberUids.distinct().filter { it.isNotBlank() && it != ownerUid }

        // Execute strictly in permission-valid order BEFORE removing ownerUid from /meta and /acl!
        val restSucceeded = runCatching {
            // 1. Clean 7-day history, pending commands, invitations, and shared members while still Owner
            coroutineScope {
                val histJob = async {
                    executeAuthenticatedRest("DELETE", "/deviceHistory/$deviceId.json", requireAuth = true)
                }
                val cmdJob = async {
                    executeAuthenticatedRest("DELETE", "/devices/$deviceId/commandRequests.json", requireAuth = true)
                }
                val invJob = async {
                    executeAuthenticatedRest("DELETE", "/devices/$deviceId/acl/invitations.json", requireAuth = true)
                }
                val photoJob = async {
                    executeAuthenticatedRest("DELETE", "/devices/$deviceId/cropPhotos.json", requireAuth = true)
                }
                val memJobs = sharedMemberUids.map { mUid ->
                    async {
                        executeAuthenticatedRest("DELETE", "/users/$mUid/devices/$deviceId.json", requireAuth = true)
                        executeAuthenticatedRest("DELETE", "/devices/$deviceId/acl/members/$mUid.json", requireAuth = true)
                    }
                }
                histJob.await()
                cmdJob.await()
                invJob.await()
                photoJob.await()
                memJobs.forEach { it.await() }
            }

            // 2. Transition /meta to RESET_PENDING (ESP32 stops pump/valves, wipes Wi-Fi, re-opens SPC-SETUP-*)
            val resetMeta = JSONObject().apply {
                put("ownershipState", "RESET_PENDING")
                put("ownerUid", JSONObject.NULL)
                put("ownershipEpoch", nextEpoch)
                put("resetRequired", true)
                put("displayName", "SmartPlantCare Box")
                put("unclaimedAt", now)
                put("wifiVerifiedEpoch", JSONObject.NULL)
                put("wifiVerifiedBootId", JSONObject.NULL)
                put("wifiVerifiedAt", JSONObject.NULL)
            }
            val metaResp = executeAuthenticatedRest(
                "PATCH",
                "/devices/$deviceId/meta.json",
                resetMeta.toString(),
                requireAuth = true
            )
            if (metaResp.code !in 200..299) error("Unclaim meta PATCH HTTP ${metaResp.code}")

            // 3. Remove owner's own ACL & device link LAST
            if (ownerUid.isNotBlank()) {
                executeAuthenticatedRest("DELETE", "/devices/$deviceId/acl/members/$ownerUid.json", requireAuth = true)
                executeAuthenticatedRest("DELETE", "/users/$ownerUid/devices/$deviceId.json", requireAuth = true)
            }
            executeAuthenticatedRest("DELETE", "/devices/$deviceId/acl/ownerUid.json", requireAuth = true)
            true
        }.getOrDefault(false)

        if (restSucceeded) {
            return@runCatching
        }

        // SDK fallback (preserving the exact same permission-valid order)
        val devRef = rootRef.child("devices").child(deviceId)
        runCatching { rootRef.child("deviceHistory").child(deviceId).removeValue().await() }
        runCatching { devRef.child("commandRequests").removeValue().await() }
        runCatching { devRef.child("acl").child("invitations").removeValue().await() }
        runCatching { devRef.child("cropPhotos").removeValue().await() }
        for (uid in sharedMemberUids) {
            runCatching { rootRef.child("users").child(uid).child("devices").child(deviceId).removeValue().await() }
            runCatching { devRef.child("acl").child("members").child(uid).removeValue().await() }
        }
        devRef.child("meta").updateChildren(
            mapOf(
                "ownershipState" to "RESET_PENDING",
                "ownerUid" to null,
                "ownershipEpoch" to nextEpoch,
                "resetRequired" to true,
                "displayName" to "SmartPlantCare Box",
                "unclaimedAt" to now,
                "wifiVerifiedEpoch" to null,
                "wifiVerifiedBootId" to null,
                "wifiVerifiedAt" to null
            )
        ).await()
        if (ownerUid.isNotBlank()) {
            runCatching { devRef.child("acl").child("members").child(ownerUid).removeValue().await() }
            runCatching { rootRef.child("users").child(ownerUid).child("devices").child(deviceId).removeValue().await() }
        }
        runCatching { devRef.child("acl").child("ownerUid").removeValue().await() }
    }

    /**
     * Saves Cloudinary crop photo metadata under `/devices/$deviceId/cropPhotos/$cropSlug`.
     * Enforced by RTDB rules: Owner & Operator only; Viewers are rejected with 401.
     */
    suspend fun saveCropPhotoMetadata(
        deviceId: String,
        cropSlug: String,
        photo: CropZonePhoto
    ): Result<Unit> = runCatching {
        val normalizedSlug = cropSlug.lowercase().trim()
        require(normalizedSlug == "tomato" || normalizedSlug == "chili" || normalizedSlug == "eggplant") {
            "Unsupported crop zone: $cropSlug"
        }
        val payloadJson = JSONObject().apply {
            put("url", photo.url)
            put("publicId", photo.publicId)
            put("updatedAt", photo.updatedAt)
            put("updatedByUid", photo.updatedByUid)
            put("updatedByName", photo.updatedByName)
        }.toString()

        val restOk = runCatching {
            executeAuthenticatedRest(
                method = "PUT",
                pathWithJsonAndOptionalQuery = "/devices/$deviceId/cropPhotos/$normalizedSlug.json",
                jsonBody = payloadJson,
                requireAuth = true
            ).code in 200..299
        }.getOrDefault(false)

        if (!restOk) {
            rootRef.child("devices").child(deviceId)
                .child("cropPhotos").child(normalizedSlug)
                .setValue(
                    mapOf(
                        "url" to photo.url,
                        "publicId" to photo.publicId,
                        "updatedAt" to photo.updatedAt,
                        "updatedByUid" to photo.updatedByUid,
                        "updatedByName" to photo.updatedByName
                    )
                )
                .await()
        }
    }

    /**
     * Removes crop photo metadata from `/devices/$deviceId/cropPhotos/$cropSlug`.
     * Enforced by RTDB rules: Owner & Operator only; Viewers are rejected with 401.
     */
    suspend fun deleteCropPhotoMetadata(
        deviceId: String,
        cropSlug: String
    ): Result<Unit> = runCatching {
        val normalizedSlug = cropSlug.lowercase().trim()
        val restOk = runCatching {
            executeAuthenticatedRest(
                method = "DELETE",
                pathWithJsonAndOptionalQuery = "/devices/$deviceId/cropPhotos/$normalizedSlug.json",
                requireAuth = true
            ).code in 200..299
        }.getOrDefault(false)

        if (!restOk) {
            rootRef.child("devices").child(deviceId)
                .child("cropPhotos").child(normalizedSlug)
                .removeValue()
                .await()
        }
    }

    // ─── 7-Day Historical Monitoring & Irrigation Records RTDB Operations ─────

    fun observeDeviceHistory(deviceId: String): Flow<Map<String, DailyHistorySnapshot>> = callbackFlow {
        val ref = rootRef.child("deviceHistory").child(deviceId).child("days")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cutoff = IotDomainLogic.sevenDayCutoffDateKeyPht()
                val parsed = buildMap {
                    for (dayChild in snapshot.children) {
                        val dateKey = dayChild.key ?: continue
                        if (dateKey.length != 10 || dateKey < cutoff) continue
                        put(dateKey, parseDailyHistorySnapshot(deviceId, dateKey, dayChild))
                    }
                }
                trySend(parsed)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyMap())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun fetchDeviceHistoryViaRest(deviceId: String): Result<Map<String, DailyHistorySnapshot>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = executeAuthenticatedRest("GET", "/deviceHistory/$deviceId/days.json")
                if (resp.code !in 200..299) {
                    throw IllegalStateException("Firebase History REST returned HTTP ${resp.code}")
                }
                val trimmed = resp.body.trim()
                if (trimmed.isEmpty() || trimmed == "null") {
                    emptyMap()
                } else {
                    val rootJson = JSONObject(trimmed)
                    val cutoff = IotDomainLogic.sevenDayCutoffDateKeyPht()
                    buildMap {
                        val keys = rootJson.keys()
                        while (keys.hasNext()) {
                            val dateKey = keys.next()
                            if (dateKey.length != 10 || dateKey < cutoff) continue
                            val dayObj = rootJson.optJSONObject(dateKey) ?: continue
                            put(dateKey, parseDailyHistoryFromJson(deviceId, dateKey, dayObj))
                        }
                    }
                }
            }
        }

    /**
     * Observes authoritative ESP32 1-minute records (`/deviceHistory/{deviceId}/minuteDays/{dateKey}`)
     * lazily for the currently selected calendar date in Philippine Time (UTC+8).
     */
    fun observeDeviceMinuteDay(
        deviceId: String,
        dateKey: String
    ): Flow<Map<Int, HistoricalMinuteRecord>> = callbackFlow {
        val ref = rootRef.child("deviceHistory").child(deviceId).child("minuteDays").child(dateKey)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val parsed = buildMap {
                    for (mChild in snapshot.children) {
                        val rec = parseMinuteRecordFromSnapshot(mChild) ?: continue
                        put(rec.minuteOfDay, rec)
                    }
                }
                trySend(parsed)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyMap())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun fetchDeviceMinuteDayViaRest(
        deviceId: String,
        dateKey: String
    ): Result<Map<Int, HistoricalMinuteRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val cutoff = IotDomainLogic.sevenDayCutoffDateKeyPht()
            if (dateKey.length != 10 || dateKey < cutoff) {
                return@runCatching emptyMap()
            }
            val resp = executeAuthenticatedRest("GET", "/deviceHistory/$deviceId/minuteDays/$dateKey.json")
            if (resp.code !in 200..299) {
                throw IllegalStateException("Firebase Minute History REST returned HTTP ${resp.code}")
            }
            val trimmed = resp.body.trim()
            if (trimmed.isEmpty() || trimmed == "null") {
                emptyMap()
            } else {
                val rootJson = JSONObject(trimmed)
                buildMap {
                    val keys = rootJson.keys()
                    while (keys.hasNext()) {
                        val mKey = keys.next()
                        val mObj = rootJson.optJSONObject(mKey) ?: continue
                        val rec = parseMinuteRecordFromJson(mKey, mObj) ?: continue
                        put(rec.minuteOfDay, rec)
                    }
                }
            }
        }
    }

    /**
     * Automatically removes any historical days & minuteDays older than the 7-day Philippine local time window.
     */
    suspend fun pruneExpiredHistoryDays(deviceId: String, cutoffDateKey: String): Result<Int> =
        runCatching {
            var removed = 0
            for (bucket in listOf("days", "minuteDays")) {
                val bucketRef = rootRef.child("deviceHistory").child(deviceId).child(bucket)
                val snap = bucketRef.get().await()
                for (dayChild in snap.children) {
                    val dateKey = dayChild.key ?: continue
                    if (dateKey.length == 10 && dateKey < cutoffDateKey) {
                        bucketRef.child(dateKey).removeValue().await()
                        removed++
                    }
                }
            }
            removed
        }

    private fun parseDailyHistorySnapshot(
        deviceId: String,
        dateKey: String,
        node: DataSnapshot
    ): DailyHistorySnapshot {
        val hours = buildMap {
            for (hChild in node.child("hours").children) {
                val hourNum = hChild.key?.toIntOrNull() ?: hChild.child("hour").asInt(-1)
                if (hourNum !in 0..23) continue
                val segs = hChild.child("segments").children.mapNotNull { sChild ->
                    val segId = sChild.key ?: return@mapNotNull null
                    HistoricalHourSegment(
                        segmentId = segId,
                        startAt = sChild.child("startAt").asLong(0L),
                        endAt = sChild.child("endAt").asLong(0L),
                        sampleCount = sChild.child("samples").asInt(0),
                        tomatoPct = sChild.child("tomatoPct").asFloatOrNull(),
                        chiliPct = sChild.child("chiliPct").asFloatOrNull(),
                        eggplantPct = sChild.child("eggplantPct").asFloatOrNull(),
                        temperatureC = sChild.child("temperatureC").asFloatOrNull(),
                        humidityPct = sChild.child("humidityPct").asFloatOrNull(),
                        lightPct = sChild.child("lightPct").asFloatOrNull(),
                        reservoirPct = sChild.child("reservoirPct").asFloatOrNull(),
                        reservoirGapIn = sChild.child("reservoirGapIn").asFloatOrNull(),
                        rainProbPct = sChild.child("rainProbPct").asIntOrNull()
                    )
                }.sortedBy { it.startAt }

                put(
                    hourNum,
                    HistoricalHourRecord(
                        hour = hourNum,
                        firstSeenAt = hChild.child("firstSeenAt").asLong(0L),
                        lastSeenAt = hChild.child("lastSeenAt").asLong(0L),
                        sampleCount = hChild.child("sampleCount").asInt(0),
                        gapCount = hChild.child("gapCount").asInt(0),
                        tomatoPct = hChild.child("tomatoPct").asFloatOrNull(),
                        chiliPct = hChild.child("chiliPct").asFloatOrNull(),
                        eggplantPct = hChild.child("eggplantPct").asFloatOrNull(),
                        temperatureC = hChild.child("temperatureC").asFloatOrNull(),
                        humidityPct = hChild.child("humidityPct").asFloatOrNull(),
                        lightPct = hChild.child("lightPct").asFloatOrNull(),
                        reservoirPct = hChild.child("reservoirPct").asFloatOrNull(),
                        reservoirGapIn = hChild.child("reservoirGapIn").asFloatOrNull(),
                        rainProbPct = hChild.child("rainProbPct").asIntOrNull(),
                        rainWetSamples = hChild.child("rainWetSamples").asInt(0),
                        rainSuspendedSamples = hChild.child("rainSuspendedSamples").asInt(0),
                        segments = segs
                    )
                )
            }
        }

        val irrigations = node.child("irrigations").children.mapNotNull { iChild ->
            val id = iChild.key ?: return@mapNotNull null
            val startedAt = iChild.child("startedAt").asLong(0L)
            val endedAt = iChild.child("endedAt").asLong(startedAt)
            val hour = iChild.child("hour").asInt(
                if (startedAt >= 1_700_000_000_000L) IotDomainLogic.phHourOfDay(startedAt) else 0
            ).coerceIn(0, 23)
            val minuteOfDay = iChild.child("minuteOfDay").asInt(
                if (startedAt >= 1_700_000_000_000L) IotDomainLogic.phMinuteOfDay(startedAt) else (hour * 60)
            ).coerceIn(0, 1439)
            IrrigationHistoryItem(
                irrigationId = iChild.child("irrigationId").asStr(id),
                crop = iChild.child("crop").asStr("tomato"),
                trigger = iChild.child("trigger").asStr("AUTO"),
                status = iChild.child("status").asStr("COMPLETED"),
                reason = iChild.child("reason").asStr(""),
                startedAt = startedAt,
                endedAt = endedAt,
                hour = hour,
                minuteOfDay = minuteOfDay,
                durationMs = iChild.child("durationMs").asLong(4000L),
                startMoisturePct = iChild.child("startMoisturePct").asFloatOrNull(),
                endMoisturePct = iChild.child("endMoisturePct").asFloatOrNull(),
                recordedOffline = iChild.child("recordedOffline").asBool(false)
            )
        }.sortedByDescending { it.startedAt }

        return DailyHistorySnapshot(
            deviceId = deviceId,
            dateKey = dateKey,
            updatedAt = node.child("updatedAt").asLong(0L),
            hours = hours,
            irrigations = irrigations
        )
    }

    private fun parseDailyHistoryFromJson(
        deviceId: String,
        dateKey: String,
        node: JSONObject
    ): DailyHistorySnapshot {
        val hoursObj = node.optJSONObject("hours")
        val hours = buildMap {
            if (hoursObj != null) {
                val keys = hoursObj.keys()
                while (keys.hasNext()) {
                    val hKey = keys.next()
                    val hObj = hoursObj.optJSONObject(hKey) ?: continue
                    val hourNum = hKey.toIntOrNull() ?: hObj.intVal("hour", -1)
                    if (hourNum !in 0..23) continue

                    val segsObj = hObj.optJSONObject("segments")
                    val segs = buildList {
                        if (segsObj != null) {
                            val sKeys = segsObj.keys()
                            while (sKeys.hasNext()) {
                                val sId = sKeys.next()
                                val sObj = segsObj.optJSONObject(sId) ?: continue
                                add(
                                    HistoricalHourSegment(
                                        segmentId = sId,
                                        startAt = sObj.lng("startAt", 0L),
                                        endAt = sObj.lng("endAt", 0L),
                                        sampleCount = sObj.intVal("samples", 0),
                                        tomatoPct = sObj.fltOrNull("tomatoPct"),
                                        chiliPct = sObj.fltOrNull("chiliPct"),
                                        eggplantPct = sObj.fltOrNull("eggplantPct"),
                                        temperatureC = sObj.fltOrNull("temperatureC"),
                                        humidityPct = sObj.fltOrNull("humidityPct"),
                                        lightPct = sObj.fltOrNull("lightPct"),
                                        reservoirPct = sObj.fltOrNull("reservoirPct"),
                                        reservoirGapIn = sObj.fltOrNull("reservoirGapIn"),
                                        rainProbPct = sObj.intOrNull("rainProbPct")
                                    )
                                )
                            }
                        }
                    }.sortedBy { it.startAt }

                    put(
                        hourNum,
                        HistoricalHourRecord(
                            hour = hourNum,
                            firstSeenAt = hObj.lng("firstSeenAt", 0L),
                            lastSeenAt = hObj.lng("lastSeenAt", 0L),
                            sampleCount = hObj.intVal("sampleCount", 0),
                            gapCount = hObj.intVal("gapCount", 0),
                            tomatoPct = hObj.fltOrNull("tomatoPct"),
                            chiliPct = hObj.fltOrNull("chiliPct"),
                            eggplantPct = hObj.fltOrNull("eggplantPct"),
                            temperatureC = hObj.fltOrNull("temperatureC"),
                            humidityPct = hObj.fltOrNull("humidityPct"),
                            lightPct = hObj.fltOrNull("lightPct"),
                            reservoirPct = hObj.fltOrNull("reservoirPct"),
                            reservoirGapIn = hObj.fltOrNull("reservoirGapIn"),
                            rainProbPct = hObj.intOrNull("rainProbPct"),
                            rainWetSamples = hObj.intVal("rainWetSamples", 0),
                            rainSuspendedSamples = hObj.intVal("rainSuspendedSamples", 0),
                            segments = segs
                        )
                    )
                }
            }
        }

        val irrObj = node.optJSONObject("irrigations")
        val irrigations = buildList {
            if (irrObj != null) {
                val keys = irrObj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val iObj = irrObj.optJSONObject(id) ?: continue
                    val startedAt = iObj.lng("startedAt", 0L)
                    val endedAt = iObj.lng("endedAt", startedAt)
                    val hour = iObj.intVal(
                        "hour",
                        if (startedAt >= 1_700_000_000_000L) IotDomainLogic.phHourOfDay(startedAt) else 0
                    ).coerceIn(0, 23)
                    val minuteOfDay = iObj.intVal(
                        "minuteOfDay",
                        if (startedAt >= 1_700_000_000_000L) IotDomainLogic.phMinuteOfDay(startedAt) else (hour * 60)
                    ).coerceIn(0, 1439)
                    add(
                        IrrigationHistoryItem(
                            irrigationId = iObj.str("irrigationId", id),
                            crop = iObj.str("crop", "tomato"),
                            trigger = iObj.str("trigger", "AUTO"),
                            status = iObj.str("status", "COMPLETED"),
                            reason = iObj.str("reason", ""),
                            startedAt = startedAt,
                            endedAt = endedAt,
                            hour = hour,
                            minuteOfDay = minuteOfDay,
                            durationMs = iObj.lng("durationMs", 4000L),
                            startMoisturePct = iObj.fltOrNull("startMoisturePct"),
                            endMoisturePct = iObj.fltOrNull("endMoisturePct"),
                            recordedOffline = iObj.bool("recordedOffline", false)
                        )
                    )
                }
            }
        }.sortedByDescending { it.startedAt }

        return DailyHistorySnapshot(
            deviceId = deviceId,
            dateKey = dateKey,
            updatedAt = node.lng("updatedAt", 0L),
            hours = hours,
            irrigations = irrigations
        )
    }

    private fun decodeHhmmKeyToMinuteOfDay(rawKey: String?): Int? {
        val clean = rawKey?.trim() ?: return null
        if (clean.length == 4 && clean.all { it.isDigit() }) {
            val hh = clean.substring(0, 2).toIntOrNull() ?: return null
            val mm = clean.substring(2, 4).toIntOrNull() ?: return null
            if (hh in 0..23 && mm in 0..59) return hh * 60 + mm
        }
        val asInt = clean.toIntOrNull() ?: return null
        return asInt.takeIf { it in 0..1439 }
    }

    private fun parseMinuteRecordFromSnapshot(mChild: DataSnapshot): HistoricalMinuteRecord? {
        val mOfDay = mChild.child("m").asIntOrNull()?.takeIf { it in 0..1439 }
            ?: decodeHhmmKeyToMinuteOfDay(mChild.key)
            ?: return null
        val samples = mChild.child("samples").asInt(mChild.child("sampleCount").asInt(0))
        val wetSamples = mChild.child("rainWetSamples").asInt(0)
        return HistoricalMinuteRecord(
            minuteOfDay = mOfDay,
            firstSeenAt = mChild.child("firstSeenAt").asLong(0L),
            lastSeenAt = mChild.child("lastSeenAt").asLong(0L),
            sampleCount = samples,
            tomatoPct = mChild.child("tomatoPct").asFloatOrNull(),
            chiliPct = mChild.child("chiliPct").asFloatOrNull(),
            eggplantPct = mChild.child("eggplantPct").asFloatOrNull(),
            temperatureC = mChild.child("temperatureC").asFloatOrNull(),
            humidityPct = mChild.child("humidityPct").asFloatOrNull(),
            lightPct = mChild.child("lightPct").asFloatOrNull(),
            reservoirPct = mChild.child("reservoirPct").asFloatOrNull(),
            reservoirGapIn = mChild.child("reservoirGapIn").asFloatOrNull(),
            reservoirState = mChild.child("reservoirState").asStr("UNKNOWN"),
            rainProbPct = mChild.child("rainProbPct").asIntOrNull(),
            rainWet = mChild.child("rainWet").asBool(wetSamples > 0),
            rainConfirmed = mChild.child("rainConfirmed").asBool(false),
            rainSuspended = mChild.child("rainSuspended").asBool(false),
            rainWetSamples = wetSamples
        )
    }

    private fun parseMinuteRecordFromJson(mKey: String, mObj: JSONObject): HistoricalMinuteRecord? {
        val mOfDay = mObj.intOrNull("m")?.takeIf { it in 0..1439 }
            ?: decodeHhmmKeyToMinuteOfDay(mKey)
            ?: return null
        val samples = mObj.intVal("samples", mObj.intVal("sampleCount", 0))
        val wetSamples = mObj.intVal("rainWetSamples", 0)
        return HistoricalMinuteRecord(
            minuteOfDay = mOfDay,
            firstSeenAt = mObj.lng("firstSeenAt", 0L),
            lastSeenAt = mObj.lng("lastSeenAt", 0L),
            sampleCount = samples,
            tomatoPct = mObj.fltOrNull("tomatoPct"),
            chiliPct = mObj.fltOrNull("chiliPct"),
            eggplantPct = mObj.fltOrNull("eggplantPct"),
            temperatureC = mObj.fltOrNull("temperatureC"),
            humidityPct = mObj.fltOrNull("humidityPct"),
            lightPct = mObj.fltOrNull("lightPct"),
            reservoirPct = mObj.fltOrNull("reservoirPct"),
            reservoirGapIn = mObj.fltOrNull("reservoirGapIn"),
            reservoirState = mObj.str("reservoirState", "UNKNOWN"),
            rainProbPct = mObj.intOrNull("rainProbPct"),
            rainWet = mObj.bool("rainWet", wetSamples > 0),
            rainConfirmed = mObj.bool("rainConfirmed", false),
            rainSuspended = mObj.bool("rainSuspended", false),
            rainWetSamples = wetSamples
        )
    }

    /**
     * Serializes a lightweight local cache JSON for instant startup restore (<50ms),
     * and restores it strictly tagged with `isFromLocalCache = true` so UI never confuses
     * cached readings with verified live ESP32 data.
     */
    fun serializeSnapshotForLocalCache(snapshot: LiveDeviceSnapshot): String {
        return JSONObject().apply {
            put("cachedAtMs", System.currentTimeMillis())
            put("receivedAtClientMs", snapshot.receivedAtClientMs)
            put("meta", JSONObject().apply {
                put("deviceId", snapshot.meta.deviceId)
                put("displayName", snapshot.meta.displayName)
                put("model", snapshot.meta.model)
                put("hardwareRevision", snapshot.meta.hardwareRevision)
                put("firmwareVersion", snapshot.meta.firmwareVersion)
                put("ownershipState", snapshot.meta.ownershipState)
                put("ownerUid", snapshot.meta.ownerUid)
                put("ownershipEpoch", snapshot.meta.ownershipEpoch)
                put("resetRequired", snapshot.meta.resetRequired)
                put("claimedAt", snapshot.meta.claimedAt)
                put("location", JSONObject().apply {
                    put("lat", snapshot.meta.lat)
                    put("lon", snapshot.meta.lon)
                    put("name", snapshot.meta.locationName)
                    put("source", snapshot.meta.locationSource)
                })
            })
            put("status", JSONObject().apply {
                put("online", snapshot.status.online)
                put("lastSeenAt", snapshot.status.lastSeenAt)
                put("bootId", snapshot.status.bootId)
                put("firmwareVersion", snapshot.status.firmwareVersion)
                put("ipAddress", snapshot.status.ipAddress)
                put("wifiRssi", snapshot.status.wifiRssi)
                put("mode", snapshot.status.mode)
                put("autoIrrigationEnabled", snapshot.status.autoIrrigationEnabled)
                put("rainSuspensionActive", snapshot.status.rainSuspensionActive)
                put("weatherAdvisoryActive", snapshot.status.weatherAdvisoryActive)
                put("weatherUnavailableFault", snapshot.status.weatherUnavailableFault)
                put("emergencyStop", snapshot.status.emergencyStop)
                put("faultLockout", snapshot.status.faultLockout)
                put("activeValve", snapshot.status.activeValve)
                put("pumpState", snapshot.status.pumpState)
                put("irrigationState", snapshot.status.irrigationState)
                put("activePumpElapsedSec", snapshot.status.activePumpElapsedSec)
                put("activeTargetDurationSec", snapshot.status.activeTargetDurationSec)
                put("lastAbortReason", snapshot.status.lastAbortReason)
                put("physicalLoadsEnabled", snapshot.status.physicalLoadsEnabled)
                put("ownershipEpoch", snapshot.status.ownershipEpoch)
            })
            put("telemetry", JSONObject().apply {
                put("updatedAt", snapshot.telemetry.updatedAt)
                snapshot.telemetry.temperatureC?.let { put("temperatureC", it.toDouble()) }
                snapshot.telemetry.humidityPct?.let { put("humidityPct", it.toDouble()) }
                put("dhtValid", snapshot.telemetry.dhtValid)
                put("tempBand", snapshot.telemetry.tempBand)
                put("humidityBand", snapshot.telemetry.humidityBand)
                put("lightRaw", snapshot.telemetry.lightRaw)
                snapshot.telemetry.lightBrightnessPct?.let { put("lightBrightnessPct", it.toDouble()) }
                put("lightValid", snapshot.telemetry.lightValid)
                put("lightBand", snapshot.telemetry.lightBand)
                put("rainRawWet", snapshot.telemetry.rainRawWet)
                put("rainWetConfirmed", snapshot.telemetry.rainWetConfirmed)
                put("rainAccidentalWet", snapshot.telemetry.rainAccidentalWet)
                put("rainValid", snapshot.telemetry.rainValid)
                snapshot.telemetry.reservoirAirGapInches?.let { put("reservoirAirGapInches", it.toDouble()) }
                snapshot.telemetry.reservoirPct?.let { put("reservoirPct", it.toDouble()) }
                put("reservoirValid", snapshot.telemetry.reservoirValid)
                put("reservoirState", snapshot.telemetry.reservoirState)
                put("soil", JSONObject().apply {
                    for (z in snapshot.telemetry.zones) {
                        put(z.slug, JSONObject().apply {
                            put("raw", z.raw)
                            z.pct?.let { put("pct", it.toDouble()) }
                            put("valid", z.valid)
                            put("needsWater", z.needsWater)
                        })
                    }
                })
            })
            put("weather", JSONObject().apply {
                put("provider", snapshot.weather.provider)
                put("valid", snapshot.weather.valid)
                put("rainProbabilityPct", snapshot.weather.rainProbabilityPct)
                put("windowHours", snapshot.weather.windowHours)
                put("windowLabel", snapshot.weather.windowLabel)
                put("locationName", snapshot.weather.locationName)
                put("lat", snapshot.weather.lat)
                put("lon", snapshot.weather.lon)
                put("fetchedAt", snapshot.weather.fetchedAt)
                put("validUntil", snapshot.weather.validUntil)
            })
            put("config", JSONObject(defaultConfigMap()).apply {
                put("mode", snapshot.config.mode)
                put("autoIrrigationEnabled", snapshot.config.autoIrrigationEnabled)
                put("rainThresholdPct", snapshot.config.rainThresholdPct)
                put("wetConfirmMs", snapshot.config.wetConfirmMs)
                put("dryRecoverMs", snapshot.config.dryRecoverMs)
                put("reservoirFullGapIn", snapshot.config.reservoirFullGapIn.toDouble())
                put("reservoirEmptyGapIn", snapshot.config.reservoirEmptyGapIn.toDouble())
                put("reservoirLowPct", snapshot.config.reservoirLowPct.toDouble())
                put("reservoirCriticalPct", snapshot.config.reservoirCriticalPct.toDouble())
                put("tempHighC", snapshot.config.tempHighC.toDouble())
                put("humLowRh", snapshot.config.humLowRh.toDouble())
                put("lightHighPct", snapshot.config.lightHighPct.toDouble())
                put("lightLowPct", snapshot.config.lightLowPct.toDouble())
                put("buzzerMuted", snapshot.config.buzzerMuted)
                put("physicalLoadsEnabled", snapshot.config.physicalLoadsEnabled)
            })
        }.toString()
    }

    fun deserializeCachedSnapshot(deviceId: String, rawJson: String): LiveDeviceSnapshot? {
        return runCatching {
            val root = JSONObject(rawJson)
            val savedRx = root.lng("receivedAtClientMs", 0L)
            parseLiveDeviceSnapshotFromJson(deviceId, root).copy(
                receivedAtClientMs = savedRx,
                isFromLocalCache = true,
                syncOrigin = SyncDataOrigin.LOCAL_CACHE,
                lastSyncLatencyMs = 0L
            )
        }.getOrNull()
    }

    private fun cropConfigToMap(c: CropZoneConfig): Map<String, Any> = mapOf(
        "enabled" to c.enabled,
        "dryRaw" to c.dryRaw.coerceIn(500, 4000),
        "wetRaw" to c.wetRaw.coerceIn(300, 3500),
        "startPct" to c.startPct.coerceIn(10f, 80f),
        "stopPct" to c.stopPct.coerceIn(c.startPct + 5f, 95f),
        "pulseMs" to IotDomainLogic.clampPulseDurationMs(c.pulseMs),
        "maxRuntimeMs" to c.maxRuntimeMs.coerceIn(4000L, 15000L),
        "cooldownMs" to c.cooldownMs.coerceIn(30000L, 900000L)
    )

    private fun defaultConfigMap(): Map<String, Any> = mapOf(
        "mode" to "OUTDOOR",
        "autoIrrigationEnabled" to true,
        "rainThresholdPct" to 60,
        "wetConfirmMs" to 30000L,
        "dryRecoverMs" to 120000L,
        "reservoirFullGapIn" to 1.0f,
        "reservoirEmptyGapIn" to 7.0f,
        "reservoirLowPct" to 20f,
        "reservoirCriticalPct" to 10f,
        "tempHighC" to 35f,
        "humLowRh" to 40f,
        "lightHighPct" to 85f,
        "lightLowPct" to 20f,
        "buzzerMuted" to false,
        "physicalLoadsEnabled" to true,
        "crops" to mapOf(
            "tomato" to cropConfigToMap(CropZoneConfig("tomato", startPct = 35f, stopPct = 55f)),
            "chili" to cropConfigToMap(CropZoneConfig("chili", startPct = 32f, stopPct = 52f)),
            "eggplant" to cropConfigToMap(CropZoneConfig("eggplant", startPct = 38f, stopPct = 58f))
        )
    )

    private fun parseLiveDeviceSnapshot(deviceId: String, root: DataSnapshot): LiveDeviceSnapshot {
        val m = root.child("meta")
        val meta = DeviceMeta(
            deviceId = m.child("deviceId").asStr(deviceId),
            displayName = m.child("displayName").asStr("SmartPlantCare Box"),
            model = m.child("model").asStr("SmartPlantCare-3Zone"),
            hardwareRevision = m.child("hardwareRevision").asStr("ESP32-WROOM32-V3.1-I2C"),
            firmwareVersion = m.child("firmwareVersion").asStr("3.1.0-i2c"),
            ownershipState = m.child("ownershipState").asStr("UNCLAIMED"),
            ownerUid = m.child("ownerUid").asStr(""),
            ownershipEpoch = m.child("ownershipEpoch").asLong(0L),
            resetRequired = m.child("resetRequired").asBool(false),
            claimedAt = m.child("claimedAt").asLong(0L),
            lat = m.child("location").child("lat").asDouble(14.5995),
            lon = m.child("location").child("lon").asDouble(120.9842),
            locationName = m.child("location").child("name").asStr(""),
            locationSource = m.child("location").child("source").asStr("")
        )

        val s = root.child("status")
        val status = DeviceStatus(
            online = s.child("online").asBool(false),
            lastSeenAt = s.child("lastSeenAt").asLong(0L),
            bootId = s.child("bootId").asStr(""),
            firmwareVersion = s.child("firmwareVersion").asStr(meta.firmwareVersion),
            ipAddress = s.child("ipAddress").asStr(""),
            wifiRssi = s.child("wifiRssi").asInt(-127),
            mode = s.child("mode").asStr("OUTDOOR"),
            autoIrrigationEnabled = s.child("autoIrrigationEnabled").asBool(true),
            rainSuspensionActive = s.child("rainSuspensionActive").asBool(false),
            weatherAdvisoryActive = s.child("weatherAdvisoryActive").asBool(false),
            weatherUnavailableFault = s.child("weatherUnavailableFault").asBool(false),
            emergencyStop = s.child("emergencyStop").asBool(false),
            faultLockout = s.child("faultLockout").asBool(false),
            activeValve = s.child("activeValve").asStr("none"),
            pumpState = s.child("pumpState").asStr("OFF"),
            irrigationState = s.child("irrigationState").asStr("IDLE"),
            activePumpElapsedSec = s.child("activePumpElapsedSec").asInt(0),
            activeTargetDurationSec = s.child("activeTargetDurationSec").asInt(4),
            lastAbortReason = s.child("lastAbortReason").asStr("NONE"),
            physicalLoadsEnabled = s.child("physicalLoadsEnabled").asBool(false),
            lcdAddress = s.child("lcdAddress").asInt(0),
            freeHeap = s.child("freeHeap").asLong(0L),
            ownershipEpoch = s.child("ownershipEpoch").asLong(meta.ownershipEpoch)
        )

        val t = root.child("telemetry")
        val soilNode = t.child("soil")
        val telemetry = DeviceTelemetry(
            updatedAt = t.child("updatedAt").asLong(0L),
            temperatureC = t.child("temperatureC").asFloatOrNull(),
            humidityPct = t.child("humidityPct").asFloatOrNull(),
            dhtValid = t.child("dhtValid").asBool(false),
            tempBand = t.child("tempBand").asStr("ERR"),
            humidityBand = t.child("humidityBand").asStr("ERR"),
            lightRaw = t.child("lightRaw").asInt(0),
            lightBrightnessPct = t.child("lightBrightnessPct").asFloatOrNull(),
            lightValid = t.child("lightValid").asBool(false),
            lightBand = t.child("lightBand").asStr("ERR"),
            rainRawWet = t.child("rainRawWet").asBool(false),
            rainWetConfirmed = t.child("rainWetConfirmed").asBool(false),
            rainAccidentalWet = t.child("rainAccidentalWet").asBool(false),
            rainValid = t.child("rainValid").asBool(true),
            reservoirAirGapInches = t.child("reservoirAirGapInches").asFloatOrNull(),
            reservoirPct = t.child("reservoirPct").asFloatOrNull(),
            reservoirValid = t.child("reservoirValid").asBool(false),
            reservoirState = t.child("reservoirState").asStr("UNKNOWN"),
            tomato = parseSoilZone(soilNode.child("tomato"), "tomato", "Tomato", "Zone 1", "Tomato Zone"),
            chili = parseSoilZone(soilNode.child("chili"), "chili", "Chili", "Zone 2", "Chili Zone"),
            eggplant = parseSoilZone(soilNode.child("eggplant"), "eggplant", "Eggplant", "Zone 3", "Eggplant Zone")
        )

        val w = root.child("weather")
        val weather = DeviceWeather(
            provider = w.child("provider").asStr("open-meteo"),
            valid = w.child("valid").asBool(false),
            rainProbabilityPct = w.child("rainProbabilityPct").asInt(-1),
            windowHours = w.child("windowHours").asInt(3),
            windowLabel = w.child("windowLabel").asStr("NEXT 3 H"),
            locationName = w.child("locationName").asStr(meta.locationName),
            lat = w.child("lat").asDouble(meta.lat),
            lon = w.child("lon").asDouble(meta.lon),
            fetchedAt = w.child("fetchedAt").asLong(0L),
            validUntil = w.child("validUntil").asLong(0L)
        )

        val c = root.child("config")
        val cropsNode = c.child("crops")
        val config = DeviceConfig(
            mode = c.child("mode").asStr(status.mode),
            autoIrrigationEnabled = c.child("autoIrrigationEnabled").asBool(true),
            rainThresholdPct = c.child("rainThresholdPct").asInt(60),
            wetConfirmMs = c.child("wetConfirmMs").asLong(30000L),
            dryRecoverMs = c.child("dryRecoverMs").asLong(120000L),
            reservoirFullGapIn = c.child("reservoirFullGapIn").asFloatOrNull() ?: 1.0f,
            reservoirEmptyGapIn = c.child("reservoirEmptyGapIn").asFloatOrNull() ?: 7.0f,
            reservoirLowPct = c.child("reservoirLowPct").asFloatOrNull() ?: 20f,
            reservoirCriticalPct = c.child("reservoirCriticalPct").asFloatOrNull() ?: 10f,
            tempHighC = c.child("tempHighC").asFloatOrNull() ?: 35f,
            humLowRh = c.child("humLowRh").asFloatOrNull() ?: 40f,
            lightHighPct = c.child("lightHighPct").asFloatOrNull() ?: 85f,
            lightLowPct = c.child("lightLowPct").asFloatOrNull() ?: 20f,
            buzzerMuted = c.child("buzzerMuted").asBool(false),
            physicalLoadsEnabled = c.child("physicalLoadsEnabled").asBool(status.physicalLoadsEnabled),
            tomato = parseCropConfig(cropsNode.child("tomato"), "tomato", 35f, 55f),
            chili = parseCropConfig(cropsNode.child("chili"), "chili", 32f, 52f),
            eggplant = parseCropConfig(cropsNode.child("eggplant"), "eggplant", 38f, 58f)
        )

        val members = root.child("acl").child("members").children.mapNotNull { mChild ->
            val mUid = mChild.key ?: return@mapNotNull null
            AclMember(
                uid = mUid,
                email = mChild.child("email").asStr(""),
                displayName = mChild.child("displayName").asStr(mUid.take(8)),
                role = DeviceRole.fromSlug(mChild.child("role").asStr("viewer")),
                addedAt = mChild.child("addedAt").asLong(0L)
            )
        }.sortedBy { it.role.ordinal }

        val now = System.currentTimeMillis()
        val invites = root.child("acl").child("invitations").children.mapNotNull { iChild ->
            val code = iChild.key ?: return@mapNotNull null
            val st = iChild.child("status").asStr("")
            val exp = iChild.child("expiresAt").asLong(0L)
            val crAt = iChild.child("createdAt").asLong(0L)
            val is24hPassed = crAt > 0L && (now - crAt) >= 24 * 60 * 60 * 1000L
            if (st != "PENDING" || (exp > 0L && now > exp) || is24hPassed) return@mapNotNull null
            ShareInvite(
                inviteCode = code,
                deviceId = deviceId,
                role = DeviceRole.fromSlug(iChild.child("role").asStr("viewer")),
                status = st,
                createdAt = crAt,
                expiresAt = exp
            )
        }.sortedByDescending { it.createdAt }

        val events = root.child("events").children.mapNotNull { eChild ->
            val evId = eChild.key ?: return@mapNotNull null
            DeviceEvent(
                eventId = evId,
                type = eChild.child("type").asStr("INFO"),
                severity = eChild.child("severity").asStr("info"),
                title = eChild.child("title").asStr("Device Event"),
                message = eChild.child("message").asStr(""),
                crop = eChild.child("crop").getValue(String::class.java),
                createdAt = eChild.child("createdAt").asLong(0L)
            )
        }.sortedByDescending { it.createdAt }.take(50)

        val cropPhotos = buildMap {
            for (pChild in root.child("cropPhotos").children) {
                val slug = pChild.key?.lowercase() ?: continue
                val url = pChild.child("url").asStr("")
                if (url.isNotBlank()) {
                    put(
                        slug,
                        CropZonePhoto(
                            url = url,
                            publicId = pChild.child("publicId").asStr(""),
                            updatedAt = pChild.child("updatedAt").asLong(0L),
                            updatedByUid = pChild.child("updatedByUid").asStr(""),
                            updatedByName = pChild.child("updatedByName").asStr("")
                        )
                    )
                }
            }
        }

        val cmdResults = buildMap {
            for (rChild in root.child("commandResults").children) {
                val id = rChild.key ?: continue
                put(
                    id,
                    CommandResult(
                        commandId = id,
                        state = rChild.child("state").asStr(""),
                        reason = rChild.child("reason").asStr(""),
                        updatedAt = rChild.child("updatedAt").asLong(0L)
                    )
                )
            }
        }

        return LiveDeviceSnapshot(
            deviceId = deviceId,
            meta = meta,
            status = status,
            telemetry = telemetry,
            weather = weather,
            config = config,
            cropPhotos = cropPhotos,
            members = members,
            activeInvites = invites,
            events = events,
            commandResults = cmdResults,
            receivedAtClientMs = System.currentTimeMillis(),
            isFromLocalCache = false,
            syncOrigin = SyncDataOrigin.LIVE_WEBSOCKET
        )
    }

    private fun parseSoilZone(
        node: DataSnapshot,
        slug: String,
        displayName: String,
        gpio: String,
        valve: String
    ): SoilZoneTelemetry = SoilZoneTelemetry(
        slug = slug,
        displayName = displayName,
        gpioLabel = gpio,
        valveLabel = valve,
        raw = node.child("raw").asInt(0),
        pct = node.child("pct").asFloatOrNull(),
        valid = node.child("valid").asBool(false),
        needsWater = node.child("needsWater").asBool(false)
    )

    private fun parseCropConfig(
        node: DataSnapshot,
        slug: String,
        defaultStart: Float,
        defaultStop: Float
    ): CropZoneConfig = CropZoneConfig(
        slug = slug,
        enabled = node.child("enabled").asBool(true),
        dryRaw = node.child("dryRaw").asInt(3250),
        wetRaw = node.child("wetRaw").asInt(1350),
        startPct = node.child("startPct").asFloatOrNull() ?: defaultStart,
        stopPct = node.child("stopPct").asFloatOrNull() ?: defaultStop,
        pulseMs = node.child("pulseMs").asLong(4000L),
        maxRuntimeMs = node.child("maxRuntimeMs").asLong(15000L),
        cooldownMs = node.child("cooldownMs").asLong(180000L)
    )

    private fun DataSnapshot.asStr(default: String): String =
        getValue(String::class.java) ?: default

    private fun DataSnapshot.asBool(default: Boolean): Boolean =
        when (val v = value) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> default
        }

    private fun DataSnapshot.asInt(default: Int): Int =
        when (val v = value) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }

    private fun DataSnapshot.asIntOrNull(): Int? =
        when (val v = value) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }

    private fun DataSnapshot.asLong(default: Long): Long =
        when (val v = value) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }

    private fun DataSnapshot.asDouble(default: Double): Double =
        when (val v = value) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }

    private fun DataSnapshot.asFloatOrNull(): Float? =
        when (val v = value) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull()
            else -> null
        }

    // ─── Direct HTTPS REST JSON Parser (mirrors parseLiveDeviceSnapshot 1:1) ──

    private fun parseLiveDeviceSnapshotFromJson(deviceId: String, root: JSONObject): LiveDeviceSnapshot {
        val m = root.optJSONObject("meta")
        val loc = m?.optJSONObject("location")
        val meta = DeviceMeta(
            deviceId = m.str("deviceId", deviceId),
            displayName = m.str("displayName", "SmartPlantCare Box"),
            model = m.str("model", "SmartPlantCare-3Zone"),
            hardwareRevision = m.str("hardwareRevision", "ESP32-WROOM32-V3.1-I2C"),
            firmwareVersion = m.str("firmwareVersion", "3.1.0-i2c"),
            ownershipState = m.str("ownershipState", "UNCLAIMED"),
            ownerUid = m.str("ownerUid", ""),
            ownershipEpoch = m.lng("ownershipEpoch", 0L),
            resetRequired = m.bool("resetRequired", false),
            claimedAt = m.lng("claimedAt", 0L),
            lat = loc.dbl("lat", 14.5995),
            lon = loc.dbl("lon", 120.9842),
            locationName = loc.str("name", ""),
            locationSource = loc.str("source", "")
        )

        val s = root.optJSONObject("status")
        val status = DeviceStatus(
            online = s.bool("online", false),
            lastSeenAt = s.lng("lastSeenAt", 0L),
            bootId = s.str("bootId", ""),
            firmwareVersion = s.str("firmwareVersion", meta.firmwareVersion),
            ipAddress = s.str("ipAddress", ""),
            wifiRssi = s.intVal("wifiRssi", -127),
            mode = s.str("mode", "OUTDOOR"),
            autoIrrigationEnabled = s.bool("autoIrrigationEnabled", true),
            rainSuspensionActive = s.bool("rainSuspensionActive", false),
            weatherAdvisoryActive = s.bool("weatherAdvisoryActive", false),
            weatherUnavailableFault = s.bool("weatherUnavailableFault", false),
            emergencyStop = s.bool("emergencyStop", false),
            faultLockout = s.bool("faultLockout", false),
            activeValve = s.str("activeValve", "none"),
            pumpState = s.str("pumpState", "OFF"),
            irrigationState = s.str("irrigationState", "IDLE"),
            activePumpElapsedSec = s.intVal("activePumpElapsedSec", 0),
            activeTargetDurationSec = s.intVal("activeTargetDurationSec", 4),
            lastAbortReason = s.str("lastAbortReason", "NONE"),
            physicalLoadsEnabled = s.bool("physicalLoadsEnabled", false),
            lcdAddress = s.intVal("lcdAddress", 0),
            freeHeap = s.lng("freeHeap", 0L),
            ownershipEpoch = s.lng("ownershipEpoch", meta.ownershipEpoch)
        )

        val t = root.optJSONObject("telemetry")
        val soilNode = t?.optJSONObject("soil")
        val telemetry = DeviceTelemetry(
            updatedAt = t.lng("updatedAt", 0L),
            temperatureC = t.fltOrNull("temperatureC"),
            humidityPct = t.fltOrNull("humidityPct"),
            dhtValid = t.bool("dhtValid", false),
            tempBand = t.str("tempBand", "ERR"),
            humidityBand = t.str("humidityBand", "ERR"),
            lightRaw = t.intVal("lightRaw", 0),
            lightBrightnessPct = t.fltOrNull("lightBrightnessPct"),
            lightValid = t.bool("lightValid", false),
            lightBand = t.str("lightBand", "ERR"),
            rainRawWet = t.bool("rainRawWet", false),
            rainWetConfirmed = t.bool("rainWetConfirmed", false),
            rainAccidentalWet = t.bool("rainAccidentalWet", false),
            rainValid = t.bool("rainValid", true),
            reservoirAirGapInches = t.fltOrNull("reservoirAirGapInches"),
            reservoirPct = t.fltOrNull("reservoirPct"),
            reservoirValid = t.bool("reservoirValid", false),
            reservoirState = t.str("reservoirState", "UNKNOWN"),
            tomato = parseSoilZoneFromJson(soilNode?.optJSONObject("tomato"), "tomato", "Tomato", "Zone 1", "Tomato Zone"),
            chili = parseSoilZoneFromJson(soilNode?.optJSONObject("chili"), "chili", "Chili", "Zone 2", "Chili Zone"),
            eggplant = parseSoilZoneFromJson(soilNode?.optJSONObject("eggplant"), "eggplant", "Eggplant", "Zone 3", "Eggplant Zone")
        )

        val w = root.optJSONObject("weather")
        val weather = DeviceWeather(
            provider = w.str("provider", "open-meteo"),
            valid = w.bool("valid", false),
            rainProbabilityPct = w.intVal("rainProbabilityPct", -1),
            windowHours = w.intVal("windowHours", 3),
            windowLabel = w.str("windowLabel", "NEXT 3 H"),
            locationName = w.str("locationName", meta.locationName),
            lat = w.dbl("lat", meta.lat),
            lon = w.dbl("lon", meta.lon),
            fetchedAt = w.lng("fetchedAt", 0L),
            validUntil = w.lng("validUntil", 0L)
        )

        val c = root.optJSONObject("config")
        val cropsNode = c?.optJSONObject("crops")
        val config = DeviceConfig(
            mode = c.str("mode", status.mode),
            autoIrrigationEnabled = c.bool("autoIrrigationEnabled", true),
            rainThresholdPct = c.intVal("rainThresholdPct", 60),
            wetConfirmMs = c.lng("wetConfirmMs", 30000L),
            dryRecoverMs = c.lng("dryRecoverMs", 120000L),
            reservoirFullGapIn = c.fltOrNull("reservoirFullGapIn") ?: 1.0f,
            reservoirEmptyGapIn = c.fltOrNull("reservoirEmptyGapIn") ?: 7.0f,
            reservoirLowPct = c.fltOrNull("reservoirLowPct") ?: 20f,
            reservoirCriticalPct = c.fltOrNull("reservoirCriticalPct") ?: 10f,
            tempHighC = c.fltOrNull("tempHighC") ?: 35f,
            humLowRh = c.fltOrNull("humLowRh") ?: 40f,
            lightHighPct = c.fltOrNull("lightHighPct") ?: 85f,
            lightLowPct = c.fltOrNull("lightLowPct") ?: 20f,
            buzzerMuted = c.bool("buzzerMuted", false),
            physicalLoadsEnabled = c.bool("physicalLoadsEnabled", status.physicalLoadsEnabled),
            tomato = parseCropConfigFromJson(cropsNode?.optJSONObject("tomato"), "tomato", 35f, 55f),
            chili = parseCropConfigFromJson(cropsNode?.optJSONObject("chili"), "chili", 32f, 52f),
            eggplant = parseCropConfigFromJson(cropsNode?.optJSONObject("eggplant"), "eggplant", 38f, 58f)
        )

        val acl = root.optJSONObject("acl")
        val membersObj = acl?.optJSONObject("members")
        val members = buildList {
            if (membersObj != null) {
                val keys = membersObj.keys()
                while (keys.hasNext()) {
                    val mUid = keys.next()
                    val mChild = membersObj.optJSONObject(mUid) ?: continue
                    add(
                        AclMember(
                            uid = mUid,
                            email = mChild.str("email", ""),
                            displayName = mChild.str("displayName", mUid.take(8)),
                            role = DeviceRole.fromSlug(mChild.str("role", "viewer")),
                            addedAt = mChild.lng("addedAt", 0L)
                        )
                    )
                }
            }
        }.sortedBy { it.role.ordinal }

        val now = System.currentTimeMillis()
        val invitesObj = acl?.optJSONObject("invitations")
        val invites = buildList {
            if (invitesObj != null) {
                val keys = invitesObj.keys()
                while (keys.hasNext()) {
                    val code = keys.next()
                    val iChild = invitesObj.optJSONObject(code) ?: continue
                    val st = iChild.str("status", "")
                    val exp = iChild.lng("expiresAt", 0L)
                    val crAt = iChild.lng("createdAt", 0L)
                    val is24hPassed = crAt > 0L && (now - crAt) >= 24 * 60 * 60 * 1000L
                    if (st != "PENDING" || (exp > 0L && now > exp) || is24hPassed) continue
                    add(
                        ShareInvite(
                            inviteCode = code,
                            deviceId = deviceId,
                            role = DeviceRole.fromSlug(iChild.str("role", "viewer")),
                            status = st,
                            createdAt = crAt,
                            expiresAt = exp
                        )
                    )
                }
            }
        }.sortedByDescending { it.createdAt }

        val eventsObj = root.optJSONObject("events")
        val events = buildList {
            if (eventsObj != null) {
                val keys = eventsObj.keys()
                while (keys.hasNext()) {
                    val evId = keys.next()
                    val eChild = eventsObj.optJSONObject(evId) ?: continue
                    val cropVal = if (eChild.isNull("crop")) null else eChild.optString("crop").takeIf { it.isNotBlank() }
                    add(
                        DeviceEvent(
                            eventId = evId,
                            type = eChild.str("type", "INFO"),
                            severity = eChild.str("severity", "info"),
                            title = eChild.str("title", "Device Event"),
                            message = eChild.str("message", ""),
                            crop = cropVal,
                            createdAt = eChild.lng("createdAt", 0L)
                        )
                    )
                }
            }
        }.sortedByDescending { it.createdAt }.take(50)

        val cropPhotosObj = root.optJSONObject("cropPhotos")
        val cropPhotos = buildMap {
            if (cropPhotosObj != null) {
                val keys = cropPhotosObj.keys()
                while (keys.hasNext()) {
                    val rawSlug = keys.next()
                    val pChild = cropPhotosObj.optJSONObject(rawSlug) ?: continue
                    val url = pChild.str("url", "")
                    if (url.isNotBlank()) {
                        put(
                            rawSlug.lowercase(),
                            CropZonePhoto(
                                url = url,
                                publicId = pChild.str("publicId", ""),
                                updatedAt = pChild.lng("updatedAt", 0L),
                                updatedByUid = pChild.str("updatedByUid", ""),
                                updatedByName = pChild.str("updatedByName", "")
                            )
                        )
                    }
                }
            }
        }

        val cmdResultsObj = root.optJSONObject("commandResults")
        val cmdResults = buildMap {
            if (cmdResultsObj != null) {
                val keys = cmdResultsObj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val rChild = cmdResultsObj.optJSONObject(id) ?: continue
                    put(
                        id,
                        CommandResult(
                            commandId = id,
                            state = rChild.str("state", ""),
                            reason = rChild.str("reason", ""),
                            updatedAt = rChild.lng("updatedAt", 0L)
                        )
                    )
                }
            }
        }

        return LiveDeviceSnapshot(
            deviceId = deviceId,
            meta = meta,
            status = status,
            telemetry = telemetry,
            weather = weather,
            config = config,
            cropPhotos = cropPhotos,
            members = members,
            activeInvites = invites,
            events = events,
            commandResults = cmdResults,
            receivedAtClientMs = System.currentTimeMillis(),
            isFromLocalCache = false,
            syncOrigin = SyncDataOrigin.LIVE_REST
        )
    }

    private fun parseSoilZoneFromJson(
        node: JSONObject?,
        slug: String,
        displayName: String,
        gpio: String,
        valve: String
    ): SoilZoneTelemetry = SoilZoneTelemetry(
        slug = slug,
        displayName = displayName,
        gpioLabel = gpio,
        valveLabel = valve,
        raw = node.intVal("raw", 0),
        pct = node.fltOrNull("pct"),
        valid = node.bool("valid", false),
        needsWater = node.bool("needsWater", false)
    )

    private fun parseCropConfigFromJson(
        node: JSONObject?,
        slug: String,
        defaultStart: Float,
        defaultStop: Float
    ): CropZoneConfig = CropZoneConfig(
        slug = slug,
        enabled = node.bool("enabled", true),
        dryRaw = node.intVal("dryRaw", 3250),
        wetRaw = node.intVal("wetRaw", 1350),
        startPct = node.fltOrNull("startPct") ?: defaultStart,
        stopPct = node.fltOrNull("stopPct") ?: defaultStop,
        pulseMs = node.lng("pulseMs", 4000L),
        maxRuntimeMs = node.lng("maxRuntimeMs", 15000L),
        cooldownMs = node.lng("cooldownMs", 180000L)
    )

    private fun JSONObject?.str(key: String, default: String): String {
        if (this == null || !has(key) || isNull(key)) return default
        return opt(key)?.toString() ?: default
    }

    private fun JSONObject?.bool(key: String, default: Boolean): Boolean {
        if (this == null || !has(key) || isNull(key)) return default
        return when (val v = opt(key)) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> default
        }
    }

    private fun JSONObject?.intVal(key: String, default: Int): Int {
        if (this == null || !has(key) || isNull(key)) return default
        return when (val v = opt(key)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    private fun JSONObject?.intOrNull(key: String): Int? {
        if (this == null || !has(key) || isNull(key)) return null
        return when (val v = opt(key)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject?.lng(key: String, default: Long): Long {
        if (this == null || !has(key) || isNull(key)) return default
        return when (val v = opt(key)) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }
    }

    private fun JSONObject?.dbl(key: String, default: Double): Double {
        if (this == null || !has(key) || isNull(key)) return default
        return when (val v = opt(key)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }
    }

    private fun JSONObject?.fltOrNull(key: String): Float? {
        if (this == null || !has(key) || isNull(key)) return null
        return when (val v = opt(key)) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull()
            else -> null
        }
    }
}
