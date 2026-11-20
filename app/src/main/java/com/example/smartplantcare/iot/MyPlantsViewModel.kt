package com.example.smartplantcare.iot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isSubmittingAction: Boolean = false,

    // Page 4: Share My Plant
    val latestGeneratedInvite: ShareInvite? = null,

    // Feedback
    val snackbarMessage: String? = null
)

class MyPlantsViewModel(
    appContext: Context,
    private val repository: IotFirebaseRepository = IotFirebaseRepository(),
    private val provClient: Esp32ProvisioningClient = Esp32ProvisioningClient(appContext.applicationContext)
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPlantsUiState())
    val uiState: StateFlow<MyPlantsUiState> = _uiState.asStateFlow()

    private var boundUid: String? = null
    private var boundEmail: String = ""
    private var boundName: String = ""
    private var userDevicesJob: Job? = null
    private var liveDeviceJob: Job? = null
    private var espScanJob: Job? = null
    private var lastReportedCmdId: String? = null

    fun bindUser(uid: String?, email: String, displayName: String, isGuest: Boolean) {
        if (isGuest || uid.isNullOrBlank()) {
            userDevicesJob?.cancel()
            liveDeviceJob?.cancel()
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
                } else {
                    liveDeviceJob?.cancel()
                }
            }
        }
    }

    private fun observeLiveDevice(deviceId: String) {
        if (liveDeviceJob?.isActive == true && _uiState.value.liveSnapshot?.deviceId == deviceId) {
            return
        }
        liveDeviceJob?.cancel()
        liveDeviceJob = viewModelScope.launch {
            repository.observeDevice(deviceId).collect { snapshot ->
                _uiState.update { st ->
                    var nextSnackbar = st.snackbarMessage
                    var nextPendingCmd = st.pendingCommandId
                    val trackedId = st.pendingCommandId
                    if (trackedId != null && snapshot != null) {
                        val res = snapshot.commandResults[trackedId]
                        if (res != null && lastReportedCmdId != "${trackedId}_${res.state}") {
                            lastReportedCmdId = "${trackedId}_${res.state}"
                            nextSnackbar = "${res.state}: ${res.reason}"
                            if (res.state in setOf("DONE", "REJECTED", "ABORTED")) {
                                nextPendingCmd = null
                            }
                        }
                    }

                    // Keep role synchronized with /acl/members
                    val uid = boundUid
                    val aclRole = snapshot?.members?.firstOrNull { it.uid == uid }?.role
                        ?: st.activeRole

                    st.copy(
                        liveSnapshot = snapshot,
                        activeRole = aclRole,
                        pendingCommandId = nextPendingCmd,
                        snackbarMessage = nextSnackbar
                    )
                }
            }
        }
    }

    // ─── STEP 1: Nearby ESP32 Scan & Connect (2-Minute Active Radar Scan) ───────

    fun startScanningNearbyEsp() {
        espScanJob?.cancel()
        espScanJob = viewModelScope.launch {
            val maxScanDurationMs = 120_000L  // 2 minutes timeout
            val startedAtMs = System.currentTimeMillis()

            _uiState.update {
                it.copy(
                    isScanningEsp = true,
                    scanElapsedSeconds = 0,
                    scanTimedOutNoDevice = false,
                    nearbyEspDevices = emptyList(),
                    scanStatusMessage = null
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
                    val found = provClient.scanNearbyEspDevices()
                    if (found.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                isScanningEsp = false,
                                scanTimedOutNoDevice = false,
                                nearbyEspDevices = found,
                                scanStatusMessage = null
                            )
                        }
                        return@launch
                    }

                    val remainingMs = maxScanDurationMs - (System.currentTimeMillis() - startedAtMs)
                    if (remainingMs <= 0L) break
                    delay(minOf(4000L, remainingMs))
                }

                // 2 minutes elapsed with zero active ESP32 found
                _uiState.update {
                    it.copy(
                        isScanningEsp = false,
                        scanElapsedSeconds = 120,
                        scanTimedOutNoDevice = true,
                        nearbyEspDevices = emptyList(),
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
                scanTimedOutNoDevice = false
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
        _uiState.update { it.copy(selectedWifiSsid = ssid, provisioningError = null) }
    }

    fun updateWifiPassword(password: String) {
        _uiState.update { it.copy(wifiPassword = password, provisioningError = null) }
    }

    fun updateCustomBoxName(name: String) {
        _uiState.update { it.copy(customBoxName = name.take(35), provisioningError = null) }
    }

    fun updateClaimCode(code: String) {
        _uiState.update { it.copy(claimCode = code, provisioningError = null) }
    }

    fun cancelWifiSetupAndReturnToScan() {
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
        val ssid = state.selectedWifiSsid.trim()
        if (ssid.isEmpty()) {
            _uiState.update { it.copy(provisioningError = "Please select or enter a 2.4 GHz Wi-Fi network.") }
            return
        }
        val claim = state.claimCode.trim()
        if (claim.isEmpty()) {
            _uiState.update { it.copy(provisioningError = "Please enter the physical Claim Code (e.g. SPC-CLAIM-0001).") }
            return
        }
        val boxName = state.customBoxName.trim().ifEmpty { "SmartPlantCare Box" }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProvisioning = true,
                    provisioningError = null,
                    provisioningProgressMessage = "1/3 Sending Wi-Fi & \"$boxName\" to ESP32 NVS..."
                )
            }

            val nextEpoch = (espInfo.ownershipEpoch.coerceAtLeast(0L)) + 1L
            val provResult = provClient.sendProvisioningPayload(
                wifiSsid = ssid,
                wifiPassword = state.wifiPassword,
                claimCode = claim,
                displayName = boxName,
                ownerUid = uid,
                nextOwnershipEpoch = nextEpoch
            )

            if (!provResult.success) {
                _uiState.update {
                    it.copy(
                        isProvisioning = false,
                        provisioningProgressMessage = null,
                        provisioningError = provResult.errorMessage
                            ?: "ESP32 rejected provisioning. Check your Claim Code and Wi-Fi."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(provisioningProgressMessage = "2/3 ESP32 saved Wi-Fi! Reconnecting phone to internet...")
            }
            provClient.disconnectFromEspHotspot()
            delay(2800L)

            _uiState.update {
                it.copy(provisioningProgressMessage = "3/3 Binding \"$boxName\" to your owner account in Firebase...")
            }

            var claimSuccess = false
            var lastError: String? = null
            // Retry up to 3 times while phone transitions from ESP32 SoftAP back to Wi-Fi/Cellular
            for (attempt in 1..3) {
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
                    delay(2000L)
                }
            }

            if (!claimSuccess) {
                _uiState.update {
                    it.copy(
                        isProvisioning = false,
                        provisioningProgressMessage = null,
                        provisioningError = "ESP32 received Wi-Fi, but Firebase sync failed (${lastError ?: "No internet"}). Reconnect to internet and tap Retry."
                    )
                }
                return@launch
            }

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
            observeLiveDevice(finalDeviceId)
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
                .onSuccess { showSnackbar("Mode switched to $mode.") }
                .onFailure { showSnackbar("Failed to switch mode: ${it.localizedMessage}") }
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

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true) }
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "WATER_CROP",
                requestedByUid = uid,
                ownershipEpoch = snapshot.meta.ownershipEpoch,
                cropSlug = cropSlug,
                durationMs = durationMs
            ).onSuccess { cmdId ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
                        pendingCommandId = cmdId,
                        snackbarMessage = "Sent ${seconds}s watering command for ${cropSlug.uppercase()}..."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingAction = false,
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

        viewModelScope.launch {
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "STOP_WATERING",
                requestedByUid = uid,
                ownershipEpoch = snapshot.meta.ownershipEpoch
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

        viewModelScope.launch {
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "CLEAR_EMERGENCY",
                requestedByUid = uid,
                ownershipEpoch = snapshot.meta.ownershipEpoch
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

        viewModelScope.launch {
            repository.sendCommand(
                deviceId = snapshot.deviceId,
                type = "MUTE_BUZZER",
                requestedByUid = uid,
                ownershipEpoch = snapshot.meta.ownershipEpoch
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
        provClient.disconnectFromEspHotspot()
        super.onCleared()
    }
}
