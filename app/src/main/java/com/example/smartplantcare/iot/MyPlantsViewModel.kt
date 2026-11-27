package com.example.smartplantcare.iot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplantcare.data.NotificationHistory
import com.example.smartplantcare.data.NotificationHistoryRepository
import com.example.smartplantcare.notification.NotificationHelper
import com.example.smartplantcare.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class MyPlantsStep {
    SCAN_ESP32,
    WIFI_AND_CLAIM,
    LIVE_DASHBOARD
}

enum class MyPlantsSubPage {
    NONE,
    SHARE_PLANT,
    EVENT_LOG,
    DEVICE_SETTINGS,
    HISTORY_MONITORING
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
    val pendingCommandPhase: PendingCommandPhase = PendingCommandPhase.IDLE,
    val isSubmittingAction: Boolean = false,
    val uploadingPhotoCropSlug: String? = null,
    val isRefreshingWeather: Boolean = false,
    val isSyncingDevice: Boolean = false,
    val lastSyncCompletedAtMs: Long = 0L,
    val lastSyncLatencyMs: Long = 0L,
    val shouldPromptOwnerLocation: Boolean = false,

    // Page 4: Share My Plant
    val latestGeneratedInvite: ShareInvite? = null,

    // Page 7: 7-Day Historical Monitoring & Irrigation Records (Asia/Manila PHT, UTC+8)
    val selectedHistoryDateKey: String = IotDomainLogic.formatPhDateKey(System.currentTimeMillis()),
    val historyDaysWindow: List<HistoryDateOption> = IotDomainLogic.generateSevenDayWindowPht(),
    val historyByDate: Map<String, DailyHistorySnapshot> = emptyMap(),
    val historyGranularity: HistoryGranularity = HistoryGranularity.BY_HOURS,
    val selectedMinuteHourFilter: Int? = null, // null = All Day (00:00-23:59), 0..23 = Hour (:00-:59)
    val isLoadingHistory: Boolean = false,
    val isLoadingMinuteHistory: Boolean = false,

    // Network & Phone Connectivity
    val isPhoneOnline: Boolean = true,
    val phoneDisconnectedAtMs: Long? = null,

