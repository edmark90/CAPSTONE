package com.example.smartplantcare.iot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import com.example.smartplantcare.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MyPlantsStep {
    SCAN_ESP32,
    WIFI_AND_CLAIM,
    LIVE_DASHBOARD
}

enum class MyPlantsSubPage {
    NONE,
    SHARE_PLANT,
    EVENT_LOG,
    DEVICE_SETTINGS
}

data class MyPlantsUiState(
    val step: MyPlantsStep = MyPlantsStep.SCAN_ESP32,
    val subPage: MyPlantsSubPage = MyPlantsSubPage.NONE,
    val isLoadingInitialDevices: Boolean = true,

    // Step 1: Nearby ESP32 Scan
    val isScanningEsp: Boolean = false,
    val scanElapsedSeconds: Int = 0,
    val scanTimedOutNoDevice: Boolean = false,
    val nearbyEspDevices: List<NearbyEspDevice> = emptyList(),
    val isConnectingToEsp: Boolean = false,
    val scanStatusMessage: String? = null,
    val scanBlocker: EspScanBlocker = EspScanBlocker.NONE,

    // Step 2: 2.4 GHz Wi-Fi Picker, Password, Custom Box Name & Claim
    val connectedEspInfo: EspDeviceInfo? = null,
    val isScanningWifi: Boolean = false,
    val scannedWifiNetworks: List<ScannedWifiNetwork> = emptyList(),
    val selectedWifiSsid: String = "",
    val manualWifiMode: Boolean = false,
    val wifiPassword: String = "",
    val customBoxName: String = "SmartPlantCare Box",
    val claimCode: String = IotDomainLogic.DEFAULT_CLAIM_CODE,
    val isProvisioning: Boolean = false,
    val provisioningProgressMessage: String? = null,
    val provisioningError: String? = null,

    // Step 3: Live Dashboard
    val userDevices: List<UserDeviceLink> = emptyList(),
    val activeDeviceId: String? = null,
    val activeRole: DeviceRole = DeviceRole.OWNER,
    val liveSnapshot: LiveDeviceSnapshot? = null,
    val selectedPulseSecByCrop: Map<String, Int> = mapOf(
        "tomato" to 4,
        "chili" to 4,
        "eggplant" to 4
    ),
    val pendingCommandId: String? = null,
    val pendingWaterCrop: String? = null,
    val isSubmittingAction: Boolean = false,
    val isRefreshingWeather: Boolean = false,
    val isSyncingDevice: Boolean = false,
    val shouldPromptOwnerLocation: Boolean = false,

    // Page 4: Share My Plant
    val latestGeneratedInvite: ShareInvite? = null,

    // Feedback
    val snackbarMessage: String? = null
)

