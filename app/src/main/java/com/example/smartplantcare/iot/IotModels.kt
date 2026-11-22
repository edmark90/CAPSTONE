package com.example.smartplantcare.iot

import androidx.compose.runtime.Immutable

enum class DeviceRole(val slug: String, val label: String) {
    OWNER("owner", "OWNER"),
    OPERATOR("operator", "OPERATOR"),
    VIEWER("viewer", "VIEWER");

    val canControl: Boolean get() = this == OWNER || this == OPERATOR
    val canManageSharing: Boolean get() = this == OWNER
    val canUnclaim: Boolean get() = this == OWNER

    companion object {
        fun fromSlug(raw: String?): DeviceRole = when (raw?.lowercase()) {
            "owner" -> OWNER
            "operator" -> OPERATOR
            else -> VIEWER
        }
    }
}

@Immutable
data class NearbyEspDevice(
    val ssid: String,
    val deviceId: String,
    val rssi: Int
) {
    val signalBars: Int
        get() = when {
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -78 -> 2
            else -> 1
        }
}

@Immutable
data class EspDeviceInfo(
    val deviceId: String = "spc-unit-0001",
    val displayName: String = "SmartPlantCare Box",
    val setupApSsid: String = "SPC-SETUP-spc-unit-0001",
    val model: String = "SmartPlantCare-3Zone",
    val firmwareVersion: String = "3.1.0-i2c",
    val hardwareRevision: String = "ESP32-WROOM32-V3.1-I2C",
    val ownershipState: String = "UNCLAIMED",
    val ownershipEpoch: Long = 0L
)

@Immutable
data class ScannedWifiNetwork(
    val ssid: String,
    val rssi: Int,
    val secure: Boolean
) {
    val signalBars: Int
        get() = when {
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -78 -> 2
            else -> 1
        }
}

@Immutable
data class ProvisionResult(
    val success: Boolean,
    val deviceId: String = "",
    val displayName: String = "",
    val ownershipEpoch: Long = 1L,
    val errorMessage: String? = null,
    val contactLostAfterAccept: Boolean = false
)

@Immutable
data class UserDeviceLink(
    val deviceId: String,
    val role: DeviceRole,
    val ownershipEpoch: Long = 1L,
    val addedAt: Long = 0L
)

@Immutable
data class DeviceMeta(
    val deviceId: String = "",
    val displayName: String = "SmartPlantCare Box",
    val model: String = "SmartPlantCare-3Zone",
    val hardwareRevision: String = "ESP32-WROOM32-V3.1-I2C",
    val firmwareVersion: String = "3.1.0-i2c",
    val ownershipState: String = "UNCLAIMED",
    val ownerUid: String = "",
    val ownershipEpoch: Long = 0L,
    val resetRequired: Boolean = false,
    val claimedAt: Long = 0L,
    val lat: Double = 14.5995,
    val lon: Double = 120.9842,
    val locationName: String = "",
    val locationSource: String = ""
)

@Immutable
data class DeviceStatus(
    val online: Boolean = false,
    val lastSeenAt: Long = 0L,
    val bootId: String = "",
    val firmwareVersion: String = "3.1.0-i2c",
    val ipAddress: String = "",
    val wifiRssi: Int = -127,
    val mode: String = "OUTDOOR",
    val autoIrrigationEnabled: Boolean = true,
    val rainSuspensionActive: Boolean = false,
    val weatherAdvisoryActive: Boolean = false,
    val weatherUnavailableFault: Boolean = false,
    val emergencyStop: Boolean = false,
    val faultLockout: Boolean = false,
    val activeValve: String = "none",
    val pumpState: String = "OFF",
    val irrigationState: String = "IDLE",
    val activePumpElapsedSec: Int = 0,
    val activeTargetDurationSec: Int = 4,
    val lastAbortReason: String = "NONE",
    val physicalLoadsEnabled: Boolean = false,
    val lcdAddress: Int = 0,
    val freeHeap: Long = 0L,
    val ownershipEpoch: Long = 0L
) {
    val isOutdoor: Boolean get() = mode.equals("OUTDOOR", ignoreCase = true)

    val isActivelyIrrigating: Boolean
        get() = irrigationState in setOf("QUEUED", "OPEN_VALVE", "WATERING", "STOP_PUMP", "CLOSE_VALVE")

    val isInSoakSettle: Boolean
        get() = irrigationState == "SOAK_WAIT"
}

@Immutable
data class SoilZoneTelemetry(
    val slug: String,
    val displayName: String,
    val gpioLabel: String,
    val valveLabel: String,
    val raw: Int = 0,
    val pct: Float? = null,
    val valid: Boolean = false,
    val needsWater: Boolean = false
)