    // Shared Device Access Revocation Dialog (single unacknowledged event shown at a time)
    val pendingRevocationDialog: AccessRevocationEvent? = null,

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
    private val fastCachePrefs by lazy {
        appCtx.getSharedPreferences("spc_iot_fast_cache", Context.MODE_PRIVATE)
    }
    private val notifLock = Any()
    private val seededDevices = mutableSetOf<String>()
    private val seenEventIdsByDevice = mutableMapOf<String, LinkedHashSet<String>>()
    private val lastNotifiedAtByKey = mutableMapOf<String, Long>()
    private val ownerLocationPromptedDevices = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(
        MyPlantsUiState(
            isPhoneOnline = NetworkMonitor.isOnlineFlow.value,
            phoneDisconnectedAtMs = NetworkMonitor.disconnectedAtMsFlow.value
        )
    )
    val uiState: StateFlow<MyPlantsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            NetworkMonitor.isOnlineFlow.collect { online ->
                val prevOnline = _uiState.value.isPhoneOnline
                val disconnectedAt = NetworkMonitor.disconnectedAtMsFlow.value
                _uiState.update { st ->
                    var nextSnackbar = st.snackbarMessage
                    if (prevOnline && !online) {
                        val snap = st.liveSnapshot
                        val evalMs = disconnectedAt ?: System.currentTimeMillis()
                        val wasBoxOnline = snap != null && IotDomainLogic.isDeviceOnlineFresh(
                            status = snap.status,
                            nowMs = evalMs,
                            receivedAtClientMs = snap.receivedAtClientMs,
                            telemetryUpdatedAt = snap.telemetry.updatedAt
                        )
                        if (wasBoxOnline) {
                            nextSnackbar = "No internet connection. Please check your internet connection."
                        }
                    }
                    st.copy(
                        isPhoneOnline = online,
                        phoneDisconnectedAtMs = disconnectedAt,
                        snackbarMessage = nextSnackbar
                    )
                }
                if (!prevOnline && online) {
                    syncPendingRevocationsNow()
                    if (_uiState.value.activeDeviceId != null) {
                        resyncLiveDevice(silent = true)
                    }
                }
            }
        }
        viewModelScope.launch {
            RevocationEventCoordinator.liveEvents.collect { event ->
                val uid = boundUid ?: return@collect
                handleRevocationEvents(uid, listOf(event))
            }
        }
    }

    private var boundUid: String? = null
    private var boundEmail: String = ""
    private var boundName: String = ""
    private var userDevicesJob: Job? = null
    private var userRevocationsJob: Job? = null
    private var liveDeviceJob: Job? = null
    private var liveWatchdogJob: Job? = null
    private var historyJob: Job? = null
    private var minuteHistoryJob: Job? = null
    private val pendingRevocationQueue = ArrayDeque<AccessRevocationEvent>()
    private var observedMinuteKeyToken: String? = null
    private var lastPrunedHistoryKey: String? = null
    private var rawCloudHistoryByDate: Map<String, DailyHistorySnapshot> = emptyMap()
    private val rawCloudMinutesByDate = mutableMapOf<String, Map<Int, HistoricalMinuteRecord>>()
    private val sessionLiveMinutesByDate = mutableMapOf<String, MutableMap<Int, HistoricalMinuteRecord>>()
    private var lastRecordedSessionTelemetryTs: Long = 0L
    private var espScanJob: Job? = null
    private var lastReportedCmdId: String? = null
    private var lastAutoWeatherSyncDeviceId: String? = null
    private var lastReconciledEpochKey: String? = null
    private var lastRealtimeDeliveryAtMs: Long = 0L
    private var lastSocketReconnectAtMs: Long = 0L
    private var pendingFirebaseClaimResult: ProvisionResult? = null

    private fun currentTransportLabel(): String {
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "UNKNOWN"
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return "OFFLINE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE_DATA"
            else -> "OTHER"
        }
    }

    private fun saveDeviceLinksCache(uid: String, links: List<UserDeviceLink>, activeDeviceId: String?) {
        runCatching {
            val arr = JSONArray()
            for (l in links) {
                arr.put(JSONObject().apply {
                    put("deviceId", l.deviceId)
                    put("role", l.role.slug)
                    put("ownershipEpoch", l.ownershipEpoch)
                    put("addedAt", l.addedAt)
                })
            }
            fastCachePrefs.edit()
                .putString("links_$uid", arr.toString())
                .putString("active_$uid", activeDeviceId.orEmpty())
                .apply()
        }
    }

    private fun loadCachedDeviceLinks(uid: String): Pair<List<UserDeviceLink>, String?> {
        return runCatching {
            val raw = fastCachePrefs.getString("links_$uid", null) ?: return@runCatching emptyList<UserDeviceLink>() to null
            val arr = JSONArray(raw)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val devId = o.optString("deviceId", "")
                    if (devId.isBlank()) continue
                    add(
                        UserDeviceLink(
                            deviceId = devId,
                            role = DeviceRole.fromSlug(o.optString("role", "viewer")),
                            ownershipEpoch = o.optLong("ownershipEpoch", 1L),
                            addedAt = o.optLong("addedAt", 0L)
                        )
                    )
                }
            }
            val active = fastCachePrefs.getString("active_$uid", null)?.takeIf { it.isNotBlank() }
            list to active
        }.getOrDefault(emptyList<UserDeviceLink>() to null)
    }

    private fun saveSnapshotCache(uid: String, snapshot: LiveDeviceSnapshot) {
        if (snapshot.isFromLocalCache || uid.isBlank()) return
        runCatching {
            val json = repository.serializeSnapshotForLocalCache(snapshot)
            fastCachePrefs.edit().putString("snap_${uid}_${snapshot.deviceId}", json).apply()
        }
    }

    private fun loadCachedSnapshot(uid: String, deviceId: String): LiveDeviceSnapshot? {
        if (uid.isBlank() || deviceId.isBlank()) return null
        val raw = fastCachePrefs.getString("snap_${uid}_$deviceId", null) ?: return null
        return repository.deserializeCachedSnapshot(deviceId, raw)
    }

    private fun clearCachedDevice(uid: String?, deviceId: String) {
        if (uid.isNullOrBlank()) return
        runCatching {
            fastCachePrefs.edit().remove("snap_${uid}_$deviceId").apply()
        }
    }

    fun bindUser(uid: String?, email: String, displayName: String, isGuest: Boolean) {
        if (isGuest || uid.isNullOrBlank()) {
            userDevicesJob?.cancel()
            userRevocationsJob?.cancel()
            liveDeviceJob?.cancel()
            liveWatchdogJob?.cancel()
            historyJob?.cancel()
            minuteHistoryJob?.cancel()
            observedMinuteKeyToken = null
            rawCloudHistoryByDate = emptyMap()
            rawCloudMinutesByDate.clear()
            sessionLiveMinutesByDate.clear()
            pendingRevocationQueue.clear()
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

        // Sync FCM token & profile display name to RTDB for accurate revocation notices & push delivery
        viewModelScope.launch(Dispatchers.IO) {
            com.example.smartplantcare.notification.FcmTokenManager.syncCurrentUserTokenAndProfile(
                context = appCtx,
                uid = uid,
                displayName = displayName,
                email = email
            )
        }

        if (boundUid == uid && userDevicesJob?.isActive == true) {
            return
        }
        boundUid = uid
        pendingRevocationQueue.clear()

        // 1. Instant local cache restore (<30ms) clearly tagged with isFromLocalCache = true
        val (cachedLinks, cachedActiveId) = loadCachedDeviceLinks(uid)
        if (cachedLinks.isNotEmpty()) {
            val chosenLink = cachedLinks.firstOrNull { it.deviceId == cachedActiveId } ?: cachedLinks.first()
            val cachedSnap = loadCachedSnapshot(uid, chosenLink.deviceId)
            _uiState.update { st ->
                st.copy(
                    isLoadingInitialDevices = false,
                    userDevices = cachedLinks,
                    activeDeviceId = chosenLink.deviceId,
                    activeRole = chosenLink.role,
                    step = MyPlantsStep.LIVE_DASHBOARD,
                    liveSnapshot = cachedSnap ?: st.liveSnapshot
                )
            }
            observeLiveDevice(chosenLink.deviceId)
            startLiveSyncWatchdog(chosenLink.deviceId)
            startObservingDeviceHistory(chosenLink.deviceId, forceRefresh = false)
        }

        observeUserDevices(uid)
        observeUserRevocations(uid)
    }

    private fun applyDiscoveredDeviceLinks(uid: String, links: List<UserDeviceLink>) {
        val previousLinks = _uiState.value.userDevices
        val newIds = links.map { it.deviceId }.toSet()
        // Immediately purge local cached snapshots for any device removed from user's authorized links
        previousLinks.forEach { oldLink ->
            if (oldLink.deviceId !in newIds) {
                clearCachedDevice(uid, oldLink.deviceId)
            }
        }

        val currentActive = _uiState.value.activeDeviceId
        val activeWasRemoved = currentActive != null && currentActive !in newIds
        val chosen = links.firstOrNull { it.deviceId == currentActive } ?: links.firstOrNull()
        saveDeviceLinksCache(uid, links, chosen?.deviceId)

        val cachedFallback = if (chosen != null && _uiState.value.liveSnapshot?.deviceId != chosen.deviceId) {
            loadCachedSnapshot(uid, chosen.deviceId)
        } else if (activeWasRemoved) {
            null
        } else {
            _uiState.value.liveSnapshot
        }

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
                subPage = if (shouldReturnToScan || activeWasRemoved) MyPlantsSubPage.NONE else st.subPage,
                liveSnapshot = if (chosen == null) null else cachedFallback
            )
        }

        if (chosen != null) {
            if (activeWasRemoved || liveDeviceJob?.isActive != true || _uiState.value.liveSnapshot?.deviceId != chosen.deviceId) {
                observeLiveDevice(chosen.deviceId)
                startLiveSyncWatchdog(chosen.deviceId)
                startObservingDeviceHistory(chosen.deviceId, forceRefresh = activeWasRemoved)
            }
        } else {
            disconnectActiveDeviceListeners()
        }
    }

    private fun disconnectActiveDeviceListeners() {
        liveDeviceJob?.cancel()
        liveWatchdogJob?.cancel()
        historyJob?.cancel()
        minuteHistoryJob?.cancel()
        observedMinuteKeyToken = null
        rawCloudHistoryByDate = emptyMap()
        rawCloudMinutesByDate.clear()
        sessionLiveMinutesByDate.clear()
    }

    private fun observeUserRevocations(uid: String) {
        userRevocationsJob?.cancel()
        userRevocationsJob = viewModelScope.launch {
            launch {
                if (NetworkMonitor.refreshOnlineState()) {
                    repository.fetchUserRevocationsViaRest(uid).onSuccess { events ->
                        if (boundUid == uid) {
                            handleRevocationEvents(uid, events)
                        }
                    }
                }
            }
            repository.observeUserRevocations(uid).collect { events ->
                if (boundUid == uid) {
                    handleRevocationEvents(uid, events)
                }
            }
        }
    }

    fun syncPendingRevocationsNow() {
        val uid = boundUid ?: return
        if (!NetworkMonitor.refreshOnlineState()) return
        viewModelScope.launch {
            repository.fetchUserDevicesViaRest(uid).onSuccess { links ->
                if (boundUid == uid) {
                    applyDiscoveredDeviceLinks(uid, links)
                }
            }
            repository.fetchUserRevocationsViaRest(uid).onSuccess { events ->
                if (boundUid == uid) {
                    handleRevocationEvents(uid, events)
                }
            }
        }
    }

    private fun handleRevocationEvents(uid: String, events: List<AccessRevocationEvent>) {
        if (events.isEmpty() || boundUid != uid) return

        val unacknowledged = events
            .filter { !it.acknowledged && !RevocationEventCoordinator.isDialogAcknowledged(appCtx, uid, it.eventId) }
            .sortedBy { it.revokedAt }

        for (event in unacknowledged) {
            // Delayed-event guard: never revoke or disrupt newly re-authorized access (addedAt > revokedAt)
            if (RevocationEventCoordinator.isSupersededByNewerAccess(event, _uiState.value.userDevices)) {
                RevocationEventCoordinator.markDialogAcknowledged(appCtx, uid, event.eventId)
                viewModelScope.launch { repository.acknowledgeRevocation(uid, event.eventId) }
                continue
            }

            // 1. Immediately purge cached data & tear down listeners for the revoked device
            clearCachedDevice(uid, event.deviceId)
            val currentLinks = _uiState.value.userDevices
            val remainingLinks = currentLinks.filterNot { it.deviceId == event.deviceId }
            if (remainingLinks.size != currentLinks.size || _uiState.value.activeDeviceId == event.deviceId) {
                applyDiscoveredDeviceLinks(uid, remainingLinks)
            } else {
                saveDeviceLinksCache(uid, remainingLinks, _uiState.value.activeDeviceId)
            }

            // 2. Persist deduplicated in-app notification record (covers offline / FCM-failed / permission-denied cases)
            if (RevocationEventCoordinator.tryMarkHistorySaved(appCtx, uid, event.eventId)) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        notifHistoryRepo.saveNotification(
                            NotificationHistory(
                                id = RevocationEventCoordinator.notificationHistoryId(event.eventId),
                                plantId = "",
                                plantName = event.deviceName,
                                plantImageUrl = "",
                                day = 0,
                                title = "Monitoring Access Removed",
                                message = "${event.ownerName} has removed your monitoring access to ${event.deviceName}.",
                                timestamp = if (event.revokedAt > 0L) event.revokedAt else System.currentTimeMillis(),
                                isRead = false,
                                isCompleted = true
                            )
                        )
                    }
                }
            }

            // 3. Queue dialog (deduplicated by eventId)
            val currentDialog = _uiState.value.pendingRevocationDialog
            val alreadyQueued = currentDialog?.eventId == event.eventId ||
                pendingRevocationQueue.any { it.eventId == event.eventId }
            if (!alreadyQueued) {
                if (currentDialog == null) {
                    _uiState.update { it.copy(pendingRevocationDialog = event) }
                } else {
                    pendingRevocationQueue.addLast(event)
                }
            }
        }
    }

    fun dismissRevocationDialog() {
        val current = _uiState.value.pendingRevocationDialog ?: return
        val uid = boundUid
        if (!uid.isNullOrBlank()) {
            RevocationEventCoordinator.markDialogAcknowledged(appCtx, uid, current.eventId)
            viewModelScope.launch {
                repository.acknowledgeRevocation(uid, current.eventId)
            }
        }
        val next = if (pendingRevocationQueue.isNotEmpty()) pendingRevocationQueue.removeFirst() else null
        _uiState.update { st ->
            st.copy(
                pendingRevocationDialog = next,
                subPage = if (st.activeDeviceId == null) MyPlantsSubPage.NONE else st.subPage
            )
        }
    }

    private fun observeUserDevices(uid: String) {
        userDevicesJob?.cancel()
        userDevicesJob = viewModelScope.launch {
            // Parallel fast authenticated REST discovery so startup never blocks on slow WebSocket upgrade
            launch {
                if (NetworkMonitor.refreshOnlineState()) {
                    repository.fetchUserDevicesViaRest(uid).onSuccess { restLinks ->
                        if (boundUid == uid) {
                            applyDiscoveredDeviceLinks(uid, restLinks)
                        }
                    }
                }
            }
            repository.observeUserDevices(uid).collect { links ->
                if (boundUid == uid) {
                    applyDiscoveredDeviceLinks(uid, links)
                }
            }
        }
    }

    private fun recordVerifiedLiveMinuteFallback(snapshot: LiveDeviceSnapshot) {
        // Strictly record ONLY verified live server snapshots (NEVER cached readings or stale offline data!)
        if (snapshot.isFromLocalCache) return
        val telTs = snapshot.telemetry.updatedAt
        if (telTs < 1_700_000_000_000L || telTs == lastRecordedSessionTelemetryTs) return
        if (!IotDomainLogic.isDeviceOnlineFresh(snapshot)) return

        lastRecordedSessionTelemetryTs = telTs
        val dateKey = IotDomainLogic.formatPhDateKey(telTs)
        val mOfDay = IotDomainLogic.phMinuteOfDay(telTs)
        val t = snapshot.telemetry
        val w = snapshot.weather

        val dayMap = sessionLiveMinutesByDate.getOrPut(dateKey) { mutableMapOf() }
        val prev = dayMap[mOfDay]
        val nextSamples = (prev?.sampleCount ?: 0) + 1
        fun blend(oldVal: Float?, newVal: Float?, valid: Boolean): Float? {
            if (!valid || newVal == null || !newVal.isFinite()) return oldVal
            return if (oldVal == null || prev == null || prev.sampleCount <= 0) {
                newVal
            } else {
                (oldVal * prev.sampleCount + newVal) / (prev.sampleCount + 1)
            }
        }

        dayMap[mOfDay] = HistoricalMinuteRecord(
            minuteOfDay = mOfDay,
            firstSeenAt = if (prev != null && prev.firstSeenAt > 0L) minOf(prev.firstSeenAt, telTs) else telTs,
            lastSeenAt = maxOf(prev?.lastSeenAt ?: 0L, telTs),
            sampleCount = nextSamples,
            tomatoPct = blend(prev?.tomatoPct, t.tomato.pct, t.tomato.valid),
            chiliPct = blend(prev?.chiliPct, t.chili.pct, t.chili.valid),
            eggplantPct = blend(prev?.eggplantPct, t.eggplant.pct, t.eggplant.valid),
            temperatureC = blend(prev?.temperatureC, t.temperatureC, t.dhtValid),
            humidityPct = blend(prev?.humidityPct, t.humidityPct, t.dhtValid),
            lightPct = blend(prev?.lightPct, t.lightBrightnessPct, t.lightValid),
            reservoirPct = blend(prev?.reservoirPct, t.reservoirPct, t.reservoirValid),
            reservoirGapIn = blend(prev?.reservoirGapIn, t.reservoirAirGapInches, t.reservoirValid),
            reservoirState = t.reservoirState,
            rainProbPct = if (w.valid && w.rainProbabilityPct in 0..100) w.rainProbabilityPct else prev?.rainProbPct,
            rainWet = (prev?.rainWet == true) || t.rainRawWet || t.rainWetConfirmed,
            rainConfirmed = (prev?.rainConfirmed == true) || t.rainWetConfirmed,
            rainSuspended = (prev?.rainSuspended == true) || snapshot.status.rainSuspensionActive,
            rainWetSamples = (prev?.rainWetSamples ?: 0) + if (t.rainRawWet || t.rainWetConfirmed) 1 else 0
        )
    }

    private fun applyLiveSnapshot(deviceId: String, snapshot: LiveDeviceSnapshot?, fromRealtime: Boolean) {
        val nowMs = System.currentTimeMillis()
        if (fromRealtime && snapshot != null && !snapshot.isFromLocalCache) {
            lastRealtimeDeliveryAtMs = nowMs
        }
        if (snapshot != null && !snapshot.isFromLocalCache) {
            boundUid?.let { uid -> saveSnapshotCache(uid, snapshot) }
            recordVerifiedLiveMinuteFallback(snapshot)
        }

        _uiState.update { st ->
            var nextSnackbar = st.snackbarMessage
            var nextPendingCmd = st.pendingCommandId
            var nextPendingCrop = st.pendingWaterCrop
            var nextPhase = st.pendingCommandPhase
            val trackedId = st.pendingCommandId

            if (trackedId != null && snapshot != null && !snapshot.isFromLocalCache) {
                val res = snapshot.commandResults[trackedId]
                if (res != null) {
                    val stateUpper = res.state.uppercase()
                    val reportToken = "${trackedId}_$stateUpper"
                    when (stateUpper) {
                        "ACCEPTED" -> {
                            nextPhase = PendingCommandPhase.ACKNOWLEDGED
                            if (lastReportedCmdId != reportToken) {
                                lastReportedCmdId = reportToken
                                nextSnackbar = "Acknowledged by ESP32: ${res.reason}"
                            }
                        }
                        "RUNNING" -> {
                            nextPhase = PendingCommandPhase.EXECUTING
                            lastReportedCmdId = reportToken
                        }
                        "DONE" -> {
                            if (lastReportedCmdId != reportToken) {
                                lastReportedCmdId = reportToken
                                nextSnackbar = "Completed: ${res.reason}"
                            }
                            nextPendingCmd = null
                            nextPendingCrop = null
                            nextPhase = PendingCommandPhase.IDLE
                        }
                        "REJECTED", "ABORTED" -> {
                            if (lastReportedCmdId != reportToken) {
                                lastReportedCmdId = reportToken
                                nextSnackbar = "${stateUpper}: ${res.reason}"
                            }
                            nextPendingCmd = null
                            nextPendingCrop = null
                            nextPhase = PendingCommandPhase.IDLE
                        }
                    }
                }
            }
            if (snapshot != null && !snapshot.isFromLocalCache) {
                if (snapshot.status.isActivelyIrrigating) {
                    if (nextPendingCrop != null && snapshot.status.activeValve.equals(nextPendingCrop, ignoreCase = true)) {
                        nextPhase = PendingCommandPhase.EXECUTING
                    }
                } else if (snapshot.status.isInSoakSettle && nextPhase == PendingCommandPhase.EXECUTING) {
                    nextPendingCrop = null
                    nextPendingCmd = null
                    nextPhase = PendingCommandPhase.IDLE
                }
            }

            // Keep role synchronized with /acl/members when full ACL is present
            val uid = boundUid
            val aclRole = if (snapshot != null && snapshot.members.isNotEmpty()) {
                snapshot.members.firstOrNull { it.uid == uid }?.role ?: st.activeRole
            } else {
                st.activeRole
            }

            // Filter latest generated invite if older than 24 hours or expired
            val validInvite = st.latestGeneratedInvite?.takeIf { invite ->
                val now = System.currentTimeMillis()
                invite.status == "PENDING" &&
                    (invite.expiresAt <= 0L || now < invite.expiresAt) &&
                    (invite.createdAt <= 0L || (now - invite.createdAt) < 24 * 60 * 60 * 1000L)
            }

            st.copy(
                liveSnapshot = snapshot,
                activeRole = aclRole,
                latestGeneratedInvite = validInvite,
                pendingCommandId = nextPendingCmd,
                pendingWaterCrop = nextPendingCrop,
                pendingCommandPhase = nextPhase,
                lastSyncCompletedAtMs = if (snapshot != null && !snapshot.isFromLocalCache) nowMs else st.lastSyncCompletedAtMs,
                lastSyncLatencyMs = if (snapshot != null && snapshot.lastSyncLatencyMs > 0L) snapshot.lastSyncLatencyMs else st.lastSyncLatencyMs,
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
        // ONLY for verified live server snapshots (never for locally restored cache!)
        if (snapshot != null && !snapshot.isFromLocalCache) {
            dispatchNewDeviceEventNotifications(snapshot)
            recomputeMergedHistoryUiState(deviceId, snapshot.events)
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
            if (!com.example.smartplantcare.notification.AlertPreferences.isEventAllowed(appCtx, ev.type)) {
                continue
            }
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
        if (current == null || current.isFromLocalCache) return true
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
            candidate.weather.fetchedAt != current.weather.fetchedAt ||
            candidate.commandResults != current.commandResults
    }

    /**
     * Background safety-net watchdog (active across BOTH Home and My Plants tabs):
     * - Immediately promotes cached cold-start state to verified live data via authenticated REST.
     * - NEVER tears down a healthy WebSocket (previously `>= 5_000L` collided with ESP32's 6,000ms cadence!).
     * - Only reconnects the Firebase WebSocket when genuinely stalled (`>= 24s` without realtime delivery
     *   while REST proves newer ESP32 server data exists, with a 30s cooldown between socket resets).
     */
    private fun startLiveSyncWatchdog(deviceId: String) {
        if (liveWatchdogJob?.isActive == true && _uiState.value.activeDeviceId == deviceId) {
            return
        }
        liveWatchdogJob?.cancel()
        liveWatchdogJob = viewModelScope.launch {
            delay(150L)
            while (true) {
                val phoneOnlineNow = NetworkMonitor.refreshOnlineState()
                val st = _uiState.value
                val currentSnap = st.liveSnapshot
                val now = System.currentTimeMillis()
                val isCachedOrEmpty = currentSnap == null || currentSnap.isFromLocalCache
                val currentStale = isCachedOrEmpty || !IotDomainLogic.isDeviceOnlineFresh(
                    status = currentSnap!!.status,
                    nowMs = now,
                    receivedAtClientMs = currentSnap.receivedAtClientMs,
                    telemetryUpdatedAt = currentSnap.telemetry.updatedAt
                )
                val eligibleScreen = st.activeDeviceId == deviceId &&
                    st.step != MyPlantsStep.WIFI_AND_CLAIM &&
                    !st.isProvisioning &&
                    !st.isSyncingDevice

                if (phoneOnlineNow && eligibleScreen) {
                    val hasPendingCmd = st.pendingCommandId != null
                    val wsQuietMs = if (lastRealtimeDeliveryAtMs > 0L) (now - lastRealtimeDeliveryAtMs) else Long.MAX_VALUE
                    val shouldProbeRest = isCachedOrEmpty ||
                        hasPendingCmd ||
                        currentStale ||
                        wsQuietMs >= 15_000L

                    if (shouldProbeRest) {
                        val restSnap = repository.fetchDeviceSnapshotViaRest(
                            deviceId = deviceId,
                            existingSnapshot = currentSnap,
                            slimDelta = !isCachedOrEmpty,
                            pendingCommandId = st.pendingCommandId
                        ).getOrNull()

                        if (restSnap != null && isNewerSnapshot(restSnap, _uiState.value.liveSnapshot)) {
                            val transport = currentTransportLabel()
                            Log.d(
                                "SpcSyncPerf",
                                "Watchdog REST sync [$transport] device=$deviceId latency=${restSnap.lastSyncLatencyMs}ms wsQuietMs=$wsQuietMs"
                            )
                            applyLiveSnapshot(deviceId, restSnap, fromRealtime = false)

                            // Reconnect WebSocket ONLY when genuinely stalled (>=24s silent while server advanced)
                            val socketGenuinelyStalled = lastRealtimeDeliveryAtMs > 0L &&
                                wsQuietMs >= 24_000L &&
                                (now - lastSocketReconnectAtMs) >= 30_000L
                            if (socketGenuinelyStalled) {
                                lastSocketReconnectAtMs = now
                                Log.w("SpcSyncPerf", "WebSocket stalled (${wsQuietMs}ms); reconnecting socket once.")
                                repository.reconnectRealtimeSocket()
                                observeLiveDevice(deviceId, forceRestart = true)
                            }
                        }
                    }
                }

                val nextDelayMs = when {
                    !phoneOnlineNow -> 2_500L
                    _uiState.value.pendingCommandId != null -> 1_600L
                    currentStale -> 4_500L
                    else -> 6_500L
                }
                delay(nextDelayMs)
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

    private suspend fun awaitValidatedInternet(maxWaitMs: Long = 5_000L): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (NetworkMonitor.refreshOnlineState()) return true
            delay(200L)
        }
        return NetworkMonitor.refreshOnlineState()
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
                awaitValidatedInternet(maxWaitMs = 3_500L)
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
                    },
                    onSoftApInterruptedCheckCloud = {
                        if (NetworkMonitor.refreshOnlineState()) {
                            repository.isDeviceProvisionedOnline(
                                deviceId = espInfo.deviceId,
                                expectedEpoch = nextEpoch,
                                minEpochTimestampMs = provAttemptStartedAtMs - 15_000L
                            )
                        } else {
                            false
                        }
                    }
                )

                val verifiedProv = if (freshProv.success) {
                    freshProv
                } else if (freshProv.contactLostAfterAccept) {
                    // ESP32 switched to the router's 2.4 GHz channel or pure-STA verification mode.
                    // Immediately release WifiNetworkSpecifier so Android restores internet, then poll
                    // Firebase via fast HTTP/2 REST for ESP32's non-owning wifiVerifiedEpoch stamp!
                    _uiState.update {
                        it.copy(provisioningProgressMessage = "2/3 Reconnecting phone internet & checking ESP32 router link...")
                    }
                    provClient.disconnectFromEspHotspot()
                    awaitValidatedInternet(maxWaitMs = 5_000L)
                    repository.reconnectRealtimeSocket()
                    val targetDeviceId = freshProv.deviceId.ifBlank { espInfo.deviceId }
                    var verifiedInCloud = false
                    val maxCloudProbes = 14
                    for (probe in 1..maxCloudProbes) {
                        _uiState.update {
                            it.copy(provisioningProgressMessage = "2/3 Waiting for ESP32 to confirm router Wi-Fi... ($probe/$maxCloudProbes)")
                        }
                        if (!NetworkMonitor.isOnlineFlow.value) {
                            awaitValidatedInternet(maxWaitMs = 1_500L)
                        }
                        if (repository.isDeviceProvisionedOnline(
                                deviceId = targetDeviceId,
                                expectedEpoch = nextEpoch,
                                minEpochTimestampMs = provAttemptStartedAtMs - 15_000L
                            )
                        ) {
                            verifiedInCloud = true
                            break
                        }
                        delay(1600L)
                    }
                    if (verifiedInCloud) {
                        freshProv.copy(success = true, errorMessage = null)
                    } else {
                        freshProv.copy(
                            errorMessage = "ESP32 could not connect to \"$ssid\". Please verify your 2.4 GHz Wi-Fi password, reconnect to ${espInfo.setupApSsid}, and try again."
                        )
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
                awaitValidatedInternet(maxWaitMs = 5_000L)
                repository.reconnectRealtimeSocket()
                verifiedProv
            }

            _uiState.update {
                it.copy(provisioningProgressMessage = "3/3 Binding \"$boxName\" to your owner account in Firebase...")
            }

            var claimSuccess = false
            var lastError: String? = null
            // Retry up to 6 times while phone transitions from ESP32 SoftAP back to Wi-Fi/Cellular
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
                    delay(1200L)
                    awaitValidatedInternet(maxWaitMs = 1_500L)
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
            val updatedLinks = listOf(
                UserDeviceLink(
                    deviceId = finalDeviceId,
                    role = DeviceRole.OWNER,
                    ownershipEpoch = provResult.ownershipEpoch,
                    addedAt = System.currentTimeMillis()
                )
            ) + _uiState.value.userDevices.filterNot { it.deviceId == finalDeviceId }
            saveDeviceLinksCache(uid, updatedLinks, finalDeviceId)

            _uiState.update {
                it.copy(
                    isProvisioning = false,
                    provisioningProgressMessage = null,
                    provisioningError = null,
                    connectedEspInfo = null,
                    wifiPassword = "",
                    userDevices = updatedLinks,
                    activeDeviceId = finalDeviceId,
                    activeRole = DeviceRole.OWNER,
                    step = MyPlantsStep.LIVE_DASHBOARD,
                    subPage = MyPlantsSubPage.NONE,
                    snackbarMessage = "Device Paired! \"$boxName\" is now linked to your account."
                )
            }
            observeLiveDevice(finalDeviceId, forceRestart = true)
            startLiveSyncWatchdog(finalDeviceId)
            startObservingDeviceHistory(finalDeviceId, forceRefresh = false)
            // Fast post-pairing REST snapshot fetch so dashboard renders immediately without waiting for WebSocket warmup
            launch {
                repository.fetchDeviceSnapshotViaRest(
                    deviceId = finalDeviceId,
                    existingSnapshot = null,
                    slimDelta = false
                ).getOrNull()?.let { snap ->
                    Log.d(
                        "SpcSyncPerf",
                        "Post-pairing fast REST sync [${currentTransportLabel()}] device=$finalDeviceId latency=${snap.lastSyncLatencyMs}ms"
                    )
                    applyLiveSnapshot(finalDeviceId, snap, fromRealtime = false)
                }
            }
        }
    }

    // ─── STEP 3: Live Dashboard Controls ────────────────────────────────────────

    fun selectActiveDevice(deviceId: String) {
        val link = _uiState.value.userDevices.firstOrNull { it.deviceId == deviceId } ?: return
        historyJob?.cancel()
        minuteHistoryJob?.cancel()
        observedMinuteKeyToken = null
        rawCloudHistoryByDate = emptyMap()
        rawCloudMinutesByDate.clear()
        val uid = boundUid
        if (!uid.isNullOrBlank()) {
            saveDeviceLinksCache(uid, _uiState.value.userDevices, link.deviceId)
        }
        val cachedSnap = uid?.let { loadCachedSnapshot(it, link.deviceId) }
        _uiState.update {
            it.copy(
                activeDeviceId = link.deviceId,
                activeRole = link.role,
                subPage = MyPlantsSubPage.NONE,
                liveSnapshot = cachedSnap ?: it.liveSnapshot?.takeIf { s -> s.deviceId == link.deviceId },
                historyByDate = emptyMap()
            )
        }
        observeLiveDevice(link.deviceId, forceRestart = true)
        startLiveSyncWatchdog(link.deviceId)
        startObservingDeviceHistory(link.deviceId, forceRefresh = false)
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
        if (page == MyPlantsSubPage.HISTORY_MONITORING) {
            val deviceId = _uiState.value.activeDeviceId ?: return
            startObservingDeviceHistory(deviceId, forceRefresh = true)
        }
    }

    fun closeSubPage() {
        _uiState.update { it.copy(subPage = MyPlantsSubPage.NONE) }
    }

    fun selectHistoryDate(dateKey: String) {
        val validKeys = _uiState.value.historyDaysWindow.map { it.dateKey }.toSet()
        if (dateKey !in validKeys) return
        _uiState.update { it.copy(selectedHistoryDateKey = dateKey) }
        val deviceId = _uiState.value.activeDeviceId ?: return
        ensureObservingMinuteDay(
            deviceId = deviceId,
            dateKey = dateKey,
            forceRefresh = _uiState.value.historyGranularity == HistoryGranularity.BY_MINUTES
        )
    }

    fun setHistoryGranularity(granularity: HistoryGranularity) {
        _uiState.update { it.copy(historyGranularity = granularity) }
        if (granularity == HistoryGranularity.BY_MINUTES) {
            val deviceId = _uiState.value.activeDeviceId ?: return
            ensureObservingMinuteDay(
                deviceId = deviceId,
                dateKey = _uiState.value.selectedHistoryDateKey,
                forceRefresh = true
            )
        }
    }

    fun selectMinuteHourFilter(hour: Int?) {
        val normalized = hour?.coerceIn(0, 23)
        _uiState.update { it.copy(selectedMinuteHourFilter = normalized) }
    }

    private fun ensureObservingMinuteDay(
        deviceId: String,
        dateKey: String,
        forceRefresh: Boolean = false
    ) {
        if (deviceId.isBlank() || dateKey.isBlank()) return
        val token = "${deviceId}_$dateKey"
        if (observedMinuteKeyToken != token || minuteHistoryJob?.isActive != true) {
            minuteHistoryJob?.cancel()
            observedMinuteKeyToken = token
            _uiState.update {
                it.copy(isLoadingMinuteHistory = rawCloudMinutesByDate[dateKey].isNullOrEmpty())
            }
            minuteHistoryJob = viewModelScope.launch {
                repository.observeDeviceMinuteDay(deviceId, dateKey).collect { mins ->
                    rawCloudMinutesByDate[dateKey] = mins
                    recomputeMergedHistoryUiState(deviceId, _uiState.value.liveSnapshot?.events.orEmpty())
                    _uiState.update { it.copy(isLoadingMinuteHistory = false) }
                }
            }
        }

        if (forceRefresh && NetworkMonitor.refreshOnlineState()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMinuteHistory = true) }
                repository.fetchDeviceMinuteDayViaRest(deviceId, dateKey)
                    .onSuccess { mins ->
                        rawCloudMinutesByDate[dateKey] = mins
                        recomputeMergedHistoryUiState(deviceId, _uiState.value.liveSnapshot?.events.orEmpty())
                        _uiState.update { it.copy(isLoadingMinuteHistory = false) }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isLoadingMinuteHistory = false) }
                    }
            }
        }
    }

    fun refreshHistory(silent: Boolean = false) {
        val deviceId = _uiState.value.activeDeviceId ?: return
        val selectedDate = _uiState.value.selectedHistoryDateKey
        if (!NetworkMonitor.refreshOnlineState()) {
            if (!silent) {
                showSnackbar("No internet connection. Showing saved 7-day records.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, isLoadingMinuteHistory = true) }
            val minuteRes = repository.fetchDeviceMinuteDayViaRest(deviceId, selectedDate)
            minuteRes.onSuccess { mins ->
                rawCloudMinutesByDate[selectedDate] = mins
            }
            val result = repository.fetchDeviceHistoryViaRest(deviceId)
            result.onSuccess { daysMap ->
                rawCloudHistoryByDate = daysMap
                recomputeMergedHistoryUiState(deviceId, _uiState.value.liveSnapshot?.events.orEmpty())
                _uiState.update { it.copy(isLoadingHistory = false, isLoadingMinuteHistory = false) }
                if (!silent) {
                    showSnackbar("7-day hourly & minute records synced.")
                }
            }.onFailure { err ->
                recomputeMergedHistoryUiState(deviceId, _uiState.value.liveSnapshot?.events.orEmpty())
                _uiState.update { it.copy(isLoadingHistory = false, isLoadingMinuteHistory = false) }
                if (!silent) {
                    showSnackbar("History refresh failed: ${err.localizedMessage ?: "Check connection"}")
                }
            }
        }
    }

    private fun startObservingDeviceHistory(deviceId: String, forceRefresh: Boolean = false) {
        val nowMs = System.currentTimeMillis()
        val window = IotDomainLogic.generateSevenDayWindowPht(nowMs, rawCloudHistoryByDate)
        val validKeys = window.map { it.dateKey }.toSet()
        val currentSelected = _uiState.value.selectedHistoryDateKey
        val safeSelected = if (currentSelected in validKeys) currentSelected else window.last().dateKey

        _uiState.update {
            it.copy(
                historyDaysWindow = window,
                selectedHistoryDateKey = safeSelected,
                isLoadingHistory = rawCloudHistoryByDate.isEmpty() || forceRefresh
            )
        }

        if (historyJob?.isActive != true) {
            historyJob = viewModelScope.launch {
                repository.observeDeviceHistory(deviceId).collect { daysMap ->
                    rawCloudHistoryByDate = daysMap
                    recomputeMergedHistoryUiState(deviceId, _uiState.value.liveSnapshot?.events.orEmpty())
                    _uiState.update { it.copy(isLoadingHistory = false) }
                }
            }
        }

        ensureObservingMinuteDay(deviceId, safeSelected, forceRefresh = forceRefresh)

        if (forceRefresh && NetworkMonitor.refreshOnlineState()) {
            refreshHistory(silent = true)
        }

        // Enforce 7-day retention pruning once per PHT day for Owners/Operators
        val todayKey = window.last().dateKey
        val cutoffKey = window.first().dateKey
        val pruneToken = "${deviceId}_$todayKey"
        if (_uiState.value.activeRole.canControl && lastPrunedHistoryKey != pruneToken) {
            lastPrunedHistoryKey = pruneToken
            viewModelScope.launch {
                repository.pruneExpiredHistoryDays(deviceId, cutoffKey)
            }
        }
    }

    private fun recomputeMergedHistoryUiState(deviceId: String, liveEvents: List<DeviceEvent>) {
        val nowMs = System.currentTimeMillis()
        val baseWindow = IotDomainLogic.generateSevenDayWindowPht(nowMs)
        val validKeys = baseWindow.map { it.dateKey }.toSet()

        // Prune out-of-window in-memory minute caches strictly to 7 PHT calendar days
        rawCloudMinutesByDate.keys.retainAll(validKeys)
        sessionLiveMinutesByDate.keys.retainAll(validKeys)

        val baseMerged = IotDomainLogic.mergeLegacyIrrigationEventsIntoHistory(
            deviceId = deviceId,
            historyByDate = rawCloudHistoryByDate.filterKeys { it in validKeys },
            events = liveEvents,
            validDateKeys = validKeys
        ).toMutableMap()

        // Merge ESP32 cloud minute records over read-only session fallback (ESP32 cloud ALWAYS wins)
        for (dateKey in validKeys) {
            val sessionMins = sessionLiveMinutesByDate[dateKey].orEmpty()
            val cloudMins = rawCloudMinutesByDate[dateKey].orEmpty()
            if (sessionMins.isNotEmpty() || cloudMins.isNotEmpty()) {
                val combinedMinutes = sessionMins + cloudMins
                val existingDay = baseMerged[dateKey]
                baseMerged[dateKey] = if (existingDay != null) {
                    existingDay.copy(minutes = combinedMinutes)
                } else {
                    DailyHistorySnapshot(
                        deviceId = deviceId,
                        dateKey = dateKey,
                        updatedAt = combinedMinutes.values.maxOfOrNull { it.lastSeenAt } ?: nowMs,
                        minutes = combinedMinutes
                    )
                }
            }
        }

        val updatedWindow = IotDomainLogic.generateSevenDayWindowPht(nowMs, baseMerged)
        _uiState.update { st ->
            val safeSelected = if (st.selectedHistoryDateKey in validKeys) {
                st.selectedHistoryDateKey
            } else {
                updatedWindow.last().dateKey
            }
            st.copy(
                historyDaysWindow = updatedWindow,
                selectedHistoryDateKey = safeSelected,
                historyByDate = baseMerged
            )
        }
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
                .onSuccess { showSnackbar("Watering output enabled! Your plant box is ready to water.") }
                .onFailure { showSnackbar("Failed to enable watering output: ${it.localizedMessage}") }
        }
    }

    /**
     * Fetches the latest server state directly via authenticated slim HTTPS REST,
     * reconnecting the Firebase RTDB WebSocket ONLY when genuinely stalled.
     * Available as a 1-tap Sync button on both Homepage and My Plants, and called on app resume.
     */
    fun resyncLiveDevice(silent: Boolean = false) {
        syncPendingRevocationsNow()
        val st = _uiState.value
        val deviceId = st.activeDeviceId ?: return
        if (st.step == MyPlantsStep.WIFI_AND_CLAIM || st.isProvisioning) return
        if (st.isSyncingDevice) return

        if (!NetworkMonitor.refreshOnlineState()) {
            if (!silent) {
                val snap = st.liveSnapshot
                val evalMs = NetworkMonitor.disconnectedAtMsFlow.value ?: System.currentTimeMillis()
                val wasBoxOnline = snap != null && !snap.isFromLocalCache && IotDomainLogic.isDeviceOnlineFresh(
                    status = snap.status,
                    nowMs = evalMs,
                    receivedAtClientMs = snap.receivedAtClientMs,
                    telemetryUpdatedAt = snap.telemetry.updatedAt
                )
                if (wasBoxOnline) {
                    showSnackbar("No internet connection. Please check your internet connection.")
                } else {
                    showSnackbar("No internet connection. ESP32 readings are offline.")
                }
            }
            return
        }

        viewModelScope.launch {
            val nowStart = System.currentTimeMillis()
            val wsQuietMs = if (lastRealtimeDeliveryAtMs > 0L) (nowStart - lastRealtimeDeliveryAtMs) else 0L
            val shouldReconnectSocket = lastRealtimeDeliveryAtMs > 0L &&
                wsQuietMs >= 22_000L &&
                (nowStart - lastSocketReconnectAtMs) >= 20_000L
            if (shouldReconnectSocket) {
                lastSocketReconnectAtMs = nowStart
            }

            _uiState.update { it.copy(isSyncingDevice = true) }
            // Ensure no lingering ESP32 SoftAP network binding blocks normal internet traffic
            provClient.disconnectFromEspHotspot()
            val refreshRes = repository.forceRefreshDevice(
                deviceId = deviceId,
                reconnectSocket = shouldReconnectSocket,
                existingSnapshot = st.liveSnapshot,
                pendingCommandId = st.pendingCommandId
            )
            observeLiveDevice(deviceId, forceRestart = shouldReconnectSocket)
            startLiveSyncWatchdog(deviceId)
            if (_uiState.value.historyGranularity == HistoryGranularity.BY_MINUTES ||
                _uiState.value.subPage == MyPlantsSubPage.HISTORY_MONITORING
            ) {
                ensureObservingMinuteDay(deviceId, _uiState.value.selectedHistoryDateKey, forceRefresh = true)
            }

            refreshRes
                .onSuccess { fresh ->
                    _uiState.update { it.copy(isSyncingDevice = false) }
                    val transport = currentTransportLabel()
                    Log.d(
                        "SpcSyncPerf",
                        "Manual/Resume sync [$transport] device=$deviceId silent=$silent reconnectWs=$shouldReconnectSocket latency=${fresh?.lastSyncLatencyMs ?: -1}ms"
                    )
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
                                "Cloud OK, but ESP32 last sent data $ageText — check box power or Wi-Fi."
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
        if (st.isSubmittingAction || st.pendingCommandPhase == PendingCommandPhase.SUBMITTING) {
            return
        }
        if (!NetworkMonitor.refreshOnlineState()) {
            showSnackbar("Blocked: No internet connection")
            return
        }
        if (snapshot.isFromLocalCache) {
            showSnackbar("Waiting for live ESP32 verification before sending watering command...")
            resyncLiveDevice(silent = true)
            return
        }
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
            _uiState.update {
                it.copy(
                    isSubmittingAction = true,
                    pendingWaterCrop = cropSlug,
                    pendingCommandPhase = PendingCommandPhase.SUBMITTING
                )
            }
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "WATER_CROP",
                requestedByUid = uid,
                ownershipEpoch = effectiveEpoch,
                cropSlug = cropSlug,
                durationMs = durationMs
            ).onSuccess { cmdId ->
                val prettyCrop = cropSlug.replaceFirstChar { it.uppercase() }
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        pendingCommandId = cmdId,
                        pendingWaterCrop = cropSlug,
                        pendingCommandPhase = PendingCommandPhase.SENT_WAITING_ACK,
                        snackbarMessage = "Submitted ${seconds}s watering for $prettyCrop — waiting for ESP32 acknowledgment..."
                    )
                }
                // Clear unacknowledged pending state after 18s if ESP32 never picked it up
                launch {
                    delay(18_000L)
                    _uiState.update { cur ->
                        if (cur.pendingCommandId == cmdId &&
                            cur.pendingCommandPhase == PendingCommandPhase.SENT_WAITING_ACK
                        ) {
                            cur.copy(
                                pendingCommandId = null,
                                pendingWaterCrop = null,
                                pendingCommandPhase = PendingCommandPhase.IDLE,
                                snackbarMessage = "ESP32 did not acknowledge the watering request in time. Check box Wi-Fi and tap Sync."
                            )
                        } else {
                            cur
                        }
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        pendingWaterCrop = null,
                        pendingCommandPhase = PendingCommandPhase.IDLE,
                        snackbarMessage = "Command submission failed: ${err.localizedMessage}"
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
            _uiState.update { it.copy(pendingCommandPhase = PendingCommandPhase.SUBMITTING) }
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "STOP_WATERING",
                requestedByUid = uid,
                ownershipEpoch = effectiveEpoch
            ).onSuccess { cmdId ->
                _uiState.update {
                    it.copy(
                        pendingCommandId = cmdId,
                        pendingWaterCrop = null,
                        pendingCommandPhase = PendingCommandPhase.SENT_WAITING_ACK,
                        snackbarMessage = "Emergency Stop submitted — waiting for ESP32 acknowledgment..."
                    )
                }
            }.onFailure {
                _uiState.update { cur -> cur.copy(pendingCommandPhase = PendingCommandPhase.IDLE) }
                showSnackbar("Stop command failed: ${it.localizedMessage}")
            }
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
            _uiState.update { it.copy(pendingCommandPhase = PendingCommandPhase.SUBMITTING) }
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "CLEAR_EMERGENCY",
                requestedByUid = uid,
                ownershipEpoch = effectiveEpoch
            ).onSuccess { cmdId ->
                _uiState.update {
                    it.copy(
                        pendingCommandId = cmdId,
                        pendingCommandPhase = PendingCommandPhase.SENT_WAITING_ACK,
                        snackbarMessage = "Clear Fault command submitted."
                    )
                }
            }.onFailure {
                _uiState.update { cur -> cur.copy(pendingCommandPhase = PendingCommandPhase.IDLE) }
                showSnackbar("Failed: ${it.localizedMessage}")
            }
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
                startObservingDeviceHistory(joinedDeviceId, forceRefresh = false)
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

    fun uploadCropPhoto(cropSlug: String, sourceUri: Uri) {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canControl) {
            showSnackbar("Viewers have read-only access and cannot upload crop photos.")
            return
        }
        if (!NetworkMonitor.refreshOnlineState()) {
            showSnackbar("No internet connection. Connect online to upload crop photo.")
            return
        }
        if (!CloudinaryCropPhotoClient.isConfigured()) {
            showSnackbar("Cloudinary is not configured yet.")
            return
        }
        if (st.uploadingPhotoCropSlug != null) return

        val normalizedSlug = cropSlug.lowercase().trim()
        val cropName = snapshot.telemetry.zones.firstOrNull { it.slug == normalizedSlug }?.displayName
            ?: normalizedSlug.replaceFirstChar { it.uppercase() }
        val previousPublicId = snapshot.cropPhotos[normalizedSlug]?.publicId.orEmpty()
        val uploaderName = boundName.ifBlank { boundEmail.substringBefore("@").ifBlank { "Member" } }

        viewModelScope.launch {
            _uiState.update { it.copy(uploadingPhotoCropSlug = normalizedSlug) }

            val uploadRes = CloudinaryCropPhotoClient.uploadCropPhoto(
                context = appCtx,
                deviceId = snapshot.deviceId,
                cropSlug = normalizedSlug,
                sourceUri = sourceUri
            )
            if (uploadRes.isFailure) {
                _uiState.update { it.copy(uploadingPhotoCropSlug = null) }
                showSnackbar("Upload failed: ${uploadRes.exceptionOrNull()?.localizedMessage ?: "Try again."}")
                return@launch
            }

            val uploaded = uploadRes.getOrThrow()
            val photoMeta = CropZonePhoto(
                url = uploaded.secureUrl,
                publicId = uploaded.publicId,
                updatedAt = System.currentTimeMillis(),
                updatedByUid = uid,
                updatedByName = uploaderName
            )

            val saveRes = repository.saveCropPhotoMetadata(snapshot.deviceId, normalizedSlug, photoMeta)
            if (saveRes.isFailure) {
                // Clean up newly uploaded Cloudinary asset so zero orphan files remain
                CloudinaryCropPhotoClient.deleteCropPhoto(uploaded.publicId)
                _uiState.update { it.copy(uploadingPhotoCropSlug = null) }
                showSnackbar("Could not save photo: ${saveRes.exceptionOrNull()?.localizedMessage ?: "Permission denied."}")
                return@launch
            }

            // Optimistically update current snapshot so UI updates instantly
            _uiState.update { cur ->
                val curSnap = cur.liveSnapshot
                cur.copy(
                    uploadingPhotoCropSlug = null,
                    liveSnapshot = if (curSnap != null && curSnap.deviceId == snapshot.deviceId) {
                        curSnap.copy(cropPhotos = curSnap.cropPhotos + (normalizedSlug to photoMeta))
                    } else curSnap
                )
            }

            // Permanently remove the replaced asset from Cloudinary
            if (previousPublicId.isNotBlank() && previousPublicId != uploaded.publicId) {
                CloudinaryCropPhotoClient.deleteCropPhoto(previousPublicId)
            }

            showSnackbar("$cropName photo updated.")
        }
    }

    fun deleteCropPhoto(cropSlug: String) {
        val st = _uiState.value
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canControl) {
            showSnackbar("Viewers have read-only access and cannot remove crop photos.")
            return
        }
        if (!NetworkMonitor.refreshOnlineState()) {
            showSnackbar("No internet connection. Connect online to remove crop photo.")
            return
        }
        if (st.uploadingPhotoCropSlug != null) return

        val normalizedSlug = cropSlug.lowercase().trim()
        val existingPhoto = snapshot.cropPhotos[normalizedSlug] ?: return
        val cropName = snapshot.telemetry.zones.firstOrNull { it.slug == normalizedSlug }?.displayName
            ?: normalizedSlug.replaceFirstChar { it.uppercase() }

        viewModelScope.launch {
            _uiState.update { it.copy(uploadingPhotoCropSlug = normalizedSlug) }

            val cloudDelRes = if (existingPhoto.publicId.isNotBlank()) {
                CloudinaryCropPhotoClient.deleteCropPhoto(existingPhoto.publicId)
            } else {
                Result.success(Unit)
            }

            if (cloudDelRes.isFailure) {
                _uiState.update { it.copy(uploadingPhotoCropSlug = null) }
                showSnackbar("Could not delete photo from Cloudinary: ${cloudDelRes.exceptionOrNull()?.localizedMessage ?: "Try again."}")
                return@launch
            }

            val dbDelRes = repository.deleteCropPhotoMetadata(snapshot.deviceId, normalizedSlug)
            if (dbDelRes.isFailure) {
                _uiState.update { it.copy(uploadingPhotoCropSlug = null) }
                showSnackbar("Could not remove photo link: ${dbDelRes.exceptionOrNull()?.localizedMessage ?: "Permission denied."}")
                return@launch
            }

            _uiState.update { cur ->
                val curSnap = cur.liveSnapshot
                cur.copy(
                    uploadingPhotoCropSlug = null,
                    liveSnapshot = if (curSnap != null && curSnap.deviceId == snapshot.deviceId) {
                        curSnap.copy(cropPhotos = curSnap.cropPhotos - normalizedSlug)
                    } else curSnap
                )
            }
            showSnackbar("$cropName photo removed.")
        }
    }

    fun unclaimAndResetCurrentDevice() {
        val st = _uiState.value
        val uid = boundUid ?: return
        val snapshot = st.liveSnapshot ?: return
        if (!st.activeRole.canUnclaim) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true) }
            // Best-effort cleanup of shared crop photos from Cloudinary before wiping device metadata
            snapshot.cropPhotos.values.forEach { photo ->
                if (photo.publicId.isNotBlank()) {
                    CloudinaryCropPhotoClient.deleteCropPhoto(photo.publicId)
                }
            }
            repository.unclaimAndResetDevice(
                deviceId = snapshot.deviceId,
                currentEpoch = snapshot.meta.ownershipEpoch,
                memberUids = snapshot.members.map { it.uid },
                ownerUid = uid
            ).onSuccess {
                clearCachedDevice(uid, snapshot.deviceId)
                minuteHistoryJob?.cancel()
                observedMinuteKeyToken = null
                rawCloudMinutesByDate.clear()
                sessionLiveMinutesByDate.clear()
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        subPage = MyPlantsSubPage.NONE,
                        step = MyPlantsStep.SCAN_ESP32,
                        liveSnapshot = null,
                        pendingCommandId = null,
                        pendingWaterCrop = null,
                        pendingCommandPhase = PendingCommandPhase.IDLE,
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
        userDevicesJob?.cancel()
        liveDeviceJob?.cancel()
        liveWatchdogJob?.cancel()
        historyJob?.cancel()
        minuteHistoryJob?.cancel()
        provClient.disconnectFromEspHotspot()
        super.onCleared()
    }
}
