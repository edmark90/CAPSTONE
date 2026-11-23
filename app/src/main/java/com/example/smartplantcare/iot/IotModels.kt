package com.example.smartplantcare.iot

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

// ─── 7-Day Historical Monitoring & Irrigation Records Models ─────────────────

@Immutable
data class HistoricalHourSegment(
    val segmentId: String,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val sampleCount: Int = 0,
    val tomatoPct: Float? = null,
    val chiliPct: Float? = null,
    val eggplantPct: Float? = null,
    val temperatureC: Float? = null,
    val humidityPct: Float? = null,
    val lightPct: Float? = null,
    val reservoirPct: Float? = null,
    val reservoirGapIn: Float? = null,
    val rainProbPct: Int? = null
)

@Immutable
data class HistoricalHourRecord(
    val hour: Int,
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val sampleCount: Int = 0,
    val gapCount: Int = 0,
    val tomatoPct: Float? = null,
    val chiliPct: Float? = null,
    val eggplantPct: Float? = null,
    val temperatureC: Float? = null,
    val humidityPct: Float? = null,
    val lightPct: Float? = null,
    val reservoirPct: Float? = null,
    val reservoirGapIn: Float? = null,
    val rainProbPct: Int? = null,
    val rainWetSamples: Int = 0,
    val segments: List<HistoricalHourSegment> = emptyList()
)

@Immutable
data class IrrigationHistoryItem(
    val irrigationId: String,
    val crop: String,
    val trigger: String,         // "AUTO" | "MANUAL"
    val status: String,          // "COMPLETED" | "ABORTED"
    val reason: String = "",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val hour: Int = 0,
    val durationMs: Long = 0L,
    val startMoisturePct: Float? = null,
    val endMoisturePct: Float? = null,
    val recordedOffline: Boolean = false
) {
    val durationSec: Int get() = ((durationMs + 500L) / 1000L).toInt().coerceAtLeast(1)
    val isManual: Boolean get() = trigger.equals("MANUAL", ignoreCase = true)
    val isAborted: Boolean get() = status.equals("ABORTED", ignoreCase = true)
    val cropDisplayName: String
        get() = when (crop.lowercase(Locale.US)) {
            "tomato" -> "Tomato"
            "chili" -> "Chili"
            "eggplant" -> "Eggplant"
            else -> crop.replaceFirstChar { it.uppercase(Locale.US) }
        }
}

@Immutable
data class DailyHistorySnapshot(
    val deviceId: String,
    val dateKey: String, // YYYY-MM-DD in Asia/Manila (PHT)
    val updatedAt: Long = 0L,
    val hours: Map<Int, HistoricalHourRecord> = emptyMap(),
    val irrigations: List<IrrigationHistoryItem> = emptyList()
) {
    val hasTelemetry: Boolean get() = hours.isNotEmpty()
    val hasIrrigation: Boolean get() = irrigations.isNotEmpty()
    val hasAnyRecord: Boolean get() = hasTelemetry || hasIrrigation
}

@Immutable
data class HistoryDateOption(
    val dateKey: String,         // "2026-11-22"
    val dayOfWeekShort: String,  // "Sun"
    val dayOfMonth: String,      // "22"
    val monthShort: String,      // "Nov"
    val fullDateLabel: String,   // "Sun, Nov 22, 2026"
    val isToday: Boolean,
    val hasTelemetry: Boolean,
    val hasIrrigation: Boolean
)

@Immutable
data class ChartDataPoint(
    val xHour: Float,    // 0.0f .. 23.98f (12:00 AM .. 11:59 PM PHT)
    val value: Float,
    val epochMs: Long,
    val hour: Int
)

@Immutable
data class OfflineGapWindow(
    val startXHour: Float,
    val endXHour: Float
)

