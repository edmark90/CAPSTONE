package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.smartplantcare.iot.SoilZoneTelemetry
import java.util.Locale

// ─── PAGE 3: Main Live IoT Sensor & Irrigation Dashboard ─────────────────────

@Composable
internal fun MyPlantsDashboardScreen(
    uiState: MyPlantsUiState,
    onSelectDevice: (String) -> Unit,
    onAddAnotherDevice: () -> Unit,
    onOpenSubPage: (MyPlantsSubPage) -> Unit,
    onSwitchMode: (String) -> Unit,
    onSelectPulseSec: (String, Int) -> Unit,
    onManualWater: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onClearEmergency: () -> Unit,
    onMuteBuzzer: () -> Unit
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
                CircularProgressIndicator(color = HomeColors.ForestPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Syncing live telemetry from ESP32...",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 14.dp,
            bottom = HomeDimens.BottomBarClearance
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Multi-device pill switcher (if user owns/shares more than 1 plant box)
        if (uiState.userDevices.size > 1) {
            item(key = "device_switcher") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.userDevices, key = { it.deviceId }) { link ->
                        val selected = link.deviceId == uiState.activeDeviceId
                        Surface(
                            color = if (selected) HomeColors.ForestPrimary else HomeColors.SurfaceWhite,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, if (selected) HomeColors.ForestPrimary else HomeColors.CardBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectDevice(link.deviceId) }
                        ) {
                            Text(
                                text = "${link.deviceId} (${link.role.label})",
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else HomeColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    item {
                        Surface(
                            color = HomeColors.SoftMint,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onAddAnotherDevice() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Box",
                                    tint = HomeColors.ForestPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add Box",
                                    fontFamily = HomeInterFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeColors.ForestPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Top Device Header Bar
        item(key = "header") {
            DashboardHeaderCard(
                snapshot = snapshot,
                role = role,
                onShareClick = { onOpenSubPage(MyPlantsSubPage.SHARE_PLANT) },
                onLogClick = { onOpenSubPage(MyPlantsSubPage.EVENT_LOG) },
                onSettingsClick = { onOpenSubPage(MyPlantsSubPage.DEVICE_SETTINGS) },
                onAddBoxClick = onAddAnotherDevice
            )
        }

        // 3. Outdoor / Indoor Segmented Mode Switcher
        item(key = "mode_switch") {
            OutdoorIndoorModeCard(
                isOutdoor = config.mode.equals("OUTDOOR", ignoreCase = true),
                canControl = role.canControl,
                onSwitchMode = onSwitchMode
            )
        }

        // 4. Priority Safety & Active Watering Banners
        if (status.emergencyStop || status.faultLockout) {
            item(key = "banner_estop") {
                EmergencyLockoutBanner(
                    abortReason = status.lastAbortReason,
                    canControl = role.canControl,
                    onClearFault = onClearEmergency,
                    onMuteBuzzer = onMuteBuzzer
                )
            }
        }

        if (status.isActivelyIrrigating || status.isInSoakSettle) {
            item(key = "banner_active_watering") {
                ActiveIrrigationBanner(
                    status = status,
                    canControl = role.canControl,
                    onEmergencyStop = onEmergencyStop
                )
            }
        }

        if (status.rainSuspensionActive) {
            item(key = "banner_rain_pause") {
                StatusAlertBanner(
                    bg = HomeColors.WaterBg,
                    border = HomeColors.WaterBlue,
                    icon = Icons.Filled.Cloud,
                    title = "Auto-Watering Paused (Confirmed Rain)",
                    body = "Outdoor mode + Forecast >=${config.rainThresholdPct}% AND Rain Sensor Wet Confirmed (30s). Resumes after 120s dry + <${config.rainThresholdPct}% forecast."
                )
            }
        } else if (status.weatherAdvisoryActive) {
            item(key = "banner_weather_advisory") {
                StatusAlertBanner(
                    bg = HomeColors.WarningBg,
                    border = HomeColors.WarningAmber,
                    icon = Icons.Filled.Warning,
                    title = "Rain Forecast Advisory (>=${config.rainThresholdPct}% Next 3 H)",
                    body = "Physical rain plate (GPIO32) is still DRY — automatic watering is NOT paused and buzzer stays silent."
                )
            }
        }

        if (status.weatherUnavailableFault && status.isOutdoor) {
            item(key = "banner_weather_fault") {
                StatusAlertBanner(
                    bg = HomeColors.WarningBg,
                    border = HomeColors.WarningAmber,
                    icon = Icons.Filled.CloudOff,
                    title = "Open-Meteo Forecast Unavailable / Stale",
                    body = "Unattended outdoor auto-watering is paused safely until fresh weather arrives (never treated as fake rain)."
                )
            }
        }

        // 5. Section: 3 Independent Crop Zone Cards + Manual Irrigation
        item(key = "crops_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Crop Zones & Manual Irrigation",
                    fontFamily = HomeInterFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Surface(color = HomeColors.SoftMint, shape = RoundedCornerShape(50)) {
                    Text(
                        text = "1 Valve At A Time",
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }

        items(telemetry.zones, key = { it.slug }) { zone ->
            val cropCfg = config.cropConfig(zone.slug)
            val lockReason = IotDomainLogic.manualWaterLockReason(role, snapshot, zone.slug)
            CropZoneCard(
                zone = zone,
                cropConfig = cropCfg,
                isWateringThisZone = status.isActivelyIrrigating && status.activeValve == zone.slug,
                selectedPulseSec = uiState.selectedPulseSecByCrop[zone.slug] ?: 4,
                lockReason = lockReason,
                canControl = role.canControl,
                isBusy = uiState.isSubmittingAction,
                onSelectPulseSec = { sec -> onSelectPulseSec(zone.slug, sec) },
                onWaterClick = { onManualWater(zone.slug) }
            )
        }

        // 6. 7-Inch Ultrasonic Water Reservoir Card
        item(key = "reservoir_card") {
            ReservoirMonitorCard(
                telemetry = telemetry,
                emptyGapIn = config.reservoirEmptyGapIn,
                fullGapIn = config.reservoirFullGapIn
            )
        }

        // 7. Rain Sensor + Open-Meteo Visual AND-Gate Card
        item(key = "rain_weather_card") {
            RainAndWeatherGateCard(
                status = status,
                telemetry = telemetry,
                weather = weather,
                rainThresholdPct = config.rainThresholdPct
            )
        }

        // 8. DHT22 + LDR 3-Tile Environmental Grid
        item(key = "env_row") {
            EnvironmentSensorsRow(telemetry = telemetry)
        }
    }
}

@Composable
private fun DashboardHeaderCard(
    snapshot: LiveDeviceSnapshot,
    role: DeviceRole,
    onShareClick: () -> Unit,
    onLogClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddBoxClick: () -> Unit
) {
    val status = snapshot.status
    val isOnlineFresh = status.online &&
        (status.lastSeenAt == 0L || (System.currentTimeMillis() - status.lastSeenAt) < 180_000L)

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.meta.displayName.ifBlank { "SmartPlantCare Box" },
                        fontFamily = HomeInterFont,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(snapshot.deviceId)
                            if (status.lastSeenAt > 0L) {
                                append(" • ")
                                append(formatRelativeAge(status.lastSeenAt))
                            }
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary
                    )
                }

                // Quick Action Icons: Share, Alerts Log, Settings
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderIconCircle(Icons.Filled.Share, "Share My Plant", onShareClick)
                    HeaderIconCircle(Icons.Filled.Notifications, "Device Alerts", onLogClick)
                    HeaderIconCircle(Icons.Filled.Tune, "Device Settings", onSettingsClick)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Online / Offline pill
                Surface(
                    color = if (isOnlineFresh) HomeColors.SuccessBg else HomeColors.AlertBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isOnlineFresh) {
                            "● ONLINE (${status.wifiRssi} dBm)"
                        } else {
                            "● OFFLINE"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnlineFresh) HomeColors.SuccessGreen else HomeColors.AlertRed,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                // Role Badge
                val (roleBg, roleFg) = when (role) {
                    DeviceRole.OWNER -> HomeColors.SoftMint to HomeColors.ForestPrimary
                    DeviceRole.OPERATOR -> HomeColors.WaterBg to HomeColors.WaterBlue
                    DeviceRole.VIEWER -> HomeColors.PlaceholderBg to HomeColors.TextSecondary
                }
                Surface(color = roleBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = role.label,
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = roleFg,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                if (!status.physicalLoadsEnabled) {
                    Surface(color = HomeColors.WarningBg, shape = RoundedCornerShape(50)) {
                        Text(
                            text = "BENCH SAFE",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.WarningAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onAddBoxClick,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Pair Another ESP32",
                        tint = HomeColors.ForestMedium,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIconCircle(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        color = HomeColors.SoftMint,
        shape = CircleShape,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = HomeColors.ForestPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun OutdoorIndoorModeCard(
    isOutdoor: Boolean,
    canControl: Boolean,
    onSwitchMode: (String) -> Unit
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HomeColors.SoftMint)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModePillButton(
                    label = "OUTDOOR MODE",
                    icon = Icons.Filled.WbSunny,
                    selected = isOutdoor,
                    enabled = canControl,
                    onClick = { onSwitchMode("OUTDOOR") },
                    modifier = Modifier.weight(1f)
                )
                ModePillButton(
                    label = "INDOOR MODE",
                    icon = Icons.Filled.Home,
                    selected = !isOutdoor,
                    enabled = canControl,
                    onClick = { onSwitchMode("INDOOR") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = if (isOutdoor) {
                    "Outdoor Mode: Rain Sensor (GPIO32) + Open-Meteo (>=60%) auto-pause is ACTIVE."
                } else {
                    "Indoor Mode: Outdoor weather rain-based irrigation suspension is DISABLED."
                },
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                color = HomeColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ModePillButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) HomeColors.ForestPrimary else Color.Transparent,
        shape = RoundedCornerShape(11.dp),
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = enabled && !selected, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else HomeColors.ForestPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else HomeColors.ForestPrimary
            )
        }
    }
}

@Composable
private fun EmergencyLockoutBanner(
    abortReason: String,
    canControl: Boolean,
    onClearFault: () -> Unit,
    onMuteBuzzer: () -> Unit
) {
    Surface(
        color = HomeColors.AlertBg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, HomeColors.AlertRed),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = HomeColors.AlertRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SYSTEM LOCKED — PUMP & VALVES OFF",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.AlertRed
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reason: $abortReason",
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                color = HomeColors.TextPrimary
            )
            if (canControl) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onClearFault,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HomeColors.AlertRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Fault", fontFamily = HomeInterFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onMuteBuzzer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HomeColors.AlertRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.VolumeOff, null, tint = HomeColors.AlertRed, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mute Buzzer", fontFamily = HomeInterFont, fontSize = 12.sp, color = HomeColors.AlertRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveIrrigationBanner(
    status: DeviceStatus,
    canControl: Boolean,
    onEmergencyStop: () -> Unit
) {
    Surface(
        color = HomeColors.WaterBg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, HomeColors.WaterBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = HomeColors.WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${status.irrigationState}: ${status.activeValve.uppercase()}",
                            fontFamily = HomeInterFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.WaterBlue
                        )
                        Text(
                            text = "Pump: ${status.pumpState} • Elapsed ${status.activePumpElapsedSec}s / ${status.activeTargetDurationSec}s",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextSecondary
                        )
                    }
                }
                if (canControl) {
                    Button(
                        onClick = onEmergencyStop,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HomeColors.AlertRed)
                    ) {
                        Text(
                            text = "STOP NOW",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusAlertBanner(
    bg: Color,
    border: Color,
    icon: ImageVector,
    title: String,
    body: String
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, border.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = border,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun CropZoneCard(
    zone: SoilZoneTelemetry,
    cropConfig: CropZoneConfig,
    isWateringThisZone: Boolean,
    selectedPulseSec: Int,
    lockReason: String?,
    canControl: Boolean,
    isBusy: Boolean,
    onSelectPulseSec: (Int) -> Unit,
    onWaterClick: () -> Unit
) {
    val (badgeText, badgeBg, badgeFg) = when {
        isWateringThisZone -> Triple("WATERING", HomeColors.WaterBg, HomeColors.WaterBlue)
        !cropConfig.enabled -> Triple("DISABLED", HomeColors.PlaceholderBg, HomeColors.TextMuted)
        !zone.valid -> Triple("SENSOR FAULT", HomeColors.AlertBg, HomeColors.AlertRed)
        zone.needsWater -> Triple("NEEDS WATER", HomeColors.WarningBg, HomeColors.WarningAmber)
        else -> Triple("OPTIMAL", HomeColors.SuccessBg, HomeColors.SuccessGreen)
    }

    val progress = ((zone.pct ?: 0f) / 100f).coerceIn(0f, 1f)
    val barColor = when {
        !zone.valid -> HomeColors.AlertRed
        zone.needsWater -> HomeColors.WarningAmber
        else -> HomeColors.EmeraldAccent
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (isWateringThisZone) 1.5.dp else 1.dp,
            color = if (isWateringThisZone) HomeColors.WaterBlue else HomeColors.CardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(HomeColors.SoftMint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Eco,
                            contentDescription = null,
                            tint = HomeColors.ForestPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = zone.displayName,
                            fontFamily = HomeInterFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                        Text(
                            text = "${zone.gpioLabel} • ${zone.valveLabel}",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextMuted
                        )
                    }
                }

                Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = badgeText,
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (zone.valid && zone.pct != null) {
                        String.format(Locale.US, "%.1f%%", zone.pct)
                    } else {
                        "--%"
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (zone.valid) HomeColors.TextPrimary else HomeColors.AlertRed
                )
                Text(
                    text = "Start <${cropConfig.startPct.toInt()}% • Stop ${cropConfig.stopPct.toInt()}% • Raw ${zone.raw}",
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = HomeColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = barColor,
                trackColor = HomeColors.PlaceholderBg
            )

            if (canControl) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(4, 8, 12).forEach { sec ->
                        val chosen = selectedPulseSec == sec
                        Surface(
                            color = if (chosen) HomeColors.SoftMintStrong else HomeColors.PageBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (chosen) HomeColors.ForestPrimary else HomeColors.CardBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectPulseSec(sec) }
                        ) {
                            Text(
                                text = "${sec}s",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (chosen) HomeColors.ForestPrimary else HomeColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = onWaterClick,
                        enabled = lockReason == null && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = HomeColors.PlaceholderBg,
                            disabledContentColor = HomeColors.TextMuted
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Water ${zone.displayName}",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (lockReason != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Safety Lock: $lockReason",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.WarningAmber,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservoirMonitorCard(
    telemetry: DeviceTelemetry,
    emptyGapIn: Float,
    fullGapIn: Float
) {
    val valid = telemetry.reservoirValid && telemetry.reservoirPct != null
    val state = telemetry.reservoirState
    val (badgeBg, badgeFg, barColor) = when (state) {
        "NORMAL" -> Triple(HomeColors.SuccessBg, HomeColors.SuccessGreen, HomeColors.WaterBlue)
        "LOW" -> Triple(HomeColors.WarningBg, HomeColors.WarningAmber, HomeColors.WarningAmber)
        else -> Triple(HomeColors.AlertBg, HomeColors.AlertRed, HomeColors.AlertRed)
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HomeColors.WaterBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            tint = HomeColors.WaterBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Water Reservoir (7-Inch Tank)",
                            fontFamily = HomeInterFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                        Text(
                            text = "Ultrasonic TRIG 16 / ECHO 17",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextMuted
                        )
                    }
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = state,
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (valid) {
                        String.format(Locale.US, "%.1f%% Full", telemetry.reservoirPct)
                    } else {
                        "UNKNOWN"
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (valid) HomeColors.TextPrimary else HomeColors.AlertRed
                )
                Text(
                    text = if (valid && telemetry.reservoirAirGapInches != null) {
                        String.format(Locale.US, "Air Gap: %.2f in (Empty %.1f in)", telemetry.reservoirAirGapInches, emptyGapIn)
                    } else {
                        "No Echo — Pump Locked OFF"
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = HomeColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { if (valid) ((telemetry.reservoirPct ?: 0f) / 100f).coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = barColor,
                trackColor = HomeColors.PlaceholderBg
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format(
                    Locale.US,
                    "Full <= %.1f in • Low <= 20%% (2 beeps) • Critical <= 10%% Pump Cutoff (3 beeps)",
                    fullGapIn
                ),
                fontFamily = HomeInterFont,
                fontSize = 10.sp,
                color = HomeColors.TextMuted
            )
        }
    }
}

@Composable
private fun RainAndWeatherGateCard(
    status: DeviceStatus,
    telemetry: DeviceTelemetry,
    weather: DeviceWeather,
    rainThresholdPct: Int
) {
    val forecastHigh = weather.valid && weather.rainProbabilityPct >= rainThresholdPct

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rain & Open-Meteo Protection",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Surface(
                    color = if (status.rainSuspensionActive) HomeColors.WaterBg else HomeColors.SoftMint,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (status.rainSuspensionActive) "PAUSED" else "ACTIVE",
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (status.rainSuspensionActive) HomeColors.WaterBlue else HomeColors.ForestPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = HomeColors.WaterBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Open-Meteo (${weather.windowLabel})",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeColors.WaterBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (weather.valid && weather.rainProbabilityPct >= 0) {
                                "${weather.rainProbabilityPct}%"
                            } else {
                                "N/A"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                        Text(
                            text = when {
                                !weather.valid -> "Unavailable"
                                forecastHigh -> ">= $rainThresholdPct% Rain Risk"
                                else -> "< $rainThresholdPct% Clear"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = if (forecastHigh) HomeColors.WarningAmber else HomeColors.TextSecondary
                        )
                    }
                }

                Surface(
                    color = HomeColors.SoftMint,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Rain Plate (GPIO32)",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeColors.ForestMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                !telemetry.rainValid -> "FAULT"
                                telemetry.rainWetConfirmed -> "WET (30s)"
                                telemetry.rainRawWet -> "WET..."
                                else -> "DRY"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telemetry.rainWetConfirmed) HomeColors.WaterBlue else HomeColors.TextPrimary
                        )
                        Text(
                            text = when {
                                telemetry.rainWetConfirmed -> "Confirmed Wet"
                                telemetry.rainRawWet -> "Confirming 30s..."
                                else -> "No rain detected"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            color = HomeColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visual Strict AND-Condition Formula Strip
            Surface(
                color = HomeColors.PageBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GateConditionChip("Outdoor", status.isOutdoor)
                    Text("+", fontFamily = HomeInterFont, fontSize = 11.sp, color = HomeColors.TextMuted)
                    GateConditionChip(">=$rainThresholdPct%", forecastHigh)
                    Text("+", fontFamily = HomeInterFont, fontSize = 11.sp, color = HomeColors.TextMuted)
                    GateConditionChip("Wet 30s", telemetry.rainWetConfirmed)
                }
            }
        }
    }
}

@Composable
private fun GateConditionChip(label: String, met: Boolean) {
    Surface(
        color = if (met) HomeColors.WaterBg else HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (met) HomeColors.WaterBlue else HomeColors.CardBorder)
    ) {
        Text(
            text = "${if (met) "✓" else "○"} $label",
            fontFamily = HomeInterFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (met) HomeColors.WaterBlue else HomeColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EnvironmentSensorsRow(telemetry: DeviceTelemetry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EnvSensorTile(
            icon = Icons.Filled.Thermostat,
            label = "Temp",
            value = if (telemetry.dhtValid && telemetry.temperatureC != null) {
                String.format(Locale.US, "%.1f°C", telemetry.temperatureC)
            } else {
                "--"
            },
            band = telemetry.tempBand,
            subLabel = "DHT22",
            modifier = Modifier.weight(1f)
        )
        EnvSensorTile(
            icon = Icons.Filled.WaterDrop,
            label = "Humidity",
            value = if (telemetry.dhtValid && telemetry.humidityPct != null) {
                String.format(Locale.US, "%.0f%%", telemetry.humidityPct)
            } else {
                "--"
            },
            band = telemetry.humidityBand,
            subLabel = "DHT22",
            modifier = Modifier.weight(1f)
        )
        EnvSensorTile(
            icon = Icons.Filled.WbSunny,
            label = "Brightness",
            value = if (telemetry.lightValid && telemetry.lightBrightnessPct != null) {
                String.format(Locale.US, "%.0f%%", telemetry.lightBrightnessPct)
            } else {
                "--"
            },
            band = telemetry.lightBand,
            subLabel = "LDR Index",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EnvSensorTile(
    icon: ImageVector,
    label: String,
    value: String,
    band: String,
    subLabel: String,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (band) {
        "NORMAL" -> HomeColors.SuccessBg to HomeColors.SuccessGreen
        "HIGH", "LOW" -> HomeColors.WarningBg to HomeColors.WarningAmber
        else -> HomeColors.AlertBg to HomeColors.AlertRed
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HomeColors.ForestPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                color = HomeColors.TextSecondary
            )
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(color = bg, shape = RoundedCornerShape(50)) {
                Text(
                    text = band,
                    fontFamily = HomeInterFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                fontFamily = HomeInterFont,
                fontSize = 9.sp,
                color = HomeColors.TextMuted
            )
        }
    }
}

private fun formatRelativeAge(timestampMs: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Synced ${diffSec}s ago"
        diffSec < 3600 -> "Synced ${diffSec / 60}m ago"
        else -> "Last seen ${diffSec / 3600}h ago"
    }
}