class MyPlantsViewModel(
    appContext: Context,
    private val repository: IotFirebaseRepository = IotFirebaseRepository(),
    private val provClient: Esp32ProvisioningClient = Esp32ProvisioningClient(appContext.applicationContext),
    private val weatherClient: WifiWeatherLocationClient = WifiWeatherLocationClient()
) : ViewModel() {

    private val appCtx: Context = appContext.applicationContext
    private val notifHistoryRepo: NotificationHistoryRepository by lazy {
        NotificationHistoryRepository(appCtx)
    }
    private val seenEventsPrefs by lazy {
        appCtx.getSharedPreferences("spc_iot_seen_events", Context.MODE_PRIVATE)
    }
    private val notifLock = Any()
    private val seededDevices = mutableSetOf<String>()
    private val seenEventIdsByDevice = mutableMapOf<String, LinkedHashSet<String>>()
    private val lastNotifiedAtByKey = mutableMapOf<String, Long>()
    private val ownerLocationPromptedDevices = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(MyPlantsUiState())
    val uiState: StateFlow<MyPlantsUiState> = _uiState.asStateFlow()

    private var boundUid: String? = null
    private var boundEmail: String = ""
    private var boundName: String = ""
    private var userDevicesJob: Job? = null
    private var liveDeviceJob: Job? = null
    private var liveWatchdogJob: Job? = null
    private var espScanJob: Job? = null
    private var lastReportedCmdId: String? = null
    private var lastAutoWeatherSyncDeviceId: String? = null
    private var lastReconciledEpochKey: String? = null
    private var lastRealtimeDeliveryAtMs: Long = 0L
    private var pendingFirebaseClaimResult: ProvisionResult? = null

    fun bindUser(uid: String?, email: String, displayName: String, isGuest: Boolean) {
        if (isGuest || uid.isNullOrBlank()) {
            userDevicesJob?.cancel()
            liveDeviceJob?.cancel()
            liveWatchdogJob?.cancel()
            boundUid = null
            _uiState.update {
                MyPlantsUiState(
                    step = MyPlantsStep.SCAN_ESP32,
                    isLoadingInitialDevices = false
                )
            }
            return
        }

        boundEmail = email
        boundName = displayName

        if (boundUid == uid && userDevicesJob?.isActive == true) {
            return
        }
        boundUid = uid
        observeUserDevices(uid)
    }

    private fun observeUserDevices(uid: String) {
        userDevicesJob?.cancel()
        userDevicesJob = viewModelScope.launch {
            repository.observeUserDevices(uid).collect { links ->
                val currentActive = _uiState.value.activeDeviceId
                val chosen = links.firstOrNull { it.deviceId == currentActive } ?: links.firstOrNull()

                _uiState.update { st ->
                    val shouldEnterDashboard = chosen != null &&
                        st.step != MyPlantsStep.WIFI_AND_CLAIM &&
                        !st.isProvisioning
                    val shouldReturnToScan = chosen == null &&
                        st.step == MyPlantsStep.LIVE_DASHBOARD
                    st.copy(
                        isLoadingInitialDevices = false,
                        userDevices = links,
                        activeDeviceId = chosen?.deviceId,
                        activeRole = chosen?.role ?: DeviceRole.OWNER,
                        step = when {
                            shouldEnterDashboard -> MyPlantsStep.LIVE_DASHBOARD
                            shouldReturnToScan -> MyPlantsStep.SCAN_ESP32
                            else -> st.step
                        },
                        subPage = if (shouldReturnToScan) MyPlantsSubPage.NONE else st.subPage,
                        liveSnapshot = if (chosen == null) null else st.liveSnapshot
                    )
                }

                if (chosen != null) {
                    observeLiveDevice(chosen.deviceId)
                    startLiveSyncWatchdog(chosen.deviceId)
                } else {
                    liveDeviceJob?.cancel()
                    liveWatchdogJob?.cancel()
                }
            }
        }
    }

    private fun applyLiveSnapshot(deviceId: String, snapshot: LiveDeviceSnapshot?, fromRealtime: Boolean) {
        if (fromRealtime) {
            lastRealtimeDeliveryAtMs = System.currentTimeMillis()
        }
        _uiState.update { st ->
            var nextSnackbar = st.snackbarMessage
            var nextPendingCmd = st.pendingCommandId
            var nextPendingCrop = st.pendingWaterCrop
            val trackedId = st.pendingCommandId
            if (trackedId != null && snapshot != null) {
                val res = snapshot.commandResults[trackedId]
                if (res != null && lastReportedCmdId != "${trackedId}_${res.state}") {
                    lastReportedCmdId = "${trackedId}_${res.state}"
                    nextSnackbar = "${res.state}: ${res.reason}"
                    if (res.state in setOf("DONE", "REJECTED", "ABORTED")) {
                        nextPendingCmd = null
                        nextPendingCrop = null
                    }
                }
            }
            if (snapshot != null && (snapshot.status.isActivelyIrrigating || snapshot.status.isInSoakSettle)) {
                nextPendingCrop = null
            }

            // Keep role synchronized with /acl/members
            val uid = boundUid
            val aclRole = snapshot?.members?.firstOrNull { it.uid == uid }?.role
                ?: st.activeRole

            st.copy(
                liveSnapshot = snapshot,
                activeRole = aclRole,
                pendingCommandId = nextPendingCmd,
                pendingWaterCrop = nextPendingCrop,
                snackbarMessage = nextSnackbar
            )
        }

        // If ESP32 status.ownershipEpoch differs from /meta/ownershipEpoch while OWNED,
        // automatically reconcile /meta/ownershipEpoch using the authenticated Owner session
        // so ESP32 /events writes (401 rule validation) and manual commands immediately align!
        if (snapshot != null &&
            snapshot.meta.ownershipState.equals("OWNED", ignoreCase = true) &&
            snapshot.status.ownershipEpoch > 0L &&
            snapshot.meta.ownershipEpoch != snapshot.status.ownershipEpoch &&
            _uiState.value.activeRole == DeviceRole.OWNER
        ) {
            val key = "${deviceId}_${snapshot.status.ownershipEpoch}"
            if (lastReconciledEpochKey != key) {
                lastReconciledEpochKey = key
                val ownerUid = boundUid.orEmpty()
                viewModelScope.launch {
                    repository.reconcileMetaOwnershipEpoch(
                        deviceId = deviceId,
                        espOwnershipEpoch = snapshot.status.ownershipEpoch,
                        ownerUid = ownerUid
                    )
                }
            }
        }

        // Weather & Box Location sync:
        // - OWNER ONLY: use the Owner's mobile GPS to set accurate box coordinates in /meta/location.
        // - Shared users (OPERATOR / VIEWER): NEVER request mobile GPS permission; always fetch
        //   Open-Meteo using the Owner's already-saved box coordinates.
        if (snapshot != null && lastAutoWeatherSyncDeviceId != deviceId) {
            val effectiveRole = _uiState.value.activeRole
            val isOwner = IotDomainLogic.canRequestDeviceLocationPermission(effectiveRole)
            val hasOwnerGps = snapshot.meta.locationSource.equals("owner_gps", ignoreCase = true)
            val w = snapshot.weather
            val now = System.currentTimeMillis()
            val isWeatherMissingOrStale = !w.valid ||
                w.rainProbabilityPct < 0 ||
                w.locationName.isBlank() ||
                (w.fetchedAt > 1_700_000_000_000L && (now - w.fetchedAt) > 900_000L)

            lastAutoWeatherSyncDeviceId = deviceId

            if (isOwner && !hasOwnerGps) {
                if (weatherClient.hasLocationPermission(appCtx)) {
                    refreshWeatherWithOwnerGps(silent = true)
                } else if (ownerLocationPromptedDevices.add(deviceId)) {
                    _uiState.update { it.copy(shouldPromptOwnerLocation = true) }
                }
            } else if (isWeatherMissingOrStale) {
                refreshWeatherForSavedBoxLocation(silent = true)
            }
        }

        // Dispatch deduplicated Android system notifications & NotificationHistory entries
        // for new ESP32 state-transition events (per V3.1 Specification Section 14.2)
        if (snapshot != null) {
            dispatchNewDeviceEventNotifications(snapshot)
        }
    }

    /**
     * Implements V3.1 Guide Section 14.2 Notification Trigger Table with anti-loop protection:
     * - Synchronized in-memory + ordered persisted seen-ID cache (never randomly evicts recent IDs).
     * - Drops historical/stale events (>2 min old) so syncing or rebooting never replays old faults.
     * - Enforces per-(deviceId, eventType, crop) cooldowns (60 min for sensor faults, 15 min for
     *   advisories, 5 min for critical safety alerts) and stable per-category notification IDs.
     */
    private fun dispatchNewDeviceEventNotifications(snapshot: LiveDeviceSnapshot) {
        val events = snapshot.events
        if (events.isEmpty()) {
            synchronized(notifLock) { seededDevices.add(snapshot.deviceId) }
            return
        }

        val nowMs = System.currentTimeMillis()
        val newAlertsToPost = mutableListOf<DeviceEvent>()

        synchronized(notifLock) {
            val seenKey = "seen_ordered_${snapshot.deviceId}"
            val seenIds = seenEventIdsByDevice.getOrPut(snapshot.deviceId) {
                val raw = seenEventsPrefs.getString(seenKey, "").orEmpty()
                val restored = LinkedHashSet<String>()
                if (raw.isNotBlank()) {
                    raw.split('\n').forEach { id -> if (id.isNotBlank()) restored.add(id) }
                }
                // Migrate any legacy StringSet IDs once
                seenEventsPrefs.getStringSet("seen_ids_${snapshot.deviceId}", emptySet())
                    ?.forEach { id -> if (id.isNotBlank()) restored.add(id) }
                restored
            }

            // Seed historical events on first observation in this session so launch/sync never replays old alerts
            val isFirstSeed = snapshot.deviceId !in seededDevices
            seededDevices.add(snapshot.deviceId)

            var prefsDirty = false
            val cooldownUpdates = mutableMapOf<String, Long>()

            // Process chronologically (oldest -> newest)
            val orderedEvents = events.sortedBy { it.createdAt }
            for (ev in orderedEvents) {
                if (ev.eventId.isBlank()) continue
                val alreadySeen = ev.eventId in seenIds
                if (!alreadySeen) {
                    seenIds.add(ev.eventId)
                    prefsDirty = true
                }

                // On first seed when local cache was empty, never pop notifications for events > 60s old
                if (isFirstSeed && alreadySeen) continue

                val cooldownKey = IotDomainLogic.eventCooldownKey(snapshot.deviceId, ev.type, ev.crop)
                val cooldownPrefKey = "cd_$cooldownKey"
                val lastNotifiedMs = lastNotifiedAtByKey[cooldownKey]
                    ?: seenEventsPrefs.getLong(cooldownPrefKey, 0L).takeIf { it > 0L }?.also {
                        lastNotifiedAtByKey[cooldownKey] = it
                    }

                val shouldNotify = IotDomainLogic.shouldDispatchDeviceEventNotification(
                    event = ev,
                    nowMs = nowMs,
                    isAlreadySeen = alreadySeen,
                    lastNotifiedSameTypeMs = lastNotifiedMs
                )
                if (shouldNotify) {
                    lastNotifiedAtByKey[cooldownKey] = nowMs
                    cooldownUpdates[cooldownPrefKey] = nowMs
                    newAlertsToPost.add(ev)
                }
            }

            if (prefsDirty || cooldownUpdates.isNotEmpty()) {
                // Trim deterministically from the oldest head (LinkedHashSet preserves insertion order!)
                while (seenIds.size > 200) {
                    val oldest = seenIds.firstOrNull() ?: break
                    seenIds.remove(oldest)
                }
                val editor = seenEventsPrefs.edit()
                if (prefsDirty) {
                    editor.putString(seenKey, seenIds.joinToString("\n"))
                }
                cooldownUpdates.forEach { (k, ts) -> editor.putLong(k, ts) }
                editor.apply()
            }
        }

        if (newAlertsToPost.isEmpty()) return

        val boxName = snapshot.meta.displayName.ifBlank { "SmartPlantCare Box" }
        for (ev in newAlertsToPost.takeLast(3)) {
            val isCrit = ev.severity.equals("critical", ignoreCase = true) ||
                ev.type.uppercase() in setOf(
                    "RAIN_SUSPENSION_STARTED",
                    "RESERVOIR_CRITICAL",
                    "RESERVOIR_EMPTY",
                    "RESERVOIR_SENSOR_FAULT",
                    "EMERGENCY_STOP",
                    "POWER_OR_RELAY_FAULT",
                    "IRRIGATION_ABORTED"
                )
            val notifId = IotDomainLogic.stableNotificationId(snapshot.deviceId, ev.type, ev.crop)
            NotificationHelper.showIotDeviceAlert(
                context = appCtx,
                notificationId = notifId,
                title = ev.title,
                message = ev.message,
                boxName = boxName,
                isCritical = isCrit
            )
            viewModelScope.launch(Dispatchers.IO) {
                notifHistoryRepo.saveNotification(
                    NotificationHistory(
                        id = "iot_${ev.eventId}",
                        plantId = snapshot.deviceId,
                        plantName = boxName,
                        plantImageUrl = "",
                        day = 1,
                        title = ev.title,
                        message = ev.message,
                        timestamp = if (ev.createdAt >= 1_700_000_000_000L) ev.createdAt else System.currentTimeMillis(),
                        isRead = false,
                        isCompleted = false
                    )
                )
            }
        }
    }

    private fun isNewerSnapshot(candidate: LiveDeviceSnapshot, current: LiveDeviceSnapshot?): Boolean {
        if (current == null) return true
        return candidate.status.lastSeenAt != current.status.lastSeenAt ||
            candidate.telemetry.updatedAt != current.telemetry.updatedAt ||
            candidate.status.bootId != current.status.bootId ||
            candidate.status.irrigationState != current.status.irrigationState ||
            candidate.status.pumpState != current.status.pumpState ||
            candidate.status.physicalLoadsEnabled != current.status.physicalLoadsEnabled ||
            candidate.status.rainSuspensionActive != current.status.rainSuspensionActive ||
            candidate.telemetry.rainRawWet != current.telemetry.rainRawWet ||
            candidate.telemetry.rainWetConfirmed != current.telemetry.rainWetConfirmed ||
            candidate.telemetry.rainAccidentalWet != current.telemetry.rainAccidentalWet ||
            candidate.config != current.config ||
            candidate.weather.fetchedAt != current.weather.fetchedAt
    }

    /**
     * Background safety-net watchdog: if Firebase SDK's WebSocket silently stalls (e.g. after
     * SoftAP->Wi-Fi switching or ESP32 power-cycling), polls Firebase directly via no-cache HTTPS REST
     * every 6s (when offline/stale) or 10s (when healthy). Whenever newer ESP32 data is found on the
     * server, it updates the Dashboard immediately AND resets the Firebase WebSocket stream!
     */
    private fun startLiveSyncWatchdog(deviceId: String) {
        liveWatchdogJob?.cancel()
        liveWatchdogJob = viewModelScope.launch {
            // Immediate direct REST check 1.2s after entering dashboard so stale cache never sticks
            delay(1200L)
            while (true) {
                val st = _uiState.value
                val currentSnap = st.liveSnapshot
                val now = System.currentTimeMillis()
                val currentStale = currentSnap == null || !IotDomainLogic.isDeviceOnlineFresh(
                    status = currentSnap.status,
                    nowMs = now,
                    receivedAtClientMs = currentSnap.receivedAtClientMs,
                    telemetryUpdatedAt = currentSnap.telemetry.updatedAt
                )
                if (st.step == MyPlantsStep.LIVE_DASHBOARD && st.activeDeviceId == deviceId && !st.isSyncingDevice) {
                    val realtimeSilent = (now - lastRealtimeDeliveryAtMs) >= 10_000L
                    if (realtimeSilent || currentStale) {
                        val restSnap = repository.fetchDeviceSnapshotViaRest(deviceId).getOrNull()
                        if (restSnap != null && isNewerSnapshot(restSnap, _uiState.value.liveSnapshot)) {
                            applyLiveSnapshot(deviceId, restSnap, fromRealtime = false)
                            // WebSocket missed a real server update — reset socket so live streaming recovers!
                            repository.reconnectRealtimeSocket()
                            observeLiveDevice(deviceId, forceRestart = true)
                        }
                    }
                }
                delay(if (currentStale) 6_000L else 10_000L)
            }
        }
    }

    private fun observeLiveDevice(deviceId: String, forceRestart: Boolean = false) {
        if (!forceRestart && liveDeviceJob?.isActive == true && _uiState.value.liveSnapshot?.deviceId == deviceId) {
            return
        }
        liveDeviceJob?.cancel()
        liveDeviceJob = viewModelScope.launch {
            repository.observeDevice(deviceId).collect { snapshot ->
                applyLiveSnapshot(deviceId, snapshot, fromRealtime = true)
            }
        }
    }

    // ─── STEP 1: Nearby ESP32 Scan & Connect (2-Minute Active Radar Scan) ───────

    private fun messageForBlocker(blocker: EspScanBlocker): String = when (blocker) {
        EspScanBlocker.WIFI_DISABLED ->
            "Phone Wi-Fi is turned OFF. Turn on Wi-Fi, then tap Start Scanning."
        EspScanBlocker.LOCATION_PERMISSION_MISSING ->
            "Precise Location permission is required by Android to detect nearby ESP32 Wi-Fi hotspots. Open App Permissions and allow Location (Precise), or connect manually in Wi-Fi Settings."
        EspScanBlocker.LOCATION_SERVICE_DISABLED ->
            "Phone Location (GPS) is turned OFF. Android requires Location Services ON to scan nearby ESP32 Wi-Fi hotspots. Turn on Location, then tap Start Scanning."
        EspScanBlocker.NONE -> ""
    }

    fun reportScanBlocker(blocker: EspScanBlocker, customMessage: String? = null) {
        espScanJob?.cancel()
        espScanJob = null
        _uiState.update {
            it.copy(
                isScanningEsp = false,
                scanElapsedSeconds = 0,
                scanTimedOutNoDevice = false,
                scanBlocker = blocker,
                scanStatusMessage = customMessage ?: messageForBlocker(blocker)
            )
        }
    }

    fun startScanningNearbyEsp() {
        espScanJob?.cancel()

        // Immediately verify Wi-Fi ON, Precise Location permission, and System Location (GPS) ON
        val initialBlocker = provClient.checkScanBlocker()
        if (initialBlocker != EspScanBlocker.NONE) {
            reportScanBlocker(initialBlocker)
            return
        }

        espScanJob = viewModelScope.launch {
            val maxScanDurationMs = 120_000L      // 2 minutes timeout
            val activeScanIntervalMs = 30_000L    // Max 4 active startScan() calls / 2 min (Android throttle limit)
            val cachePollIntervalMs = 3_000L      // Poll passive/OS Wi-Fi scan cache every 3s without wasting quota
            val startedAtMs = System.currentTimeMillis()
            var lastActiveScanAtMs = 0L

            _uiState.update {
                it.copy(
                    isScanningEsp = true,
                    scanElapsedSeconds = 0,
                    scanTimedOutNoDevice = false,
                    nearbyEspDevices = emptyList(),
                    scanStatusMessage = null,
                    scanBlocker = EspScanBlocker.NONE
                )
            }

            // Live 1-second timer coroutine running alongside scan probes
            val timerJob = launch {
                while (true) {
                    val elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt().coerceAtMost(120)
                    _uiState.update { it.copy(scanElapsedSeconds = elapsedSec) }
                    if (elapsedSec >= 120) break
                    delay(1000L)
                }
            }

            try {
                while (System.currentTimeMillis() - startedAtMs < maxScanDurationMs) {
                    val now = System.currentTimeMillis()
                    val shouldTriggerActive = (lastActiveScanAtMs == 0L) ||
                        (now - lastActiveScanAtMs >= activeScanIntervalMs)

                    val snapshot = provClient.scanNearbyEspDevicesDetailed(
                        triggerActiveScan = shouldTriggerActive
                    )
                    if (shouldTriggerActive) {
                        lastActiveScanAtMs = System.currentTimeMillis()
                    }

                    if (snapshot.blocker != EspScanBlocker.NONE) {
                        _uiState.update {
                            it.copy(
                                isScanningEsp = false,
                                scanTimedOutNoDevice = false,
                                scanBlocker = snapshot.blocker,
                                scanStatusMessage = messageForBlocker(snapshot.blocker)
                            )
                        }
                        return@launch
                    }

                    if (snapshot.devices.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                isScanningEsp = false,
                                scanTimedOutNoDevice = false,
                                nearbyEspDevices = snapshot.devices,
                                scanStatusMessage = null,
                                scanBlocker = EspScanBlocker.NONE
                            )
                        }
                        return@launch
                    }

                    val remainingMs = maxScanDurationMs - (System.currentTimeMillis() - startedAtMs)
                    if (remainingMs <= 0L) break
                    delay(minOf(cachePollIntervalMs, remainingMs))
                }

                // 2 minutes elapsed with zero active ESP32 found
                _uiState.update {
                    it.copy(
                        isScanningEsp = false,
                        scanElapsedSeconds = 120,
                        scanTimedOutNoDevice = true,
                        nearbyEspDevices = emptyList(),
                        scanBlocker = EspScanBlocker.NONE,
                        scanStatusMessage = "No active ESP32 nearby"
                    )
                }
            } finally {
                timerJob.cancel()
            }
        }
    }

    fun stopScanningNearbyEsp() {
        espScanJob?.cancel()
        espScanJob = null
        _uiState.update {
            it.copy(
                isScanningEsp = false,
                scanElapsedSeconds = 0,
                scanTimedOutNoDevice = false,
                scanBlocker = EspScanBlocker.NONE
            )
        }
    }

    fun connectToNearbyEsp(device: NearbyEspDevice) {
        espScanJob?.cancel()
        espScanJob = null
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanningEsp = false,
                    isConnectingToEsp = true,
                    scanStatusMessage = "Connecting to ${device.ssid}..."
                )
            }
            val result = provClient.connectToEspHotspot(device.ssid)
            result.onSuccess { info ->
                enterWifiSetupStep(info)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConnectingToEsp = false,
                        scanStatusMessage = err.localizedMessage
                            ?: "Connection failed. Tap Connect or join ${device.ssid} in Wi-Fi Settings."
                    )
                }
            }
        }
    }

    /**
     * Manual fallback if user joined `SPC-SETUP-spc-unit-0001` via Android Wi-Fi Settings directly.
     */
    fun verifyManualHotspotConnection() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConnectingToEsp = true,
                    scanStatusMessage = "Checking connection to ESP32 at 192.168.4.1..."
                )
            }
            val result = provClient.verifyAlreadyConnectedToEsp()
            result.onSuccess { info ->
                enterWifiSetupStep(info)
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isConnectingToEsp = false,
                        scanStatusMessage = "ESP32 not reachable yet. Connect your phone Wi-Fi to SPC-SETUP-spc-unit-0001 (PIN: spc-pop-0001), then tap Check Again."
                    )
                }
            }
        }
    }

    private fun enterWifiSetupStep(info: EspDeviceInfo) {
        pendingFirebaseClaimResult = null
        _uiState.update {
            it.copy(
                isConnectingToEsp = false,
                connectedEspInfo = info,
                customBoxName = info.displayName.ifBlank { "SmartPlantCare Box" },
                provisioningError = null,
                step = MyPlantsStep.WIFI_AND_CLAIM
            )
        }
        refresh24GhzWifiList()
    }

    // ─── STEP 2: 2.4 GHz Wi-Fi Picker, Password, Custom Box Name & Claim ───────

    fun refresh24GhzWifiList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningWifi = true, provisioningError = null) }
            val espScan = provClient.scanEsp24GhzWifi().getOrElse { emptyList() }
            val networks = espScan.ifEmpty { provClient.getLocalPhone24GhzNetworks() }
            _uiState.update {
                it.copy(
                    isScanningWifi = false,
                    scannedWifiNetworks = networks
                )
            }
        }
    }

    fun selectWifiSsid(ssid: String) {
        _uiState.update {
            it.copy(
                selectedWifiSsid = ssid,
                manualWifiMode = false,
                provisioningError = null
            )
        }
    }

    fun toggleManualWifiMode(manual: Boolean) {
        _uiState.update {
            it.copy(
                manualWifiMode = manual,
                provisioningError = null
            )
        }
    }

    fun updateManualWifiSsid(ssid: String) {
        _uiState.update { it.copy(selectedWifiSsid = ssid.replace("\r", "").replace("\n", ""), provisioningError = null) }
    }

    fun updateWifiPassword(password: String) {
        _uiState.update { it.copy(wifiPassword = IotDomainLogic.sanitizeWifiPassword(password), provisioningError = null) }
    }

    fun updateCustomBoxName(name: String) {
        _uiState.update { it.copy(customBoxName = name.take(35), provisioningError = null) }
    }

    fun updateClaimCode(code: String) {
        _uiState.update { it.copy(claimCode = code, provisioningError = null) }
    }

    fun cancelWifiSetupAndReturnToScan() {
        pendingFirebaseClaimResult = null
        provClient.disconnectFromEspHotspot()
        _uiState.update {
            it.copy(
                step = if (it.userDevices.isNotEmpty()) MyPlantsStep.LIVE_DASHBOARD else MyPlantsStep.SCAN_ESP32,
                connectedEspInfo = null,
                isProvisioning = false,
                provisioningProgressMessage = null,
                provisioningError = null
            )
        }
    }

    /**
     * Strict 2-Stage Provisioning + Firebase Claim:
     * 1. Sends 2.4 GHz Wi-Fi SSID + Password + Custom Box Name + Claim Code to ESP32 (`192.168.4.1/api/provision`).
     * 2. Releases the ESP32 SoftAP so phone regains internet and registers the logged-in user as Owner in Firebase RTDB.
     * 3. Only transitions to LIVE_DASHBOARD once BOTH steps succeed!
     */
    fun submitWifiAndClaimDevice() {
        val uid = boundUid
        if (uid.isNullOrBlank()) {
            _uiState.update { it.copy(provisioningError = "Sign in with a registered account first.") }
            return
        }
        val state = _uiState.value
        val espInfo = state.connectedEspInfo
        if (espInfo == null) {
            _uiState.update { it.copy(provisioningError = "Lost connection to ESP32. Reconnect and try again.") }
            return
        }
        val ssid = IotDomainLogic.sanitizeWifiSsid(state.selectedWifiSsid)
        if (ssid.isEmpty()) {
            _uiState.update { it.copy(provisioningError = "Please select or enter a 2.4 GHz Wi-Fi network.") }
            return
        }
        val selectedNet = state.scannedWifiNetworks.firstOrNull { it.ssid == ssid }
        val cleanPassword = IotDomainLogic.sanitizeWifiPassword(state.wifiPassword)
        if (selectedNet?.secure == true && cleanPassword.isEmpty()) {
            _uiState.update { it.copy(provisioningError = "Please enter the Wi-Fi password for \"$ssid\".") }
            return
        }
        if (cleanPassword.isNotEmpty() && cleanPassword.length < 8) {
            _uiState.update { it.copy(provisioningError = "WPA/WPA2 Wi-Fi passwords must be at least 8 characters (currently ${cleanPassword.length}).") }
            return
        }
        val claim = state.claimCode.trim()
        if (claim.isEmpty()) {
            _uiState.update { it.copy(provisioningError = "Please enter the physical Claim Code (e.g. SPC-CLAIM-0001).") }
            return
        }
        val boxName = state.customBoxName.trim().ifEmpty { "SmartPlantCare Box" }

        viewModelScope.launch {
            val cachedProv = pendingFirebaseClaimResult
            val provResult = if (cachedProv != null && cachedProv.success) {
                // Stage 1/3 already succeeded on ESP32 and closed SoftAP; retry Stage 3/3 in Firebase directly
                _uiState.update {
                    it.copy(
                        isProvisioning = true,
                        provisioningError = null,
                        provisioningProgressMessage = "3/3 Retrying Firebase binding for \"$boxName\"..."
                    )
                }
                provClient.disconnectFromEspHotspot()
                cachedProv
            } else {
                _uiState.update {
                    it.copy(
                        isProvisioning = true,
                        provisioningError = null,
                        provisioningProgressMessage = "1/3 Sending Wi-Fi & testing password on ESP32..."
                    )
                }

                val provAttemptStartedAtMs = System.currentTimeMillis()
                val nextEpoch = (espInfo.ownershipEpoch.coerceAtLeast(0L)) + 1L
                val freshProv = provClient.sendProvisioningPayload(
                    wifiSsid = ssid,
                    wifiPassword = cleanPassword,
                    claimCode = claim,
                    displayName = boxName,
                    ownerUid = uid,
                    nextOwnershipEpoch = nextEpoch,
                    onStatusUpdate = { statusMsg ->
                        _uiState.update { it.copy(provisioningProgressMessage = "1/3 $statusMsg") }
                    }
                )

                val verifiedProv = if (freshProv.success) {
                    freshProv
                } else if (freshProv.contactLostAfterAccept) {
                    // ESP32 accepted credentials and may have verified via pure-STA fallback (closing SoftAP).
                    // Release SoftAP so phone regains internet, then check if ESP32 checked in online on Firebase!
                    _uiState.update {
                        it.copy(provisioningProgressMessage = "2/3 Checking if ESP32 came online via router...")
                    }
                    provClient.disconnectFromEspHotspot()
                    delay(3000L)
                    val targetDeviceId = freshProv.deviceId.ifBlank { espInfo.deviceId }
                    var verifiedInCloud = false
                    for (probe in 1..6) {
                        if (repository.isDeviceProvisionedOnline(
                                deviceId = targetDeviceId,
                                expectedEpoch = nextEpoch,
                                minEpochTimestampMs = provAttemptStartedAtMs - 15_000L
                            )
                        ) {
                            verifiedInCloud = true
                            break
                        }
                        delay(2500L)
                    }
                    if (verifiedInCloud) {
                        freshProv.copy(success = true, errorMessage = null)
                    } else {
                        freshProv
                    }
                } else {
                    freshProv
                }

                if (!verifiedProv.success) {
                    // WRONG PASSWORD OR CLAIM CODE: Keep user on Step 2 (DO NOT go to Dashboard!)
                    // Keep ESP32 hotspot connected when SoftAP stayed up so user can retry immediately.
                    pendingFirebaseClaimResult = null
                    _uiState.update {
                        it.copy(
                            isProvisioning = false,
                            provisioningProgressMessage = null,
                            provisioningError = verifiedProv.errorMessage
                                ?: "Invalid Wi-Fi password or Claim Code. Please check and try again."
                        )
                    }
                    return@launch
                }

                pendingFirebaseClaimResult = verifiedProv
                _uiState.update {
                    it.copy(provisioningProgressMessage = "2/3 Wi-Fi password verified! ESP32 connected — reconnecting phone to internet...")
                }
                provClient.disconnectFromEspHotspot()
                delay(3000L)
                verifiedProv
            }

            _uiState.update {
                it.copy(provisioningProgressMessage = "3/3 Binding \"$boxName\" to your owner account in Firebase...")
            }

            var claimSuccess = false
            var lastError: String? = null
            // Retry up to 6 times (~18s total) while phone transitions from ESP32 SoftAP back to Wi-Fi/Cellular
            for (attempt in 1..6) {
                val claimRes = repository.completeDeviceClaim(
                    deviceId = provResult.deviceId.ifBlank { espInfo.deviceId },
                    ownerUid = uid,
                    ownerEmail = boundEmail,
                    ownerName = boundName,
                    displayName = boxName,
                    ownershipEpoch = provResult.ownershipEpoch
                )
                if (claimRes.isSuccess) {
                    claimSuccess = true
                    break
                } else {
                    lastError = claimRes.exceptionOrNull()?.localizedMessage
                    delay(2500L)
                }
            }

            if (!claimSuccess) {
                _uiState.update {
                    it.copy(
                        isProvisioning = false,
                        provisioningProgressMessage = null,
                        provisioningError = "ESP32 received Wi-Fi, but Firebase sync failed (${lastError ?: "No internet"}). Make sure phone internet is back, then tap Retry."
                    )
                }
                return@launch
            }

            pendingFirebaseClaimResult = null
            val finalDeviceId = provResult.deviceId.ifBlank { espInfo.deviceId }
            _uiState.update {
                it.copy(
                    isProvisioning = false,
                    provisioningProgressMessage = null,
                    provisioningError = null,
                    connectedEspInfo = null,
                    wifiPassword = "",
                    activeDeviceId = finalDeviceId,
                    activeRole = DeviceRole.OWNER,
                    step = MyPlantsStep.LIVE_DASHBOARD,
                    subPage = MyPlantsSubPage.NONE,
                    snackbarMessage = "Device Paired! \"$boxName\" is now linked to your account."
                )
            }
            observeLiveDevice(finalDeviceId, forceRestart = true)
            startLiveSyncWatchdog(finalDeviceId)
        }
    }

    // ─── STEP 3: Live Dashboard Controls ────────────────────────────────────────

    fun selectActiveDevice(deviceId: String) {
        val link = _uiState.value.userDevices.firstOrNull { it.deviceId == deviceId } ?: return
        _uiState.update {
            it.copy(
                activeDeviceId = link.deviceId,
                activeRole = link.role,
                subPage = MyPlantsSubPage.NONE
            )
        }
        observeLiveDevice(link.deviceId)
        startLiveSyncWatchdog(link.deviceId)
    }

    fun openAddAnotherDeviceFlow() {
        _uiState.update {
            it.copy(
                step = MyPlantsStep.SCAN_ESP32,
                subPage = MyPlantsSubPage.NONE,
                scanStatusMessage = null
            )
        }
    }

    fun returnToLiveDashboard() {
        if (_uiState.value.activeDeviceId != null) {
            _uiState.update {
                it.copy(step = MyPlantsStep.LIVE_DASHBOARD, subPage = MyPlantsSubPage.NONE)
            }
        }
    }

    fun openSubPage(page: MyPlantsSubPage) {
        _uiState.update { it.copy(subPage = page) }
    }

    fun closeSubPage() {
        _uiState.update { it.copy(subPage = MyPlantsSubPage.NONE) }
    }

    fun setPulseSecondsForCrop(cropSlug: String, seconds: Int) {
        val clamped = seconds.coerceIn(1, 15)
        _uiState.update {
            it.copy(selectedPulseSecByCrop = it.selectedPulseSecByCrop + (cropSlug to clamped))
        }
    }

    fun switchMode(mode: String) {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (!st.activeRole.canControl) {
            showSnackbar("Viewer accounts have read-only access.")
            return
        }
        viewModelScope.launch {
            repository.setDeviceMode(deviceId, mode)
                .onSuccess { showSnackbar("Environment set to $mode.") }
                .onFailure { showSnackbar("Failed to switch mode: ${it.localizedMessage}") }
        }
    }

    fun switchAutoIrrigation(enabled: Boolean) {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (!st.activeRole.canControl) {
            showSnackbar("Viewer accounts have read-only access.")
            return
        }
        viewModelScope.launch {
            repository.setAutoIrrigationEnabled(deviceId, enabled)
                .onSuccess {
                    showSnackbar(
                        if (enabled) "Automatic Irrigation enabled."
                        else "Switched to Manual Irrigation mode."
                    )
                }
                .onFailure { showSnackbar("Failed to update irrigation mode: ${it.localizedMessage}") }
        }
    }

    fun enablePhysicalRelayLoads() {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (!st.activeRole.canControl) {
            showSnackbar("Viewer accounts have read-only access.")
            return
        }
        viewModelScope.launch {
            repository.setPhysicalLoadsEnabled(deviceId, true)
                .onSuccess { showSnackbar("Physical relays ENABLED! ESP32 will energize valves & pump.") }
                .onFailure { showSnackbar("Failed to enable relays: ${it.localizedMessage}") }
        }
    }

    /**
     * Tears down any stale Firebase RTDB WebSocket, fetches the latest server state directly
     * via no-cache HTTPS REST (bypassing local SDK cache), and restarts the live stream.
     * Automatically called on app resume and available as a 1-tap Sync button on the Hero Card.
     */
    fun resyncLiveDevice(silent: Boolean = false) {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (st.step != MyPlantsStep.LIVE_DASHBOARD) return
        if (st.isSyncingDevice) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingDevice = true) }
            // Ensure no lingering ESP32 SoftAP network binding blocks normal internet traffic
            provClient.disconnectFromEspHotspot()
            val refreshRes = repository.forceRefreshDevice(deviceId)
            // Re-attach live listener AFTER socket reset so real-time updates resume cleanly
            observeLiveDevice(deviceId, forceRestart = true)
            startLiveSyncWatchdog(deviceId)

            refreshRes
                .onSuccess { fresh ->
                    _uiState.update { it.copy(isSyncingDevice = false) }
                    if (fresh != null) {
                        applyLiveSnapshot(deviceId, fresh, fromRealtime = false)
                    }
                    if (!silent) {
                        val now = System.currentTimeMillis()
                        val isFresh = fresh != null && IotDomainLogic.isDeviceOnlineFresh(
                            status = fresh.status,
                            nowMs = now,
                            receivedAtClientMs = fresh.receivedAtClientMs,
                            telemetryUpdatedAt = fresh.telemetry.updatedAt
                        )
                        val latestHeartbeat = if (fresh != null) {
                            maxOf(fresh.status.lastSeenAt, fresh.telemetry.updatedAt)
                        } else 0L
                        val msg = when {
                            isFresh -> {
                                val ipSuffix = fresh?.status?.ipAddress?.takeIf { it.isNotBlank() }
                                    ?.let { " ($it)" } ?: ""
                                "Synced live data from ESP32!$ipSuffix"
                            }
                            fresh != null && latestHeartbeat >= 1_700_000_000_000L -> {
                                val ageMin = ((now - latestHeartbeat) / 60_000L).coerceAtLeast(1L)
                                val ageText = if (ageMin < 60L) "${ageMin}m ago" else "${ageMin / 60L}h ago"
                                "Cloud OK, but ESP32 last sent data $ageText — re-upload updated ESP32 sketch & re-pair once if SPC-SETUP is open."
                            }
                            else -> "Cloud connected — waiting for ESP32 Wi-Fi heartbeat..."
                        }
                        showSnackbar(msg)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSyncingDevice = false) }
                    if (!silent) {
                        showSnackbar("Sync failed: ${err.localizedMessage ?: "Check phone internet"}")
                    }
                }
        }
    }

    fun consumeOwnerLocationPrompt() {
        _uiState.update { it.copy(shouldPromptOwnerLocation = false) }
    }

    fun onOwnerLocationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(shouldPromptOwnerLocation = false) }
        val st = _uiState.value
        if (!IotDomainLogic.canRequestDeviceLocationPermission(st.activeRole)) {
            return
        }
        if (granted) {
            refreshWeatherWithOwnerGps(silent = false)
        } else {
            showSnackbar("Location permission skipped. Tap Rain Guard refresh anytime to set exact GPS.")
            refreshWeatherForSavedBoxLocation(silent = true)
        }
    }

    /**
     * OWNER-ONLY: Reads the Owner's mobile GPS coordinates + city name, writes them to
     * `/devices/{deviceId}/meta/location` (so ESP32 & all shared users use the exact box location),
     * and updates the live Open-Meteo 3-hour rain forecast.
     */
    fun refreshWeatherWithOwnerGps(silent: Boolean = false) {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (!IotDomainLogic.canRequestDeviceLocationPermission(st.activeRole)) {
            refreshWeatherForSavedBoxLocation(silent = silent)
            return
        }
        if (st.isRefreshingWeather) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingWeather = true) }
            weatherClient.fetchOwnerMobileGpsAndRainForecast(appCtx)
                .onSuccess { weatherSnap ->
                    repository.updateOwnerLocationAndWeather(deviceId, weatherSnap)
                    _uiState.update { it.copy(isRefreshingWeather = false) }
                    if (!silent) {
                        showSnackbar("Box GPS saved (${weatherSnap.locationName}): ${weatherSnap.rainProbabilityPct}% rain chance")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isRefreshingWeather = false) }
                    if (!silent) {
                        showSnackbar("GPS weather update failed: ${err.localizedMessage ?: "Turn on phone Location"}")
                    }
                    // Fallback to refreshing weather for existing saved coordinates without overwriting them
                    refreshWeatherForSavedBoxLocation(silent = true)
                }
        }
    }

    /**
     * Refreshes Open-Meteo 3-hour rain probability using the Owner's already-saved box coordinates.
     * NEVER requests phone GPS permission or mutates `/meta/location` — safe for shared users.
     */
    fun refreshWeatherForSavedBoxLocation(silent: Boolean = false) {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        val snapshot = st.liveSnapshot ?: return
        if (st.isRefreshingWeather) return

        val lat = snapshot.meta.lat
        val lon = snapshot.meta.lon
        val savedName = snapshot.meta.locationName.ifBlank { snapshot.weather.locationName }
        val savedSource = snapshot.meta.locationSource.ifBlank { "owner_gps" }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingWeather = true) }
            weatherClient.fetchRainForecastForCoordinates(lat, lon, savedName, savedSource)
                .onSuccess { weatherSnap ->
                    if (st.activeRole.canControl) {
                        repository.updateWeatherForecastOnly(deviceId, weatherSnap)
                    } else {
                        // Viewer fallback: update local display without writing to RTDB
                        _uiState.update { cur ->
                            val live = cur.liveSnapshot ?: return@update cur
                            val now = System.currentTimeMillis()
                            cur.copy(
                                liveSnapshot = live.copy(
                                    weather = live.weather.copy(
                                        provider = weatherSnap.provider,
                                        valid = true,
                                        rainProbabilityPct = weatherSnap.rainProbabilityPct,
                                        locationName = weatherSnap.locationName,
                                        lat = weatherSnap.lat,
                                        lon = weatherSnap.lon,
                                        fetchedAt = now,
                                        validUntil = now + 3_600_000L
                                    )
                                )
                            )
                        }
                    }
                    _uiState.update { it.copy(isRefreshingWeather = false) }
                    if (!silent) {
                        showSnackbar("Weather synced (${weatherSnap.locationName}): ${weatherSnap.rainProbabilityPct}% rain chance")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isRefreshingWeather = false) }
                    if (!silent) {
                        showSnackbar("Weather refresh failed: ${err.localizedMessage ?: "No internet"}")
                    }
                }
        }
    }

    fun triggerManualWater(cropSlug: String) {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        val lockReason = IotDomainLogic.manualWaterLockReason(st.activeRole, snapshot, cropSlug)
        if (lockReason != null) {
            showSnackbar("Blocked: $lockReason")
            return
        }
        val seconds = st.selectedPulseSecByCrop[cropSlug] ?: 4
        val durationMs = seconds * 1000L
        val effectiveEpoch = if (snapshot.status.ownershipEpoch > 0L) {
            snapshot.status.ownershipEpoch
        } else {
            snapshot.meta.ownershipEpoch
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true, pendingWaterCrop = cropSlug) }
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "WATER_CROP",
                requestedByUid = uid,
                ownershipEpoch = effectiveEpoch,
                cropSlug = cropSlug,
                durationMs = durationMs
            ).onSuccess { cmdId ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        pendingCommandId = cmdId,
                        pendingWaterCrop = cropSlug,
                        snackbarMessage = "Sent ${seconds}s watering command for ${cropSlug.uppercase()}..."
                    )
                }
                // Clear optimistic starting badge after 8s if ESP32 didn't pick it up
                launch {
                    delay(8000L)
                    _uiState.update { cur ->
                        if (cur.pendingCommandId == cmdId) cur.copy(pendingWaterCrop = null) else cur
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        pendingWaterCrop = null,
                        snackbarMessage = "Command failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun triggerEmergencyStop() {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canControl) return
        val effectiveEpoch = if (snapshot.status.ownershipEpoch > 0L) {
            snapshot.status.ownershipEpoch
        } else {
            snapshot.meta.ownershipEpoch
        }

        viewModelScope.launch {
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "STOP_WATERING",
                requestedByUid = uid,
                ownershipEpoch = effectiveEpoch
            ).onSuccess { cmdId ->
                _uiState.update {
                    it.copy(
                        pendingCommandId = cmdId,
                        snackbarMessage = "Emergency Stop sent — shutting pump & valves OFF."
                    )
                }
            }.onFailure { showSnackbar("Stop command failed: ${it.localizedMessage}") }
        }
    }

    fun clearEmergencyFault() {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canControl) return
        val effectiveEpoch = if (snapshot.status.ownershipEpoch > 0L) {
            snapshot.status.ownershipEpoch
        } else {
            snapshot.meta.ownershipEpoch
        }

        viewModelScope.launch {
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "CLEAR_EMERGENCY",
                requestedByUid = uid,
                ownershipEpoch = effectiveEpoch
            ).onSuccess { cmdId ->
                _uiState.update {
                    it.copy(pendingCommandId = cmdId, snackbarMessage = "Clear Fault command sent.")
                }
            }.onFailure { showSnackbar("Failed: ${it.localizedMessage}") }
        }
    }

    fun muteBuzzerCommand() {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canControl) return
        val effectiveEpoch = if (snapshot.status.ownershipEpoch > 0L) {
            snapshot.status.ownershipEpoch
        } else {
            snapshot.meta.ownershipEpoch
        }

        viewModelScope.launch {
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "MUTE_BUZZER",
                requestedByUid = uid,
                ownershipEpoch = effectiveEpoch
            ).onSuccess { showSnackbar("Mute buzzer command sent.") }
        }
    }

    // ─── PAGE 4: Share My Plant ─────────────────────────────────────────────────

    fun createShareInvite(role: DeviceRole) {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canManageSharing) {
            showSnackbar("Only the device owner can create share invites.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true) }
            repository.generateShareInvite(
                deviceId = snapshot.deviceId,
                role = role,
                ownerUid = uid,
                ownershipEpoch = snapshot.meta.ownershipEpoch
            ).onSuccess { invite ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        latestGeneratedInvite = invite,
                        snackbarMessage = "24-hour ${role.label} invite code created!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        snackbarMessage = "Could not create invite: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun redeemInviteCode(codeInput: String, onSuccessDismiss: () -> Unit) {
        val uid = boundUid
        if (uid.isNullOrBlank()) {
            showSnackbar("Sign in with a registered account to join a shared plant box.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true) }
            repository.redeemShareInvite(
                rawCodeInput = codeInput,
                uid = uid,
                userEmail = boundEmail,
                userName = boundName
            ).onSuccess { joinedDeviceId ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        activeDeviceId = joinedDeviceId,
                        step = MyPlantsStep.LIVE_DASHBOARD,
                        subPage = MyPlantsSubPage.NONE,
                        snackbarMessage = "Joined shared plant box ($joinedDeviceId)!"
                    )
                }
                observeLiveDevice(joinedDeviceId)
                startLiveSyncWatchdog(joinedDeviceId)
                onSuccessDismiss()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        snackbarMessage = err.localizedMessage ?: "Invalid or expired invite code."
                    )
                }
            }
        }
    }

    fun revokeMember(targetUid: String) {
        val st = _uiState.value
        val snapshot = st.liveSnapshot ?: return
        val callerUid = boundUid ?: return
        if (!st.activeRole.canManageSharing && targetUid != callerUid) return

        viewModelScope.launch {
            repository.revokeMember(snapshot.deviceId, targetUid)
                .onSuccess {
                    if (targetUid == callerUid) {
                        showSnackbar("You left this shared plant box.")
                    } else {
                        showSnackbar("Member access removed.")
                    }
                }
                .onFailure { showSnackbar("Failed to remove access: ${it.localizedMessage}") }
        }
    }

    // ─── PAGE 6: Device Settings, Rename & Unclaim Reset ────────────────────────

    fun renameBox(newName: String) {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (!st.activeRole.canControl) return
        viewModelScope.launch {
            repository.renameDevice(deviceId, newName)
                .onSuccess { showSnackbar("Plant box renamed to \"${newName.trim()}\".") }
                .onFailure { showSnackbar(it.localizedMessage ?: "Rename failed.") }
        }
    }

    fun saveDeviceConfig(updated: DeviceConfig) {
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (!st.activeRole.canControl) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true) }
            repository.updateDeviceConfig(deviceId, updated)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmittingAction = false,
                            snackbarMessage = "Settings synced to ESP32 & Firebase."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmittingAction = false,
                            snackbarMessage = "Save failed: ${err.localizedMessage}"
                        )
                    }
                }
        }
    }

    fun unclaimAndResetCurrentDevice() {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canUnclaim) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true) }
            repository.unclaimAndResetDevice(
                deviceId = snapshot.deviceId,
                currentEpoch = snapshot.meta.ownershipEpoch,
                memberUids = snapshot.members.map { it.uid },
                ownerUid = uid
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        subPage = MyPlantsSubPage.NONE,
                        step = MyPlantsStep.SCAN_ESP32,
                        liveSnapshot = null,
                        snackbarMessage = "Device reset triggered (RESET_PENDING). ESP32 will stop loads, wipe Wi-Fi, and re-open Setup Mode."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        snackbarMessage = "Unclaim failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    override fun onCleared() {
        espScanJob?.cancel()
        liveWatchdogJob?.cancel()
        provClient.disconnectFromEspHotspot()
        super.onCleared()
    }
}