@Immutable
data class SegmentedChartSeries(
    val segments: List<List<ChartDataPoint>>,
    val gaps: List<OfflineGapWindow>
) {
    val totalPoints: Int get() = segments.sumOf { it.size }
    val isEmpty: Boolean get() = segments.isEmpty()
}

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

    // ─── 7-Day Historical Monitoring (Philippine Local Time — Asia/Manila) ───

    const val PHILIPPINES_TZ_ID = "Asia/Manila"
    const val HISTORY_RETENTION_DAYS = 7
    const val MAX_CONTINUITY_GAP_MS = 15L * 60_000L     // >15m gap between adjacent hours breaks line
    const val INTRA_HOUR_GAP_MS = 5L * 60_000L          // >=5m outage within same hour breaks line

    private val phTimeZone: TimeZone = TimeZone.getTimeZone(PHILIPPINES_TZ_ID)

    fun formatPhDateKey(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = phTimeZone }
        return sdf.format(Date(epochMs))
    }

    fun formatPhTimeLabel(epochMs: Long): String {
        if (epochMs < 1_700_000_000_000L) return "--"
        val sdf = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = phTimeZone }
        return sdf.format(Date(epochMs))
    }

    fun phHourOfDay(epochMs: Long): Int {
        val cal = Calendar.getInstance(phTimeZone, Locale.US)
        cal.timeInMillis = epochMs
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    fun phHourFloat(epochMs: Long): Float {
        val cal = Calendar.getInstance(phTimeZone, Locale.US)
        cal.timeInMillis = epochMs
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return (h + m / 60f).coerceIn(0f, 23.98f)
    }

    fun formatHourRangeLabel(hour: Int): String {
        val h = hour.coerceIn(0, 23)
        val start = formatSingleHour12(h)
        val endMinute = when {
            h == 0 -> "12:59 AM"
            h < 12 -> "$h:59 AM"
            h == 12 -> "12:59 PM"
            else -> "${h - 12}:59 PM"
        }
        return "$start – $endMinute"
    }

    fun formatSingleHour12(hour: Int): String {
        val h = hour.coerceIn(0, 23)
        return when {
            h == 0 -> "12:00 AM"
            h < 12 -> "$h:00 AM"
            h == 12 -> "12:00 PM"
            else -> "${h - 12}:00 PM"
        }
    }

    /**
     * Generates the strict 7-day retention date window ending at Today in Philippine Time (Asia/Manila).
     * Index 0 = 6 days ago ... Index 6 = Today.
     */
    fun generateSevenDayWindowPht(
        nowMs: Long = System.currentTimeMillis(),
        historyByDate: Map<String, DailyHistorySnapshot> = emptyMap()
    ): List<HistoryDateOption> {
        val dateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = phTimeZone }
        val dowFmt = SimpleDateFormat("EEE", Locale.US).apply { timeZone = phTimeZone }
        val domFmt = SimpleDateFormat("dd", Locale.US).apply { timeZone = phTimeZone }
        val monFmt = SimpleDateFormat("MMM", Locale.US).apply { timeZone = phTimeZone }
        val fullFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).apply { timeZone = phTimeZone }

        val cal = Calendar.getInstance(phTimeZone, Locale.US).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(HISTORY_RETENTION_DAYS - 1))
        }

        return List(HISTORY_RETENTION_DAYS) { idx ->
            val d = cal.time
            val key = dateKeyFmt.format(d)
            val snap = historyByDate[key]
            val option = HistoryDateOption(
                dateKey = key,
                dayOfWeekShort = if (idx == HISTORY_RETENTION_DAYS - 1) "Today" else dowFmt.format(d),
                dayOfMonth = domFmt.format(d),
                monthShort = monFmt.format(d),
                fullDateLabel = fullFmt.format(d),
                isToday = idx == HISTORY_RETENTION_DAYS - 1,
                hasTelemetry = snap?.hasTelemetry == true,
                hasIrrigation = snap?.hasIrrigation == true
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
            option
        }
    }

    fun sevenDayCutoffDateKeyPht(nowMs: Long = System.currentTimeMillis()): String {
        val window = generateSevenDayWindowPht(nowMs = nowMs)
        return window.first().dateKey
    }

    /**
     * Builds strictly discontinuous hourly line segments across 12:00 AM (0) .. 11:59 PM (23.98) PHT.
     *
     * Guarantees:
     * 1. Missing hours break the line cleanly (NEVER bridged, NEVER converted to 0).
     * 2. Invalid/null sensor readings break that sensor's line without inventing data.
     * 3. Legitimate 0.0f values (non-null) are plotted normally.
     * 4. Boundary outages between consecutive hours (firstSeenAt[H] - lastSeenAt[H-1] > 15 min)
     *    break the line instead of hiding the disconnection.
     * 5. Intra-hour disconnections (segments.size > 1 with >= 5 min gap) plot each sub-segment
     *    at its true PHT fractional hour with a visible gap between them.
     */
    fun buildSegmentedHourlySeries(
        day: DailyHistorySnapshot?,
        isToday: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
        selector: (HistoricalHourRecord, HistoricalHourSegment?) -> Float?
    ): SegmentedChartSeries {
        if (day == null || day.hours.isEmpty()) {
            return SegmentedChartSeries(emptyList(), emptyList())
        }

        val segments = mutableListOf<List<ChartDataPoint>>()
        val gaps = mutableListOf<OfflineGapWindow>()
        var current = mutableListOf<ChartDataPoint>()

        var lastValidPointX = -1f
        var lastValidEndAtMs = 0L

        fun flushSegment() {
            if (current.isNotEmpty()) {
                segments.add(current.toList())
                current = mutableListOf()
            }
        }

        for (hour in 0..23) {
            val rec = day.hours[hour]
            if (rec == null || rec.sampleCount <= 0) {
                flushSegment()
                continue
            }

            val orderedSegs = rec.segments.sortedBy { it.startAt }
            if (orderedSegs.size > 1) {
                for (seg in orderedSegs) {
                    val value = selector(rec, seg)
                    val midEpoch = if (seg.startAt > 0L && seg.endAt >= seg.startAt) {
                        seg.startAt + (seg.endAt - seg.startAt) / 2L
                    } else {
                        seg.endAt.takeIf { it > 0L } ?: rec.lastSeenAt
                    }
                    val xHour = if (midEpoch >= 1_700_000_000_000L) {
                        phHourFloat(midEpoch).coerceIn(hour.toFloat(), hour + 0.96f)
                    } else {
                        hour.toFloat()
                    }

                    val hasOutageBefore = lastValidEndAtMs >= 1_700_000_000_000L &&
                        seg.startAt >= 1_700_000_000_000L &&
                        seg.startAt > lastValidEndAtMs &&
                        (seg.startAt - lastValidEndAtMs) >= INTRA_HOUR_GAP_MS

                    if (hasOutageBefore) {
                        flushSegment()
                        if (lastValidPointX >= 0f && xHour > lastValidPointX) {
                            gaps.add(OfflineGapWindow(lastValidPointX, xHour))
                        }
                    }

                    if (value == null || !value.isFinite()) {
                        flushSegment()
                        continue
                    }

                    current.add(
                        ChartDataPoint(
                            xHour = xHour,
                            value = value,
                            epochMs = seg.endAt.takeIf { it > 0L } ?: rec.lastSeenAt,
                            hour = hour
                        )
                    )
                    lastValidPointX = xHour
                    lastValidEndAtMs = seg.endAt.takeIf { it > 0L } ?: rec.lastSeenAt
                }
            } else {
                val singleSeg = orderedSegs.firstOrNull()
                val value = selector(rec, singleSeg)
                val xHour = hour.toFloat()

                val firstTs = rec.firstSeenAt.takeIf { it >= 1_700_000_000_000L }
                    ?: singleSeg?.startAt?.takeIf { it >= 1_700_000_000_000L } ?: 0L
                val lastTs = rec.lastSeenAt.takeIf { it >= 1_700_000_000_000L }
                    ?: singleSeg?.endAt?.takeIf { it >= 1_700_000_000_000L } ?: 0L

                val hasBoundaryOutage = lastValidEndAtMs >= 1_700_000_000_000L &&
                    firstTs >= 1_700_000_000_000L &&
                    firstTs > lastValidEndAtMs &&
                    (firstTs - lastValidEndAtMs) > MAX_CONTINUITY_GAP_MS

                val skippedFullHours = lastValidPointX >= 0f && (xHour - lastValidPointX) > 1.05f

                if (hasBoundaryOutage || skippedFullHours) {
                    flushSegment()
                    if (lastValidPointX >= 0f && xHour > lastValidPointX) {
                        gaps.add(OfflineGapWindow(lastValidPointX, xHour))
                    }
                }

                if (value == null || !value.isFinite()) {
                    flushSegment()
                    continue
                }

                current.add(
                    ChartDataPoint(
                        xHour = xHour,
                        value = value,
                        epochMs = lastTs,
                        hour = hour
                    )
                )
                lastValidPointX = xHour
                lastValidEndAtMs = lastTs
            }
        }
        flushSegment()

        // On Today, if ESP32 stopped reporting for >15 min before the current PHT hour, mark trailing offline band
        if (isToday && lastValidPointX >= 0f && lastValidEndAtMs >= 1_700_000_000_000L) {
            val currentX = phHourFloat(nowMs)
            if ((nowMs - lastValidEndAtMs) > MAX_CONTINUITY_GAP_MS && currentX > lastValidPointX + 0.15f) {
                gaps.add(OfflineGapWindow(lastValidPointX, currentX))
            }
        }

        return SegmentedChartSeries(segments = segments, gaps = gaps)
    }

    /**
     * Averages only valid, finite crop soil moisture readings across Tomato, Chili, and Eggplant.
     * Returns null when no crop sensor has a valid reading (never substitutes 0%).
     */
    fun averageValidSoilMoisture(
        tomatoPct: Float?,
        chiliPct: Float?,
        eggplantPct: Float?
    ): Float? {
        var sum = 0f
        var count = 0
        if (tomatoPct != null && tomatoPct.isFinite()) {
            sum += tomatoPct
            count++
        }
        if (chiliPct != null && chiliPct.isFinite()) {
            sum += chiliPct
            count++
        }
        if (eggplantPct != null && eggplantPct.isFinite()) {
            sum += eggplantPct
            count++
        }
        return if (count > 0) sum / count else null
    }

    /**
     * Appends the ESP32's real live reading onto Today's segmented series when the device is online
     * and the reading is valid, while strictly respecting ESP32 offline gaps:
     * - Never adds anything when offline, invalid, or viewing a past date.
     * - If >15 min elapsed since the last recorded point, starts a new disconnected segment and
     *   records the offline gap instead of bridging across the outage.
     */
    fun appendLivePointIfFresh(
        base: SegmentedChartSeries,
        liveValue: Float?,
        liveEpochMs: Long,
        isToday: Boolean,
        isDeviceOnline: Boolean
    ): SegmentedChartSeries {
        if (!isToday || !isDeviceOnline || liveValue == null || !liveValue.isFinite()) {
            return base
        }
        if (liveEpochMs < 1_700_000_000_000L) return base

        val liveX = phHourFloat(liveEpochMs)
        val liveHour = phHourOfDay(liveEpochMs)
        val livePt = ChartDataPoint(
            xHour = liveX,
            value = liveValue,
            epochMs = liveEpochMs,
            hour = liveHour
        )

        if (base.segments.isEmpty()) {
            return SegmentedChartSeries(
                segments = listOf(listOf(livePt)),
                gaps = base.gaps
            )
        }

        val lastSeg = base.segments.last()
        val lastPt = lastSeg.lastOrNull() ?: return base
        val elapsedMs = if (lastPt.epochMs >= 1_700_000_000_000L) {
            liveEpochMs - lastPt.epochMs
        } else {
            Long.MAX_VALUE
        }

        // Ignore if history already has a newer or matching timestamp
        if (elapsedMs < -30_000L) return base

        val withinContinuity = elapsedMs in 0L..MAX_CONTINUITY_GAP_MS && (liveX - lastPt.xHour) <= 1.05f
        return if (withinContinuity) {
            val updatedLastSeg = if (liveHour == lastPt.hour && liveX - lastPt.xHour < 0.08f) {
                lastSeg.dropLast(1) + livePt.copy(xHour = maxOf(lastPt.xHour, liveX))
            } else if (liveX > lastPt.xHour) {
                lastSeg + livePt
            } else {
                lastSeg.dropLast(1) + livePt.copy(xHour = lastPt.xHour)
            }
            SegmentedChartSeries(
                segments = base.segments.dropLast(1) + listOf(updatedLastSeg),
                gaps = base.gaps
            )
        } else if (liveX > lastPt.xHour + 0.05f) {
            val gapAlreadyPresent = base.gaps.any {
                kotlin.math.abs(it.startXHour - lastPt.xHour) < 0.1f &&
                    kotlin.math.abs(it.endXHour - liveX) < 0.2f
            }
            val nextGaps = if (gapAlreadyPresent) {
                base.gaps
            } else {
                base.gaps + OfflineGapWindow(lastPt.xHour, liveX)
            }
            SegmentedChartSeries(
                segments = base.segments + listOf(listOf(livePt)),
                gaps = nextGaps
            )
        } else {
            base
        }
    }

    /**
     * Merges legacy /devices/{id}/events irrigation alerts (within the 7-day PHT retention window)
     * as fallback records if structured /deviceHistory irrigations don't already cover them.
     */
    fun mergeLegacyIrrigationEventsIntoHistory(
        deviceId: String,
        historyByDate: Map<String, DailyHistorySnapshot>,
        events: List<DeviceEvent>,
        validDateKeys: Set<String>
    ): Map<String, DailyHistorySnapshot> {
        if (events.isEmpty()) return historyByDate

        val irrigationEvents = events.filter { ev ->
            ev.createdAt >= 1_700_000_000_000L &&
                (ev.type.equals("IRRIGATION_COMPLETED", ignoreCase = true) ||
                    ev.type.equals("IRRIGATION_ABORTED", ignoreCase = true))
        }
        if (irrigationEvents.isEmpty()) return historyByDate

        val mutable = historyByDate.toMutableMap()
        for (ev in irrigationEvents) {
            val dateKey = formatPhDateKey(ev.createdAt)
            if (dateKey !in validDateKeys) continue

            val cropSlug = ev.crop?.lowercase(Locale.US)?.takeIf { it.isNotBlank() } ?: continue
            val existingDay = mutable[dateKey] ?: DailyHistorySnapshot(deviceId = deviceId, dateKey = dateKey)

            val alreadyCovered = existingDay.irrigations.any { rec ->
                rec.crop.equals(cropSlug, ignoreCase = true) &&
                    kotlin.math.abs(rec.endedAt - ev.createdAt) <= 30_000L
            }
            if (alreadyCovered) continue

            val isManual = ev.message.contains("manual", ignoreCase = true)
            val isAborted = ev.type.equals("IRRIGATION_ABORTED", ignoreCase = true)
            val fallbackItem = IrrigationHistoryItem(
                irrigationId = "legacy_${ev.eventId}",
                crop = cropSlug,
                trigger = if (isManual) "MANUAL" else "AUTO",
                status = if (isAborted) "ABORTED" else "COMPLETED",
                reason = ev.message.ifBlank { ev.title },
                startedAt = (ev.createdAt - 4_000L).coerceAtLeast(0L),
                endedAt = ev.createdAt,
                hour = phHourOfDay(ev.createdAt),
                durationMs = 4_000L,
                startMoisturePct = null,
                endMoisturePct = null,
                recordedOffline = false
            )
            val mergedList = (existingDay.irrigations + fallbackItem)
                .sortedByDescending { it.startedAt }
            mutable[dateKey] = existingDay.copy(irrigations = mergedList)
        }
        return mutable
    }
}