@Immutable
data class DeviceTelemetry(
    val updatedAt: Long = 0L,
    val temperatureC: Float? = null,
    val humidityPct: Float? = null,
    val dhtValid: Boolean = false,
    val tempBand: String = "ERR",
    val humidityBand: String = "ERR",
    val lightRaw: Int = 0,
    val lightBrightnessPct: Float? = null,
    val lightValid: Boolean = false,
    val lightBand: String = "ERR",
    val rainRawWet: Boolean = false,
    val rainWetConfirmed: Boolean = false,
    val rainAccidentalWet: Boolean = false,
    val rainValid: Boolean = true,
    val reservoirAirGapInches: Float? = null,
    val reservoirPct: Float? = null,
    val reservoirValid: Boolean = false,
    val reservoirState: String = "UNKNOWN",
    val tomato: SoilZoneTelemetry = SoilZoneTelemetry("tomato", "Tomato", "Zone 1", "Tomato Zone"),
    val chili: SoilZoneTelemetry = SoilZoneTelemetry("chili", "Chili", "Zone 2", "Chili Zone"),
    val eggplant: SoilZoneTelemetry = SoilZoneTelemetry("eggplant", "Eggplant", "Zone 3", "Eggplant Zone")
) {
    val zones: List<SoilZoneTelemetry> get() = listOf(tomato, chili, eggplant)
}

@Immutable
data class DeviceWeather(
    val provider: String = "open-meteo",
    val valid: Boolean = false,
    val rainProbabilityPct: Int = -1,
    val windowHours: Int = 3,
    val windowLabel: String = "NEXT 3 H",
    val locationName: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val fetchedAt: Long = 0L,
    val validUntil: Long = 0L
)

@Immutable
data class CropZoneConfig(
    val slug: String,
    val enabled: Boolean = true,
    val dryRaw: Int = 3250,
    val wetRaw: Int = 1350,
    val startPct: Float = 35f,
    val stopPct: Float = 55f,
    val pulseMs: Long = 4000L,
    val maxRuntimeMs: Long = 15000L,
    val cooldownMs: Long = 180000L
)

@Immutable
data class DeviceConfig(
    val mode: String = "OUTDOOR",
    val autoIrrigationEnabled: Boolean = true,
    val rainThresholdPct: Int = 60,
    val wetConfirmMs: Long = 30000L,
    val dryRecoverMs: Long = 120000L,
    val reservoirFullGapIn: Float = 1.0f,
    val reservoirEmptyGapIn: Float = 7.0f,
    val reservoirLowPct: Float = 20f,
    val reservoirCriticalPct: Float = 10f,
    val tempHighC: Float = 35f,
    val humLowRh: Float = 40f,
    val lightHighPct: Float = 85f,
    val lightLowPct: Float = 20f,
    val buzzerMuted: Boolean = false,
    val physicalLoadsEnabled: Boolean = true,
    val tomato: CropZoneConfig = CropZoneConfig("tomato", startPct = 35f, stopPct = 55f),
    val chili: CropZoneConfig = CropZoneConfig("chili", startPct = 32f, stopPct = 52f),
    val eggplant: CropZoneConfig = CropZoneConfig("eggplant", startPct = 38f, stopPct = 58f)
) {
    fun cropConfig(slug: String): CropZoneConfig = when (slug) {
        "tomato" -> tomato
        "chili" -> chili
        "eggplant" -> eggplant
        else -> tomato
    }
}

@Immutable
data class AclMember(
    val uid: String,
    val email: String = "",
    val displayName: String = "",
    val role: DeviceRole = DeviceRole.VIEWER,
    val addedAt: Long = 0L
)

@Immutable
data class ShareInvite(
    val inviteCode: String,
    val deviceId: String,
    val role: DeviceRole,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long
)

@Immutable
data class DeviceEvent(
    val eventId: String,
    val type: String,
    val severity: String,
    val title: String,
    val message: String,
    val crop: String? = null,
    val createdAt: Long = 0L
)

@Immutable
data class CommandResult(
    val commandId: String,
    val state: String,
    val reason: String,
    val updatedAt: Long = 0L
)

@Immutable
data class LiveDeviceSnapshot(
    val deviceId: String,
    val meta: DeviceMeta = DeviceMeta(deviceId = deviceId),
    val status: DeviceStatus = DeviceStatus(),
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val weather: DeviceWeather = DeviceWeather(),
    val config: DeviceConfig = DeviceConfig(),
    val members: List<AclMember> = emptyList(),
    val activeInvites: List<ShareInvite> = emptyList(),
    val events: List<DeviceEvent> = emptyList(),
    val commandResults: Map<String, CommandResult> = emptyMap(),
    val receivedAtClientMs: Long = 0L
)

