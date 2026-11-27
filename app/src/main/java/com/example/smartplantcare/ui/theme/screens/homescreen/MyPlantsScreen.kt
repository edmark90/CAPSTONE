package com.example.smartplantcare.ui.theme.screens.homescreen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartplantcare.iot.EspScanBlocker
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.MyPlantsStep
import com.example.smartplantcare.iot.MyPlantsSubPage
import com.example.smartplantcare.iot.MyPlantsViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun MyPlantsScreen(
    onBackClick: () -> Unit = {},
    onStartScanning: () -> Unit = {},
    isScanning: Boolean = false,
    isAuthenticated: Boolean = true,
    isGuest: Boolean = false,
    userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
    userEmail: String = "",
    userName: String = "",
    onNavigateToLogin: () -> Unit = {},
    sharedViewModel: MyPlantsViewModel? = null
) {
    val context = LocalContext.current
    val vm: MyPlantsViewModel = sharedViewModel ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MyPlantsViewModel(context.applicationContext) as T
            }
        }
    )

    val uiState by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Automatically re-attach Firebase stream & fetch fresh ESP32 heartbeat whenever user returns to app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.resyncLiveDevice(silent = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(userId, userEmail, userName, isGuest, isAuthenticated) {
        val effectiveUid = if (isAuthenticated && !isGuest) {
            userId ?: FirebaseAuth.getInstance().currentUser?.uid
        } else {
            null
        }
        vm.bindUser(effectiveUid, userEmail, userName, isGuest)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearSnackbar()
        }
    }

    // Android 12+ (targetSdk >= 31) requires ACCESS_COARSE_LOCATION to be requested
    // together with ACCESS_FINE_LOCATION in the same runtime array; otherwise the OS
    // ignores and auto-denies ACCESS_FINE_LOCATION, breaking WifiManager.scanResults.
    val requiredScanPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            onStartScanning()
            vm.startScanningNearbyEsp()
        } else {
            vm.reportScanBlocker(EspScanBlocker.LOCATION_PERMISSION_MISSING)
        }
    }

    val ownerGpsPermissions = remember {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val ownerLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        vm.onOwnerLocationPermissionResult(granted)
    }

    // Strict OWNER-ONLY mobile GPS prompt: shared OPERATOR / VIEWER users are NEVER asked for location.
    LaunchedEffect(uiState.step, uiState.shouldPromptOwnerLocation, uiState.activeRole) {
        if (uiState.step == MyPlantsStep.LIVE_DASHBOARD &&
            uiState.shouldPromptOwnerLocation &&
            IotDomainLogic.canRequestDeviceLocationPermission(uiState.activeRole)
        ) {
            vm.consumeOwnerLocationPrompt()
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                vm.refreshWeatherWithOwnerGps(silent = true)
            } else {
                ownerLocationPermissionLauncher.launch(ownerGpsPermissions)
            }
        }
    }

    fun requestWeatherOrOwnerGpsRefresh() {
        if (IotDomainLogic.canRequestDeviceLocationPermission(uiState.activeRole)) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                vm.refreshWeatherWithOwnerGps(silent = false)
            } else {
                ownerLocationPermissionLauncher.launch(ownerGpsPermissions)
            }
        } else {
            // Shared Operator / Viewer: refresh forecast using Owner's saved coordinates (no GPS prompt!)
            vm.refreshWeatherForSavedBoxLocation(silent = false)
        }
    }

    fun requestScanWithPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (fineGranted && nearbyGranted) {
            onStartScanning()
            vm.startScanningNearbyEsp()
        } else {
            permissionLauncher.launch(requiredScanPermissions)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
    ) {
        when {
            (uiState.isLoadingInitialDevices ||
                (uiState.step == MyPlantsStep.LIVE_DASHBOARD && uiState.liveSnapshot == null)) &&
                isAuthenticated && !isGuest -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HomeColors.ForestPrimary)
                }
            }

            uiState.step == MyPlantsStep.WIFI_AND_CLAIM -> {
                MyPlantsWifiSetupScreen(
                    uiState = uiState,
                    onBack = { vm.cancelWifiSetupAndReturnToScan() },
                    onRefreshWifi = { vm.refresh24GhzWifiList() },
                    onSelectWifi = { vm.selectWifiSsid(it) },
                    onToggleManualWifi = { vm.toggleManualWifiMode(it) },
                    onManualWifiChange = { vm.updateManualWifiSsid(it) },
                    onPasswordChange = { vm.updateWifiPassword(it) },
                    onBoxNameChange = { vm.updateCustomBoxName(it) },
                    onClaimCodeChange = { vm.updateClaimCode(it) },
                    onSubmitProvisioning = { vm.submitWifiAndClaimDevice() }
                )
            }

            uiState.step == MyPlantsStep.LIVE_DASHBOARD && uiState.liveSnapshot != null -> {
                val snapshot = uiState.liveSnapshot!!
                when (uiState.subPage) {
                    MyPlantsSubPage.SHARE_PLANT -> {
                        MyPlantsShareScreen(
                            snapshot = snapshot,
                            currentUserUid = userId ?: FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                            currentUserRole = uiState.activeRole,
                            latestInvite = uiState.latestGeneratedInvite,
                            isBusy = uiState.isSubmittingAction,
                            onBack = { vm.closeSubPage() },
                            onGenerateInvite = { vm.createShareInvite(it) },
                            onRevokeMember = { vm.revokeMember(it) }
                        )
                    }

                    MyPlantsSubPage.EVENT_LOG -> {
                        MyPlantsEventLogScreen(
                            snapshot = snapshot,
                            onBack = { vm.closeSubPage() },
                            onOpenHistory = { vm.openSubPage(MyPlantsSubPage.HISTORY_MONITORING) }
                        )
                    }

                    MyPlantsSubPage.HISTORY_MONITORING -> {
                        MyPlantsHistoryScreen(
                            snapshot = snapshot,
                            historyDaysWindow = uiState.historyDaysWindow,
                            selectedDateKey = uiState.selectedHistoryDateKey,
                            historyByDate = uiState.historyByDate,
                            historyGranularity = uiState.historyGranularity,
                            selectedMinuteHourFilter = uiState.selectedMinuteHourFilter,
                            isLoadingHistory = uiState.isLoadingHistory || uiState.isLoadingMinuteHistory,
                            isPhoneOnline = uiState.isPhoneOnline,
                            onSelectDate = { vm.selectHistoryDate(it) },
                            onSelectGranularity = { vm.setHistoryGranularity(it) },
                            onSelectMinuteHour = { vm.selectMinuteHourFilter(it) },
                            onRefresh = { vm.refreshHistory(silent = false) },
                            onBack = { vm.closeSubPage() }
                        )
                    }

                    MyPlantsSubPage.DEVICE_SETTINGS -> {
                        MyPlantsSettingsScreen(
                            snapshot = snapshot,
                            role = uiState.activeRole,
                            isBusy = uiState.isSubmittingAction,
                            onBack = { vm.closeSubPage() },
                            onRenameBox = { vm.renameBox(it) },
                            onSaveConfig = { vm.saveDeviceConfig(it) },
                            onUnclaimDevice = { vm.unclaimAndResetCurrentDevice() }
                        )
                    }

                    MyPlantsSubPage.NONE -> {
                        MyPlantsDashboardScreen(
                            uiState = uiState,
                            onSelectDevice = { vm.selectActiveDevice(it) },
                            onAddAnotherDevice = { vm.openAddAnotherDeviceFlow() },
                            onOpenSubPage = { vm.openSubPage(it) },
                            onSwitchMode = { vm.switchMode(it) },
                            onSwitchAutoIrrigation = { vm.switchAutoIrrigation(it) },
                            onSelectPulseSec = { crop, sec -> vm.setPulseSecondsForCrop(crop, sec) },
                            onManualWater = { vm.triggerManualWater(it) },
                            onEmergencyStop = { vm.triggerEmergencyStop() },
                            onClearEmergency = { vm.clearEmergencyFault() },
                            onMuteBuzzer = { vm.muteBuzzerCommand() },
                            onRefreshWeather = { requestWeatherOrOwnerGpsRefresh() },
                            onEnablePhysicalLoads = { vm.enablePhysicalRelayLoads() },
                            onResyncDevice = { vm.resyncLiveDevice(silent = false) },
                            onUploadCropPhoto = { slug, uri -> vm.uploadCropPhoto(slug, uri) },
                            onDeleteCropPhoto = { slug -> vm.deleteCropPhoto(slug) }
                        )
                    }
                }
            }

            else -> {
                MyPlantsScanStepScreen(
                    uiState = uiState,
                    isAuthenticated = isAuthenticated,
                    isGuest = isGuest,
                    hasExistingDevices = uiState.userDevices.isNotEmpty(),
                    onStartScanRequest = { requestScanWithPermissions() },
                    onStopScan = { vm.stopScanningNearbyEsp() },
                    onConnectDevice = { vm.connectToNearbyEsp(it) },
                    onVerifyManualConnection = { vm.verifyManualHotspotConnection() },
                    onJoinInviteSubmit = { code, onDone -> vm.redeemInviteCode(code, onDone) },
                    onBackToDashboard = { vm.returnToLiveDashboard() },
                    onNavigateToLogin = onNavigateToLogin
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .padding(bottom = HomeDimens.BottomBarClearance)
        )
    }
}
