package com.example.smartplantcare.ui.theme.screens.homescreen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.iot.CropZoneConfig
import com.example.smartplantcare.iot.CropZonePhoto
import com.example.smartplantcare.iot.DeviceRole
import com.example.smartplantcare.iot.DeviceStatus
import com.example.smartplantcare.iot.DeviceTelemetry
import com.example.smartplantcare.iot.DeviceWeather
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.LiveDeviceSnapshot
import com.example.smartplantcare.iot.MyPlantsSubPage
import com.example.smartplantcare.iot.MyPlantsUiState
import com.example.smartplantcare.iot.PendingCommandPhase
import com.example.smartplantcare.iot.SoilZoneTelemetry
import com.example.smartplantcare.util.NetworkMonitor
import kotlinx.coroutines.delay
import java.util.Locale

// ─── PAGE 3: Flagship Smart Botanical IoT Dashboard ──────────────────────────

@Composable
internal fun MyPlantsDashboardScreen(
    uiState: MyPlantsUiState,
    onSelectDevice: (String) -> Unit,
    onAddAnotherDevice: () -> Unit,
    onOpenSubPage: (MyPlantsSubPage) -> Unit,
    onSwitchMode: (String) -> Unit,
    onSwitchAutoIrrigation: (Boolean) -> Unit,
    onSelectPulseSec: (String, Int) -> Unit,
    onManualWater: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onClearEmergency: () -> Unit,
    onMuteBuzzer: () -> Unit,
    onRefreshWeather: () -> Unit = {},
    onEnablePhysicalLoads: () -> Unit = {},
    onResyncDevice: () -> Unit = {},
    onUploadCropPhoto: (String, Uri) -> Unit = { _, _ -> },
    onDeleteCropPhoto: (String) -> Unit = {}
) {
    val snapshot = uiState.liveSnapshot
    val role = uiState.activeRole

    if (snapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HomeColors.PageBg)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = HomeColors.ForestPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Syncing with your SmartPlantCare box...",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeColors.TextSecondary
                )
            }
        }
        return
    }

    val status = snapshot.status
    val telemetry = snapshot.telemetry
    val weather = snapshot.weather
    val config = snapshot.config
    val isOutdoor = config.mode.equals("OUTDOOR", ignoreCase = true)
    val isAutoIrrigation = config.autoIrrigationEnabled
    val hasCriticalAlert = snapshot.events.take(5).any { it.severity.equals("critical", true) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(
        snapshot.deviceId,
        snapshot.receivedAtClientMs,
        snapshot.status.lastSeenAt,
        snapshot.telemetry.updatedAt
    ) {
        NetworkMonitor.refreshOnlineState()
        nowMs = System.currentTimeMillis()
        while (true) {
            delay(2_000L)
            NetworkMonitor.refreshOnlineState()
            nowMs = System.currentTimeMillis()
        }
    }
    val isPhoneOnline = uiState.isPhoneOnline
    val isCachedSnapshot = snapshot.isFromLocalCache
    val evalNowMs = if (!isPhoneOnline && uiState.phoneDisconnectedAtMs != null) {
        uiState.phoneDisconnectedAtMs
    } else {
        nowMs
    }
    val isDeviceOnline = !isCachedSnapshot && IotDomainLogic.isDeviceOnlineFresh(
        status = status,
        nowMs = evalNowMs,
        receivedAtClientMs = snapshot.receivedAtClientMs,
        telemetryUpdatedAt = telemetry.updatedAt
    )

    val canManageCropPhotos = role.canControl
    var pendingPhotoCropSlug by remember { mutableStateOf<String?>(null) }
    var previewPhotoZone by remember { mutableStateOf<SoilZoneTelemetry?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        val slug = pendingPhotoCropSlug
        pendingPhotoCropSlug = null
        if (pickedUri != null && slug != null && canManageCropPhotos) {
            onUploadCropPhoto(slug, pickedUri)
        }
    }

    fun launchCropPhotoPicker(slug: String) {
        if (!canManageCropPhotos) return
        pendingPhotoCropSlug = slug
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
    ) {
        // Subtle Top & Bottom Organic Botanical Wave Accents (matching reference image)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = HomeColors.SoftMintStrong.copy(alpha = 0.55f),
                radius = size.width * 0.28f,
                center = Offset(size.width * 0.02f, -size.width * 0.12f)
            )
            drawCircle(
                color = HomeColors.SoftMint.copy(alpha = 0.7f),
                radius = size.width * 0.24f,
                center = Offset(size.width * 0.98f, size.height * 0.04f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = HomeDimens.BottomBarClearance + 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Minimal Editorial Top App Bar
            item(key = "top_bar") {
                DashboardTopBar(
                    hasCriticalAlert = hasCriticalAlert,
                    isDeviceOnline = isDeviceOnline && isPhoneOnline,
                    onShareClick = { onOpenSubPage(MyPlantsSubPage.SHARE_PLANT) },
                    onLogClick = { onOpenSubPage(MyPlantsSubPage.EVENT_LOG) },
                    onSettingsClick = { onOpenSubPage(MyPlantsSubPage.DEVICE_SETTINGS) },
                    onAddBoxClick = onAddAnotherDevice
                )
            }

            // 2. Multi-Box Switcher Pills (Only shown if user has 2+ boxes)
            if (uiState.userDevices.size > 1) {
                item(key = "device_switcher") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.userDevices, key = { it.deviceId }) { link ->
                            val selected = link.deviceId == uiState.activeDeviceId
                            Surface(
                                color = if (selected) HomeColors.ForestPrimary else HomeColors.SurfaceWhite,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) HomeColors.ForestPrimary else HomeColors.CardBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onSelectDevice(link.deviceId) }
                            ) {
                                Text(
                                    text = link.deviceId,
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else HomeColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Botanical Hero Card
            item(key = "hero_card") {
                BotanicalHeroCard(
                    snapshot = snapshot,
                    role = role,
                    isDeviceOnline = isDeviceOnline,
                    isPhoneOnline = isPhoneOnline,
                    nowMs = evalNowMs,
                    isOutdoor = isOutdoor,
                    isAutoIrrigation = isAutoIrrigation,
                    isSyncing = uiState.isSyncingDevice,
                    onResyncClick = onResyncDevice,
                    onLocationRefreshClick = onRefreshWeather
                )
            }

            // 4. Priority Live Status Banners (Offline / No Internet — never "Cached")
            if (!isPhoneOnline) {
                item(key = "banner_no_internet") {
                    InlineStatusBanner(
                        bg = HomeColors.AlertBg,
                        accent = HomeColors.AlertRed,
                        icon = Icons.Filled.WifiOff,
                        title = "No Internet Connection",
                        subtitle = "Check your internet connection and tap Sync to load live ESP32 readings."
                    )
                }
            } else if (!isDeviceOnline) {
                item(key = "banner_offline") {
                    InlineStatusBanner(
                        bg = HomeColors.AlertBg,
                        accent = HomeColors.AlertRed,
                        icon = Icons.Filled.CloudOff,
                        title = "ESP32 Offline",
                        subtitle = "Sensors and weather readings are paused (--). Check box power or Wi-Fi and tap Sync."
                    )
                }
            }

            if (isDeviceOnline && isPhoneOnline) {
                if (!status.physicalLoadsEnabled && role.canControl) {
                    item(key = "banner_bench_safe") {
                        Surface(
                            color = HomeColors.WarningBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, HomeColors.WarningAmber.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Watering Output Paused",
                                        fontFamily = HomeInterFont,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HomeColors.TextPrimary
                                    )
                                    Text(
                                        text = "Turn on watering output so your plant box can start delivering water.",
                                        fontFamily = HomeInterFont,
                                        fontSize = 11.sp,
                                        color = HomeColors.TextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = onEnablePhysicalLoads,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HomeColors.ForestPrimary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Enable",
                                        fontFamily = HomeInterFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                if (status.emergencyStop || status.faultLockout) {
                    item(key = "banner_estop") {
                        EmergencyLockoutCard(
                            abortReason = status.lastAbortReason,
                            canControl = role.canControl,
                            onClearFault = onClearEmergency,
                            onMuteBuzzer = onMuteBuzzer
                        )
                    }
                }

                if (status.rainSuspensionActive) {
                    item(key = "banner_rain_pause") {
                        InlineStatusBanner(
                            bg = HomeColors.WaterBg,
                            accent = HomeColors.WaterBlue,
                            icon = Icons.Filled.Cloud,
                            title = "Auto-Watering Paused • Rain Confirmed",
                            subtitle = "Outdoor ≥${config.rainThresholdPct}% rain & wet plate confirmed. Resumes once dry."
                        )
                    }
                } else if (status.weatherAdvisoryActive) {
                    item(key = "banner_weather_advisory") {
                        InlineStatusBanner(
                            bg = HomeColors.WarningBg,
                            accent = HomeColors.WarningAmber,
                            icon = Icons.Filled.Warning,
                            title = "Rain Risk Advisory (≥${config.rainThresholdPct}%)",
                            subtitle = "Rain sensor is still dry — automatic watering remains ready."
                        )
                    }
                } else if (status.weatherUnavailableFault && isOutdoor) {
                    item(key = "banner_weather_fault") {
                        InlineStatusBanner(
                            bg = HomeColors.WarningBg,
                            accent = HomeColors.WarningAmber,
                            icon = Icons.Filled.CloudOff,
                            title = "Weather Sync Delayed",
                            subtitle = "Waiting for fresh Open-Meteo data before outdoor auto-watering."
                        )
                    }
                }
            }

            // 5. Side-by-Side 2-Column Horizontal Smart Control Tiles (matching reference image)
            item(key = "smart_controls_bento") {
                SmartControlTilesRow(
                    isAutoIrrigation = isAutoIrrigation,
                    isOutdoor = isOutdoor,
                    canControl = role.canControl && isDeviceOnline && isPhoneOnline,
                    onToggleAutoIrrigation = { onSwitchAutoIrrigation(!isAutoIrrigation) },
                    onToggleOutdoor = { onSwitchMode(if (isOutdoor) "INDOOR" else "OUTDOOR") }
                )
            }

            // 6. Crop Zones Section (Header + Sleek Horizontal Zone Cards matching image)
            item(key = "crops_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleRow(
                        title = "Crop Zones",
                        trailingIcon = if (isAutoIrrigation) Icons.Filled.AutoAwesome else Icons.Filled.TouchApp,
                        trailingLabel = when {
                            !isDeviceOnline -> "Offline"
                            !isPhoneOnline -> "No Internet"
                            isAutoIrrigation -> "Auto Active"
                            else -> "Manual Mode"
                        },
                        trailingIconTint = HomeColors.ForestPrimary,
                        trailingFg = HomeColors.TextPrimary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        telemetry.zones.forEach { zone ->
                            val cropCfg = config.cropConfig(zone.slug)
                            val baseLockReason = IotDomainLogic.manualWaterLockReason(role, snapshot, zone.slug)
                            val effectiveLockReason = when {
                                !isPhoneOnline -> "No internet connection"
                                !isDeviceOnline -> "ESP32 offline"
                                else -> baseLockReason
                            }
                            val isZoneWatering = isDeviceOnline && status.isActivelyIrrigating && status.activeValve == zone.slug
                            val isZoneSoaking = isDeviceOnline && status.isInSoakSettle && status.activeValve == zone.slug
                            val isZoneStarting = isDeviceOnline && !status.isActivelyIrrigating && uiState.pendingWaterCrop == zone.slug
                            val zoneCommandPhase = if (uiState.pendingWaterCrop == zone.slug) {
                                uiState.pendingCommandPhase
                            } else {
                                PendingCommandPhase.IDLE
                            }

                            val zonePhoto = snapshot.cropPhotos[zone.slug]
                            val isZonePhotoBusy = uiState.uploadingPhotoCropSlug == zone.slug

                            ProCropZoneCard(
                                zone = zone,
                                cropConfig = cropCfg,
                                cropPhoto = zonePhoto,
                                canManagePhoto = canManageCropPhotos,
                                isPhotoBusy = isZonePhotoBusy,
                                onPhotoClick = {
                                    if (zonePhoto != null && zonePhoto.url.isNotBlank()) {
                                        previewPhotoZone = zone
                                    } else if (canManageCropPhotos) {
                                        launchCropPhotoPicker(zone.slug)
                                    }
                                },
                                isDeviceOnline = isDeviceOnline && isPhoneOnline,
                                isAutoIrrigation = isAutoIrrigation,
                                isWateringThisZone = isZoneWatering,
                                isSoakingThisZone = isZoneSoaking,
                                isStartingThisZone = isZoneStarting,
                                pendingCommandPhase = zoneCommandPhase,
                                selectedPulseSec = uiState.selectedPulseSecByCrop[zone.slug] ?: 4,
                                lockReason = effectiveLockReason,
                                canControl = role.canControl,
                                isBusy = uiState.isSubmittingAction,
                                onSelectPulseSec = { sec -> onSelectPulseSec(zone.slug, sec) },
                                onWaterClick = { onManualWater(zone.slug) },
                                onStopClick = onEmergencyStop
                            )
                        }
                    }
                }
            }

            // 7. Reservoir & Weather Section (Header + 2-Column Bento Grid matching image)
            item(key = "tank_weather_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleRow(
                        title = "Reservoir & Weather",
                        trailingIcon = if (isOutdoor) Icons.Filled.WbSunny else Icons.Filled.Home,
                        trailingLabel = when {
                            !isDeviceOnline -> "Offline"
                            isOutdoor -> "Outdoor"
                            else -> "Indoor"
                        },
                        trailingIconTint = HomeColors.ForestPrimary,
                        trailingFg = HomeColors.TextPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReservoirBentoCard(
                            telemetry = telemetry,
                            isDeviceOnline = isDeviceOnline,
                            emptyGapIn = config.reservoirEmptyGapIn,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        WeatherRainBentoCard(
                            isOutdoor = isOutdoor,
                            isOwner = IotDomainLogic.canRequestDeviceLocationPermission(role),
                            isDeviceOnline = isDeviceOnline,
                            status = status,
                            telemetry = telemetry,
                            weather = weather,
                            fallbackLocationName = snapshot.meta.locationName,
                            rainThresholdPct = config.rainThresholdPct,
                            isRefreshing = uiState.isRefreshingWeather,
                            onRefreshWeather = onRefreshWeather,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // 8. 7-Day Historical Monitoring & Irrigation Records Entry Card
            item(key = "history_records_entry") {
                HistoryRecordsEntryCard(
                    onClick = { onOpenSubPage(MyPlantsSubPage.HISTORY_MONITORING) }
                )
            }
        }

        val activePreviewZone = previewPhotoZone
        if (activePreviewZone != null) {
            val activePhoto = snapshot.cropPhotos[activePreviewZone.slug]
            if (activePhoto != null && activePhoto.url.isNotBlank()) {
                CropZonePhotoPreviewDialog(
                    cropName = activePreviewZone.displayName,
                    cropPhoto = activePhoto,
                    canManagePhoto = canManageCropPhotos,
                    onChangePhoto = {
                        previewPhotoZone = null
                        launchCropPhotoPicker(activePreviewZone.slug)
                    },
                    onRemovePhoto = {
                        val slug = activePreviewZone.slug
                        previewPhotoZone = null
                        onDeleteCropPhoto(slug)
                    },
                    onDismiss = { previewPhotoZone = null }
                )
            } else {
                previewPhotoZone = null
            }
        }
    }
}

// ─── 1. Minimal Editorial Top App Bar ────────────────────────────────────────

@Composable
private fun DashboardTopBar(
    hasCriticalAlert: Boolean,
    isDeviceOnline: Boolean,
    onShareClick: () -> Unit,
    onLogClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddBoxClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = "My Plants",
                fontFamily = HomeInterFont,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary,
                letterSpacing = (-0.4).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Smart IoT Garden",
                fontFamily = HomeInterFont,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = HomeColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TopBarActionButton(
                icon = Icons.Filled.Add,
                contentDescription = "Pair New Box",
                onClick = onAddBoxClick
            )
            TopBarActionButton(
                icon = Icons.Filled.Share,
                contentDescription = "Share My Plant",
                onClick = onShareClick
            )
            Box {
                TopBarActionButton(
                    icon = Icons.Filled.Notifications,
                    contentDescription = "Device Alerts",
                    onClick = onLogClick
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 7.dp, end = 7.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                hasCriticalAlert -> HomeColors.AlertRed
                                isDeviceOnline -> HomeColors.SuccessGreen
                                else -> HomeColors.TextMuted
                            }
                        )
                )
            }
            TopBarActionButton(
                icon = Icons.Filled.Tune,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun TopBarActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = HomeColors.ForestPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

// ─── 2. Botanical Hero Card (Matching Uploaded Reference Image) ──────────────

@Composable
private fun BotanicalHeroCard(
    snapshot: LiveDeviceSnapshot,
    role: DeviceRole,
    isDeviceOnline: Boolean,
    isPhoneOnline: Boolean = true,
    nowMs: Long,
    isOutdoor: Boolean,
    isAutoIrrigation: Boolean,
    isSyncing: Boolean,
    onResyncClick: () -> Unit,
    onLocationRefreshClick: () -> Unit
) {
    val status = snapshot.status
    val telemetry = snapshot.telemetry
    val isCachedSnapshot = snapshot.isFromLocalCache
    val latestHeartbeatMs = maxOf(status.lastSeenAt, telemetry.updatedAt)
    val isOnlineFresh = isDeviceOnline && !isCachedSnapshot
    val canShowValues = isOnlineFresh && isPhoneOnline
    val isNoInternetWhileOnline = isOnlineFresh && !isPhoneOnline

    val pulseAlpha = if (isOnlineFresh && isPhoneOnline) {
        val infinite = rememberInfiniteTransition(label = "online_dot")
        val a by infinite.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        a
    } else {
        1f
    }

    val summaryStatus = when {
        !isPhoneOnline -> "No Internet"
        !isOnlineFresh -> "ESP32 Offline"
        status.emergencyStop || status.faultLockout -> "System Locked"
        status.isActivelyIrrigating -> {
            val crop = status.activeValve.takeIf { it.isNotBlank() && !it.equals("none", true) }
                ?.replaceFirstChar { it.uppercase() } ?: "Crop"
            "Watering $crop"
        }
        status.isInSoakSettle -> "Soaking Soil"
        status.rainSuspensionActive -> "Rain Paused"
        !telemetry.reservoirValid || telemetry.reservoirState in setOf("CRITICAL", "EMPTY") -> "Check Tank"
        isAutoIrrigation -> "Auto Ready"
        else -> "Manual Ready"
    }

    val statusPillIcon = when {
        isAutoIrrigation -> Icons.Filled.AutoAwesome
        else -> Icons.Filled.TouchApp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A2E1F),
                        HomeColors.ForestPrimary,
                        Color(0xFF1F573E)
                    )
                )
            )
    ) {
        // Subtle Ambient Organic Decorative Circles & Corner Leaf Accents (matching reference image)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = HomeColors.MintGlow.copy(alpha = 0.08f),
                radius = size.width * 0.40f,
                center = Offset(size.width * 0.88f, size.height * 0.14f)
            )
            drawCircle(
                color = Color(0xFF4ADE80).copy(alpha = 0.16f),
                radius = size.width * 0.22f,
                center = Offset(size.width * 0.96f, size.height * 0.96f)
            )
            drawCircle(
                color = Color(0xFF86EFAC).copy(alpha = 0.12f),
                radius = size.width * 0.14f,
                center = Offset(size.width * 0.84f, size.height * 1.02f)
            )
        }

        Column(modifier = Modifier.padding(18.dp)) {
            // Top Status Row: [● Online] [OWNER]  ..........  [👆 Manual Ready]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isOnlineFresh && isPhoneOnline) {
                            Color(0xFF10B981).copy(alpha = 0.24f)
                        } else {
                            HomeColors.AlertRed.copy(alpha = 0.28f)
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (isOnlineFresh && isPhoneOnline) {
                                HomeColors.MintGlow.copy(alpha = 0.30f)
                            } else {
                                Color(0xFFFCA5A5).copy(alpha = 0.35f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isOnlineFresh && isPhoneOnline) {
                                            HomeColors.MintGlow.copy(alpha = pulseAlpha)
                                        } else {
                                            Color(0xFFFCA5A5)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    isOnlineFresh && isPhoneOnline -> "Online"
                                    !isPhoneOnline -> "No Internet"
                                    else -> "Offline"
                                },
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnlineFresh && isPhoneOnline) {
                                    HomeColors.MintGlow
                                } else {
                                    Color(0xFFFCA5A5)
                                },
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right Frosted Status Pill ([👆 Manual Ready] / [✨ Auto Ready])
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = statusPillIcon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = summaryStatus,
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.94f),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Box Title + Subtitle on Left, [↻ Sync] Capsule on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = snapshot.meta.displayName.ifBlank { "Chili • Tomato • Eggplant" },
                        fontFamily = HomeInterFont,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append(role.label.uppercase(Locale.US))
                            append(" • ")
                            append(if (isOutdoor) "Outdoor" else "Indoor")
                            append(" • ")
                            append(
                                when {
                                    latestHeartbeatMs > 0L || snapshot.receivedAtClientMs > 0L ->
                                        formatRelativeAge(latestHeartbeatMs, snapshot.receivedAtClientMs, nowMs)
                                    else -> "Offline"
                                }
                            )
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, HomeColors.MintGlow.copy(alpha = 0.38f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !isSyncing, onClick = onResyncClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync ESP32",
                                tint = HomeColors.MintGlow,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isSyncing) "Syncing" else "Sync",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Location | Rain Forecast Capsule (with center vertical divider matching image)
            val isOwnerRole = IotDomainLogic.canRequestDeviceLocationPermission(role)
            val heroLocation = if (!isOnlineFresh) {
                "--"
            } else {
                snapshot.meta.locationName
                    .ifBlank { snapshot.weather.locationName }
                    .ifBlank { if (isOwnerRole) "Tap to set Box GPS" else "Waiting for Owner GPS" }
            }
            val heroRainText = if (isOnlineFresh && snapshot.weather.valid && snapshot.weather.rainProbabilityPct >= 0) {
                "Rain ${snapshot.weather.rainProbabilityPct}%"
            } else {
                "Rain --"
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = Color.White.copy(alpha = 0.11f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onLocationRefreshClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "Box GPS Location",
                            tint = if (isOnlineFresh) HomeColors.MintGlow else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = heroLocation,
                            fontFamily = HomeInterFont,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = if (isOnlineFresh) 0.95f else 0.72f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color.White.copy(alpha = 0.26f))
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = "Rain Forecast",
                            tint = if (isOnlineFresh) Color(0xFF93C5FD) else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = heroRainText,
                            fontFamily = HomeInterFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = if (isOnlineFresh) 0.95f else 0.72f),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Frosted Climate Summary Strip (Temperature | Humidity | Light)
            Surface(
                color = Color.White.copy(alpha = 0.11f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroClimateCell(
                        icon = Icons.Filled.Thermostat,
                        label = "Temperature",
                        value = if (canShowValues && telemetry.dhtValid && telemetry.temperatureC != null) {
                            String.format(Locale.US, "%.1f°C", telemetry.temperatureC)
                        } else "--",
                        dotColor = if (isOnlineFresh) HomeColors.MintGlow else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.weight(1f)
                    )
                    HeroClimateDivider()
                    HeroClimateCell(
                        icon = Icons.Filled.WaterDrop,
                        label = "Humidity",
                        value = if (canShowValues && telemetry.dhtValid && telemetry.humidityPct != null) {
                            String.format(Locale.US, "%.0f%%", telemetry.humidityPct)
                        } else "--",
                        dotColor = if (isOnlineFresh) HomeColors.MintGlow else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.weight(1f)
                    )
                    HeroClimateDivider()
                    HeroClimateCell(
                        icon = Icons.Filled.WbSunny,
                        label = "Light",
                        value = if (canShowValues && telemetry.lightValid && telemetry.lightBrightnessPct != null) {
                            String.format(Locale.US, "%.0f%%", telemetry.lightBrightnessPct)
                        } else "--",
                        dotColor = if (isOnlineFresh) Color(0xFFFACC15) else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroClimateDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(Color.White.copy(alpha = 0.22f))
    )
}

@Composable
private fun HeroClimateCell(
    icon: ImageVector,
    label: String,
    value: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.88f),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

// ─── 3. Side-by-Side 2-Column Horizontal Smart Control Cards (Matching Image) ─

@Composable
private fun SmartControlTilesRow(
    isAutoIrrigation: Boolean,
    isOutdoor: Boolean,
    canControl: Boolean,
    onToggleAutoIrrigation: () -> Unit,
    onToggleOutdoor: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SmartModeHorizontalTile(
            valueTitle = if (isAutoIrrigation) "Automatic Care" else "Manual Watering",
            subtitle = if (isAutoIrrigation) "Auto by soil level" else "Tap any crop to water",
            icon = if (isAutoIrrigation) Icons.Filled.AutoAwesome else Icons.Filled.WaterDrop,
            isTintedCard = true,
            enabled = canControl,
            onClick = onToggleAutoIrrigation,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        SmartModeHorizontalTile(
            valueTitle = if (isOutdoor) "Outdoor Mode" else "Indoor Mode",
            subtitle = if (isOutdoor) "Rain guard active" else "Rain sensor pause off",
            icon = if (isOutdoor) Icons.Filled.WbSunny else Icons.Filled.Home,
            isTintedCard = false,
            enabled = canControl,
            onClick = onToggleOutdoor,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun SmartModeHorizontalTile(
    valueTitle: String,
    subtitle: String,
    icon: ImageVector,
    isTintedCard: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBg = if (isTintedCard) Color(0xFFE9F6EE) else HomeColors.SurfaceWhite
    val borderColor = if (isTintedCard) Color(0xFF95D5B2) else HomeColors.CardBorder
    val iconBoxBg = if (isTintedCard) Color(0xFFC7EAD4) else HomeColors.SoftMint

    Surface(
        color = containerBg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(iconBoxBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            Column {
                Text(
                    text = valueTitle,
                    fontFamily = HomeInterFont,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}

// ─── 4. Priority Live Banners (Emergency / Active Watering / Weather) ────────

@Composable
private fun EmergencyLockoutCard(
    abortReason: String,
    canControl: Boolean,
    onClearFault: () -> Unit,
    onMuteBuzzer: () -> Unit
) {
    Surface(
        color = HomeColors.AlertBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, HomeColors.AlertRed),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(HomeColors.AlertRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = HomeColors.AlertRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "System Locked — Outputs OFF",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.AlertRed
                    )
                    Text(
                        text = abortReason,
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary
                    )
                }
            }
            if (canControl) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onClearFault,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.AlertRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Clear Fault",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    OutlinedButton(
                        onClick = onMuteBuzzer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HomeColors.AlertRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeOff,
                            null,
                            tint = HomeColors.AlertRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Mute Buzzer", fontFamily = HomeInterFont, fontSize = 12.sp, color = HomeColors.AlertRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineStatusBanner(
    bg: Color,
    accent: Color,
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─── 5. Pro Crop Zone Card (Professional 2-Tier Botanical Telemetry Card) ────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProCropZoneCard(
    zone: SoilZoneTelemetry,
    cropConfig: CropZoneConfig,
    cropPhoto: CropZonePhoto?,
    canManagePhoto: Boolean,
    isPhotoBusy: Boolean,
    onPhotoClick: () -> Unit,
    isDeviceOnline: Boolean,
    isCachedData: Boolean = false,
    isAutoIrrigation: Boolean,
    isWateringThisZone: Boolean,
    isSoakingThisZone: Boolean,
    isStartingThisZone: Boolean,
    pendingCommandPhase: PendingCommandPhase = PendingCommandPhase.IDLE,
    selectedPulseSec: Int,
    lockReason: String?,
    canControl: Boolean,
    isBusy: Boolean,
    onSelectPulseSec: (Int) -> Unit,
    onWaterClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val validMoisture = isDeviceOnline && zone.valid && zone.pct != null
    val moistureFraction = if (validMoisture) ((zone.pct ?: 0f) / 100f).coerceIn(0f, 1f) else 0f
    val animatedSweep by animateFloatAsState(
        targetValue = moistureFraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "soil_ring_${zone.slug}"
    )

    val phaseBadgeLabel = when (pendingCommandPhase) {
        PendingCommandPhase.SUBMITTING -> "Submitting"
        PendingCommandPhase.SENT_WAITING_ACK -> "Sent • Waiting ACK"
        PendingCommandPhase.ACKNOWLEDGED -> "Acknowledged"
        PendingCommandPhase.EXECUTING -> "Starting"
        PendingCommandPhase.IDLE -> "Starting"
    }
    val phaseButtonLabel = when (pendingCommandPhase) {
        PendingCommandPhase.SUBMITTING -> "Submitting..."
        PendingCommandPhase.SENT_WAITING_ACK -> "Waiting ACK..."
        PendingCommandPhase.ACKNOWLEDGED -> "Acknowledged..."
        PendingCommandPhase.EXECUTING -> "Opening Valve..."
        PendingCommandPhase.IDLE -> "Starting..."
    }

    val (statusLabel, badgeBg, badgeFg, accentColor) = when {
        !isDeviceOnline -> Quad("Offline", HomeColors.PlaceholderBg, HomeColors.TextSecondary, HomeColors.NavInactive)
        isWateringThisZone -> Quad("Watering", HomeColors.WaterBg, HomeColors.WaterBlue, HomeColors.WaterBlue)
        isStartingThisZone -> Quad(phaseBadgeLabel, HomeColors.WaterBg, HomeColors.WaterBlue, HomeColors.WaterBlue)
        isSoakingThisZone -> Quad("Soaking", HomeColors.SoftMint, HomeColors.ForestPrimary, HomeColors.EmeraldAccent)
        !cropConfig.enabled -> Quad("Disabled", HomeColors.PlaceholderBg, HomeColors.TextMuted, HomeColors.TextMuted)
        !zone.valid -> Quad("Fault", Color(0xFFFEE2E2), HomeColors.AlertRed, HomeColors.AlertRed)
        zone.needsWater -> Quad("Needs Water", HomeColors.WarningBg, HomeColors.WarningAmber, HomeColors.WarningAmber)
        else -> Quad("Optimal", HomeColors.SuccessBg, HomeColors.SuccessGreen, HomeColors.EmeraldAccent)
    }

    val cardBg by animateColorAsState(
        targetValue = if (isWateringThisZone) Color(0xFFF6FBFF) else HomeColors.SurfaceWhite,
        animationSpec = tween(300),
        label = "crop_bg_${zone.slug}"
    )

    val canTapWater = lockReason == null && !isBusy && !isStartingThisZone && !isWateringThisZone
    val context = LocalContext.current

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isWateringThisZone || isStartingThisZone) 1.5.dp else 1.dp,
            color = if (isWateringThisZone || isStartingThisZone) HomeColors.WaterBlue else HomeColors.CardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            // ── Top Tier: Crop Photo Thumbnail + Crop Identity + Circular Moisture Gauge ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Uploadable / Shared Cloudinary Crop Image Tile (Owner & Operator can edit; Viewer is read-only)
                val hasPhoto = cropPhoto != null && cropPhoto.url.isNotBlank()
                val tileClickable = !isPhotoBusy && (hasPhoto || canManagePhoto)
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (tileClickable) Modifier.clickable(onClick = onPhotoClick) else Modifier)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (hasPhoto) HomeColors.PlaceholderBg else HomeColors.SoftMint,
                        border = BorderStroke(
                            1.dp,
                            if (hasPhoto) HomeColors.CardBorder else HomeColors.SoftMintStrong
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (hasPhoto) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(cropPhoto!!.url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${zone.displayName} photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Spa,
                                    contentDescription = if (canManagePhoto) "Add ${zone.displayName} photo" else zone.displayName,
                                    tint = HomeColors.ForestMedium,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    if (isPhotoBusy) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else if (canManagePhoto) {
                        // Subtle camera badge shown ONLY for Owner & Operator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(HomeColors.ForestPrimary)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = "Upload photo",
                                tint = Color.White,
                                modifier = Modifier.size(10.5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Crop Name + Status Pill + Target Range + Progress Bar
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = zone.displayName,
                            fontFamily = HomeInterFont,
                            fontSize = 16.5.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )

                        Surface(
                            color = badgeBg,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = statusLabel,
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAutoIrrigation) {
                            "Auto dry ≤${cropConfig.startPct.toInt()}% → stop ${cropConfig.stopPct.toInt()}%"
                        } else {
                            "Target ${cropConfig.startPct.toInt()}% – ${cropConfig.stopPct.toInt()}% moisture"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sleek Linear Moisture Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(HomeColors.PageBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedSweep.coerceIn(0.04f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (validMoisture) accentColor else HomeColors.CardBorder
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Compact Circular Moisture Ring Gauge on Right
                CircularMoistureRing(
                    progress = animatedSweep,
                    ringColor = accentColor,
                    isPulsingWater = isWateringThisZone || isStartingThisZone,
                    valueText = if (validMoisture) {
                        String.format(Locale.US, "%.0f%%", zone.pct)
                    } else "--",
                    ringSize = 52.dp
                )
            }

            // ── Bottom Tier: Full-Size Watering Controls ──────────────────────────
            if (canControl) {
                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(HomeColors.DividerLine)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (!isAutoIrrigation) {
                    // Manual Mode: Adaptive layout so [4s | 8s | 12s] + [Water Eggplant] never clip
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stackControls = maxWidth < 310.dp
                        val btnBg = when {
                            isWateringThisZone -> HomeColors.AlertRed
                            isStartingThisZone -> HomeColors.WaterBlue
                            else -> HomeColors.ForestPrimary
                        }

                        if (stackControls) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(HomeColors.PageBg)
                                        .padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    listOf(4, 8, 12).forEach { sec ->
                                        val chosen = selectedPulseSec == sec
                                        Surface(
                                            color = if (chosen) HomeColors.ForestPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onSelectPulseSec(sec) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${sec}s",
                                                    fontFamily = HomeInterFont,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (chosen) Color.White else HomeColors.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                                Button(
                                    onClick = if (isWateringThisZone) onStopClick else onWaterClick,
                                    enabled = canTapWater || isWateringThisZone,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 44.dp),
                                    shape = RoundedCornerShape(13.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = btnBg,
                                        contentColor = Color.White,
                                        disabledContainerColor = HomeColors.ForestPrimary.copy(alpha = 0.55f),
                                        disabledContentColor = Color.White.copy(alpha = 0.9f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    if (isStartingThisZone) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.WaterDrop,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            isWateringThisZone -> "Stop Watering"
                                            isStartingThisZone -> phaseButtonLabel
                                            else -> "Water ${zone.displayName}"
                                        },
                                        fontFamily = HomeInterFont,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(HomeColors.PageBg)
                                        .padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    listOf(4, 8, 12).forEach { sec ->
                                        val chosen = selectedPulseSec == sec
                                        Surface(
                                            color = if (chosen) HomeColors.ForestPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onSelectPulseSec(sec) }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Text(
                                                    text = "${sec}s",
                                                    fontFamily = HomeInterFont,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (chosen) Color.White else HomeColors.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = if (isWateringThisZone) onStopClick else onWaterClick,
                                    enabled = canTapWater || isWateringThisZone,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 44.dp),
                                    shape = RoundedCornerShape(13.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = btnBg,
                                        contentColor = Color.White,
                                        disabledContainerColor = HomeColors.ForestPrimary.copy(alpha = 0.55f),
                                        disabledContentColor = Color.White.copy(alpha = 0.9f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    if (isStartingThisZone) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.WaterDrop,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = when {
                                            isWateringThisZone -> "Stop Watering"
                                            isStartingThisZone -> phaseButtonLabel
                                            else -> "Water ${zone.displayName}"
                                        },
                                        fontFamily = HomeInterFont,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Auto Mode: Clean status summary on left + Quick Water button on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isWateringThisZone) {
                                "Actively watering ${zone.displayName}..."
                            } else {
                                "Automatic moisture care active"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isWateringThisZone) HomeColors.WaterBlue else HomeColors.TextSecondary,
                            maxLines = 2,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        )

                        Button(
                            onClick = if (isWateringThisZone) onStopClick else onWaterClick,
                            enabled = canTapWater || isWateringThisZone,
                            modifier = Modifier.defaultMinSize(minHeight = 42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWateringThisZone) HomeColors.AlertRed else HomeColors.ForestPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = HomeColors.ForestPrimary.copy(alpha = 0.55f),
                                disabledContentColor = Color.White.copy(alpha = 0.9f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WaterDrop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isWateringThisZone) "Stop" else "Water ${zone.displayName}",
                                fontFamily = HomeInterFont,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropZonePhotoPreviewDialog(
    cropName: String,
    cropPhoto: CropZonePhoto,
    canManagePhoto: Boolean,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = HomeColors.SurfaceWhite,
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$cropName Photo",
                            fontFamily = HomeInterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                        if (cropPhoto.updatedByName.isNotBlank()) {
                            Text(
                                text = "Updated by ${cropPhoto.updatedByName}",
                                fontFamily = HomeInterFont,
                                fontSize = 11.5.sp,
                                color = HomeColors.TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = HomeColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(cropPhoto.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "$cropName plant photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, HomeColors.CardBorder, RoundedCornerShape(18.dp))
                )

                if (canManagePhoto) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRemovePhoto,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HomeColors.AlertRed.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeColors.AlertRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Remove",
                                fontFamily = HomeInterFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = onChangePhoto,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HomeColors.ForestPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1.25f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Change Photo",
                                fontFamily = HomeInterFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularMoistureRing(
    progress: Float,
    ringColor: Color,
    isPulsingWater: Boolean = false,
    valueText: String,
    ringSize: Dp = 56.dp
) {
    val ripple = if (isPulsingWater) {
        val infinite = rememberInfiniteTransition(label = "ring_ripple")
        val v by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_ripple_v"
        )
        v
    } else {
        0f
    }

    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isPulsingWater) {
                drawCircle(
                    color = HomeColors.WaterBlue.copy(alpha = (1f - ripple) * 0.24f),
                    radius = (size.minDimension / 2f) * (0.72f + 0.28f * ripple)
                )
            }
            val strokePx = 5.5.dp.toPx()
            val inset = strokePx / 2f
            val arcDim = size.minDimension - strokePx
            val topLeft = Offset(inset, inset)
            val arcSize = androidx.compose.ui.geometry.Size(arcDim, arcDim)

            drawArc(
                color = HomeColors.PlaceholderBg,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
        Text(
            text = valueText,
            fontFamily = HomeInterFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary
        )
    }
}

// ─── 6. 2-Column Bento Cards: Water Tank & Rain Guard (Matching Image) ───────

@Composable
private fun ReservoirBentoCard(
    telemetry: DeviceTelemetry,
    isDeviceOnline: Boolean,
    emptyGapIn: Float,
    modifier: Modifier = Modifier
) {
    val valid = isDeviceOnline && telemetry.reservoirValid && telemetry.reservoirPct != null
    val fillPct = if (valid) ((telemetry.reservoirPct ?: 0f) / 100f).coerceIn(0f, 1f) else 0f
    val animatedFill by animateFloatAsState(
        targetValue = fillPct,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "tank_fill"
    )

    val (statusLabel, badgeBg, badgeFg, liquidColor) = if (!isDeviceOnline) {
        Quad("Offline", HomeColors.PlaceholderBg, HomeColors.TextSecondary, HomeColors.NavInactive)
    } else {
        when (telemetry.reservoirState) {
            "NORMAL" -> Quad("Normal", HomeColors.SuccessBg, HomeColors.SuccessGreen, Color(0xFF0284C7))
            "LOW" -> Quad("Low", HomeColors.WarningBg, HomeColors.WarningAmber, HomeColors.WarningAmber)
            "CRITICAL" -> Quad("Critical", HomeColors.AlertBg, HomeColors.AlertRed, HomeColors.AlertRed)
            "EMPTY" -> Quad("Empty", HomeColors.AlertBg, HomeColors.AlertRed, HomeColors.AlertRed)
            else -> Quad("Fault", HomeColors.AlertBg, HomeColors.AlertRed, HomeColors.AlertRed)
        }
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle bottom-right water wave decoration matching reference image
            Canvas(modifier = Modifier.matchParentSize()) {
                val wavePath = Path().apply {
                    moveTo(size.width * 0.45f, size.height)
                    quadraticTo(
                        size.width * 0.75f,
                        size.height * 0.84f,
                        size.width,
                        size.height * 0.88f
                    )
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path = wavePath, color = HomeColors.SoftMint.copy(alpha = 0.7f))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Water Tank",
                        fontFamily = HomeInterFont,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1
                    )
                    Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                        Text(
                            text = statusLabel,
                            fontFamily = HomeInterFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Visual Glass Tank Fill + Big Percentage & Details Side-by-Side (matching image)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .width(46.dp)
                            .height(74.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedFill)
                                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                    .background(liquidColor)
                            )
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                listOf(0.28f, 0.52f, 0.76f).forEach { frac ->
                                    val y = size.height * frac
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.65f),
                                        start = Offset(4.dp.toPx(), y),
                                        end = Offset(12.dp.toPx(), y),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                !isDeviceOnline -> "--"
                                valid -> String.format(Locale.US, "%.0f%%", telemetry.reservoirPct)
                                else -> "--"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                !isDeviceOnline -> HomeColors.TextMuted
                                valid -> HomeColors.TextPrimary
                                else -> HomeColors.AlertRed
                            },
                            maxLines = 1
                        )
                        Text(
                            text = when {
                                !isDeviceOnline -> "-- in gap"
                                valid && telemetry.reservoirAirGapInches != null ->
                                    String.format(Locale.US, "%.2f in gap", telemetry.reservoirAirGapInches)
                                else -> "Pump locked"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 11.5.sp,
                            lineHeight = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = HomeColors.TextPrimary,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (!isDeviceOnline) "ESP32 offline"
                            else String.format(Locale.US, "%.0f-in tank", emptyGapIn),
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = HomeColors.TextSecondary,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherRainBentoCard(
    isOutdoor: Boolean,
    isOwner: Boolean,
    isDeviceOnline: Boolean,
    status: DeviceStatus,
    telemetry: DeviceTelemetry,
    weather: DeviceWeather,
    fallbackLocationName: String,
    rainThresholdPct: Int,
    isRefreshing: Boolean,
    onRefreshWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forecastHigh = isDeviceOnline && weather.valid && weather.rainProbabilityPct >= rainThresholdPct
    val displayLocation = if (!isDeviceOnline) {
        "--"
    } else {
        fallbackLocationName
            .ifBlank { weather.locationName }
            .ifBlank { if (isOwner) "Tap ↻ to set GPS" else "Waiting for Owner" }
    }

    val isSensorWet = telemetry.rainRawWet || telemetry.rainWetConfirmed
    val isAccidentalWet = isDeviceOnline && isSensorWet && !status.rainSuspensionActive &&
        (telemetry.rainAccidentalWet || !forecastHigh)
    val sensorDotColor = when {
        !isDeviceOnline -> HomeColors.TextMuted
        !telemetry.rainValid -> HomeColors.AlertRed
        status.rainSuspensionActive -> HomeColors.WaterBlue
        isAccidentalWet -> HomeColors.WarningAmber
        telemetry.rainWetConfirmed -> HomeColors.WaterBlue
        telemetry.rainRawWet -> HomeColors.WarningAmber
        else -> HomeColors.SuccessGreen
    }
    val sensorBgColor = when {
        !isDeviceOnline -> HomeColors.PageBg
        !telemetry.rainValid -> HomeColors.AlertBg
        status.rainSuspensionActive -> HomeColors.WaterBg
        isAccidentalWet -> HomeColors.WarningBg
        telemetry.rainWetConfirmed -> HomeColors.WaterBg
        telemetry.rainRawWet -> HomeColors.WarningBg
        else -> HomeColors.SoftMint
    }
    val sensorLabel = when {
        !isDeviceOnline -> "Sensor: --"
        !telemetry.rainValid -> "Sensor Fault"
        status.rainSuspensionActive -> "Sensor: Wet"
        isAccidentalWet -> "Accidental Wet"
        telemetry.rainWetConfirmed -> "Sensor: Wet"
        telemetry.rainRawWet -> "Sensor: Wet"
        else -> "Sensor: Dry"
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Cloud Icon + "Rain Guard" + Refresh Circle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = HomeColors.ForestMedium,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Rain Guard",
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(HomeColors.SoftMint)
                        .clickable(enabled = !isRefreshing, onClick = onRefreshWeather),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = HomeColors.ForestPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh Weather & GPS Location",
                            tint = HomeColors.ForestPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Location Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = if (isDeviceOnline) HomeColors.ForestPrimary else HomeColors.TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = displayLocation,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDeviceOnline) HomeColors.TextPrimary else HomeColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Middle Section: Big Rain % + "Rain Chance (3h)" with dedicated Sensor Pill row below (zero clipping!)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isDeviceOnline && weather.valid && weather.rainProbabilityPct >= 0) {
                            "${weather.rainProbabilityPct}%"
                        } else "--",
                        fontFamily = HomeInterFont,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !isDeviceOnline -> HomeColors.TextMuted
                            forecastHigh -> HomeColors.WaterBlue
                            else -> HomeColors.TextPrimary
                        },
                        maxLines = 1
                    )
                    Text(
                        text = "3h rain",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = sensorBgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(sensorDotColor)
                        )
                        Text(
                            text = sensorLabel,
                            fontFamily = HomeInterFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeviceOnline) sensorDotColor else HomeColors.TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            // Bottom Status Footer (wraps cleanly on narrow screens)
            val isSensorWetNow = telemetry.rainRawWet || telemetry.rainWetConfirmed
            Text(
                text = when {
                    !isDeviceOnline -> "ESP32 offline"
                    !isOutdoor -> "Paused in Indoor mode"
                    status.rainSuspensionActive -> "Raining — Auto-water paused"
                    isSensorWetNow && (telemetry.rainAccidentalWet || !forecastHigh) ->
                        "Weather <$rainThresholdPct% • Accidental wet"
                    forecastHigh -> "Weather ≥$rainThresholdPct% • Waiting sensor"
                    else -> "Active in Outdoor mode"
                },
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                lineHeight = 14.5.sp,
                color = when {
                    !isDeviceOnline -> HomeColors.TextMuted
                    status.rainSuspensionActive -> HomeColors.WaterBlue
                    isSensorWetNow && (telemetry.rainAccidentalWet || !forecastHigh) -> HomeColors.WarningAmber
                    else -> HomeColors.TextSecondary
                },
                fontWeight = if (isDeviceOnline && (status.rainSuspensionActive || isSensorWetNow)) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── 7. 7-Day History & Irrigation Records Entry Card ───────────────────────

@Composable
private fun HistoryRecordsEntryCard(
    onClick: () -> Unit
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.ForestPrimary.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(HomeColors.SoftMint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = "7-Day History & Records",
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "7-Day History & Records",
                        fontFamily = HomeInterFont,
                        fontSize = 14.5.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = HomeColors.SoftMint,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "PHT",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.ForestPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "By-hour & by-minute sensor graphs, offline gaps & watering logs",
                    fontFamily = HomeInterFont,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open 7-Day History",
                tint = HomeColors.ForestPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Shared Helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitleRow(
    title: String,
    trailingIcon: ImageVector,
    trailingLabel: String,
    trailingIconTint: Color,
    trailingFg: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontFamily = HomeInterFont,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, HomeColors.CardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = trailingIconTint,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = trailingLabel,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = trailingFg,
                    maxLines = 1
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatRelativeAge(timestampMs: Long, fallbackClientMs: Long, nowMs: Long): String {
    val effectiveMs = when {
        timestampMs >= 1_700_000_000_000L -> timestampMs
        fallbackClientMs >= 1_700_000_000_000L -> fallbackClientMs
        else -> return "Synced live"
    }
    val diffSec = ((nowMs - effectiveMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Synced ${diffSec}s ago"
        diffSec < 3600 -> "Synced ${diffSec / 60}m ago"
        else -> "Last seen ${diffSec / 3600}h ago"
    }
}
