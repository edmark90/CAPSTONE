package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.R
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.iot.CropZoneConfig
import com.example.smartplantcare.iot.DailyHistorySnapshot
import com.example.smartplantcare.iot.DeviceRole
import com.example.smartplantcare.iot.HistoryDateOption
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.LiveDeviceSnapshot
import com.example.smartplantcare.iot.MyPlantsUiState
import com.example.smartplantcare.iot.SoilZoneTelemetry
import com.example.smartplantcare.util.NetworkMonitor
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private enum class HomeTrendTab(
    val label: String,
    val unit: String,
    val weight: Float,
    val lineColor: Color,
    val fillColor: Color,
    val pillColor: Color
) {
    TEMPERATURE(
        label = "Temperature",
        unit = "°C",
        weight = 1.16f,
        lineColor = Color(0xFF16A34A),
        fillColor = Color(0xFF22C55E),
        pillColor = Color(0xFF145333)
    ),
    HUMIDITY(
        label = "Humidity",
        unit = "%",
        weight = 0.94f,
        lineColor = Color(0xFF0284C7),
        fillColor = Color(0xFF38BDF8),
        pillColor = Color(0xFF075985)
    ),
    MOISTURE(
        label = "Moisture (Avg)",
        unit = "%",
        weight = 1.24f,
        lineColor = Color(0xFF15803D),
        fillColor = Color(0xFF34D399),
        pillColor = Color(0xFF145333)
    ),
    LIGHT(
        label = "Light",
        unit = "%",
        weight = 0.66f,
        lineColor = Color(0xFFD97706),
        fillColor = Color(0xFFFBBF24),
        pillColor = Color(0xFF92400E)
    )
}

