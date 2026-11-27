package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.R
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.iot.CropZoneConfig
import com.example.smartplantcare.iot.DailyHistorySnapshot
import com.example.smartplantcare.iot.DeviceRole
import com.example.smartplantcare.iot.HistoryDateOption
import com.example.smartplantcare.iot.HistoryGranularity
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.LiveDeviceSnapshot
import com.example.smartplantcare.iot.MyPlantsUiState
import com.example.smartplantcare.iot.PointInspectionStatus
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
    val lineColor: Color,
    val fillColor: Color,
    val pillColor: Color
) {
    TEMPERATURE(
        label = "Temperature",
        unit = "°C",
        lineColor = Color(0xFF16A34A),
        fillColor = Color(0xFF22C55E),
        pillColor = Color(0xFF145333)
    ),
    HUMIDITY(
        label = "Humidity",
        unit = "%",
        lineColor = Color(0xFF0284C7),
        fillColor = Color(0xFF38BDF8),
        pillColor = Color(0xFF075985)
    ),
    MOISTURE(
        label = "Soil Moisture",
        unit = "%",
        lineColor = Color(0xFF15803D),
        fillColor = Color(0xFF34D399),
        pillColor = Color(0xFF145333)
    ),
    LIGHT(
        label = "Light",
        unit = "%",
        lineColor = Color(0xFFD97706),
        fillColor = Color(0xFFFBBF24),
        pillColor = Color(0xFF92400E)
    ),
    RESERVOIR(
        label = "Water Tank",
        unit = "%",
        lineColor = Color(0xFF0369A1),
        fillColor = Color(0xFF38BDF8),
        pillColor = Color(0xFF0C4A6E)
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
    onSelectGranularity: (HistoryGranularity) -> Unit = {},
    onSelectMinuteHour: (Int?) -> Unit = {},
    onSyncClick: () -> Unit = {},
    onClearSnackbar: () -> Unit = {},
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(myPlantsUiState?.snackbarMessage) {
        val msg = myPlantsUiState?.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearSnackbar()
        }
    }

    val snapshot = myPlantsUiState?.liveSnapshot
    val hasLinkedDevice = !myPlantsUiState?.activeDeviceId.isNullOrBlank() || snapshot != null
    val isCachedSnapshot = snapshot?.isFromLocalCache == true
    val activeRole = myPlantsUiState?.activeRole ?: DeviceRole.OWNER
    val isSyncing = myPlantsUiState?.isSyncingDevice == true

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
    val isDeviceOnline = snapshot != null && !isCachedSnapshot && IotDomainLogic.isDeviceOnlineFresh(
        status = snapshot.status,
        nowMs = evalNowMs,
        receivedAtClientMs = snapshot.receivedAtClientMs,
        telemetryUpdatedAt = snapshot.telemetry.updatedAt
    )

    // Saved plants for the Care Guides quick card
    val activePlans = plans

    // 7-day historical data (Philippine Time UTC+8)
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
    val historyGranularity = myPlantsUiState?.historyGranularity ?: HistoryGranularity.BY_HOURS
    val selectedMinuteHourFilter = myPlantsUiState?.selectedMinuteHourFilter

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = HomeDimens.ScreenHorizontalPadding,
                end = HomeDimens.ScreenHorizontalPadding,
                top = HomeDimens.ScreenTopPadding,
                bottom = HomeDimens.BottomBarClearance + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Clean Top Header
            item(key = "home_header") {
                HomeTopHeader(
                    greetingLabel = greetingLabel,
                    userName = greetingName,
                    unreadCount = unreadCount,
                    onNotificationClick = onNotificationClick
                )
            }

            // 2. Botanical Hero Card (Sync Button + Cached/Live Status + 2x2 Readable Climate Grid)
            item(key = "hero_botanical_card") {
                SmartPlantCareHeroCard(
                    snapshot = snapshot,
                    role = activeRole,
                    isConnected = hasLinkedDevice,
                    isCachedSnapshot = isCachedSnapshot,
                    isDeviceOnline = isDeviceOnline,
                    isPhoneOnline = isPhoneOnline,
                    isSyncing = isSyncing,
                    nowMs = evalNowMs,
                    onSyncClick = onSyncClick,
                    onOpenMyPlants = onOpenMyPlants
                )
            }

            // 3. Crop Zones (Shown ONLY when connected/linked to an ESP32 box)
            if (hasLinkedDevice && snapshot != null) {
                item(key = "crop_zones_section") {
                    HomeCropZonesSection(
                        snapshot = snapshot,
                        isConnected = true,
                        isDeviceOnline = isDeviceOnline && isPhoneOnline,
                        onManageZonesClick = onOpenMyPlants
                    )
                }
            }

            // 4. Live Sensor Trends (By Hours 12 AM–11 PM & By Minutes All Day / :00–:59)
            item(key = "live_sensor_trends") {
                LiveSensorTrendsCard(
                    snapshot = snapshot,
                    isConnected = snapshot != null,
                    isCachedSnapshot = isCachedSnapshot,
                    isDeviceOnline = isDeviceOnline && isPhoneOnline,
                    isPhoneOnline = isPhoneOnline,
                    nowMs = evalNowMs,
                    historyWindow = historyWindow,
                    selectedDateKey = selectedDateKey,
                    selectedDay = selectedDaySnapshot,
                    granularity = historyGranularity,
                    selectedMinuteHour = selectedMinuteHourFilter,
                    onSelectDate = onSelectHistoryDate,
                    onSelectGranularity = onSelectGranularity,
                    onSelectMinuteHour = onSelectMinuteHour,
                    onOpenFullHistory = if (snapshot != null) onOpenHistory else onOpenMyPlants
                )
            }

            // 5. Care Guides (Redesigned Full-Width Section — Irrigation Section Removed)
            item(key = "care_guides_section") {
                HomeCareGuidesSection(
                    savedPlants = activePlans,
                    onScanClick = onScanClick,
                    onPlanClick = onPlanClick,
                    onDoctorClick = onDoctorClick
                )
            }

            // 6. Plant AI Tools (Scan Leaf Health & Identify Species)
            item(key = "plant_ai_tools") {
                HomePlantAiToolsSection(
                    onScanClick = onScanClick,
                    onIdentifyClick = onIdentifyClick
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = HomeDimens.BottomBarClearance)
        )
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
                shape = RoundedCornerShape(16.dp),
                color = HomeColors.SurfaceWhite,
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .size(50.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = HomeColors.ForestDark.copy(alpha = 0.08f)
                    )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
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

            // Time-of-day accent for the greeting chip (stays within the app palette)
            val greetingIcon: ImageVector
            val greetingTint: Color
            val greetingBg: Color
            when {
                greetingLabel.contains("morning", ignoreCase = true) -> {
                    greetingIcon = Icons.Filled.WbSunny
                    greetingTint = HomeColors.WarningAmber
                    greetingBg = HomeColors.WarningBg
                }
                greetingLabel.contains("afternoon", ignoreCase = true) -> {
                    greetingIcon = Icons.Filled.WbTwilight
                    greetingTint = Color(0xFFEA580C)
                    greetingBg = Color(0xFFFFF7ED)
                }
                else -> {
                    greetingIcon = Icons.Filled.NightsStay
                    greetingTint = HomeColors.ForestMedium
                    greetingBg = HomeColors.SoftMint
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(greetingBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = greetingIcon,
                            contentDescription = null,
                            tint = greetingTint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = greetingLabel,
                        fontFamily = HomeInterFont,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = userName,
                    fontFamily = HomeInterFont,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.ForestDark,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = HomeColors.SurfaceWhite,
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .size(50.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(16.dp),
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
                        .offset(x = 0.dp, y = 0.dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(HomeColors.AlertRed)
                        .border(1.5.dp, HomeColors.PageBg, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        fontFamily = HomeInterFont,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── 2. Minimalist Botanical Hero Card (Sync + Live/Cached Status + 2x2 Grid) ─

@Composable
private fun SmartPlantCareHeroCard(
    snapshot: LiveDeviceSnapshot?,
    role: DeviceRole,
    isConnected: Boolean,
    isCachedSnapshot: Boolean,
    isDeviceOnline: Boolean,
    isPhoneOnline: Boolean,
    isSyncing: Boolean,
    nowMs: Long,
    onSyncClick: () -> Unit,
    onOpenMyPlants: () -> Unit
) {
    val isVerifiedLive = isConnected && snapshot != null && !isCachedSnapshot && isDeviceOnline && isPhoneOnline
    val canDisplayTelemetry = isVerifiedLive
    val isAuto = snapshot?.config?.autoIrrigationEnabled ?: true
    val latestHeartbeatMs = snapshot?.let { maxOf(it.status.lastSeenAt, it.telemetry.updatedAt) } ?: 0L

    val boxTitle = if (snapshot != null) {
        snapshot.meta.displayName.ifBlank { "Chili • Tomato • Eggplant" }
    } else {
        "SmartPlantCare Box"
    }

    val syncStatusSubtitle = when {
        !isConnected -> "Pair your ESP32 box for automated 3-zone watering"
        snapshot == null && isSyncing -> "Syncing your linked SmartPlantCare box..."
        !isPhoneOnline -> "No internet connection • Check Wi-Fi or mobile data"
        !isVerifiedLive -> {
            if (latestHeartbeatMs >= 1_700_000_000_000L) {
                val ageMin = ((nowMs - latestHeartbeatMs) / 60_000L).coerceAtLeast(1L)
                val ageText = if (ageMin < 60L) "${ageMin}m ago" else "${ageMin / 60L}h ago"
                "ESP32 offline • Last seen $ageText"
            } else {
                "ESP32 offline • Check box power or Wi-Fi"
            }
        }
        else -> {
            val loc = snapshot?.meta?.locationName?.takeIf { it.isNotBlank() }
                ?: snapshot?.weather?.locationName?.takeIf { it.isNotBlank() }
            val diffSec = if (latestHeartbeatMs >= 1_700_000_000_000L) {
                ((nowMs - latestHeartbeatMs) / 1000L).coerceAtLeast(0L)
            } else 0L
            val syncAge = when {
                diffSec < 8L -> "Live now"
                diffSec < 60L -> "Synced ${diffSec}s ago"
                else -> "Synced ${diffSec / 60L}m ago"
            }
            if (loc != null) "$loc • $syncAge" else "Live garden telemetry • $syncAge"
        }
    }

    val heroGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                HomeColors.HeroGradientStart,
                HomeColors.HeroGradientMid,
                HomeColors.HeroGradientEnd
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.18f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(heroGradient)
            .clickable(onClick = onOpenMyPlants)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: [Status Pill]  .....  [↻ Sync Button]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val (statusBg, statusDot, statusFg, statusText) = when {
                        !isConnected -> HeroBadgeSpec(
                            bg = Color.White.copy(alpha = 0.14f),
                            dot = HomeColors.MintGlow,
                            fg = Color.White,
                            text = "Ready to Pair"
                        )
                        isVerifiedLive -> HeroBadgeSpec(
                            bg = Color(0xFF10B981).copy(alpha = 0.26f),
                            dot = Color(0xFF34D399),
                            fg = Color(0xFFA7F3D0),
                            text = "Online • Live"
                        )
                        !isPhoneOnline -> HeroBadgeSpec(
                            bg = HomeColors.AlertRed.copy(alpha = 0.28f),
                            dot = Color(0xFFFCA5A5),
                            fg = Color(0xFFFECACA),
                            text = "No Internet"
                        )
                        else -> HeroBadgeSpec(
                            bg = HomeColors.AlertRed.copy(alpha = 0.28f),
                            dot = Color(0xFFFCA5A5),
                            fg = Color(0xFFFECACA),
                            text = "Offline"
                        )
                    }

                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, statusDot.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusFg,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (isConnected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, HomeColors.MintGlow.copy(alpha = 0.42f)),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 38.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !isSyncing, onClick = onSyncClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Sync ESP32",
                                    tint = HomeColors.MintGlow,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = if (isSyncing) "Syncing" else "Sync",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Box Title, Role/Mode & Sync Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = boxTitle,
                    fontFamily = HomeInterFont,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isConnected) {
                            "$syncStatusSubtitle • ${if (isAuto) "Auto" else "Manual"} (${role.label})"
                        } else {
                            syncStatusSubtitle
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Improved 2x2 Botanical Climate & Reservoir Tile Grid (Crisp Hierarchy, Zero Truncation)
            if (isConnected && snapshot != null) {
                val telemetry = snapshot.telemetry
                val tempVal = if (canDisplayTelemetry && telemetry.dhtValid) telemetry.temperatureC else null
                val humVal = if (canDisplayTelemetry && telemetry.dhtValid) telemetry.humidityPct else null
                val lightVal = if (canDisplayTelemetry && telemetry.lightValid) telemetry.lightBrightnessPct else null
                val tankVal = if (canDisplayTelemetry && telemetry.reservoirValid) telemetry.reservoirPct else null

                val tempText = tempVal?.let { String.format(Locale.US, "%.1f°C", it) } ?: "--"
                val humText = humVal?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"
                val lightText = lightVal?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"
                val tankText = tankVal?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"

                val tempLevel = tempVal?.let { (it / 50f).coerceIn(0.08f, 1f) } ?: 0f
                val humLevel = humVal?.let { (it / 100f).coerceIn(0.06f, 1f) } ?: 0f
                val lightLevel = lightVal?.let { (it / 100f).coerceIn(0.06f, 1f) } ?: 0f
                val tankLevel = tankVal?.let { (it / 100f).coerceIn(0.06f, 1f) } ?: 0f
                val isLowTank = tankVal != null && tankVal <= 20f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HeroSensorTile(
                            icon = Icons.Filled.Thermostat,
                            label = "Temperature",
                            value = tempText,
                            levelFraction = tempLevel,
                            accentColor = Color(0xFFFBBF24),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        HeroSensorTile(
                            icon = Icons.Filled.WaterDrop,
                            label = "Humidity",
                            value = humText,
                            levelFraction = humLevel,
                            accentColor = Color(0xFF38BDF8),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HeroSensorTile(
                            icon = Icons.Filled.WbSunny,
                            label = "Light",
                            value = lightText,
                            levelFraction = lightLevel,
                            accentColor = Color(0xFFFDE047),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        HeroSensorTile(
                            icon = Icons.Filled.Opacity,
                            label = "Water Tank",
                            value = tankText,
                            levelFraction = tankLevel,
                            accentColor = if (isLowTank) Color(0xFFFBBF24) else Color(0xFF34D399),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else if (!isConnected) {
                Button(
                    onClick = onOpenMyPlants,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = HomeColors.ForestPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Connect SmartPlantCare Box",
                        fontFamily = HomeInterFont,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private data class HeroBadgeSpec(
    val bg: Color,
    val dot: Color,
    val fg: Color,
    val text: String
)

@Composable
private fun HeroSensorTile(
    icon: ImageVector,
    label: String,
    value: String,
    levelFraction: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isLiveValue = value != "--"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accentColor.copy(alpha = if (isLiveValue) 0.20f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isLiveValue) accentColor else Color.White.copy(alpha = 0.62f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = value,
                    fontFamily = HomeInterFont,
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = if (isLiveValue) Color.White else Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    softWrap = false
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = label,
                    fontFamily = HomeInterFont,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.84f),
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    if (isLiveValue && levelFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(levelFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(accentColor.copy(alpha = 0.90f))
                        )
                    }
                }
            }
        }
    }
}

// ─── 3. Crop Zones (Always-Horizontal 3-Col Clean Cards Across All Devices) ──

@Composable
private fun HomeCropZonesSection(
    snapshot: LiveDeviceSnapshot,
    isConnected: Boolean,
    isDeviceOnline: Boolean,
    onManageZonesClick: () -> Unit
) {
    val zones = snapshot.telemetry.zones

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onManageZonesClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Manage Zones",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.ForestPrimary
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Always horizontal side-by-side across all devices — clean typography with zero "..." truncation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            zones.forEach { zone ->
                val cfg = resolveCropZoneConfig(snapshot, zone.slug)
                val isWatering = isDeviceOnline &&
                    snapshot.status.isActivelyIrrigating &&
                    snapshot.status.activeValve.equals(zone.slug, ignoreCase = true)

                CropZoneHorizontalCard(
                    zone = zone,
                    cropConfig = cfg,
                    photoUrl = snapshot.cropPhotos[zone.slug]?.url,
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

private fun resolveCropZoneConfig(snapshot: LiveDeviceSnapshot?, slug: String): CropZoneConfig {
    return snapshot?.config?.cropConfig(slug)
        ?: CropZoneConfig(
            slug = slug,
            startPct = when (slug) {
                "chili" -> 32f
                "eggplant" -> 38f
                else -> 35f
            },
            stopPct = when (slug) {
                "chili" -> 52f
                "eggplant" -> 58f
                else -> 55f
            }
        )
}

@Composable
private fun CropZoneHorizontalCard(
    zone: SoilZoneTelemetry,
    cropConfig: CropZoneConfig,
    photoUrl: String?,
    isConnected: Boolean,
    isOnline: Boolean,
    isWatering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canShowPct = isConnected && isOnline && zone.valid && zone.pct != null
    val displayPctInt = if (canShowPct) zone.pct!!.roundToInt().coerceIn(0, 100) else null
    val isBelowStart = displayPctInt != null && displayPctInt < cropConfig.startPct

    val (badgeLabel, badgeBg, badgeFg, dotColor, barColor) = when {
        !isConnected -> ZoneCardStyle("Setup", HomeColors.PlaceholderBg, HomeColors.TextSecondary, HomeColors.TextMuted, HomeColors.CardBorder)
        !isOnline -> ZoneCardStyle("Offline", HomeColors.PlaceholderBg, HomeColors.TextSecondary, HomeColors.TextMuted, HomeColors.CardBorder)
        isWatering -> ZoneCardStyle("Water", HomeColors.WaterBg, HomeColors.WaterBlue, HomeColors.WaterBlue, HomeColors.WaterBlue)
        !cropConfig.enabled -> ZoneCardStyle("Off", HomeColors.PlaceholderBg, HomeColors.TextMuted, HomeColors.TextMuted, HomeColors.CardBorder)
        !zone.valid -> ZoneCardStyle("Fault", HomeColors.AlertBg, HomeColors.AlertRed, HomeColors.AlertRed, HomeColors.AlertRed)
        zone.needsWater || isBelowStart -> ZoneCardStyle("Dry", HomeColors.WarningBg, HomeColors.WarningAmber, HomeColors.WarningAmber, HomeColors.WarningAmber)
        else -> ZoneCardStyle("Optimal", HomeColors.SuccessBg, HomeColors.SuccessGreen, HomeColors.EmeraldAccent, HomeColors.EmeraldAccent)
    }

    val progressFraction = when {
        displayPctInt != null -> (displayPctInt / 100f).coerceIn(0.06f, 1f)
        !zone.valid && isOnline && isConnected -> 0.25f
        else -> 0f
    }

    val context = LocalContext.current
    val hasPhoto = !photoUrl.isNullOrBlank()

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Clean Small Crop Photo Thumbnail (30.dp) + Live Moisture %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasPhoto) HomeColors.PlaceholderBg else HomeColors.SoftMint,
                    border = BorderStroke(
                        1.dp,
                        if (hasPhoto) HomeColors.CardBorder else HomeColors.SoftMintStrong
                    ),
                    modifier = Modifier.size(30.dp)
                ) {
                    if (hasPhoto) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "${zone.displayName} photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Spa,
                                contentDescription = null,
                                tint = HomeColors.ForestMedium,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = if (displayPctInt != null) "$displayPctInt%" else "--",
                    fontFamily = HomeInterFont,
                    fontSize = 17.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = if (displayPctInt != null) HomeColors.TextPrimary else HomeColors.TextMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Full-width crop name + compact status pill below (zero "..." truncation)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = zone.displayName,
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = badgeLabel,
                            fontFamily = HomeInterFont,
                            fontSize = 10.5.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clean moisture progress track + compact target range (no ellipsis)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(HomeColors.PillTrackBg)
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

                Text(
                    text = "${cropConfig.startPct.toInt()}–${cropConfig.stopPct.toInt()}% goal",
                    fontFamily = HomeInterFont,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeColors.TextSecondary,
                    maxLines = 1,
                    softWrap = false
                )
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

// ─── 4. Live Sensor Trends (Hours 12 AM–11 PM & Minutes All Day / :00–:59) ──

@Composable
private fun LiveSensorTrendsCard(
    snapshot: LiveDeviceSnapshot?,
    isConnected: Boolean,
    isCachedSnapshot: Boolean,
    isDeviceOnline: Boolean,
    isPhoneOnline: Boolean,
    nowMs: Long,
    historyWindow: List<HistoryDateOption>,
    selectedDateKey: String,
    selectedDay: DailyHistorySnapshot?,
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    onSelectDate: (String) -> Unit,
    onSelectGranularity: (HistoryGranularity) -> Unit,
    onSelectMinuteHour: (Int?) -> Unit,
    onOpenFullHistory: () -> Unit
) {
    var activeTab by remember { mutableStateOf(HomeTrendTab.TEMPERATURE) }
    var inspectedSlot by remember(
        selectedDateKey,
        activeTab,
        granularity,
        selectedMinuteHour
    ) { mutableStateOf<Int?>(null) }
    var dateMenuExpanded by remember { mutableStateOf(false) }

    val selectedOption = remember(historyWindow, selectedDateKey) {
        historyWindow.firstOrNull { it.dateKey == selectedDateKey } ?: historyWindow.lastOrNull()
    }
    val isToday = selectedOption?.isToday == true
    val dropdownLabel = when {
        selectedOption == null -> "Today"
        selectedOption.isToday -> "Today"
        else -> "${selectedOption.monthShort} ${selectedOption.dayOfMonth}"
    }

    val liveMetricValue = remember(snapshot, activeTab, isDeviceOnline, isCachedSnapshot) {
        if (!isDeviceOnline || isCachedSnapshot || snapshot == null) return@remember null
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
            HomeTrendTab.RESERVOIR -> if (t.reservoirValid) t.reservoirPct else null
        }
    }
    val liveEpoch = remember(snapshot, nowMs) {
        val rawTs = snapshot?.let { maxOf(it.telemetry.updatedAt, it.status.lastSeenAt) } ?: 0L
        if (rawTs >= 1_700_000_000_000L) rawTs else nowMs
    }

    val displaySeries = remember(
        selectedDay,
        activeTab,
        granularity,
        selectedMinuteHour,
        isToday,
        nowMs,
        liveMetricValue,
        liveEpoch,
        isDeviceOnline,
        isCachedSnapshot
    ) {
        if (granularity == HistoryGranularity.BY_HOURS) {
            val base = IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
                when (activeTab) {
                    HomeTrendTab.TEMPERATURE -> seg?.temperatureC ?: rec.temperatureC
                    HomeTrendTab.HUMIDITY -> seg?.humidityPct ?: rec.humidityPct
                    HomeTrendTab.MOISTURE -> IotDomainLogic.averageValidSoilMoisture(
                        tomatoPct = seg?.tomatoPct ?: rec.tomatoPct,
                        chiliPct = seg?.chiliPct ?: rec.chiliPct,
                        eggplantPct = seg?.eggplantPct ?: rec.eggplantPct
                    )
                    HomeTrendTab.LIGHT -> seg?.lightPct ?: rec.lightPct
                    HomeTrendTab.RESERVOIR -> seg?.reservoirPct ?: rec.reservoirPct
                }
            }
            IotDomainLogic.appendLivePointIfFresh(
                base = base,
                liveValue = liveMetricValue,
                liveEpochMs = liveEpoch,
                isToday = isToday,
                isDeviceOnline = isDeviceOnline,
                isFromLocalCache = isCachedSnapshot
            )
        } else {
            val base = IotDomainLogic.buildSegmentedMinuteSeries(
                day = selectedDay,
                selectedHour = selectedMinuteHour,
                isToday = isToday,
                nowMs = nowMs
            ) { rec ->
                when (activeTab) {
                    HomeTrendTab.TEMPERATURE -> rec.temperatureC
                    HomeTrendTab.HUMIDITY -> rec.humidityPct
                    HomeTrendTab.MOISTURE -> IotDomainLogic.averageValidSoilMoisture(
                        tomatoPct = rec.tomatoPct,
                        chiliPct = rec.chiliPct,
                        eggplantPct = rec.eggplantPct
                    )
                    HomeTrendTab.LIGHT -> rec.lightPct
                    HomeTrendTab.RESERVOIR -> rec.reservoirPct
                }
            }
            IotDomainLogic.appendLiveMinutePointIfFresh(
                base = base,
                selectedHour = selectedMinuteHour,
                liveValue = liveMetricValue,
                liveEpochMs = liveEpoch,
                isToday = isToday,
                isDeviceOnline = isDeviceOnline,
                isFromLocalCache = isCachedSnapshot
            )
        }
    }

    val (minY, maxY, yTicks) = remember(activeTab, displaySeries) {
        if (activeTab == HomeTrendTab.TEMPERATURE) {
            val vals = displaySeries.segments.flatten().map { it.value }
            if (vals.isEmpty()) {
                Triple(24f, 36f, listOf(24f, 28f, 32f, 36f))
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

    val activeMinuteHours = remember(selectedDay?.minutes) {
        selectedDay?.minutes?.entries
            ?.filter { it.value.sampleCount > 0 }
            ?.map { (it.key / 60).coerceIn(0, 23) }
            ?.toSet()
            .orEmpty()
    }

    val hoursRecorded = selectedDay?.hours?.count { it.value.sampleCount > 0 } ?: 0
    val minutesRecorded = remember(selectedDay, selectedMinuteHour) {
        val mins = selectedDay?.minutes.orEmpty()
        if (selectedMinuteHour == null) mins.count { it.value.sampleCount > 0 }
        else mins.count { (it.key / 60) == selectedMinuteHour && it.value.sampleCount > 0 }
    }
    val gapCount = displaySeries.gaps.size

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.05f)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Sensor Trends + Date Picker
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
                        text = "Sensor Trends",
                        fontFamily = HomeInterFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    Surface(
                        color = HomeColors.PageBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, HomeColors.CardBorder),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { dateMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = dropdownLabel,
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.TextPrimary,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Select Date",
                                tint = HomeColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
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
                                                "Today (${option.monthShort} ${option.dayOfMonth})"
                                            } else {
                                                "${option.dayOfWeekShort}, ${option.monthShort} ${option.dayOfMonth}"
                                            },
                                            fontFamily = HomeInterFont,
                                            fontSize = 13.sp,
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
                                    text = "Open 7-Day Records",
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.5.sp,
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

            Spacer(modifier = Modifier.height(12.dp))

            // By Hours / By Minutes Segmented Toggle (Clean, Zero-Ellipsis Labels)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HomeColors.PillTrackBg)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    HistoryGranularity.BY_HOURS to ("By Hours" to "12 AM – 11 PM"),
                    HistoryGranularity.BY_MINUTES to ("By Minutes" to ":00 – :59")
                ).forEach { (mode, labels) ->
                    val selected = granularity == mode
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) HomeColors.ForestPrimary else Color.Transparent)
                            .clickable { onSelectGranularity(mode) }
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = labels.first,
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) Color.White else HomeColors.TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = labels.second,
                            fontFamily = HomeInterFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) Color.White.copy(alpha = 0.84f) else HomeColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Hour zoom strip when By Minutes is active (All Day + 12 AM..11 PM)
            if (granularity == HistoryGranularity.BY_MINUTES) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item(key = "home_min_all_day") {
                        val allChosen = selectedMinuteHour == null
                        Surface(
                            color = if (allChosen) HomeColors.ForestPrimary else HomeColors.PageBg,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (allChosen) HomeColors.ForestPrimary else HomeColors.CardBorder
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectMinuteHour(null) }
                        ) {
                            Text(
                                text = "All Day",
                                fontFamily = HomeInterFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (allChosen) Color.White else HomeColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items((0..23).toList(), key = { "home_min_hr_$it" }) { hr ->
                        val chosen = selectedMinuteHour == hr
                        val hasData = hr in activeMinuteHours
                        val shortLbl = when {
                            hr == 0 -> "12 AM"
                            hr < 12 -> "$hr AM"
                            hr == 12 -> "12 PM"
                            else -> "${hr - 12} PM"
                        }
                        Surface(
                            color = if (chosen) HomeColors.ForestPrimary else HomeColors.PageBg,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    chosen -> HomeColors.ForestPrimary
                                    hasData -> HomeColors.EmeraldAccent.copy(alpha = 0.45f)
                                    else -> HomeColors.CardBorder
                                }
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectMinuteHour(hr) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (hasData) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.5.dp)
                                            .clip(CircleShape)
                                            .background(if (chosen) HomeColors.MintGlow else HomeColors.EmeraldAccent)
                                    )
                                }
                                Text(
                                    text = shortLbl,
                                    fontFamily = HomeInterFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (chosen || hasData) FontWeight.Bold else FontWeight.Medium,
                                    color = if (chosen) Color.White else HomeColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Sensor Metric Chips (Zero Truncation at Any Font Scale)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(HomeTrendTab.entries, key = { it.name }) { tab ->
                    val selected = tab == activeTab
                    Surface(
                        color = if (selected) HomeColors.ForestPrimary else HomeColors.PageBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (selected) HomeColors.ForestPrimary else HomeColors.CardBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { activeTab = tab }
                    ) {
                        Text(
                            text = tab.label,
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) Color.White else HomeColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ModernTrendLineChart(
                series = displaySeries,
                granularity = granularity,
                selectedMinuteHour = selectedMinuteHour,
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
                inspectedSlot = inspectedSlot,
                onInspectSlot = { inspectedSlot = it },
                resolveMissingStatus = { slot ->
                    if (granularity == HistoryGranularity.BY_HOURS) {
                        IotDomainLogic.classifyHourlyInspectionStatus(
                            day = selectedDay,
                            hour = slot,
                            hasSensorValue = false,
                            isToday = isToday,
                            isPhoneOnline = isPhoneOnline,
                            nowMs = nowMs
                        )
                    } else {
                        val minOfDay = if (selectedMinuteHour != null) {
                            selectedMinuteHour.coerceIn(0, 23) * 60 + slot.coerceIn(0, 59)
                        } else {
                            slot.coerceIn(0, 1439)
                        }
                        IotDomainLogic.classifyMinuteInspectionStatus(
                            day = selectedDay,
                            minuteOfDay = minOfDay,
                            hasSensorValue = false,
                            isToday = isToday,
                            isPhoneOnline = isPhoneOnline,
                            nowMs = nowMs
                        )
                    }
                },
                emptyMessage = when {
                    !isConnected -> "Pair your ESP32 box to view historical sensor trends."
                    granularity == HistoryGranularity.BY_MINUTES &&
                        (selectedDay?.minutes?.isEmpty() != false) &&
                        hoursRecorded > 0 ->
                        "Hourly records exist for this day, but per-minute records were not uploaded yet."
                    granularity == HistoryGranularity.BY_MINUTES && selectedMinuteHour != null ->
                        "No ${activeTab.label.lowercase(Locale.US)} minute records for ${IotDomainLogic.formatHourRangeLabel(selectedMinuteHour)}."
                    isToday && !isDeviceOnline ->
                        "ESP32 offline — no ${activeTab.label.lowercase(Locale.US)} readings recorded yet today."
                    else -> "No ${activeTab.label.lowercase(Locale.US)} readings recorded on this date."
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer with Single Authoritative 7-Day Records Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusSummary = when {
                    !isConnected -> "Connect box for 7-day PHT history"
                    granularity == HistoryGranularity.BY_HOURS && hoursRecorded > 0 && gapCount > 0 ->
                        "$hoursRecorded/24 hrs · $gapCount ${if (gapCount == 1) "gap" else "gaps"}"
                    granularity == HistoryGranularity.BY_HOURS && hoursRecorded > 0 ->
                        "$hoursRecorded/24 hrs recorded · PHT"
                    granularity == HistoryGranularity.BY_MINUTES && minutesRecorded > 0 && gapCount > 0 ->
                        "$minutesRecorded min · $gapCount ${if (gapCount == 1) "gap" else "gaps"}"
                    granularity == HistoryGranularity.BY_MINUTES && minutesRecorded > 0 ->
                        "$minutesRecorded min recorded · PHT"
                    displaySeries.totalPoints > 0 -> "Live reading · PHT"
                    else -> "7-day PHT retention"
                }
                Text(
                    text = statusSummary,
                    fontFamily = HomeInterFont,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (gapCount > 0) HomeColors.AlertRed.copy(alpha = 0.88f) else HomeColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = HomeColors.SoftMint,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 34.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenFullHistory)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "7-Day Records",
                            fontFamily = HomeInterFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
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
        }
    }
}

// ─── 5. Redesigned Full-Width Care Guides Section ────────────────────────────

@Composable
private fun HomeCareGuidesSection(
    savedPlants: List<TreatmentPlan>,
    onScanClick: () -> Unit,
    onPlanClick: (String) -> Unit,
    onDoctorClick: () -> Unit
) {
    val topPlan = savedPlants.firstOrNull()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    imageVector = Icons.Outlined.Spa,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Care Guides",
                    fontFamily = HomeInterFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
            }

            if (savedPlants.isNotEmpty()) {
                Surface(
                    color = HomeColors.SurfaceWhite,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 34.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDoctorClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "View All (${savedPlants.size})",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeColors.ForestPrimary
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = HomeColors.ForestPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = HomeColors.ForestDark.copy(alpha = 0.05f)
                )
                .clip(RoundedCornerShape(22.dp))
                .clickable {
                    if (topPlan != null) onPlanClick(topPlan.id) else onScanClick()
                }
        ) {
            if (topPlan == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(HomeColors.SoftMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Spa,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "No saved care guides yet",
                                fontFamily = HomeInterFont,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.TextPrimary
                            )
                            Text(
                                text = "Scan a plant leaf to save its diagnosis, causes, and tailored care tips.",
                                fontFamily = HomeInterFont,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = HomeColors.TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onScanClick,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusWeak,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan a Leaf",
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                val diseaseKey = remember(topPlan.className, topPlan.diseaseName, topPlan.isHealthy) {
                    resolveDiseaseRepositoryKey(topPlan.className, topPlan.diseaseName, topPlan.isHealthy)
                }
                val careSummary = remember(diseaseKey) {
                    com.example.smartplantcare.ML.DiseaseRepository.getDiseaseInfo(diseaseKey).careTips
                }
                val imageRequest = remember(topPlan.imageUrl) {
                    if (topPlan.imageUrl.isNotBlank()) {
                        val data: Any = if (topPlan.imageUrl.startsWith("/")) File(topPlan.imageUrl) else topPlan.imageUrl
                        ImageRequest.Builder(context)
                            .data(data)
                            .size(200)
                            .crossfade(false)
                            .build()
                    } else null
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(HomeColors.SoftMint),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageRequest != null) {
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = topPlan.plantName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.LocalFlorist,
                                    contentDescription = null,
                                    tint = HomeColors.ForestPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = topPlan.plantName.ifBlank { "Saved Plant" },
                                    fontFamily = HomeInterFont,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (topPlan.isHealthy) HomeColors.SuccessBg else HomeColors.AlertBg,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = if (topPlan.isHealthy) "Healthy" else "Needs Care",
                                        fontFamily = HomeInterFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (topPlan.isHealthy) HomeColors.SuccessGreen else HomeColors.AlertRed,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (!topPlan.isHealthy && topPlan.diseaseName.isNotBlank()) {
                                Text(
                                    text = topPlan.diseaseName,
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HomeColors.ForestMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = careSummary,
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                color = HomeColors.TextSecondary,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (savedPlants.size > 1) {
                                "Latest guide • +${savedPlants.size - 1} more in AI Doctor"
                            } else {
                                "Tap to read full guide or listen"
                            },
                            fontFamily = HomeInterFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = HomeColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Open Guide",
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.ForestPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 6. Plant AI Tools (Scan Leaf & Identify Species — Clean & Non-Redundant) ─

@Composable
private fun HomePlantAiToolsSection(
    onScanClick: () -> Unit,
    onIdentifyClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = HomeColors.ForestPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Quick AI Tools",
                fontFamily = HomeInterFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val stackTools = maxWidth < 340.dp || fontScale >= 1.25f

            if (stackTools) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AiToolActionCard(
                        icon = Icons.Filled.CenterFocusWeak,
                        iconBg = HomeColors.ForestPrimary,
                        iconTint = Color.White,
                        title = "Scan Leaf Health",
                        subtitle = "AI leaf diagnosis & care tips",
                        horizontalLayout = true,
                        onClick = onScanClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AiToolActionCard(
                        icon = Icons.Filled.LocalFlorist,
                        iconBg = HomeColors.SoftMint,
                        iconTint = HomeColors.ForestPrimary,
                        title = "Identify Species",
                        subtitle = "Instant camera plant lookup",
                        horizontalLayout = true,
                        onClick = onIdentifyClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiToolActionCard(
                        icon = Icons.Filled.CenterFocusWeak,
                        iconBg = HomeColors.ForestPrimary,
                        iconTint = Color.White,
                        title = "Scan Leaf Health",
                        subtitle = "Diagnose & care tips",
                        horizontalLayout = false,
                        onClick = onScanClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    AiToolActionCard(
                        icon = Icons.Filled.LocalFlorist,
                        iconBg = HomeColors.SoftMint,
                        iconTint = HomeColors.ForestPrimary,
                        title = "Identify Species",
                        subtitle = "Camera plant lookup",
                        horizontalLayout = false,
                        onClick = onIdentifyClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun AiToolActionCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    horizontalLayout: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = modifier
            .defaultMinSize(minHeight = 68.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = HomeColors.ForestDark.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        if (horizontalLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = HomeInterFont,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontFamily = HomeInterFont,
                        fontSize = 11.5.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 15.sp,
                        maxLines = 2
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = HomeColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = HomeColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        fontFamily = HomeInterFont,
                        fontSize = 13.5.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 2
                    )
                    Text(
                        text = subtitle,
                        fontFamily = HomeInterFont,
                        fontSize = 11.5.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 15.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
