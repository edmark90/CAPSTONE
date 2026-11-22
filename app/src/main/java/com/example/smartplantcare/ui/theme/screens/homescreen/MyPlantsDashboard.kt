package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.iot.CropZoneConfig
import com.example.smartplantcare.iot.DeviceRole
import com.example.smartplantcare.iot.DeviceStatus
import com.example.smartplantcare.iot.DeviceTelemetry
import com.example.smartplantcare.iot.DeviceWeather
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.LiveDeviceSnapshot
import com.example.smartplantcare.iot.MyPlantsSubPage
import com.example.smartplantcare.iot.MyPlantsUiState
import com.example.smartplantcare.util.NetworkMonitor
import com.example.smartplantcare.iot.SoilZoneTelemetry
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
    onResyncDevice: () -> Unit = {}
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
    val evalNowMs = if (!isPhoneOnline && uiState.phoneDisconnectedAtMs != null) {
        uiState.phoneDisconnectedAtMs
    } else {
        nowMs
    }
    val isDeviceOnline = IotDomainLogic.isDeviceOnlineFresh(
        status = status,
        nowMs = evalNowMs,
        receivedAtClientMs = snapshot.receivedAtClientMs,
        telemetryUpdatedAt = telemetry.updatedAt
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = HomeDimens.ScreenHorizontalPadding,
            end = HomeDimens.ScreenHorizontalPadding,
            top = HomeDimens.ScreenTopPadding,
            bottom = HomeDimens.BottomBarClearance + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Minimal Editorial Top App Bar (Clean navigation separated from data hero)
        item(key = "top_bar") {
            DashboardTopBar(
                hasCriticalAlert = hasCriticalAlert,
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

        // 3. Botanical Hero Card (Plant Box Identity + Status + Frosted Ambient Climate Bar)
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

        // 4. Priority Live Status Banners (Zero idle clutter — appear only when active)
        if (isDeviceOnline && !isPhoneOnline) {
            // Case 1: ESP32 is ONLINE, but user's mobile has NO INTERNET
            item(key = "banner_no_internet") {
                InlineStatusBanner(
                    bg = HomeColors.AlertBg,
                    accent = HomeColors.AlertRed,
                    icon = Icons.Filled.WifiOff,
                    title = "No Internet Connection",
                    subtitle = "Check your internet connection. Your plant box is online, but your phone has no internet."
                )
            }
        } else if (!isDeviceOnline && isPhoneOnline) {
            // Case 2: ESP32 is OFFLINE, and mobile has internet
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
        // Case 3: If !isDeviceOnline && !isPhoneOnline -> No "Check your internet connection" banner!

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

        // 5. Side-by-Side 2-Column Smart Control Tiles (Automatic/Manual + Outdoor/Indoor)
        item(key = "smart_controls_bento") {
            SmartControlTilesRow(
                isAutoIrrigation = isAutoIrrigation,
                isOutdoor = isOutdoor,
                canControl = role.canControl && isDeviceOnline && isPhoneOnline,
                isDeviceOnline = isDeviceOnline,
                onToggleAutoIrrigation = { onSwitchAutoIrrigation(!isAutoIrrigation) },
                onToggleOutdoor = { onSwitchMode(if (isOutdoor) "INDOOR" else "OUTDOOR") }
            )
        }

        // 6. My Crops Section (Custom Circular Moisture Rings + Clean Adaptive Controls)
        item(key = "crops_section_header") {
            SectionTitleRow(
                title = "Crop Zones",
                trailingLabel = when {
                    !isDeviceOnline -> "Offline"
                    !isPhoneOnline -> "No Internet"
                    isAutoIrrigation -> "Auto Active"
                    else -> "Manual Mode"
                },
                trailingBg = when {
                    !isDeviceOnline -> HomeColors.PlaceholderBg
                    !isPhoneOnline -> HomeColors.AlertBg
                    isAutoIrrigation -> HomeColors.SoftMint
                    else -> HomeColors.WaterBg
                },
                trailingFg = when {
                    !isDeviceOnline -> HomeColors.TextSecondary
                    !isPhoneOnline -> HomeColors.AlertRed
                    isAutoIrrigation -> HomeColors.ForestPrimary
                    else -> HomeColors.WaterBlue
                }
            )
        }

        items(telemetry.zones, key = { it.slug }) { zone ->
            val cropCfg = config.cropConfig(zone.slug)
            val baseLockReason = IotDomainLogic.manualWaterLockReason(role, snapshot, zone.slug)
            val effectiveLockReason = when {
                !isDeviceOnline -> "ESP32 offline"
                !isPhoneOnline -> "No internet connection"
                else -> baseLockReason
            }
            val isZoneWatering = isDeviceOnline && status.isActivelyIrrigating && status.activeValve == zone.slug
            val isZoneSoaking = isDeviceOnline && status.isInSoakSettle && status.activeValve == zone.slug
            val isZoneStarting = isDeviceOnline && !status.isActivelyIrrigating && uiState.pendingWaterCrop == zone.slug
            ProCropZoneCard(
                zone = zone,
                cropConfig = cropCfg,
                isDeviceOnline = isDeviceOnline,
                isAutoIrrigation = isAutoIrrigation,
                isWateringThisZone = isZoneWatering,
                isSoakingThisZone = isZoneSoaking,
                isStartingThisZone = isZoneStarting,
                selectedPulseSec = uiState.selectedPulseSecByCrop[zone.slug] ?: 4,
                lockReason = effectiveLockReason,
                canControl = role.canControl,
                isBusy = uiState.isSubmittingAction,
                onSelectPulseSec = { sec -> onSelectPulseSec(zone.slug, sec) },
                onWaterClick = { onManualWater(zone.slug) },
                onStopClick = onEmergencyStop
            )
        }

        // 7. 2-Column Bento Grid: Water Tank (7-Inch Reservoir) + Rain & Weather Protection
        item(key = "tank_weather_header") {
            SectionTitleRow(
                title = "Reservoir & Weather",
                trailingLabel = when {
                    !isDeviceOnline -> "Offline"
                    isOutdoor -> "Outdoor Guard"
                    else -> "Indoor"
                },
                trailingBg = if (!isDeviceOnline) HomeColors.PlaceholderBg else HomeColors.SurfaceWhite,
                trailingFg = HomeColors.TextSecondary
            )
        }

        item(key = "bento_tank_weather") {
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
}

// ─── 1. Minimal Editorial Top App Bar ────────────────────────────────────────

@Composable
private fun DashboardTopBar(
    hasCriticalAlert: Boolean,
    onShareClick: () -> Unit,
    onLogClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddBoxClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Smart IoT Garden Monitor",
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                color = HomeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (hasCriticalAlert) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HomeColors.AlertRed)
                    )
                }
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
            .size(40.dp)
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

// ─── 2. Botanical Hero Card (Ambient Glow + Box Identity + Climate Strip) ────

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
    val latestHeartbeatMs = maxOf(status.lastSeenAt, telemetry.updatedAt)
    val isOnlineFresh = isDeviceOnline
    val isNoInternetWhileOnline = isOnlineFresh && !isPhoneOnline

    // Subtle breathing dot animation when online and connected
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
        isNoInternetWhileOnline -> "No Internet Connection"
        !isOnlineFresh -> "ESP32 Offline"
        status.emergencyStop || status.faultLockout -> "System Locked"
        status.isActivelyIrrigating -> {
            val crop = status.activeValve.takeIf { it.isNotBlank() && !it.equals("none", true) }
                ?.replaceFirstChar { it.uppercase() } ?: "Crop"
            "Watering $crop"
        }
        status.isInSoakSettle -> "Soaking Soil"
        status.rainSuspensionActive -> "Rain Paused"
        !telemetry.reservoirValid || telemetry.reservoirState in setOf("CRITICAL", "EMPTY") -> "Check Water Tank"
        isAutoIrrigation -> "Auto Care Ready"
        else -> "Manual Ready"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(HomeColors.ForestDark, HomeColors.ForestPrimary, HomeColors.ForestMedium)
                )
            )
    ) {
        // Subtle Ambient Organic Decorative Circles (zero-cost Canvas)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = HomeColors.MintGlow.copy(alpha = 0.08f),
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.92f, size.height * 0.12f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = size.width * 0.30f,
                center = Offset(size.width * 0.10f, size.height * 0.95f)
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isOnlineFresh && isPhoneOnline) {
                            HomeColors.MintGlow.copy(alpha = 0.18f)
                        } else {
                            HomeColors.AlertRed.copy(alpha = 0.28f)
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
                                    isNoInternetWhileOnline -> "No Internet"
                                    isOnlineFresh -> "Online"
                                    else -> "Offline"
                                },
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOnlineFresh && isPhoneOnline) HomeColors.MintGlow else Color(0xFFFCA5A5),
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = role.label,
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (!status.physicalLoadsEnabled) {
                        Surface(
                            color = HomeColors.WarningAmber.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Watering Off",
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = summaryStatus,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Plant Box Custom Name + Subtitle
            Text(
                text = snapshot.meta.displayName.ifBlank { "SmartPlantCare Box" },
                fontFamily = HomeInterFont,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = buildString {
                        append(if (isOutdoor) "Outdoor Garden" else "Indoor Garden")
                        append("  •  ")
                        append(
                            if (latestHeartbeatMs > 0L || snapshot.receivedAtClientMs > 0L) {
                                formatRelativeAge(latestHeartbeatMs, snapshot.receivedAtClientMs, nowMs)
                            } else {
                                snapshot.deviceId
                            }
                        )
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !isSyncing, onClick = onResyncClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.6.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync ESP32",
                                tint = HomeColors.MintGlow,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (isSyncing) "Syncing" else "Sync",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Owner Mobile GPS Location & Live Rain Probability Strip
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
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onLocationRefreshClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = if (isOnlineFresh) 0.95f else 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = "Rain Forecast",
                            tint = if (isOnlineFresh) Color(0xFFBAE6FD) else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = heroRainText,
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnlineFresh) Color(0xFFE0F2FE) else Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Frosted Climate Summary Strip (Temp, Humidity, Light)
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 13.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroClimateCell(
                        icon = Icons.Filled.Thermostat,
                        label = "Temp",
                        value = if (isOnlineFresh && telemetry.dhtValid && telemetry.temperatureC != null) {
                            String.format(Locale.US, "%.1f°C", telemetry.temperatureC)
                        } else "--",
                        band = if (isOnlineFresh) telemetry.tempBand else "OFFLINE",
                        modifier = Modifier.weight(1f)
                    )
                    HeroClimateDivider()
                    HeroClimateCell(
                        icon = Icons.Filled.WaterDrop,
                        label = "Humidity",
                        value = if (isOnlineFresh && telemetry.dhtValid && telemetry.humidityPct != null) {
                            String.format(Locale.US, "%.0f%%", telemetry.humidityPct)
                        } else "--",
                        band = if (isOnlineFresh) telemetry.humidityBand else "OFFLINE",
                        modifier = Modifier.weight(1f)
                    )
                    HeroClimateDivider()
                    HeroClimateCell(
                        icon = Icons.Filled.WbSunny,
                        label = "Light",
                        value = if (isOnlineFresh && telemetry.lightValid && telemetry.lightBrightnessPct != null) {
                            String.format(Locale.US, "%.0f%%", telemetry.lightBrightnessPct)
                        } else "--",
                        band = if (isOnlineFresh) telemetry.lightBand else "OFFLINE",
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
            .height(30.dp)
            .background(Color.White.copy(alpha = 0.18f))
    )
}

@Composable
private fun HeroClimateCell(
    icon: ImageVector,
    label: String,
    value: String,
    band: String,
    modifier: Modifier = Modifier
) {
    val dotColor = when (band) {
        "NORMAL" -> HomeColors.MintGlow
        "HIGH", "LOW" -> Color(0xFFFBBF24)
        "OFFLINE" -> Color.White.copy(alpha = 0.35f)
        else -> Color(0xFFF87171)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ─── 3. Side-by-Side 2-Column Smart Control Tiles ────────────────────────────

@Composable
private fun SmartControlTilesRow(
    isAutoIrrigation: Boolean,
    isOutdoor: Boolean,
    canControl: Boolean,
    isDeviceOnline: Boolean,
    onToggleAutoIrrigation: () -> Unit,
    onToggleOutdoor: () -> Unit
) {
    val hintText = when {
        !isDeviceOnline -> "ESP32 offline"
        canControl -> "Tap to switch"
        else -> "View only"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmartModeTile(
            kicker = "IRRIGATION",
            valueTitle = if (isAutoIrrigation) "Automatic" else "Manual",
            subtitle = if (isAutoIrrigation) "Auto by soil level" else "Tap crops to water",
            icon = if (isAutoIrrigation) Icons.Filled.AutoAwesome else Icons.Filled.TouchApp,
            accentColor = if (isAutoIrrigation) HomeColors.ForestPrimary else HomeColors.WaterBlue,
            accentBg = if (isAutoIrrigation) HomeColors.SoftMint else HomeColors.WaterBg,
            switchHint = hintText,
            enabled = canControl,
            onClick = onToggleAutoIrrigation,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        SmartModeTile(
            kicker = "ENVIRONMENT",
            valueTitle = if (isOutdoor) "Outdoor" else "Indoor",
            subtitle = if (isOutdoor) "Rain guard active" else "Rain pause off",
            icon = if (isOutdoor) Icons.Filled.WbSunny else Icons.Filled.Home,
            accentColor = if (isOutdoor) HomeColors.ForestPrimary else HomeColors.TextSecondary,
            accentBg = if (isOutdoor) HomeColors.SoftMint else HomeColors.PlaceholderBg,
            switchHint = hintText,
            enabled = canControl,
            onClick = onToggleOutdoor,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun SmartModeTile(
    kicker: String,
    valueTitle: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    accentBg: Color,
    switchHint: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedIconBg by animateColorAsState(targetValue = accentBg, label = "tile_bg")
    val animatedAccent by animateColorAsState(targetValue = accentColor, label = "tile_accent")

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) animatedAccent.copy(alpha = 0.22f) else HomeColors.CardBorder
        ),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
                        .background(animatedIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = animatedAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = animatedIconBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = kicker,
                        fontFamily = HomeInterFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = animatedAccent,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column {
                Text(
                    text = valueTitle,
                    fontFamily = HomeInterFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (enabled) animatedIconBg else HomeColors.PageBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = switchHint,
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) animatedAccent else HomeColors.TextMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
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

// ─── 5. Pro Crop Zone Card (Circular Moisture Ring + Adaptive Manual Drawer) ─

@Composable
private fun ProCropZoneCard(
    zone: SoilZoneTelemetry,
    cropConfig: CropZoneConfig,
    isDeviceOnline: Boolean,
    isAutoIrrigation: Boolean,
    isWateringThisZone: Boolean,
    isSoakingThisZone: Boolean,
    isStartingThisZone: Boolean,
    selectedPulseSec: Int,
    lockReason: String?,
    canControl: Boolean,
    isBusy: Boolean,
    onSelectPulseSec: (Int) -> Unit,
    onWaterClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val (statusLabel, badgeBg, badgeFg, ringColor) = when {
        !isDeviceOnline -> Quad("Offline", HomeColors.PlaceholderBg, HomeColors.TextSecondary, HomeColors.NavInactive)
        isWateringThisZone -> Quad("Watering", HomeColors.WaterBg, HomeColors.WaterBlue, HomeColors.WaterBlue)
        isStartingThisZone -> Quad("Starting", HomeColors.WaterBg, HomeColors.WaterBlue, HomeColors.WaterBlue)
        isSoakingThisZone -> Quad("Soaking", HomeColors.SoftMint, HomeColors.ForestPrimary, HomeColors.EmeraldAccent)
        !cropConfig.enabled -> Quad("Disabled", HomeColors.PlaceholderBg, HomeColors.TextMuted, HomeColors.TextMuted)
        !zone.valid -> Quad("Fault", HomeColors.AlertBg, HomeColors.AlertRed, HomeColors.AlertRed)
        zone.needsWater -> Quad("Needs Water", HomeColors.WarningBg, HomeColors.WarningAmber, HomeColors.WarningAmber)
        else -> Quad("Optimal", HomeColors.SuccessBg, HomeColors.SuccessGreen, HomeColors.EmeraldAccent)
    }

    val targetPct = if (isDeviceOnline && zone.valid && zone.pct != null) (zone.pct / 100f).coerceIn(0f, 1f) else 0f
    val animatedSweep by animateFloatAsState(
        targetValue = targetPct,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "soil_ring_${zone.slug}"
    )
    val cardBg by animateColorAsState(
        targetValue = if (isWateringThisZone) Color(0xFFF6FBFF) else HomeColors.SurfaceWhite,
        animationSpec = tween(300),
        label = "crop_bg_${zone.slug}"
    )

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isWateringThisZone || isStartingThisZone) 1.5.dp else 1.dp,
            color = if (isWateringThisZone || isStartingThisZone) HomeColors.WaterBlue else HomeColors.CardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Circular Moisture Ring Gauge (with animated water-ripple halo when watering!)
                CircularMoistureRing(
                    progress = animatedSweep,
                    ringColor = ringColor,
                    isPulsingWater = isWateringThisZone || isStartingThisZone,
                    valueText = if (isDeviceOnline && zone.valid && zone.pct != null) {
                        String.format(Locale.US, "%.0f%%", zone.pct)
                    } else "--"
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = zone.displayName,
                            fontFamily = HomeInterFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                            Text(
                                text = statusLabel,
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (!isDeviceOnline) {
                            "Waiting for ESP32 sensor sync"
                        } else if (isAutoIrrigation) {
                            "Auto dry ≤${cropConfig.startPct.toInt()}% → stop ${cropConfig.stopPct.toInt()}%"
                        } else {
                            "Target ${cropConfig.startPct.toInt()}%–${cropConfig.stopPct.toInt()}% moisture"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // In AUTOMATIC mode: crisp solid green Quick-Water pill with pure White text!
                if (canControl && isAutoIrrigation) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val pillEnabled = lockReason == null && !isBusy && !isStartingThisZone && !isWateringThisZone
                    val pillBg = when {
                        isWateringThisZone || isStartingThisZone -> HomeColors.WaterBlue
                        pillEnabled -> HomeColors.ForestPrimary
                        else -> HomeColors.PlaceholderBg
                    }
                    val pillFg = when {
                        isWateringThisZone || isStartingThisZone || pillEnabled -> Color.White
                        else -> HomeColors.TextMuted
                    }
                    Surface(
                        color = pillBg,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(13.dp))
                            .clickable(
                                enabled = pillEnabled,
                                onClick = onWaterClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isStartingThisZone || isWateringThisZone) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.8.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.WaterDrop,
                                    contentDescription = "Quick Water",
                                    tint = pillFg,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    isWateringThisZone -> "Active"
                                    isStartingThisZone -> "Starting"
                                    else -> "Water"
                                },
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillFg,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // In MANUAL mode: smoothly expand the full Manual Irrigation Control Bar
            AnimatedVisibility(
                visible = canControl && !isAutoIrrigation,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tactile Pulse Segmented Pills (4s / 8s / 12s)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(HomeColors.PageBg)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(4, 8, 12).forEach { sec ->
                                val chosen = selectedPulseSec == sec
                                Surface(
                                    color = if (chosen) HomeColors.ForestPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable { onSelectPulseSec(sec) }
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        fontFamily = HomeInterFont,
                                        fontSize = 12.sp,
                                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                        color = if (chosen) Color.White else HomeColors.TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }

                        val canTapWater = lockReason == null && !isBusy && !isStartingThisZone && !isWateringThisZone
                        val waterBtnContentColor = if (canTapWater || isWateringThisZone || isStartingThisZone) {
                            Color.White
                        } else {
                            HomeColors.TextMuted
                        }

                        Button(
                            onClick = onWaterClick,
                            enabled = canTapWater,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWateringThisZone || isStartingThisZone) {
                                    HomeColors.WaterBlue
                                } else {
                                    HomeColors.ForestPrimary
                                },
                                contentColor = Color.White,
                                disabledContainerColor = if (isWateringThisZone || isStartingThisZone) {
                                    HomeColors.WaterBlue.copy(alpha = 0.88f)
                                } else {
                                    HomeColors.PlaceholderBg
                                },
                                disabledContentColor = if (isWateringThisZone || isStartingThisZone) {
                                    Color.White
                                } else {
                                    HomeColors.TextMuted
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            if (isStartingThisZone || isWateringThisZone) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.WaterDrop,
                                    contentDescription = null,
                                    tint = waterBtnContentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isWateringThisZone -> "Watering..."
                                    isStartingThisZone -> "Starting..."
                                    else -> "Water ${zone.displayName}"
                                },
                                fontFamily = HomeInterFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = waterBtnContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (isWateringThisZone) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = HomeColors.WaterBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Watering ${zone.displayName} now",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeColors.WaterBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        if (canControl) {
                            Surface(
                                color = HomeColors.AlertRed,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onStopClick)
                            ) {
                                Text(
                                    text = "Stop",
                                    fontFamily = HomeInterFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            } else if (canControl && lockReason != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = HomeColors.WarningBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lockReason,
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.WarningAmber,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
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
    valueText: String
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
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isPulsingWater) {
                drawCircle(
                    color = HomeColors.WaterBlue.copy(alpha = (1f - ripple) * 0.24f),
                    radius = (size.minDimension / 2f) * (0.72f + 0.28f * ripple)
                )
            }
            val strokePx = 6.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Soft circular track
            drawArc(
                color = HomeColors.PlaceholderBg,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            // Active fill arc
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = valueText,
            fontFamily = HomeInterFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary
        )
    }
}

// ─── 6. 2-Column Bento Cards: 7-Inch Water Tank & Rain/Weather Guard ─────────

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
            "NORMAL" -> Quad("Normal", HomeColors.SuccessBg, HomeColors.SuccessGreen, HomeColors.WaterBlue)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Water Tank",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                )
                Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = statusLabel,
                        fontFamily = HomeInterFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Visual Glass Tank Fill + Big Percentage Side-by-Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vertical visual tank silhouette with crisp glass border & level ticks
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = HomeColors.PageBg,
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    modifier = Modifier
                        .width(28.dp)
                        .height(66.dp)
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
                            listOf(0.25f, 0.50f, 0.75f).forEach { frac ->
                                val y = size.height * frac
                                drawLine(
                                    color = Color.White.copy(alpha = 0.45f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width * 0.35f, y),
                                    strokeWidth = 1.dp.toPx()
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
                        fontSize = 23.sp,
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
                            !isDeviceOnline -> "--"
                            valid && telemetry.reservoirAirGapInches != null -> String.format(Locale.US, "%.2f in gap", telemetry.reservoirAirGapInches)
                            else -> "Pump locked"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (!isDeviceOnline) "ESP32 offline" else String.format(Locale.US, "%.0f-inch ultrasonic tank", emptyGapIn),
                fontFamily = HomeInterFont,
                fontSize = 10.sp,
                color = HomeColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rain Guard",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(HomeColors.PageBg)
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
                            tint = HomeColors.ForestMedium,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = if (isDeviceOnline) HomeColors.ForestMedium else HomeColors.TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = displayLocation,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDeviceOnline) HomeColors.ForestPrimary else HomeColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                Text(
                    text = if (isDeviceOnline && weather.valid && weather.rainProbabilityPct >= 0) {
                        "${weather.rainProbabilityPct}%"
                    } else "--",
                    fontFamily = HomeInterFont,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !isDeviceOnline -> HomeColors.TextMuted
                        forecastHigh -> HomeColors.WaterBlue
                        else -> HomeColors.TextPrimary
                    },
                    maxLines = 1
                )
                Text(
                    text = "Rain Chance (3h)",
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                    status.rainSuspensionActive -> "Wet • Raining"
                    isAccidentalWet -> "Accidental Wet"
                    telemetry.rainWetConfirmed -> "Sensor: Wet"
                    telemetry.rainRawWet -> "Detecting Wet..."
                    else -> "Sensor: Dry"
                }

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = sensorBgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(sensorDotColor)
                        )
                        Text(
                            text = sensorLabel,
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeviceOnline) sensorDotColor else HomeColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isSensorWetNow = telemetry.rainRawWet || telemetry.rainWetConfirmed
            Text(
                text = when {
                    !isDeviceOnline -> "ESP32 offline"
                    !isOutdoor -> "Paused in Indoor mode"
                    status.rainSuspensionActive -> "Raining — Auto-water PAUSED"
                    isSensorWetNow && (telemetry.rainAccidentalWet || !forecastHigh) ->
                        "Weather <$rainThresholdPct% • Accidental wet"
                    forecastHigh -> "Weather ≥$rainThresholdPct% • Waiting sensor"
                    else -> "Pauses if ≥$rainThresholdPct% first, then wet"
                },
                fontFamily = HomeInterFont,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = when {
                    !isDeviceOnline -> HomeColors.TextMuted
                    status.rainSuspensionActive -> HomeColors.WaterBlue
                    isSensorWetNow && (telemetry.rainAccidentalWet || !forecastHigh) -> HomeColors.WarningAmber
                    else -> HomeColors.TextMuted
                },
                fontWeight = if (isDeviceOnline && (status.rainSuspensionActive || isSensorWetNow)) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Shared Helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitleRow(
    title: String,
    trailingLabel: String,
    trailingBg: Color,
    trailingFg: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontFamily = HomeInterFont,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Surface(
            color = trailingBg,
            shape = RoundedCornerShape(50),
            border = if (trailingBg == HomeColors.SurfaceWhite) BorderStroke(1.dp, HomeColors.CardBorder) else null
        ) {
            Text(
                text = trailingLabel,
                fontFamily = HomeInterFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = trailingFg,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
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