@Composable
fun HomeScreen(
    userName: String,
    userEmail: String,
    unreadCount: Int = 0,
    plans: List<TreatmentPlan> = emptyList(),
    myPlantsUiState: MyPlantsUiState? = null,
    onOpenMyPlants: () -> Unit = {},
    onOpenHistory: () -> Unit = onOpenMyPlants,
    onSelectHistoryDate: (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onIdentifyClick: () -> Unit = {},
    onDoctorClick: () -> Unit = {},
    onPlanClick: (String) -> Unit = {}
) {
    val greetingLabel = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val greetingName = userName.ifBlank { "Plant Lover" }

    val snapshot = myPlantsUiState?.liveSnapshot
    val isConnected = snapshot != null
    val activeRole = myPlantsUiState?.activeRole ?: DeviceRole.OWNER

    // Freshness clock for accurate online / offline status
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(
        snapshot?.deviceId,
        snapshot?.receivedAtClientMs,
        snapshot?.status?.lastSeenAt,
        snapshot?.telemetry?.updatedAt
    ) {
        NetworkMonitor.refreshOnlineState()
        nowMs = System.currentTimeMillis()
        while (snapshot != null) {
            delay(2_000L)
            NetworkMonitor.refreshOnlineState()
            nowMs = System.currentTimeMillis()
        }
    }
    val isPhoneOnline by NetworkMonitor.isOnlineFlow.collectAsState()
    val phoneDisconnectedAtMs by NetworkMonitor.disconnectedAtMsFlow.collectAsState()
    val evalNowMs = if (!isPhoneOnline && phoneDisconnectedAtMs != null) {
        phoneDisconnectedAtMs!!
    } else {
        nowMs
    }
    val isDeviceOnline = snapshot != null && IotDomainLogic.isDeviceOnlineFresh(
        status = snapshot.status,
        nowMs = evalNowMs,
        receivedAtClientMs = snapshot.receivedAtClientMs,
        telemetryUpdatedAt = snapshot.telemetry.updatedAt
    )

    // Active 7-day treatment plans for Today's Care Tasks
    val activePlans = remember(plans) {
        plans.filter { it.isActive && it.computedCurrentDayIndex < 7 && it.completedCount < 7 }
    }

    // 7-day historical data for Live Sensor Trends & today's irrigation summary
    val historyWindow = myPlantsUiState?.historyDaysWindow
        ?: remember { IotDomainLogic.generateSevenDayWindowPht() }
    val todayKey = remember(historyWindow) {
        historyWindow.lastOrNull()?.dateKey ?: IotDomainLogic.formatPhDateKey(System.currentTimeMillis())
    }
    val selectedDateKey = myPlantsUiState?.selectedHistoryDateKey?.takeIf { key ->
        historyWindow.any { it.dateKey == key }
    } ?: todayKey
    val historyByDate = myPlantsUiState?.historyByDate.orEmpty()
    val selectedDaySnapshot = historyByDate[selectedDateKey]
    val todayHistorySnapshot = historyByDate[todayKey]

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F6))
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = HomeDimens.BottomBarClearance + 10.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ─── 1. Compact Editorial Top Header ─────────────────────────────────
        item(key = "home_header") {
            HomeTopHeader(
                greetingLabel = greetingLabel,
                userName = greetingName,
                unreadCount = unreadCount,
                onNotificationClick = onNotificationClick
            )
        }

        // ─── 2. Deep Botanical Hero Card (4-Column Climate & Tank Strip) ─────
        item(key = "hero_botanical_card") {
            SmartPlantCareHeroCard(
                snapshot = snapshot,
                role = activeRole,
                isConnected = isConnected,
                isDeviceOnline = isDeviceOnline,
                isPhoneOnline = isPhoneOnline,
                onOpenMyPlants = onOpenMyPlants
            )
        }

        // ─── 3. Crop Zones (3 Side-by-Side Telemetry Cards) ──────────────────
        item(key = "crop_zones_section") {
            HomeCropZonesSection(
                snapshot = snapshot,
                isConnected = isConnected,
                isDeviceOnline = isDeviceOnline,
                onManageZonesClick = onOpenMyPlants
            )
        }

        // ─── 4. Compact AI Leaf Health Scanner Card ──────────────────────────
        item(key = "ai_scanner_banner") {
            CompactLeafScannerCard(onScanClick = onScanClick)
        }

        // ─── 5. Live Sensor Trends (Interactive Area-Shaded Line Graph) ──────
        item(key = "live_sensor_trends") {
            LiveSensorTrendsCard(
                snapshot = snapshot,
                isConnected = isConnected,
                isDeviceOnline = isDeviceOnline && isPhoneOnline,
                nowMs = evalNowMs,
                historyWindow = historyWindow,
                selectedDateKey = selectedDateKey,
                selectedDay = selectedDaySnapshot,
                onSelectDate = onSelectHistoryDate,
                onOpenFullHistory = if (isConnected) onOpenHistory else onOpenMyPlants
            )
        }

        // ─── 6. Bottom 2-Column Bento: Today's Care Tasks | Auto Watering ────
        item(key = "bottom_bento_cards") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeCareTasksBentoCard(
                    activePlans = activePlans,
                    onScanClick = onScanClick,
                    onPlanClick = onPlanClick,
                    onDoctorClick = onDoctorClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                HomeAutomationBentoCard(
                    snapshot = snapshot,
                    isConnected = isConnected,
                    isDeviceOnline = isDeviceOnline && isPhoneOnline,
                    todayHistory = todayHistorySnapshot,
                    onClick = if (isConnected) onOpenHistory else onOpenMyPlants,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // ─── 7. Compact Quick Access Row (Preserves Identify Species & Records)
        item(key = "quick_access_strip") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactQuickLinkChip(
                    icon = Icons.Filled.LocalFlorist,
                    iconBg = HomeColors.SoftMint,
                    iconTint = HomeColors.ForestPrimary,
                    title = "Identify Species",
                    subtitle = "Plant lookup",
                    onClick = onIdentifyClick,
                    modifier = Modifier.weight(1f)
                )
                CompactQuickLinkChip(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    iconBg = HomeColors.WaterBg,
                    iconTint = HomeColors.WaterBlue,
                    title = "7-Day Records",
                    subtitle = "Graphs & logs",
                    onClick = if (isConnected) onOpenHistory else onOpenMyPlants,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── 1. Top Header ───────────────────────────────────────────────────────────

@Composable
private fun HomeTopHeader(
    greetingLabel: String,
    userName: String,
    unreadCount: Int,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = HomeColors.SurfaceWhite,
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = HomeColors.ForestDark.copy(alpha = 0.08f)
                    )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.appslogo2),
                        contentDescription = "SmartPlantCare",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greetingLabel,",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeColors.TextSecondary,
                    maxLines = 1
                )
                Text(
                    text = userName,
                    fontFamily = HomeInterFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = HomeColors.SurfaceWhite,
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .size(44.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = HomeColors.ForestDark.copy(alpha = 0.08f)
                    )
            ) {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = if (unreadCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = HomeColors.TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = (-1).dp)
                        .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                        .clip(CircleShape)
                        .background(HomeColors.AlertRed)
                        .border(1.5.dp, Color(0xFFF5F8F6), CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        fontFamily = HomeInterFont,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── 2. Deep Botanical Hero Card ─────────────────────────────────────────────

@Composable
private fun SmartPlantCareHeroCard(
    snapshot: LiveDeviceSnapshot?,
    role: DeviceRole,
    isConnected: Boolean,
    isDeviceOnline: Boolean,
    isPhoneOnline: Boolean,
    onOpenMyPlants: () -> Unit
) {
    val isLive = isConnected && isDeviceOnline && isPhoneOnline
    val isAuto = snapshot?.config?.autoIrrigationEnabled ?: true
    val boxTitle = if (isConnected) {
        snapshot?.meta?.displayName?.ifBlank { "Chili • Tomato • Eggplant" } ?: "Chili • Tomato • Eggplant"
    } else {
        "SmartPlantCare Box"
    }
    val locationLine = when {
        !isConnected -> "Pair your ESP32 box for automated 3-zone watering"
        isDeviceOnline && !isPhoneOnline -> "No phone internet • Check connection"
        !isDeviceOnline -> "ESP32 Offline • Tap to check connection"
        snapshot?.meta?.locationName?.isNotBlank() == true -> snapshot.meta.locationName
        snapshot?.weather?.locationName?.isNotBlank() == true -> snapshot.weather.locationName
        else -> "Live automated garden monitoring"
    }

    val heroGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0A3420),
                Color(0xFF145333),
                Color(0xFF0A2E1D)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0xFF0D3B24).copy(alpha = 0.24f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(heroGradient)
            .clickable(onClick = onOpenMyPlants)
    ) {
        // Subtle organic botanical radial glows
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color(0xFF22C55E).copy(alpha = 0.14f),
                radius = size.width * 0.44f,
                center = Offset(size.width * 0.88f, size.height * 0.08f)
            )
            drawCircle(
                color = Color(0xFF34D399).copy(alpha = 0.08f),
                radius = size.width * 0.30f,
                center = Offset(size.width * 0.08f, size.height * 0.94f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Pills Row: [● Online • Live] [OWNER]  .....  [👆 Manual Mode]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusBg = when {
                        !isConnected -> Color.White.copy(alpha = 0.14f)
                        isLive -> Color(0xFF10B981).copy(alpha = 0.28f)
                        else -> HomeColors.AlertRed.copy(alpha = 0.30f)
                    }
                    val statusDot = when {
                        !isConnected -> HomeColors.MintGlow
                        isLive -> Color(0xFF22C55E)
                        else -> Color(0xFFFCA5A5)
                    }
                    val statusText = when {
                        !isConnected -> "Ready to Pair"
                        isDeviceOnline && !isPhoneOnline -> "No Internet"
                        isDeviceOnline -> "Online • Live"
                        else -> "Offline"
                    }
                    val statusFg = when {
                        !isConnected -> Color.White
                        isLive -> Color(0xFF86EFAC)
                        else -> Color(0xFFFCA5A5)
                    }

                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, statusDot.copy(alpha = 0.30f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusDot)
                            )
                            Text(
                                text = statusText,
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusFg,
                                maxLines = 1
                            )
                        }
                    }

                    if (isConnected) {
                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                        ) {
                            Text(
                                text = role.label.uppercase(Locale.US),
                                fontFamily = HomeInterFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.94f),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (isConnected) {
                    Surface(
                        color = Color.White.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isAuto) Icons.Filled.AutoAwesome else Icons.Filled.TouchApp,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isAuto) "Auto Mode" else "Manual Mode",
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Box Title & Location Pin
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = boxTitle,
                    fontFamily = HomeInterFont,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = locationLine,
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Frosted 4-Column Climate & Tank Strip (or Connect CTA when unpaired)
            if (isConnected && snapshot != null) {
                val telemetry = snapshot.telemetry
                val tempText = if (isLive && telemetry.dhtValid && telemetry.temperatureC != null) {
                    String.format(Locale.US, "%.1f°C", telemetry.temperatureC)
                } else "--"
                val humText = if (isLive && telemetry.dhtValid && telemetry.humidityPct != null) {
                    String.format(Locale.US, "%.0f%%", telemetry.humidityPct)
                } else "--"
                val lightText = if (isLive && telemetry.lightValid && telemetry.lightBrightnessPct != null) {
                    String.format(Locale.US, "%.0f%%", telemetry.lightBrightnessPct)
                } else "--"
                val tankText = if (isLive && telemetry.reservoirValid && telemetry.reservoirPct != null) {
                    String.format(Locale.US, "%.0f%%", telemetry.reservoirPct)
                } else "--"

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.17f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeroMetricColumn(
                            icon = Icons.Filled.Thermostat,
                            label = "Temperature",
                            value = tempText,
                            modifier = Modifier.weight(1f)
                        )
                        HeroMetricDivider()
                        HeroMetricColumn(
                            icon = Icons.Filled.WaterDrop,
                            label = "Humidity",
                            value = humText,
                            modifier = Modifier.weight(1f)
                        )
                        HeroMetricDivider()
                        HeroMetricColumn(
                            icon = Icons.Filled.WbSunny,
                            label = "Light",
                            value = lightText,
                            modifier = Modifier.weight(1f)
                        )
                        HeroMetricDivider()
                        HeroMetricColumn(
                            icon = Icons.Filled.Opacity,
                            label = "Water Tank",
                            value = tankText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onOpenMyPlants,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = HomeColors.ForestPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "Connect SmartPlantCare Box",
                        fontFamily = HomeInterFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetricColumn(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            fontFamily = HomeInterFont,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (value == "--") Color.White.copy(alpha = 0.65f) else Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun HeroMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.18f))
    )
}

// ─── 3. Crop Zones (3 Side-by-Side Cards) ────────────────────────────────────

@Composable
private fun HomeCropZonesSection(
    snapshot: LiveDeviceSnapshot?,
    isConnected: Boolean,
    isDeviceOnline: Boolean,
    onManageZonesClick: () -> Unit
) {
    val zones = snapshot?.telemetry?.zones ?: listOf(
        SoilZoneTelemetry("tomato", "Tomato", "Zone 1", "Tomato Zone"),
        SoilZoneTelemetry("chili", "Chili", "Zone 2", "Chili Zone"),
        SoilZoneTelemetry("eggplant", "Eggplant", "Zone 3", "Eggplant Zone")
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Section Header: [Layers] Crop Zones  ..........  [Manage Zones >]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Crop Zones",
                    fontFamily = HomeInterFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
            }

            Surface(
                color = HomeColors.SurfaceWhite,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(50),
                        spotColor = HomeColors.ForestDark.copy(alpha = 0.06f)
                    )
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onManageZonesClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Manage Zones",
                        fontFamily = HomeInterFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.ForestPrimary
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // 3 Side-by-Side Crop Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            zones.forEach { zone ->
                val cfg = snapshot?.config?.cropConfig(zone.slug)
                    ?: CropZoneConfig(
                        slug = zone.slug,
                        startPct = when (zone.slug) {
                            "chili" -> 32f
                            "eggplant" -> 38f
                            else -> 35f
                        },
                        stopPct = when (zone.slug) {
                            "chili" -> 52f
                            "eggplant" -> 58f
                            else -> 55f
                        }
                    )
                val isWatering = isDeviceOnline &&
                    snapshot?.status?.isActivelyIrrigating == true &&
                    snapshot.status.activeValve.equals(zone.slug, ignoreCase = true)

                CropZoneCompactCard(
                    zone = zone,
                    cropConfig = cfg,
                    isConnected = isConnected,
                    isOnline = isDeviceOnline,
                    isWatering = isWatering,
                    onClick = onManageZonesClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun CropZoneCompactCard(
    zone: SoilZoneTelemetry,
    cropConfig: CropZoneConfig,
    isConnected: Boolean,
    isOnline: Boolean,
    isWatering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValidPct = isConnected && isOnline && zone.valid && zone.pct != null
    val displayPctInt = if (hasValidPct) zone.pct!!.roundToInt().coerceIn(0, 100) else null
    val isBelowStart = displayPctInt != null && displayPctInt < cropConfig.startPct

    val (badgeLabel, badgeBg, badgeFg, dotColor, barColor) = when {
        !isConnected -> ZoneCardStyle(
            label = "Setup",
            badgeBg = HomeColors.PlaceholderBg,
            badgeFg = HomeColors.TextSecondary,
            dot = HomeColors.TextMuted,
            bar = HomeColors.CardBorder
        )
        !isOnline -> ZoneCardStyle(
            label = "Offline",
            badgeBg = HomeColors.PlaceholderBg,
            badgeFg = HomeColors.TextSecondary,
            dot = HomeColors.TextMuted,
            bar = HomeColors.CardBorder
        )
        isWatering -> ZoneCardStyle(
            label = "Water",
            badgeBg = HomeColors.WaterBg,
            badgeFg = HomeColors.WaterBlue,
            dot = HomeColors.WaterBlue,
            bar = HomeColors.WaterBlue
        )
        !cropConfig.enabled -> ZoneCardStyle(
            label = "Off",
            badgeBg = HomeColors.PlaceholderBg,
            badgeFg = HomeColors.TextMuted,
            dot = HomeColors.TextMuted,
            bar = HomeColors.CardBorder
        )
        !zone.valid -> ZoneCardStyle(
            label = "Fault",
            badgeBg = Color(0xFFFEE2E2),
            badgeFg = HomeColors.AlertRed,
            dot = HomeColors.AlertRed,
            bar = HomeColors.AlertRed
        )
        zone.needsWater || isBelowStart -> ZoneCardStyle(
            label = "Dry",
            badgeBg = HomeColors.WarningBg,
            badgeFg = HomeColors.WarningAmber,
            dot = HomeColors.WarningAmber,
            bar = HomeColors.AlertRed
        )
        else -> ZoneCardStyle(
            label = "OK",
            badgeBg = HomeColors.SuccessBg,
            badgeFg = HomeColors.SuccessGreen,
            dot = Color(0xFF16A34A),
            bar = Color(0xFF16A34A)
        )
    }

    val progressFraction = when {
        displayPctInt != null -> (displayPctInt / 100f).coerceIn(0.05f, 1f)
        !zone.valid && isOnline && isConnected -> 0.25f
        else -> 0f
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Row 1: Dot + Crop Name + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = zone.displayName,
                        fontFamily = HomeInterFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontFamily = HomeInterFont,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Row 2: Moisture & Target
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = if (badgeLabel == "Fault") HomeColors.AlertRed else HomeColors.ForestMedium,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = HomeColors.TextSecondary, fontWeight = FontWeight.Normal)) {
                                append("Moisture: ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = if (displayPctInt == null) HomeColors.TextMuted else HomeColors.TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(if (displayPctInt != null) "$displayPctInt%" else "--")
                            }
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.TrackChanges,
                        contentDescription = null,
                        tint = HomeColors.ForestMedium,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = HomeColors.TextSecondary, fontWeight = FontWeight.Normal)) {
                                append("Target: ")
                            }
                            withStyle(SpanStyle(color = HomeColors.TextPrimary, fontWeight = FontWeight.Bold)) {
                                append("${cropConfig.startPct.toInt()}–${cropConfig.stopPct.toInt()}%")
                            }
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Rounded Horizontal Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE9F0EC))
            ) {
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

private data class ZoneCardStyle(
    val label: String,
    val badgeBg: Color,
    val badgeFg: Color,
    val dot: Color,
    val bar: Color
)

// ─── 4. Compact AI Leaf Health Scanner Card ──────────────────────────────────

@Composable
private fun CompactLeafScannerCard(onScanClick: () -> Unit) {
    val cardGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFFF1F9F4), HomeColors.SurfaceWhite)
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = HomeColors.SurfaceWhite,
        border = BorderStroke(1.dp, HomeColors.ForestPrimary.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.07f)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onScanClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF145333)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CenterFocusWeak,
                    contentDescription = "AI Leaf Scanner",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "AI Leaf Health Scanner",
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Detect plant issues and get a 7-day care plan",
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 14.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF145333),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onScanClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Scan Leaf",
                        fontFamily = HomeInterFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// ─── 5. Live Sensor Trends (7-Day Discontinuous Hourly Area Graph) ───────────