/**
 * Pure domain helpers for ESP32 SSID filtering, 2.4 GHz Wi-Fi normalization,
 * and client-side mirroring of ESP32 irrigation safety gates (unit-testable).
 */
object IotDomainLogic {
    const val ESP_SETUP_PREFIX = "SPC-SETUP-"
    const val DEFAULT_ESP_POP = "spc-pop-0001"
    const val DEFAULT_CLAIM_CODE = "SPC-CLAIM-0001"
    const val MIN_PULSE_MS = 1000L
    const val MAX_PULSE_MS = 15000L
    const val COMMAND_TTL_MS = 60000L
    const val EVENT_MAX_PUSH_AGE_MS = 120_000L
    const val SENSOR_FAULT_COOLDOWN_MS = 3_600_000L
    const val ADVISORY_EVENT_COOLDOWN_MS = 900_000L
    const val CRITICAL_EVENT_COOLDOWN_MS = 300_000L
    const val RAIN_WET_EVENT_COOLDOWN_MS = 60_000L

    private val SILENT_HISTORY_ONLY_TYPES = setOf("DEVICE_READY")

    private val SENSOR_FAULT_EVENT_TYPES = setOf(
        "SOIL_SENSOR_FAULT",
        "DHT_SENSOR_FAULT",
        "LIGHT_SENSOR_FAULT",
        "RESERVOIR_SENSOR_FAULT",
        "RAIN_SENSOR_FAULT",
        "WEATHER_UNAVAILABLE"
    )

    private val CRITICAL_SAFETY_EVENT_TYPES = setOf(
        "RAIN_SUSPENSION_STARTED",
        "RESERVOIR_CRITICAL",
        "RESERVOIR_EMPTY",
        "EMERGENCY_STOP",
        "POWER_OR_RELAY_FAULT",
        "IRRIGATION_ABORTED"
    )

    /**
     * Strict policy: ONLY the plant box OWNER may be prompted for mobile GPS permission
     * and set the box's physical installation coordinates. Shared Operators and Viewers
     * NEVER get prompted for location and always share the Owner's saved box location.
     */
    fun canRequestDeviceLocationPermission(role: DeviceRole): Boolean = role == DeviceRole.OWNER

    fun notificationCooldownMsForEventType(eventType: String): Long {
        val norm = eventType.trim().uppercase()
        return when {
            norm == "RAIN_SENSOR_WET" -> RAIN_WET_EVENT_COOLDOWN_MS
            norm in SENSOR_FAULT_EVENT_TYPES -> SENSOR_FAULT_COOLDOWN_MS
            norm in CRITICAL_SAFETY_EVENT_TYPES -> CRITICAL_EVENT_COOLDOWN_MS
            else -> ADVISORY_EVENT_COOLDOWN_MS
        }
    }

    fun eventCooldownKey(deviceId: String, eventType: String, crop: String?): String {
        val normType = eventType.trim().uppercase()
        val normCrop = crop?.trim()?.lowercase().orEmpty()
        return if (normCrop.isNotEmpty()) "${deviceId}_${normType}_${normCrop}" else "${deviceId}_${normType}"
    }

    fun stableNotificationId(deviceId: String, eventType: String, crop: String?): Int {
        val key = eventCooldownKey(deviceId, eventType, crop)
        return (key.hashCode() and 0x7FFFFFFF).coerceAtLeast(10_000)
    }

    fun shouldDispatchDeviceEventNotification(
        event: DeviceEvent,
        nowMs: Long,
        isAlreadySeen: Boolean,
        lastNotifiedSameTypeMs: Long?
    ): Boolean {
        if (event.eventId.isBlank() || isAlreadySeen) return false
        val normType = event.type.trim().uppercase()
        if (normType in SILENT_HISTORY_ONLY_TYPES) return false

        // Drop historical / stale events (older than 2 minutes or missing valid epoch timestamp)
        if (event.createdAt < 1_700_000_000_000L) return false
        val ageMs = nowMs - event.createdAt
        if (ageMs > EVENT_MAX_PUSH_AGE_MS || ageMs < -300_000L) return false

        // Enforce per-(deviceId, eventType, crop) cooldown so disconnected sensors or reboots NEVER loop
        if (lastNotifiedSameTypeMs != null && lastNotifiedSameTypeMs > 0L) {
            val cooldownMs = notificationCooldownMsForEventType(normType)
            if ((nowMs - lastNotifiedSameTypeMs) < cooldownMs) {
                return false
            }
        }
        return true
    }

