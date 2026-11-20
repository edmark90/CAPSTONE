package com.example.smartplantcare.iot

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Firebase Realtime Database repository for Smart Plant Care V3.1 IoT devices.
 * Works seamlessly on both Free Spark Plan and Blaze Plan.
 */
class IotFirebaseRepository {

    companion object {
        private const val RTDB_URL =
            "https://smartplantcare-7c3e5-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(RTDB_URL)
    private val rootRef = db.reference

    fun observeUserDevices(uid: String): Flow<List<UserDeviceLink>> = callbackFlow {
        val ref = rootRef.child("users").child(uid).child("devices")
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

    fun observeDevice(deviceId: String): Flow<LiveDeviceSnapshot?> = callbackFlow {
        val ref = rootRef.child("devices").child(deviceId)
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
        val now = System.currentTimeMillis()
        val cleanName = displayName.trim().ifEmpty { "SmartPlantCare Box" }
        val deviceRef = rootRef.child("devices").child(deviceId)

        // Preserve existing /config calibration if already present on this unit
        val existingConfig = deviceRef.child("config").get().await()

        val metaMap = mapOf(
            "deviceId" to deviceId,
            "displayName" to cleanName,
            "model" to "SmartPlantCare-3Zone",
            "hardwareRevision" to "ESP32-WROOM32-V3.1-I2C",
            "firmwareVersion" to "3.1.0-i2c",
            "ownershipState" to "OWNED",
            "ownerUid" to ownerUid,
            "ownershipEpoch" to ownershipEpoch,
            "resetRequired" to false,
            "claimedAt" to now,
            "location" to mapOf(
                "lat" to lat,
                "lon" to lon,
                "timezone" to "Asia/Manila"
            )
        )

        // Step 1: Mark CLAIM_PENDING first so RTDB rules permit updating acl/ownerUid
        // even if this deviceId previously had a demo/seed or prior ownerUid in /acl.
        deviceRef.child("meta").child("ownershipState").setValue("CLAIM_PENDING").await()

        val aclRef = deviceRef.child("acl")
        aclRef.child("ownerUid").setValue(ownerUid).await()

        // Step 2: Commit full OWNED metadata now that acl/ownerUid belongs to this owner.
        deviceRef.child("meta").updateChildren(metaMap).await()

        aclRef.child("members").child(ownerUid).setValue(
            mapOf(
                "role" to DeviceRole.OWNER.slug,
                "email" to ownerEmail,
                "displayName" to ownerName.ifBlank { ownerEmail.substringBefore("@") },
                "addedAt" to now,
                "addedBy" to "physical_claim"
            )
        ).await()

        if (!existingConfig.exists()) {
            deviceRef.child("config").setValue(defaultConfigMap()).await()
        }

        rootRef.child("users").child(ownerUid).child("devices").child(deviceId).setValue(
            mapOf(
                "role" to DeviceRole.OWNER.slug,
                "ownershipEpoch" to ownershipEpoch,
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

    suspend fun setDeviceMode(deviceId: String, mode: String): Result<Unit> = runCatching {
        val normalized = if (mode.equals("INDOOR", ignoreCase = true)) "INDOOR" else "OUTDOOR"
        rootRef.child("devices").child(deviceId).child("config").child("mode")
            .setValue(normalized)
            .await()
    }

    suspend fun sendCommand(
        deviceId: String,
        type: String,
        requestedByUid: String,
        ownershipEpoch: Long,
        cropSlug: String? = null,
        durationMs: Long? = null
    ): Result<String> = runCatching {
        val now = System.currentTimeMillis()
        val cmdId = "cmd_${now}_${(100..999).random()}"
        val payload = mutableMapOf<String, Any>(
            "type" to type,
            "state" to "PENDING",
            "requestedByUid" to requestedByUid,
            "ownershipEpoch" to ownershipEpoch,
            "createdAt" to now,
            "expiresAt" to (now + IotDomainLogic.COMMAND_TTL_MS)
        )
        if (!cropSlug.isNullOrBlank()) {
            payload["crop"] = cropSlug
        }
        if (durationMs != null) {
            payload["durationMs"] = IotDomainLogic.clampPulseDurationMs(durationMs)
        }

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
        rootRef.child("devices").child(deviceId).child("acl").child("invitations").child(code)
            .setValue(inviteMap)
            .await()

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

        val targetDeviceId: String
        val inviteSnap: DataSnapshot

        if (explicitDeviceId != null) {
            targetDeviceId = explicitDeviceId
            inviteSnap = rootRef.child("devices").child(targetDeviceId)
                .child("acl").child("invitations").child(inviteCode).get().await()
        } else {
            // Search devices for matching pending invite code
            val devicesSnap = rootRef.child("devices").get().await()
            var foundDevId: String? = null
            var foundInvite: DataSnapshot? = null
            for (devChild in devicesSnap.children) {
                val cand = devChild.child("acl").child("invitations").child(inviteCode)
                if (cand.exists()) {
                    foundDevId = devChild.key
                    foundInvite = cand
                    break
                }
            }
            targetDeviceId = foundDevId ?: error("Invite code not found. Double-check the code.")
            inviteSnap = foundInvite ?: error("Invite code not found.")
        }

        if (!inviteSnap.exists()) {
            error("Invite code not found.")
        }

        val status = inviteSnap.child("status").getValue(String::class.java) ?: ""
        val expiresAt = inviteSnap.child("expiresAt").asLong(0L)
        val role = DeviceRole.fromSlug(inviteSnap.child("role").getValue(String::class.java))
        val createdBy = inviteSnap.child("createdByUid").getValue(String::class.java) ?: ""
        val now = System.currentTimeMillis()

        if (status != "PENDING") {
            error("This invite code has already been used.")
        }
        if (expiresAt > 0L && now > expiresAt) {
            error("This invite code has expired. Ask the owner for a new code.")
        }

        val metaSnap = rootRef.child("devices").child(targetDeviceId).child("meta").get().await()
        if (metaSnap.child("ownershipState").getValue(String::class.java) != "OWNED") {
            error("This plant box is not currently active.")
        }
        val currentEpoch = metaSnap.child("ownershipEpoch").asLong(1L)

        // 1. Mark invite accepted
        val invRef = rootRef.child("devices").child(targetDeviceId)
            .child("acl").child("invitations").child(inviteCode)
        invRef.updateChildren(
            mapOf(
                "status" to "ACCEPTED",
                "acceptedByUid" to uid,
                "acceptedAt" to now
            )
        ).await()

        // 2. Add member entry to device ACL
        rootRef.child("devices").child(targetDeviceId).child("acl").child("members").child(uid)
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
        rootRef.child("users").child(uid).child("devices").child(targetDeviceId)
            .setValue(
                mapOf(
                    "role" to role.slug,
                    "ownershipEpoch" to currentEpoch,
                    "addedAt" to now
                )
            ).await()

        targetDeviceId
    }

    suspend fun revokeMember(deviceId: String, targetUid: String): Result<Unit> = runCatching {
        rootRef.child("devices").child(deviceId).child("acl").child("members").child(targetUid)
            .removeValue().await()
        rootRef.child("users").child(targetUid).child("devices").child(deviceId)
            .removeValue().await()
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
        val devRef = rootRef.child("devices").child(deviceId)

        // 1. Transition meta to RESET_PENDING so ESP32 executes durable NVS wipe & stops loads
        devRef.child("meta").updateChildren(
            mapOf(
                "ownershipState" to "RESET_PENDING",
                "ownerUid" to null,
                "ownershipEpoch" to nextEpoch,
                "resetRequired" to true,
                "displayName" to "SmartPlantCare Box",
                "unclaimedAt" to System.currentTimeMillis()
            )
        ).await()

        // 2. Remove device from all members + owner
        val allUids = (memberUids + ownerUid).distinct().filter { it.isNotBlank() }
        for (uid in allUids) {
            runCatching {
                rootRef.child("users").child(uid).child("devices").child(deviceId)
                    .removeValue().await()
            }
        }

        // 3. Clean ACL (owner has write permission via ownerUid before removal)
        runCatching { devRef.child("acl").child("invitations").removeValue().await() }
        for (uid in allUids) {
            runCatching { devRef.child("acl").child("members").child(uid).removeValue().await() }
        }
        runCatching { devRef.child("acl").child("ownerUid").removeValue().await() }
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
        "physicalLoadsEnabled" to false,
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
            lon = m.child("location").child("lon").asDouble(120.9842)
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
            rainValid = t.child("rainValid").asBool(true),
            reservoirAirGapInches = t.child("reservoirAirGapInches").asFloatOrNull(),
            reservoirPct = t.child("reservoirPct").asFloatOrNull(),
            reservoirValid = t.child("reservoirValid").asBool(false),
            reservoirState = t.child("reservoirState").asStr("UNKNOWN"),
            tomato = parseSoilZone(soilNode.child("tomato"), "tomato", "Tomato", "GPIO34", "Valve 1 (IN1)"),
            chili = parseSoilZone(soilNode.child("chili"), "chili", "Chili", "GPIO35", "Valve 2 (IN2)"),
            eggplant = parseSoilZone(soilNode.child("eggplant"), "eggplant", "Eggplant", "GPIO33", "Valve 3 (IN3)")
        )

        val w = root.child("weather")
        val weather = DeviceWeather(
            provider = w.child("provider").asStr("open-meteo"),
            valid = w.child("valid").asBool(false),
            rainProbabilityPct = w.child("rainProbabilityPct").asInt(-1),
            windowHours = w.child("windowHours").asInt(3),
            windowLabel = w.child("windowLabel").asStr("NEXT 3 H"),
            fetchedAt = w.child("fetchedAt").asLong(0L),
            validUntil = w.child("validUntil").asLong(0L)
        )

        val c = root.child("config")
        val cropsNode = c.child("crops")
        val config = DeviceConfig(
            mode = c.child("mode").asStr(status.mode),
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
            if (st != "PENDING" || (exp > 0L && now > exp)) return@mapNotNull null
            ShareInvite(
                inviteCode = code,
                deviceId = deviceId,
                role = DeviceRole.fromSlug(iChild.child("role").asStr("viewer")),
                status = st,
                createdAt = iChild.child("createdAt").asLong(0L),
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
            members = members,
            activeInvites = invites,
            events = events,
            commandResults = cmdResults
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
}