@Composable
private fun LiveSensorTrendsCard(
    snapshot: LiveDeviceSnapshot?,
    isConnected: Boolean,
    isDeviceOnline: Boolean,
    nowMs: Long,
    historyWindow: List<HistoryDateOption>,
    selectedDateKey: String,
    selectedDay: DailyHistorySnapshot?,
    onSelectDate: (String) -> Unit,
    onOpenFullHistory: () -> Unit
) {
    var activeTab by remember { mutableStateOf(HomeTrendTab.TEMPERATURE) }
    var inspectedHour by remember(selectedDateKey, activeTab) { mutableStateOf<Int?>(null) }
    var dateMenuExpanded by remember { mutableStateOf(false) }

    val selectedOption = remember(historyWindow, selectedDateKey) {
        historyWindow.firstOrNull { it.dateKey == selectedDateKey } ?: historyWindow.lastOrNull()
    }
    val isToday = selectedOption?.isToday == true
    val dropdownLabel = when {
        selectedOption == null -> "Last 24 Hours"
        selectedOption.isToday -> "Last 24 Hours"
        else -> "${selectedOption.monthShort} ${selectedOption.dayOfMonth}"
    }

    // Build real discontinuous hourly series from /deviceHistory (PHT 12 AM – 11:59 PM)
    val baseSeries = remember(selectedDay, activeTab, isToday, nowMs) {
        IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
            when (activeTab) {
                HomeTrendTab.TEMPERATURE -> seg?.temperatureC ?: rec.temperatureC
                HomeTrendTab.HUMIDITY -> seg?.humidityPct ?: rec.humidityPct
                HomeTrendTab.MOISTURE -> IotDomainLogic.averageValidSoilMoisture(
                    tomatoPct = seg?.tomatoPct ?: rec.tomatoPct,
                    chiliPct = seg?.chiliPct ?: rec.chiliPct,
                    eggplantPct = seg?.eggplantPct ?: rec.eggplantPct
                )
                HomeTrendTab.LIGHT -> seg?.lightPct ?: rec.lightPct
            }
        }
    }

    // If viewing Today and ESP32 is live right now, reflect real latest reading without ever bridging gaps
    val liveMetricValue = remember(snapshot, activeTab, isDeviceOnline) {
        if (!isDeviceOnline || snapshot == null) return@remember null
        val t = snapshot.telemetry
        when (activeTab) {
            HomeTrendTab.TEMPERATURE -> if (t.dhtValid) t.temperatureC else null
            HomeTrendTab.HUMIDITY -> if (t.dhtValid) t.humidityPct else null
            HomeTrendTab.MOISTURE -> IotDomainLogic.averageValidSoilMoisture(
                tomatoPct = if (t.tomato.valid) t.tomato.pct else null,
                chiliPct = if (t.chili.valid) t.chili.pct else null,
                eggplantPct = if (t.eggplant.valid) t.eggplant.pct else null
            )
            HomeTrendTab.LIGHT -> if (t.lightValid) t.lightBrightnessPct else null
        }
    }
    val liveEpoch = remember(snapshot, nowMs) {
        val rawTs = snapshot?.let { maxOf(it.telemetry.updatedAt, it.status.lastSeenAt) } ?: 0L
        if (rawTs >= 1_700_000_000_000L) rawTs else nowMs
    }
    val displaySeries = remember(baseSeries, liveMetricValue, liveEpoch, isToday, isDeviceOnline) {
        IotDomainLogic.appendLivePointIfFresh(
            base = baseSeries,
            liveValue = liveMetricValue,
            liveEpochMs = liveEpoch,
            isToday = isToday,
            isDeviceOnline = isDeviceOnline
        )
    }

    // Clean Y-Axis scaling (tight readable 2°C/4°C steps for Temperature, 0–100% for % metrics)
    val (minY, maxY, yTicks) = remember(activeTab, displaySeries) {
        if (activeTab == HomeTrendTab.TEMPERATURE) {
            val vals = displaySeries.segments.flatten().map { it.value }
            if (vals.isEmpty()) {
                Triple(28f, 34f, listOf(28f, 30f, 32f, 34f))
            } else {
                val rawMin = vals.min()
                val rawMax = vals.max()
                val lo = (floor((rawMin - 1.5f) / 2f) * 2f).coerceAtLeast(10f)
                val rawSpan = (rawMax + 1.5f) - lo
                val step = when {
                    rawSpan <= 6f -> 2f
                    rawSpan <= 12f -> 4f
                    else -> 6f
                }
                val hi = lo + step * 3f
                val adjustedHi = if (rawMax + 0.5f > hi) ceil((rawMax + 1f - lo) / step) * step + lo else hi
                val finalStep = (adjustedHi - lo) / 3f
                Triple(
                    lo,
                    adjustedHi,
                    listOf(lo, lo + finalStep, lo + 2f * finalStep, adjustedHi)
                )
            }
        } else {
            Triple(0f, 100f, listOf(0f, 25f, 50f, 75f, 100f))
        }
    }

    val hoursRecorded = selectedDay?.hours?.count { it.value.sampleCount > 0 } ?: 0
    val gapCount = displaySeries.gaps.size

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.07f)
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            // Top Row: [📈] Live Sensor Trends  ..........  [Last 24 Hours ▾]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Live Sensor Trends",
                        fontFamily = HomeInterFont,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    Surface(
                        color = HomeColors.SurfaceWhite,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, HomeColors.CardBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { dateMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = dropdownLabel,
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.TextPrimary,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Select Date",
                                tint = HomeColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dateMenuExpanded,
                        onDismissRequest = { dateMenuExpanded = false },
                        containerColor = HomeColors.SurfaceWhite
                    ) {
                        historyWindow.asReversed().forEach { option ->
                            val isSelected = option.dateKey == selectedDateKey
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (option.isToday) {
                                                "Last 24 Hours (Today)"
                                            } else {
                                                "${option.dayOfWeekShort}, ${option.monthShort} ${option.dayOfMonth}"
                                            },
                                            fontFamily = HomeInterFont,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) HomeColors.ForestPrimary else HomeColors.TextPrimary
                                        )
                                        if (option.hasTelemetry) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(HomeColors.EmeraldAccent)
                                            )
                                        }
                                        if (option.hasIrrigation) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(HomeColors.WaterBlue)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    dateMenuExpanded = false
                                    onSelectDate(option.dateKey)
                                }
                            )
                        }
                        HorizontalDivider(color = HomeColors.DividerLine)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Open Full 7-Day Records →",
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeColors.ForestPrimary
                                )
                            },
                            onClick = {
                                dateMenuExpanded = false
                                onOpenFullHistory()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(11.dp))

            // 4-Tab Rounded Segmented Pill Bar (Temperature | Humidity | Moisture (Avg) | Light)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF1F5F2))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeTrendTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    Box(
                        modifier = Modifier
                            .weight(tab.weight)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Color(0xFF145333) else Color.Transparent)
                            .clickable { activeTab = tab }
                            .padding(vertical = 7.5.dp, horizontal = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            fontFamily = HomeInterFont,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else HomeColors.TextSecondary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Smooth Area-Shaded Discontinuous Hourly Line Graph
            ModernTrendLineChart(
                series = displaySeries,
                minY = minY,
                maxY = maxY,
                yTicks = yTicks,
                yAxisUnit = activeTab.unit,
                lineColor = activeTab.lineColor,
                fillTopColor = activeTab.fillColor,
                pillBgColor = activeTab.pillColor,
                formatValue = { v ->
                    if (activeTab == HomeTrendTab.TEMPERATURE) {
                        String.format(Locale.US, "%.1f°C", v)
                    } else {
                        "${v.roundToInt()}%"
                    }
                },
                inspectedHour = inspectedHour,
                onInspectHour = { inspectedHour = it },
                emptyMessage = when {
                    !isConnected -> "Pair your ESP32 box to view hourly sensor trends."
                    isToday && !isDeviceOnline -> "ESP32 offline — no ${activeTab.label.lowercase(Locale.US)} readings recorded today."
                    else -> "No ${activeTab.label.lowercase(Locale.US)} readings recorded on this date."
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtle Minimal Footer Strip linking to full 7-Day Records
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusSummary = when {
                    !isConnected -> "Connect box for 7-day history"
                    hoursRecorded > 0 && gapCount > 0 -> "$hoursRecorded/24 hrs · $gapCount offline ${if (gapCount == 1) "gap" else "gaps"}"
                    hoursRecorded > 0 -> "$hoursRecorded/24 hrs recorded · PHT"
                    displaySeries.totalPoints > 0 -> "Live reading · Hourly log syncing"
                    else -> "Tap any date for 7-day records"
                }
                Text(
                    text = statusSummary,
                    fontFamily = HomeInterFont,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (gapCount > 0) HomeColors.AlertRed.copy(alpha = 0.85f) else HomeColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenFullHistory)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Full Records",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ─── 6. Bottom 2-Column Bento Cards ──────────────────────────────────────────

@Composable
private fun HomeCareTasksBentoCard(
    activePlans: List<TreatmentPlan>,
    onScanClick: () -> Unit,
    onPlanClick: (String) -> Unit,
    onDoctorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topPlan = activePlans.firstOrNull()

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                if (topPlan != null) onPlanClick(topPlan.id) else onScanClick()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EventAvailable,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Today's Care Tasks",
                    fontFamily = HomeInterFont,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (topPlan == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(HomeColors.SoftMint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = HomeColors.ForestPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No active treatment tasks",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary,
                            lineHeight = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Scan a leaf for a 7-day care plan.",
                            fontFamily = HomeInterFont,
                            fontSize = 9.5.sp,
                            color = HomeColors.TextSecondary,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = HomeColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                val dayIndex = topPlan.computedCurrentDayIndex.coerceIn(0, 6)
                val rawTask = topPlan.sevenDayPlan.getOrNull(dayIndex) ?: "Check plant leaves."
                val taskSummary = remember(rawTask) {
                    rawTask.substringAfter(":").trim().ifBlank { rawTask }
                }
                val progress = (topPlan.completedCount / 7f).coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = topPlan.plantName.ifBlank { "Plant" },
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = HomeColors.SoftMint,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Day ${dayIndex + 1}/7",
                                fontFamily = HomeInterFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.ForestPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = taskSummary,
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 13.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = HomeColors.ForestPrimary,
                        trackColor = HomeColors.SoftMint
                    )

                    if (activePlans.size > 1) {
                        Text(
                            text = "+${activePlans.size - 1} more in AI Doctor",
                            fontFamily = HomeInterFont,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeColors.ForestMedium,
                            modifier = Modifier.clickable(onClick = onDoctorClick)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAutomationBentoCard(
    snapshot: LiveDeviceSnapshot?,
    isConnected: Boolean,
    isDeviceOnline: Boolean,
    todayHistory: DailyHistorySnapshot?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayRuns = todayHistory?.irrigations.orEmpty()
    val lastRun = todayRuns.firstOrNull()
    val lastRunTimeText = remember(lastRun?.startedAt) {
        if (lastRun != null && lastRun.startedAt >= 1_700_000_000_000L) {
            IotDomainLogic.formatPhTimeLabel(lastRun.startedAt)
        } else null
    }

    val status = snapshot?.status
    val isAuto = snapshot?.config?.autoIrrigationEnabled == true

    val (title, subtitle, pillText, pillBg, pillFg, dropTint) = when {
        !isConnected -> AutomationCardInfo(
            title = "Pair Plant Box",
            subtitle = "Enable smart watering",
            pill = "Setup",
            pillBg = HomeColors.SoftMint,
            pillFg = HomeColors.ForestPrimary,
            dropTint = HomeColors.WaterBlue
        )
        !isDeviceOnline -> AutomationCardInfo(
            title = "Device Offline",
            subtitle = if (todayRuns.isNotEmpty()) "${todayRuns.size} run(s) logged today" else "Check ESP32 Wi-Fi",
            pill = "Offline",
            pillBg = HomeColors.AlertBg,
            pillFg = HomeColors.AlertRed,
            dropTint = HomeColors.TextMuted
        )
        status?.emergencyStop == true || status?.faultLockout == true -> AutomationCardInfo(
            title = "Safety Locked",
            subtitle = "Outputs paused",
            pill = "Locked",
            pillBg = HomeColors.AlertBg,
            pillFg = HomeColors.AlertRed,
            dropTint = HomeColors.AlertRed
        )
        status?.isActivelyIrrigating == true -> {
            val crop = status.activeValve.replaceFirstChar { it.uppercase(Locale.US) }
            AutomationCardInfo(
                title = "Watering $crop",
                subtitle = "${status.activePumpElapsedSec}s / ${status.activeTargetDurationSec}s",
                pill = "Active",
                pillBg = HomeColors.WaterBg,
                pillFg = HomeColors.WaterBlue,
                dropTint = HomeColors.WaterBlue
            )
        }
        status?.rainSuspensionActive == true -> AutomationCardInfo(
            title = "Paused by Rain",
            subtitle = "Resumes when dry",
            pill = "Rain Guard",
            pillBg = HomeColors.WaterBg,
            pillFg = HomeColors.WaterBlue,
            dropTint = HomeColors.WaterBlue
        )
        isAuto -> {
            val sub = when {
                todayRuns.isNotEmpty() && lastRunTimeText != null ->
                    "Today, $lastRunTimeText (${todayRuns.size}x)"
                todayRuns.isNotEmpty() ->
                    "${todayRuns.size} watering ${if (todayRuns.size == 1) "run" else "runs"} today"
                snapshot?.telemetry?.zones?.any { it.valid && it.needsWater } == true ->
                    "Dry zone detected"
                else -> "Monitoring soil levels"
            }
            AutomationCardInfo(
                title = "Automatic Watering",
                subtitle = sub,
                pill = "Auto ON",
                pillBg = Color(0xFFDCFCE7),
                pillFg = Color(0xFF15803D),
                dropTint = Color(0xFF0EA5E9)
            )
        }
        else -> {
            val sub = when {
                todayRuns.isNotEmpty() && lastRunTimeText != null ->
                    "Last: $lastRunTimeText (${todayRuns.size}x)"
                else -> "Tap to view watering log"
            }
            AutomationCardInfo(
                title = "Manual Watering",
                subtitle = sub,
                pill = "Manual",
                pillBg = HomeColors.SoftMint,
                pillFg = HomeColors.ForestPrimary,
                dropTint = Color(0xFF0EA5E9)
            )
        }
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "Automation",
                        fontFamily = HomeInterFont,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = pillBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = pillText,
                        fontFamily = HomeInterFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillFg,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(HomeColors.WaterBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = dropTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontFamily = HomeInterFont,
                        fontSize = 9.5.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private data class AutomationCardInfo(
    val title: String,
    val subtitle: String,
    val pill: String,
    val pillBg: Color,
    val pillFg: Color,
    val dropTint: Color
)

// ─── 7. Compact Quick Access Row ─────────────────────────────────────────────

@Composable
private fun CompactQuickLinkChip(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = HomeInterFont,
                    fontSize = 10.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = HomeColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