    fun parseNearbyEspDevices(rawScan: List<Pair<String, Int>>): List<NearbyEspDevice> {
        return rawScan
            .map { (ssid, rssi) -> ssid.trim().removeSurrounding("\"") to rssi }
            .filter { (ssid, _) -> ssid.startsWith(ESP_SETUP_PREFIX) && ssid.length > ESP_SETUP_PREFIX.length }
            .groupBy { it.first }
            .map { (ssid, entries) ->
                val strongestRssi = entries.maxOf { it.second }
                NearbyEspDevice(
                    ssid = ssid,
                    deviceId = ssid.removePrefix(ESP_SETUP_PREFIX),
                    rssi = strongestRssi
                )
            }
            .sortedByDescending { it.rssi }
    }

    fun sanitizeWifiSsid(rawSsid: String): String =
        rawSsid.replace("\r", "").replace("\n", "").trim().removeSurrounding("\"")

    fun sanitizeWifiPassword(rawPassword: String): String =
        rawPassword.replace("\r", "").replace("\n", "")

    fun normalizeScannedWifi(networks: List<ScannedWifiNetwork>): List<ScannedWifiNetwork> {
        return networks
            .map { it.copy(ssid = sanitizeWifiSsid(it.ssid)) }
            .filter { it.ssid.isNotEmpty() && !it.ssid.startsWith(ESP_SETUP_PREFIX) }
            .groupBy { it.ssid }
            .map { (_, group) -> group.maxBy { it.rssi } }
            .sortedByDescending { it.rssi }
    }

    fun clampPulseDurationMs(requestedMs: Long): Long = requestedMs.coerceIn(MIN_PULSE_MS, MAX_PULSE_MS)

    /**
     * Evaluates whether the ESP32 heartbeat is fresh.
     * Uses the most recent of [status.lastSeenAt] and [telemetryUpdatedAt], and handles both
     * real Unix epoch timestamps (>= 1.7e12 ms) and pre-NTP boot uptime values (< 1.7e12 ms)
     * so an ESP32 that just rebooted before NTP sync is NEVER falsely marked Offline.
     */
    fun isDeviceOnlineFresh(
        snapshot: LiveDeviceSnapshot?,
        nowMs: Long = System.currentTimeMillis(),
        staleThresholdMs: Long = 45_000L
    ): Boolean {
        if (snapshot == null) return false
        return isDeviceOnlineFresh(
            status = snapshot.status,
            nowMs = nowMs,
            receivedAtClientMs = snapshot.receivedAtClientMs,
            telemetryUpdatedAt = snapshot.telemetry.updatedAt,
            staleThresholdMs = staleThresholdMs
        )
    }

    fun isDeviceOnlineFresh(
        status: DeviceStatus,
        nowMs: Long,
        receivedAtClientMs: Long = 0L,
        telemetryUpdatedAt: Long = 0L,
        staleThresholdMs: Long = 45_000L
    ): Boolean {
        if (!status.online) return false
        val lastSeen = maxOf(status.lastSeenAt, telemetryUpdatedAt)
        return when {
            lastSeen <= 0L -> receivedAtClientMs == 0L || (nowMs - receivedAtClientMs) < staleThresholdMs
            lastSeen >= 1_700_000_000_000L -> (nowMs - lastSeen) < staleThresholdMs
            else -> receivedAtClientMs == 0L || (nowMs - receivedAtClientMs) < staleThresholdMs
        }
    }

    /**
     * Returns null when manual watering is allowed for [cropSlug], or a concise reason.
     * Manual button commands bypass automatic sensor/rain/reservoir/crop-threshold logic
     * and only enforce role permission, Emergency Stop, and 1-valve-at-a-time electrical safety.
     */
    fun manualWaterLockReason(
        role: DeviceRole,
        snapshot: LiveDeviceSnapshot,
        cropSlug: String
    ): String? {
        if (!role.canControl) {
            return "Read-only Viewer account"
        }
        val status = snapshot.status

        if (status.emergencyStop || status.faultLockout) {
            return "Emergency Stop / Fault Lockout active"
        }
        if (status.isActivelyIrrigating && status.activeValve != "none" && status.activeValve != cropSlug) {
            return "1 valve at a time (${status.activeValve.uppercase()} active)"
        }
        if (status.isActivelyIrrigating && status.activeValve == cropSlug) {
            return "Already watering this zone"
        }
        return null
    }
}
