package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.iot.DailyHistorySnapshot
import com.example.smartplantcare.iot.DeviceConfig
import com.example.smartplantcare.iot.HistoryDateOption
import com.example.smartplantcare.iot.HistoryGranularity
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.IrrigationHistoryItem
import com.example.smartplantcare.iot.LiveDeviceSnapshot
import com.example.smartplantcare.iot.OfflineGapWindow
import com.example.smartplantcare.iot.PointInspectionStatus
import com.example.smartplantcare.iot.SegmentedChartSeries
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

// ─── Dedicated Chart Palette (Harmonized with HomeColors) ────────────────────

private object HistoryChartColors {
    val TomatoLine = Color(0xFFE11D48)      // Botanical Crimson
    val ChiliLine = Color(0xFF10B981)       // HomeColors.EmeraldAccent
    val EggplantLine = Color(0xFF7C3AED)    // Deep Botanical Plum

    val TempLine = Color(0xFFEA580C)        // Warm Terracotta Orange
    val HumidityLine = Color(0xFF0284C7)    // Water Blue
    val LightLine = Color(0xFFD97706)       // Sun Amber
    val TankLine = Color(0xFF0369A1)        // Deep Reservoir Blue
    val RainSensorLine = Color(0xFF0D9488)  // Rain Plate Teal
    val RainLine = Color(0xFF2563EB)        // Forecast Rain Blue

    val OfflineBandBg = Color(0xFFDC2626).copy(alpha = 0.065f)
    val OfflineBandBorder = Color(0xFFDC2626).copy(alpha = 0.28f)
    val GridLine = Color(0xFFE5ECE8)
}

private enum class SoilFilterTab(val slug: String, val label: String, val dotColor: Color) {
    ALL("all", "All Crops", HomeColors.ForestPrimary),
    TOMATO("tomato", "Tomato", HistoryChartColors.TomatoLine),
    CHILI("chili", "Chili", HistoryChartColors.ChiliLine),
    EGGPLANT("eggplant", "Eggplant", HistoryChartColors.EggplantLine)
}

private enum class EnvMetricTab(
    val label: String,
    val unit: String,
    val icon: ImageVector,
    val color: Color
) {
    TEMPERATURE("Temp", "°C", Icons.Filled.Thermostat, HistoryChartColors.TempLine),
    HUMIDITY("Humidity", "%", Icons.Filled.Opacity, HistoryChartColors.HumidityLine),
    LIGHT("Light", "%", Icons.Filled.WbSunny, HistoryChartColors.LightLine),
    RESERVOIR("Water Tank", "%", Icons.Filled.WaterDrop, HistoryChartColors.TankLine),
    RAIN_SENSOR("Rain Plate", "%", Icons.Filled.Umbrella, HistoryChartColors.RainSensorLine),
    RAIN("Rain Forecast", "%", Icons.Filled.Cloud, HistoryChartColors.RainLine)
}

private data class ChartLineSpec(
    val id: String,
    val label: String,
    val color: Color,
    val series: SegmentedChartSeries
)

private data class ThresholdGuide(
    val value: Float,
    val label: String,
    val color: Color
)

// ─── PAGE 7: 7-Day Historical Monitoring & Irrigation Records ────────────────

@Composable
internal fun MyPlantsHistoryScreen(
    snapshot: LiveDeviceSnapshot,
    historyDaysWindow: List<HistoryDateOption>,
    selectedDateKey: String,
    historyByDate: Map<String, DailyHistorySnapshot>,
    historyGranularity: HistoryGranularity = HistoryGranularity.BY_HOURS,
    selectedMinuteHourFilter: Int? = null,
    isLoadingHistory: Boolean,
    isPhoneOnline: Boolean = true,
    onSelectDate: (String) -> Unit,
    onSelectGranularity: (HistoryGranularity) -> Unit = {},
    onSelectMinuteHour: (Int?) -> Unit = {},
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val selectedOption = remember(historyDaysWindow, selectedDateKey) {
        historyDaysWindow.firstOrNull { it.dateKey == selectedDateKey }
            ?: historyDaysWindow.lastOrNull()
    }
    val selectedDay = historyByDate[selectedDateKey]
    val isToday = selectedOption?.isToday == true

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isToday, snapshot.receivedAtClientMs, selectedDay?.updatedAt) {
        nowMs = System.currentTimeMillis()
        while (isToday) {
            delay(15_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val isVerifiedDeviceLive = !snapshot.isFromLocalCache &&
        isPhoneOnline &&
        IotDomainLogic.isDeviceOnlineFresh(
            status = snapshot.status,
            nowMs = nowMs,
            receivedAtClientMs = snapshot.receivedAtClientMs,
            telemetryUpdatedAt = snapshot.telemetry.updatedAt
        )
    val liveEpochMs = remember(snapshot.telemetry.updatedAt, snapshot.status.lastSeenAt, nowMs) {
        val raw = maxOf(snapshot.telemetry.updatedAt, snapshot.status.lastSeenAt)
        if (raw >= 1_700_000_000_000L) raw else nowMs
    }

    var soilFilter by remember { mutableStateOf(SoilFilterTab.ALL) }
    var envMetric by remember { mutableStateOf(EnvMetricTab.TEMPERATURE) }
    var inspectedSoilSlot by remember(
        selectedDateKey,
        historyGranularity,
        selectedMinuteHourFilter
    ) { mutableStateOf<Int?>(null) }
    var inspectedEnvSlot by remember(
        selectedDateKey,
        envMetric,
        historyGranularity,
        selectedMinuteHourFilter
    ) { mutableStateOf<Int?>(null) }

    // Build discontinuous series (By Hours or By Minutes) for each crop zone
    val tomatoSeries = remember(
        selectedDay,
        historyGranularity,
        selectedMinuteHourFilter,
        isToday,
        nowMs,
        isVerifiedDeviceLive,
        snapshot.telemetry.tomato,
        liveEpochMs
    ) {
        val liveVal = if (snapshot.telemetry.tomato.valid) snapshot.telemetry.tomato.pct else null
        if (historyGranularity == HistoryGranularity.BY_HOURS) {
            val base = IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
                seg?.tomatoPct ?: rec.tomatoPct
            }
            IotDomainLogic.appendLivePointIfFresh(
                base = base,
                liveValue = liveVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        } else {
            val base = IotDomainLogic.buildSegmentedMinuteSeries(
                day = selectedDay,
                selectedHour = selectedMinuteHourFilter,
                isToday = isToday,
                nowMs = nowMs
            ) { rec -> rec.tomatoPct }
            IotDomainLogic.appendLiveMinutePointIfFresh(
                base = base,
                selectedHour = selectedMinuteHourFilter,
                liveValue = liveVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        }
    }

    val chiliSeries = remember(
        selectedDay,
        historyGranularity,
        selectedMinuteHourFilter,
        isToday,
        nowMs,
        isVerifiedDeviceLive,
        snapshot.telemetry.chili,
        liveEpochMs
    ) {
        val liveVal = if (snapshot.telemetry.chili.valid) snapshot.telemetry.chili.pct else null
        if (historyGranularity == HistoryGranularity.BY_HOURS) {
            val base = IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
                seg?.chiliPct ?: rec.chiliPct
            }
            IotDomainLogic.appendLivePointIfFresh(
                base = base,
                liveValue = liveVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        } else {
            val base = IotDomainLogic.buildSegmentedMinuteSeries(
                day = selectedDay,
                selectedHour = selectedMinuteHourFilter,
                isToday = isToday,
                nowMs = nowMs
            ) { rec -> rec.chiliPct }
            IotDomainLogic.appendLiveMinutePointIfFresh(
                base = base,
                selectedHour = selectedMinuteHourFilter,
                liveValue = liveVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        }
    }

    val eggplantSeries = remember(
        selectedDay,
        historyGranularity,
        selectedMinuteHourFilter,
        isToday,
        nowMs,
        isVerifiedDeviceLive,
        snapshot.telemetry.eggplant,
        liveEpochMs
    ) {
        val liveVal = if (snapshot.telemetry.eggplant.valid) snapshot.telemetry.eggplant.pct else null
        if (historyGranularity == HistoryGranularity.BY_HOURS) {
            val base = IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
                seg?.eggplantPct ?: rec.eggplantPct
            }
            IotDomainLogic.appendLivePointIfFresh(
                base = base,
                liveValue = liveVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        } else {
            val base = IotDomainLogic.buildSegmentedMinuteSeries(
                day = selectedDay,
                selectedHour = selectedMinuteHourFilter,
                isToday = isToday,
                nowMs = nowMs
            ) { rec -> rec.eggplantPct }
            IotDomainLogic.appendLiveMinutePointIfFresh(
                base = base,
                selectedHour = selectedMinuteHourFilter,
                liveValue = liveVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        }
    }

    // Build discontinuous series (By Hours or By Minutes) for active environmental / tank / rain metric
    val envSeries = remember(
        selectedDay,
        envMetric,
        historyGranularity,
        selectedMinuteHourFilter,
        isToday,
        nowMs,
        isVerifiedDeviceLive,
        snapshot.telemetry,
        snapshot.weather,
        liveEpochMs
    ) {
        val t = snapshot.telemetry
        val liveEnvVal: Float? = when (envMetric) {
            EnvMetricTab.TEMPERATURE -> if (t.dhtValid) t.temperatureC else null
            EnvMetricTab.HUMIDITY -> if (t.dhtValid) t.humidityPct else null
            EnvMetricTab.LIGHT -> if (t.lightValid) t.lightBrightnessPct else null
            EnvMetricTab.RESERVOIR -> if (t.reservoirValid) t.reservoirPct else null
            EnvMetricTab.RAIN_SENSOR -> if (!t.rainValid) null else if (t.rainRawWet || t.rainWetConfirmed) 100f else 0f
            EnvMetricTab.RAIN -> if (snapshot.weather.valid && snapshot.weather.rainProbabilityPct >= 0) {
                snapshot.weather.rainProbabilityPct.toFloat()
            } else null
        }

        if (historyGranularity == HistoryGranularity.BY_HOURS) {
            val base = IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
                when (envMetric) {
                    EnvMetricTab.TEMPERATURE -> seg?.temperatureC ?: rec.temperatureC
                    EnvMetricTab.HUMIDITY -> seg?.humidityPct ?: rec.humidityPct
                    EnvMetricTab.LIGHT -> seg?.lightPct ?: rec.lightPct
                    EnvMetricTab.RESERVOIR -> seg?.reservoirPct ?: rec.reservoirPct
                    EnvMetricTab.RAIN_SENSOR -> if (rec.sampleCount > 0) {
                        ((rec.rainWetSamples.toFloat() / rec.sampleCount.toFloat()) * 100f).coerceIn(0f, 100f)
                    } else null
                    EnvMetricTab.RAIN -> (seg?.rainProbPct ?: rec.rainProbPct)?.toFloat()
                }
            }
            IotDomainLogic.appendLivePointIfFresh(
                base = base,
                liveValue = liveEnvVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        } else {
            val base = IotDomainLogic.buildSegmentedMinuteSeries(
                day = selectedDay,
                selectedHour = selectedMinuteHourFilter,
                isToday = isToday,
                nowMs = nowMs
            ) { rec ->
                when (envMetric) {
                    EnvMetricTab.TEMPERATURE -> rec.temperatureC
                    EnvMetricTab.HUMIDITY -> rec.humidityPct
                    EnvMetricTab.LIGHT -> rec.lightPct
                    EnvMetricTab.RESERVOIR -> rec.reservoirPct
                    EnvMetricTab.RAIN_SENSOR -> if (rec.sampleCount > 0) {
                        ((rec.rainWetSamples.toFloat() / rec.sampleCount.toFloat()) * 100f).coerceIn(0f, 100f)
                    } else null
                    EnvMetricTab.RAIN -> rec.rainProbPct?.toFloat()
                }
            }
            IotDomainLogic.appendLiveMinutePointIfFresh(
                base = base,
                selectedHour = selectedMinuteHourFilter,
                liveValue = liveEnvVal,
                liveEpochMs = liveEpochMs,
                isToday = isToday,
                isDeviceOnline = isVerifiedDeviceLive,
                isFromLocalCache = snapshot.isFromLocalCache
            )
        }
    }

    val hoursRecorded = selectedDay?.hours?.count { it.value.sampleCount > 0 } ?: 0
    val minutesRecorded = remember(selectedDay, selectedMinuteHourFilter) {
        val mins = selectedDay?.minutes.orEmpty()
        if (selectedMinuteHourFilter == null) {
            mins.count { it.value.sampleCount > 0 }
        } else {
            mins.count { (it.key / 60) == selectedMinuteHourFilter && it.value.sampleCount > 0 }
        }
    }
    val detectedGapCount = remember(tomatoSeries, chiliSeries, eggplantSeries, envSeries) {
        maxOf(
            tomatoSeries.gaps.size,
            chiliSeries.gaps.size,
            eggplantSeries.gaps.size,
            envSeries.gaps.size
        )
    }
    val irrigations = selectedDay?.irrigations.orEmpty()
    val totalWaterSec = irrigations.sumOf { it.durationSec }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = HomeDimens.ScreenHorizontalPadding,
            end = HomeDimens.ScreenHorizontalPadding,
            top = 14.dp,
            bottom = HomeDimens.BottomBarClearance + 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header with Back & Sync
        item(key = "history_header") {
            HistoryTopHeader(
                boxName = snapshot.meta.displayName.ifBlank { "SmartPlantCare Box" },
                isLoading = isLoadingHistory,
                onBack = onBack,
                onRefresh = onRefresh
            )
        }

        // 2. 7-Day Philippine Local Time Date Strip
        item(key = "date_strip") {
            SevenDayDateStrip(
                days = historyDaysWindow,
                selectedDateKey = selectedDateKey,
                onSelectDate = onSelectDate
            )
        }

        // 3. By Hours (12 AM – 11 PM) / By Minutes (All Day & :00–:59 Hour Picker)
        item(key = "granularity_controls") {
            HistoryGranularityControlCard(
                granularity = historyGranularity,
                selectedMinuteHour = selectedMinuteHourFilter,
                day = selectedDay,
                onSelectGranularity = onSelectGranularity,
                onSelectMinuteHour = onSelectMinuteHour
            )
        }

        // 4. Selected Date Overview & Summary Badges
        item(key = "day_summary") {
            SelectedDayOverviewCard(
                fullDateLabel = selectedOption?.fullDateLabel ?: selectedDateKey,
                isToday = isToday,
                granularity = historyGranularity,
                selectedMinuteHour = selectedMinuteHourFilter,
                hoursRecorded = hoursRecorded,
                minutesRecorded = minutesRecorded,
                detectedGapCount = detectedGapCount,
                irrigationCount = irrigations.size,
                totalWaterSec = totalWaterSec
            )
        }

        // 5. Crop Soil Moisture Line Graph (0% – 100%)
        item(key = "soil_moisture_graph") {
            SoilMoistureHistoryCard(
                day = selectedDay,
                config = snapshot.config,
                granularity = historyGranularity,
                selectedMinuteHour = selectedMinuteHourFilter,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs,
                selectedFilter = soilFilter,
                onSelectFilter = { soilFilter = it },
                tomatoSeries = tomatoSeries,
                chiliSeries = chiliSeries,
                eggplantSeries = eggplantSeries,
                irrigations = irrigations,
                inspectedSlot = inspectedSoilSlot,
                onInspectSlot = { inspectedSoilSlot = it }
            )
        }

        // 6. Environmental, Reservoir & Rain Detection Line Graph
        item(key = "env_tank_graph") {
            EnvironmentalHistoryCard(
                day = selectedDay,
                config = snapshot.config,
                granularity = historyGranularity,
                selectedMinuteHour = selectedMinuteHourFilter,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs,
                selectedMetric = envMetric,
                onSelectMetric = { envMetric = it },
                series = envSeries,
                inspectedSlot = inspectedEnvSlot,
                onInspectSlot = { inspectedEnvSlot = it }
            )
        }

        // 7. 24-Hour Irrigation Activity Timeline & Start/End PHT Records
        item(key = "irrigation_history") {
            IrrigationHistoryCard(
                dateLabel = selectedOption?.fullDateLabel ?: selectedDateKey,
                irrigations = irrigations
            )
        }
    }
}

// ─── 1. Header ───────────────────────────────────────────────────────────────

@Composable
private fun HistoryTopHeader(
    boxName: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onBack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "7-Day History & Records",
                fontFamily = HomeInterFont,
                fontSize = 18.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary,
                maxLines = 2
            )
            
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !isLoading, onClick = onRefresh)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = HomeColors.ForestPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Sync 7-Day History",
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─── 2. 7-Day Philippine Date Strip ──────────────────────────────────────────

@Composable
private fun SevenDayDateStrip(
    days: List<HistoryDateOption>,
    selectedDateKey: String,
    onSelectDate: (String) -> Unit
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Date (Last 7 Days)",
                    fontFamily = HomeInterFont,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "PHT (UTC+8)",
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeColors.TextSecondary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val fitsSevenInRow = maxWidth >= 344.dp && fontScale <= 1.05f
                if (fitsSevenInRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        days.forEach { option ->
                            DateStripDayChip(
                                option = option,
                                selected = option.dateKey == selectedDateKey,
                                onClick = { onSelectDate(option.dateKey) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = (days.lastIndex - 4).coerceAtLeast(0)
                    )
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(days, key = { it.dateKey }) { option ->
                            DateStripDayChip(
                                option = option,
                                selected = option.dateKey == selectedDateKey,
                                onClick = { onSelectDate(option.dateKey) },
                                modifier = Modifier.widthIn(min = 52.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateStripDayChip(
    option: HistoryDateOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) HomeColors.ForestPrimary else HomeColors.PageBg
    val border = if (selected) HomeColors.ForestPrimary else HomeColors.CardBorder
    val dowColor = if (selected) Color.White.copy(alpha = 0.88f) else HomeColors.TextSecondary
    val numColor = if (selected) Color.White else HomeColors.TextPrimary

    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = if (option.isToday) "Today" else option.dayOfWeekShort,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                fontWeight = if (selected || option.isToday) FontWeight.Bold else FontWeight.Medium,
                color = dowColor,
                maxLines = 1
            )
            Text(
                text = option.dayOfMonth,
                fontFamily = HomeInterFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = numColor,
                maxLines = 1
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(6.dp)
            ) {
                if (option.hasTelemetry) {
                    Box(
                        modifier = Modifier
                            .size(4.5.dp)
                            .clip(CircleShape)
                            .background(if (selected) HomeColors.MintGlow else HomeColors.EmeraldAccent)
                    )
                }
                if (option.hasIrrigation) {
                    Box(
                        modifier = Modifier
                            .size(4.5.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFF93C5FD) else HomeColors.WaterBlue)
                    )
                }
                if (!option.hasTelemetry && !option.hasIrrigation) {
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Color.White.copy(alpha = 0.35f)
                                else HomeColors.TextMuted.copy(alpha = 0.45f)
                            )
                    )
                }
            }
        }
    }
}

// ─── 3. By Hours / By Minutes Granularity & Hour Window Control Card ─────────

@Composable
private fun HistoryGranularityControlCard(
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    day: DailyHistorySnapshot?,
    onSelectGranularity: (HistoryGranularity) -> Unit,
    onSelectMinuteHour: (Int?) -> Unit
) {
    val activeMinuteHours = remember(day?.minutes) {
        day?.minutes?.entries
            ?.filter { it.value.sampleCount > 0 }
            ?.map { (it.key / 60).coerceIn(0, 23) }
            ?.toSet()
            .orEmpty()
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) HomeColors.ForestPrimary else Color.Transparent)
                            .clickable { onSelectGranularity(mode) }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = labels.first,
                            fontFamily = HomeInterFont,
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) Color.White else HomeColors.TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = labels.second,
                            fontFamily = HomeInterFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) Color.White.copy(alpha = 0.85f) else HomeColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            if (granularity == HistoryGranularity.BY_MINUTES) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (selectedMinuteHour == null) {
                        "Minute View: All Day (12:00 AM – 11:59 PM) • Tap an hour to zoom :00 – :59"
                    } else {
                        "Minute View: ${IotDomainLogic.formatHourRangeLabel(selectedMinuteHour)} (:00 – :59)"
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item(key = "all_day_minutes") {
                        val allSelected = selectedMinuteHour == null
                        Surface(
                            color = if (allSelected) HomeColors.ForestPrimary else HomeColors.PageBg,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (allSelected) HomeColors.ForestPrimary else HomeColors.CardBorder
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
                                color = if (allSelected) Color.White else HomeColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }

                    items((0..23).toList(), key = { "hr_$it" }) { hour ->
                        val chosen = selectedMinuteHour == hour
                        val hasData = hour in activeMinuteHours
                        val shortLabel = when {
                            hour == 0 -> "12 AM"
                            hour < 12 -> "$hour AM"
                            hour == 12 -> "12 PM"
                            else -> "${hour - 12} PM"
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
                                .clickable { onSelectMinuteHour(hour) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (hasData) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (chosen) HomeColors.MintGlow else HomeColors.EmeraldAccent)
                                    )
                                }
                                Text(
                                    text = shortLabel,
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
        }
    }
}

// ─── 4. Selected Date Overview Strip ─────────────────────────────────────────

@Composable
private fun SelectedDayOverviewCard(
    fullDateLabel: String,
    isToday: Boolean,
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    hoursRecorded: Int,
    minutesRecorded: Int,
    detectedGapCount: Int,
    irrigationCount: Int,
    totalWaterSec: Int
) {
    val recordedText = when {
        granularity == HistoryGranularity.BY_HOURS -> "$hoursRecorded/24 hrs"
        selectedMinuteHour != null -> "$minutesRecorded/60 min"
        else -> "$minutesRecorded min"
    }
    val hasAnyRecorded = if (granularity == HistoryGranularity.BY_HOURS) {
        hoursRecorded > 0
    } else {
        minutesRecorded > 0
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isToday) "$fullDateLabel (Today)" else fullDateLabel,
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = HomeColors.SoftMint,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "7-Day Retention",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverviewMetricChip(
                    label = "Recorded",
                    value = recordedText,
                    bg = if (hasAnyRecorded) HomeColors.SuccessBg else HomeColors.PageBg,
                    fg = if (hasAnyRecorded) HomeColors.SuccessGreen else HomeColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricChip(
                    label = "Line Breaks",
                    value = when {
                        !hasAnyRecorded -> "No data"
                        detectedGapCount == 0 -> "None"
                        detectedGapCount == 1 -> "1 gap"
                        else -> "$detectedGapCount gaps"
                    },
                    bg = if (detectedGapCount > 0) HomeColors.AlertBg else HomeColors.PageBg,
                    fg = if (detectedGapCount > 0) HomeColors.AlertRed else HomeColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricChip(
                    label = "Watering",
                    value = if (irrigationCount == 0) "0 runs" else "$irrigationCount • ${totalWaterSec}s",
                    bg = if (irrigationCount > 0) HomeColors.WaterBg else HomeColors.PageBg,
                    fg = if (irrigationCount > 0) HomeColors.WaterBlue else HomeColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricChip(
    label: String,
    value: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = HomeColors.TextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// ─── 5. Crop Soil Moisture Line Graph (0% – 100%, Hours & Minutes) ───────────

@Composable
private fun SoilMoistureHistoryCard(
    day: DailyHistorySnapshot?,
    config: DeviceConfig,
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    isToday: Boolean,
    isPhoneOnline: Boolean,
    nowMs: Long,
    selectedFilter: SoilFilterTab,
    onSelectFilter: (SoilFilterTab) -> Unit,
    tomatoSeries: SegmentedChartSeries,
    chiliSeries: SegmentedChartSeries,
    eggplantSeries: SegmentedChartSeries,
    irrigations: List<IrrigationHistoryItem>,
    inspectedSlot: Int?,
    onInspectSlot: (Int?) -> Unit
) {
    val activeLines = remember(selectedFilter, tomatoSeries, chiliSeries, eggplantSeries) {
        when (selectedFilter) {
            SoilFilterTab.ALL -> listOf(
                ChartLineSpec("tomato", "Tomato", HistoryChartColors.TomatoLine, tomatoSeries),
                ChartLineSpec("chili", "Chili", HistoryChartColors.ChiliLine, chiliSeries),
                ChartLineSpec("eggplant", "Eggplant", HistoryChartColors.EggplantLine, eggplantSeries)
            )
            SoilFilterTab.TOMATO -> listOf(
                ChartLineSpec("tomato", "Tomato", HistoryChartColors.TomatoLine, tomatoSeries)
            )
            SoilFilterTab.CHILI -> listOf(
                ChartLineSpec("chili", "Chili", HistoryChartColors.ChiliLine, chiliSeries)
            )
            SoilFilterTab.EGGPLANT -> listOf(
                ChartLineSpec("eggplant", "Eggplant", HistoryChartColors.EggplantLine, eggplantSeries)
            )
        }
    }

    val thresholds = remember(selectedFilter, config) {
        when (selectedFilter) {
            SoilFilterTab.ALL -> emptyList()
            else -> {
                val cfg = config.cropConfig(selectedFilter.slug)
                listOf(
                    ThresholdGuide(cfg.startPct, "Dry ${cfg.startPct.toInt()}%", HomeColors.WarningAmber),
                    ThresholdGuide(cfg.stopPct, "Stop ${cfg.stopPct.toInt()}%", HomeColors.WaterBlue)
                )
            }
        }
    }

    val filteredIrrigations = remember(selectedFilter, irrigations) {
        irrigations.filter {
            selectedFilter == SoilFilterTab.ALL || it.crop.equals(selectedFilter.slug, true)
        }
    }

    val irrigationMarkerXCoords = remember(filteredIrrigations, granularity, selectedMinuteHour) {
        when {
            granularity == HistoryGranularity.BY_HOURS ->
                filteredIrrigations.map { it.hour.coerceIn(0, 23).toFloat() }.distinct()
            selectedMinuteHour != null -> {
                filteredIrrigations.mapNotNull { item ->
                    val minOfDay = if (item.minuteOfDay >= 0) item.minuteOfDay
                    else if (item.startedAt >= 1_700_000_000_000L) IotDomainLogic.phMinuteOfDay(item.startedAt)
                    else -1
                    if (minOfDay >= 0 && minOfDay / 60 == selectedMinuteHour) {
                        (minOfDay % 60).toFloat()
                    } else null
                }.distinct()
            }
            else -> {
                filteredIrrigations.map { item ->
                    val minOfDay = if (item.minuteOfDay >= 0) item.minuteOfDay
                    else if (item.startedAt >= 1_700_000_000_000L) IotDomainLogic.phMinuteOfDay(item.startedAt)
                    else item.hour * 60
                    (minOfDay.coerceIn(0, 1439)) / 60f
                }.distinct()
            }
        }
    }

    val unionGaps = remember(activeLines) {
        activeLines.flatMap { it.series.gaps }.distinctBy { "${it.startXHour}_${it.endXHour}" }
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Crop Soil Moisture",
                fontFamily = HomeInterFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
            Text(
                text = if (granularity == HistoryGranularity.BY_HOURS) {
                    "Hourly soil moisture 0% – 100% • Breaks immediately on missing hours"
                } else {
                    "Per-minute soil moisture 0% – 100% • Breaks immediately on 1 missing minute"
                },
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                color = HomeColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Crop filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(SoilFilterTab.entries, key = { it.name }) { tab ->
                    val chosen = selectedFilter == tab
                    Surface(
                        color = if (chosen) HomeColors.ForestPrimary else HomeColors.PageBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (chosen) HomeColors.ForestPrimary else HomeColors.CardBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectFilter(tab) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (chosen) Color.White else tab.dotColor)
                            )
                            Text(
                                text = tab.label,
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (chosen) Color.White else HomeColors.TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SoilInspectionBanner(
                day = day,
                granularity = granularity,
                selectedMinuteHour = selectedMinuteHour,
                inspectedSlot = inspectedSlot,
                selectedFilter = selectedFilter,
                filteredIrrigations = filteredIrrigations,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs
            )

            Spacer(modifier = Modifier.height(8.dp))

            DiscontinuousTimelineChart(
                lines = activeLines,
                gaps = unionGaps,
                granularity = granularity,
                selectedMinuteHour = selectedMinuteHour,
                minY = 0f,
                maxY = 100f,
                yAxisUnit = "%",
                yTickValues = listOf(0f, 25f, 50f, 75f, 100f),
                thresholds = thresholds,
                irrigationMarkerXCoords = irrigationMarkerXCoords,
                inspectedSlot = inspectedSlot,
                onInspectSlot = onInspectSlot,
                emptyMessage = when {
                    granularity == HistoryGranularity.BY_MINUTES &&
                        (day?.minutes?.isEmpty() != false) &&
                        (day?.hours?.any { it.value.sampleCount > 0 } == true) ->
                        "Hourly records exist for this date, but per-minute records were not uploaded yet."
                    granularity == HistoryGranularity.BY_MINUTES && selectedMinuteHour != null ->
                        "No minute soil moisture readings recorded during ${IotDomainLogic.formatHourRangeLabel(selectedMinuteHour)}."
                    else -> "No soil moisture readings recorded on this date."
                }
            )
        }
    }
}

@Composable
private fun SoilInspectionBanner(
    day: DailyHistorySnapshot?,
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    inspectedSlot: Int?,
    selectedFilter: SoilFilterTab,
    filteredIrrigations: List<IrrigationHistoryItem>,
    isToday: Boolean,
    isPhoneOnline: Boolean,
    nowMs: Long
) {
    Surface(
        color = HomeColors.PageBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HomeColors.DividerLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (inspectedSlot == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (granularity == HistoryGranularity.BY_HOURS) {
                        "Tap or drag graph to inspect any hour"
                    } else {
                        "Tap or drag graph to inspect any minute"
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegendDotLabel(color = HistoryChartColors.OfflineBandBorder, label = "Gap")
                    LegendDotLabel(color = HomeColors.WaterBlue, label = "Watered")
                }
            }
        } else if (granularity == HistoryGranularity.BY_HOURS) {
            val hour = inspectedSlot.coerceIn(0, 23)
            val rec = day?.hours?.get(hour)
            val hasSelectedVal = rec != null && rec.sampleCount > 0 && when (selectedFilter) {
                SoilFilterTab.ALL -> rec.tomatoPct != null || rec.chiliPct != null || rec.eggplantPct != null
                SoilFilterTab.TOMATO -> rec.tomatoPct != null
                SoilFilterTab.CHILI -> rec.chiliPct != null
                SoilFilterTab.EGGPLANT -> rec.eggplantPct != null
            }
            val status = IotDomainLogic.classifyHourlyInspectionStatus(
                day = day,
                hour = hour,
                hasSensorValue = hasSelectedVal,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs
            )
            val wateredThisHour = filteredIrrigations.any { it.hour == hour }
            InspectionDetailColumn(
                timeLabel = "${IotDomainLogic.formatHourRangeLabel(hour)} PHT",
                status = status,
                wateredLabel = if (wateredThisHour) "• Watered this hour" else null,
                validSummary = if (rec != null && rec.sampleCount > 0) {
                    formatSoilValuesSummary(
                        selectedFilter = selectedFilter,
                        tomato = rec.tomatoPct,
                        chili = rec.chiliPct,
                        eggplant = rec.eggplantPct
                    )
                } else null
            )
        } else {
            val minuteOfDay = if (selectedMinuteHour != null) {
                selectedMinuteHour.coerceIn(0, 23) * 60 + inspectedSlot.coerceIn(0, 59)
            } else {
                inspectedSlot.coerceIn(0, 1439)
            }
            val rec = day?.minutes?.get(minuteOfDay)
            val hasSelectedVal = rec != null && rec.sampleCount > 0 && when (selectedFilter) {
                SoilFilterTab.ALL -> rec.tomatoPct != null || rec.chiliPct != null || rec.eggplantPct != null
                SoilFilterTab.TOMATO -> rec.tomatoPct != null
                SoilFilterTab.CHILI -> rec.chiliPct != null
                SoilFilterTab.EGGPLANT -> rec.eggplantPct != null
            }
            val status = IotDomainLogic.classifyMinuteInspectionStatus(
                day = day,
                minuteOfDay = minuteOfDay,
                hasSensorValue = hasSelectedVal,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs
            )
            val wateredThisMinute = filteredIrrigations.any { item ->
                val m = if (item.minuteOfDay >= 0) item.minuteOfDay
                else if (item.startedAt >= 1_700_000_000_000L) IotDomainLogic.phMinuteOfDay(item.startedAt)
                else -1
                m == minuteOfDay
            }
            InspectionDetailColumn(
                timeLabel = "${IotDomainLogic.formatMinuteOfDay12(minuteOfDay)} PHT",
                status = status,
                wateredLabel = if (wateredThisMinute) "• Watered at this minute" else null,
                validSummary = if (rec != null && rec.sampleCount > 0) {
                    formatSoilValuesSummary(
                        selectedFilter = selectedFilter,
                        tomato = rec.tomatoPct,
                        chili = rec.chiliPct,
                        eggplant = rec.eggplantPct
                    )
                } else null
            )
        }
    }
}

private fun formatSoilValuesSummary(
    selectedFilter: SoilFilterTab,
    tomato: Float?,
    chili: Float?,
    eggplant: Float?
): String = buildString {
    if (selectedFilter == SoilFilterTab.ALL || selectedFilter == SoilFilterTab.TOMATO) {
        append("Tomato: ")
        append(tomato?.let { String.format(Locale.US, "%.1f%%", it) } ?: "Sensor fault")
    }
    if (selectedFilter == SoilFilterTab.ALL || selectedFilter == SoilFilterTab.CHILI) {
        if (isNotEmpty()) append("  •  ")
        append("Chili: ")
        append(chili?.let { String.format(Locale.US, "%.1f%%", it) } ?: "Sensor fault")
    }
    if (selectedFilter == SoilFilterTab.ALL || selectedFilter == SoilFilterTab.EGGPLANT) {
        if (isNotEmpty()) append("  •  ")
        append("Eggplant: ")
        append(eggplant?.let { String.format(Locale.US, "%.1f%%", it) } ?: "Sensor fault")
    }
}

@Composable
private fun InspectionDetailColumn(
    timeLabel: String,
    status: PointInspectionStatus,
    wateredLabel: String?,
    validSummary: String?
) {
    val (badgeText, badgeColor, statusExplanation) = when (status) {
        PointInspectionStatus.VALID -> Triple(null, HomeColors.ForestPrimary, "")
        PointInspectionStatus.FUTURE_TIME -> Triple(
            "Upcoming",
            HomeColors.TextSecondary,
            "Future PHT time — no readings collected yet."
        )
        PointInspectionStatus.SENSOR_FAULT -> Triple(
            "Sensor Fault",
            HomeColors.WarningAmber,
            "ESP32 was online, but this sensor reported an invalid reading."
        )
        PointInspectionStatus.PHONE_OFFLINE -> Triple(
            "No Internet",
            HomeColors.WarningAmber,
            "Phone has no internet connection — reconnect to sync latest cloud records."
        )
        PointInspectionStatus.LEGACY_HOURLY_ONLY -> Triple(
            "Hourly Only",
            HomeColors.TextSecondary,
            "Hourly record exists, but per-minute data was not uploaded for this hour."
        )
        PointInspectionStatus.DEVICE_OFFLINE -> Triple(
            "ESP32 Offline",
            HomeColors.AlertRed,
            "No readings transmitted — ESP32 offline or Wi-Fi disconnected."
        )
    }

    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeLabel,
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.ForestPrimary
            )
            if (wateredLabel != null) {
                Text(
                    text = wateredLabel,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.WaterBlue
                )
            } else if (badgeText != null) {
                Text(
                    text = badgeText,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        if (status == PointInspectionStatus.VALID && !validSummary.isNullOrBlank()) {
            Text(
                text = validSummary,
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = HomeColors.TextPrimary
            )
        } else {
            Text(
                text = statusExplanation,
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                color = HomeColors.TextSecondary
            )
        }
    }
}

// ─── 6. Environmental, Reservoir & Rain Detection Card ───────────────────────

@Composable
private fun EnvironmentalHistoryCard(
    day: DailyHistorySnapshot?,
    config: DeviceConfig,
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    isToday: Boolean,
    isPhoneOnline: Boolean,
    nowMs: Long,
    selectedMetric: EnvMetricTab,
    onSelectMetric: (EnvMetricTab) -> Unit,
    series: SegmentedChartSeries,
    inspectedSlot: Int?,
    onInspectSlot: (Int?) -> Unit
) {
    val (minY, maxY, ticks) = remember(selectedMetric, series) {
        if (selectedMetric == EnvMetricTab.TEMPERATURE) {
            val allVals = series.segments.flatten().map { it.value }
            val minVal = allVals.minOrNull() ?: 20f
            val maxVal = allVals.maxOrNull() ?: 38f
            val lo = ((minVal - 5f) / 5f).toInt().coerceAtMost(2) * 5f
            val hi = (((maxVal + 5f) / 5f).toInt() + 1).coerceAtLeast(9) * 5f
            val safeLo = lo.coerceAtLeast(0f).coerceAtMost(15f)
            val safeHi = hi.coerceAtLeast(45f).coerceAtMost(60f)
            val step = (safeHi - safeLo) / 4f
            Triple(
                safeLo,
                safeHi,
                listOf(safeLo, safeLo + step, safeLo + 2 * step, safeLo + 3 * step, safeHi)
            )
        } else {
            Triple(0f, 100f, listOf(0f, 25f, 50f, 75f, 100f))
        }
    }

    val thresholds = remember(selectedMetric, config) {
        when (selectedMetric) {
            EnvMetricTab.TEMPERATURE -> listOf(
                ThresholdGuide(config.tempHighC, "High ${config.tempHighC.toInt()}°C", HomeColors.AlertRed)
            )
            EnvMetricTab.HUMIDITY -> listOf(
                ThresholdGuide(config.humLowRh, "Low ${config.humLowRh.toInt()}%", HomeColors.WarningAmber)
            )
            EnvMetricTab.LIGHT -> listOf(
                ThresholdGuide(config.lightHighPct, "High ${config.lightHighPct.toInt()}%", HomeColors.WarningAmber)
            )
            EnvMetricTab.RESERVOIR -> listOf(
                ThresholdGuide(config.reservoirLowPct, "Low ${config.reservoirLowPct.toInt()}%", HomeColors.WarningAmber),
                ThresholdGuide(config.reservoirCriticalPct, "Crit ${config.reservoirCriticalPct.toInt()}%", HomeColors.AlertRed)
            )
            EnvMetricTab.RAIN_SENSOR -> emptyList()
            EnvMetricTab.RAIN -> listOf(
                ThresholdGuide(config.rainThresholdPct.toFloat(), "Gate ${config.rainThresholdPct}%", HomeColors.WaterBlue)
            )
        }
    }

    val lines = remember(selectedMetric, series) {
        listOf(
            ChartLineSpec(
                id = selectedMetric.name,
                label = selectedMetric.label,
                color = selectedMetric.color,
                series = series
            )
        )
    }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Environment, Rain & Water Tank",
                fontFamily = HomeInterFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
            Text(
                text = when (selectedMetric) {
                    EnvMetricTab.TEMPERATURE -> "DHT22 Air Temperature (°C)"
                    EnvMetricTab.HUMIDITY -> "DHT22 Relative Humidity (% RH)"
                    EnvMetricTab.LIGHT -> "LDR Light Intensity (%)"
                    EnvMetricTab.RESERVOIR -> "Ultrasonic Reservoir Water Level (%) & Air Gap"
                    EnvMetricTab.RAIN_SENSOR -> "Physical Rain Plate Detection (0% Dry – 100% Wet)"
                    EnvMetricTab.RAIN -> "Open-Meteo 3-Hour Rain Forecast (%)"
                },
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                color = HomeColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(EnvMetricTab.entries, key = { it.name }) { tab ->
                    val chosen = selectedMetric == tab
                    Surface(
                        color = if (chosen) HomeColors.ForestPrimary else HomeColors.PageBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (chosen) HomeColors.ForestPrimary else HomeColors.CardBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectMetric(tab) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (chosen) Color.White else tab.color,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = tab.label,
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (chosen) Color.White else HomeColors.TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            EnvInspectionBanner(
                day = day,
                granularity = granularity,
                selectedMinuteHour = selectedMinuteHour,
                inspectedSlot = inspectedSlot,
                metric = selectedMetric,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs
            )

            Spacer(modifier = Modifier.height(8.dp))

            DiscontinuousTimelineChart(
                lines = lines,
                gaps = series.gaps,
                granularity = granularity,
                selectedMinuteHour = selectedMinuteHour,
                minY = minY,
                maxY = maxY,
                yAxisUnit = selectedMetric.unit,
                yTickValues = ticks,
                thresholds = thresholds,
                irrigationMarkerXCoords = emptyList(),
                inspectedSlot = inspectedSlot,
                onInspectSlot = onInspectSlot,
                emptyMessage = when {
                    granularity == HistoryGranularity.BY_MINUTES &&
                        (day?.minutes?.isEmpty() != false) &&
                        (day?.hours?.any { it.value.sampleCount > 0 } == true) ->
                        "Hourly records exist for this date, but per-minute records were not uploaded yet."
                    granularity == HistoryGranularity.BY_MINUTES && selectedMinuteHour != null ->
                        "No ${selectedMetric.label.lowercase(Locale.US)} readings recorded during ${IotDomainLogic.formatHourRangeLabel(selectedMinuteHour)}."
                    else -> "No ${selectedMetric.label.lowercase(Locale.US)} readings recorded on this date."
                }
            )
        }
    }
}

@Composable
private fun EnvInspectionBanner(
    day: DailyHistorySnapshot?,
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    inspectedSlot: Int?,
    metric: EnvMetricTab,
    isToday: Boolean,
    isPhoneOnline: Boolean,
    nowMs: Long
) {
    Surface(
        color = HomeColors.PageBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HomeColors.DividerLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (inspectedSlot == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (granularity == HistoryGranularity.BY_HOURS) {
                        "Tap or drag graph to inspect hourly ${metric.label.lowercase(Locale.US)}"
                    } else {
                        "Tap or drag graph to inspect minute ${metric.label.lowercase(Locale.US)}"
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LegendDotLabel(color = HistoryChartColors.OfflineBandBorder, label = "Offline Gap")
            }
        } else if (granularity == HistoryGranularity.BY_HOURS) {
            val hour = inspectedSlot.coerceIn(0, 23)
            val rec = day?.hours?.get(hour)
            val hasSensorVal = rec != null && rec.sampleCount > 0 && when (metric) {
                EnvMetricTab.TEMPERATURE -> rec.temperatureC != null
                EnvMetricTab.HUMIDITY -> rec.humidityPct != null
                EnvMetricTab.LIGHT -> rec.lightPct != null
                EnvMetricTab.RESERVOIR -> rec.reservoirPct != null
                EnvMetricTab.RAIN_SENSOR -> true
                EnvMetricTab.RAIN -> rec.rainProbPct != null
            }
            val status = IotDomainLogic.classifyHourlyInspectionStatus(
                day = day,
                hour = hour,
                hasSensorValue = hasSensorVal,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs
            )
            val summary = if (rec != null && rec.sampleCount > 0 && hasSensorVal) {
                formatEnvValueSummary(
                    metric = metric,
                    temp = rec.temperatureC,
                    hum = rec.humidityPct,
                    light = rec.lightPct,
                    tankPct = rec.reservoirPct,
                    tankGapIn = rec.reservoirGapIn,
                    rainProb = rec.rainProbPct,
                    rainWetSamples = rec.rainWetSamples,
                    sampleCount = rec.sampleCount,
                    rainSuspended = false
                )
            } else null
            InspectionDetailColumn(
                timeLabel = "${IotDomainLogic.formatHourRangeLabel(hour)} PHT",
                status = status,
                wateredLabel = if (rec != null && rec.gapCount > 0) "Reconnected mid-hour" else null,
                validSummary = summary
            )
        } else {
            val minuteOfDay = if (selectedMinuteHour != null) {
                selectedMinuteHour.coerceIn(0, 23) * 60 + inspectedSlot.coerceIn(0, 59)
            } else {
                inspectedSlot.coerceIn(0, 1439)
            }
            val rec = day?.minutes?.get(minuteOfDay)
            val hasSensorVal = rec != null && rec.sampleCount > 0 && when (metric) {
                EnvMetricTab.TEMPERATURE -> rec.temperatureC != null
                EnvMetricTab.HUMIDITY -> rec.humidityPct != null
                EnvMetricTab.LIGHT -> rec.lightPct != null
                EnvMetricTab.RESERVOIR -> rec.reservoirPct != null
                EnvMetricTab.RAIN_SENSOR -> true
                EnvMetricTab.RAIN -> rec.rainProbPct != null
            }
            val status = IotDomainLogic.classifyMinuteInspectionStatus(
                day = day,
                minuteOfDay = minuteOfDay,
                hasSensorValue = hasSensorVal,
                isToday = isToday,
                isPhoneOnline = isPhoneOnline,
                nowMs = nowMs
            )
            val summary = if (rec != null && rec.sampleCount > 0 && hasSensorVal) {
                formatEnvValueSummary(
                    metric = metric,
                    temp = rec.temperatureC,
                    hum = rec.humidityPct,
                    light = rec.lightPct,
                    tankPct = rec.reservoirPct,
                    tankGapIn = rec.reservoirGapIn,
                    rainProb = rec.rainProbPct,
                    rainWetSamples = rec.rainWetSamples,
                    sampleCount = rec.sampleCount,
                    rainSuspended = rec.rainSuspended
                )
            } else null
            InspectionDetailColumn(
                timeLabel = "${IotDomainLogic.formatMinuteOfDay12(minuteOfDay)} PHT",
                status = status,
                wateredLabel = null,
                validSummary = summary
            )
        }
    }
}

private fun formatEnvValueSummary(
    metric: EnvMetricTab,
    temp: Float?,
    hum: Float?,
    light: Float?,
    tankPct: Float?,
    tankGapIn: Float?,
    rainProb: Int?,
    rainWetSamples: Int,
    sampleCount: Int,
    rainSuspended: Boolean
): String = when (metric) {
    EnvMetricTab.TEMPERATURE -> String.format(Locale.US, "Temperature: %.1f°C", temp ?: 0f)
    EnvMetricTab.HUMIDITY -> String.format(Locale.US, "Humidity: %.1f%% RH", hum ?: 0f)
    EnvMetricTab.LIGHT -> String.format(Locale.US, "Light Intensity: %.1f%%", light ?: 0f)
    EnvMetricTab.RESERVOIR -> {
        val gapSuffix = tankGapIn?.let { String.format(Locale.US, " (%.2f in air gap)", it) } ?: ""
        String.format(Locale.US, "Water Tank: %.1f%%%s", tankPct ?: 0f, gapSuffix)
    }
    EnvMetricTab.RAIN_SENSOR -> {
        val wetPct = if (sampleCount > 0) (rainWetSamples * 100f / sampleCount).roundToInt() else 0
        val stateLabel = when {
            rainSuspended -> "Wet • Auto-watering suspended"
            rainWetSamples > 0 -> "Wet detected ($wetPct%)"
            else -> "Dry (0%)"
        }
        "Rain Plate: $stateLabel"
    }
    EnvMetricTab.RAIN -> {
        val plateNote = when {
            rainSuspended -> " • Plate wet (paused)"
            rainWetSamples > 0 -> " • Plate wet"
            else -> " • Plate dry"
        }
        "Rain Forecast: ${rainProb ?: 0}%$plateNote"
    }
}

// ─── Reusable Discontinuous Timeline Canvas Chart (Hours & Minutes) ──────────

@Composable
private fun DiscontinuousTimelineChart(
    lines: List<ChartLineSpec>,
    gaps: List<OfflineGapWindow>,
    granularity: HistoryGranularity,
    selectedMinuteHour: Int?,
    minY: Float,
    maxY: Float,
    yAxisUnit: String,
    yTickValues: List<Float>,
    thresholds: List<ThresholdGuide>,
    irrigationMarkerXCoords: List<Float>,
    inspectedSlot: Int?,
    onInspectSlot: (Int?) -> Unit,
    emptyMessage: String
) {
    val hasAnyPoint = lines.any { !it.series.isEmpty }
    val isSingleSeries = lines.size == 1
    val safeSpanY = (maxY - minY).coerceAtLeast(1f)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }
    val gapEdgeEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

    val isSingleHourMinuteMode =
        granularity == HistoryGranularity.BY_MINUTES && selectedMinuteHour != null
    val maxSlotIndex = when {
        granularity == HistoryGranularity.BY_HOURS -> 23
        isSingleHourMinuteMode -> 59
        else -> 1439
    }
    val xDomainMax = when {
        granularity == HistoryGranularity.BY_HOURS -> 23.5f
        isSingleHourMinuteMode -> 59f
        else -> 23.9834f
    }
    val xClampMax = if (isSingleHourMinuteMode) 59f else 23.9834f

    val xAxisLabels = remember(granularity, selectedMinuteHour) {
        if (isSingleHourMinuteMode) {
            listOf(":00", ":15", ":30", ":45", ":59")
        } else {
            listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM")
        }
    }

    val verticalGuideCoords = remember(isSingleHourMinuteMode) {
        if (isSingleHourMinuteMode) {
            listOf(0f, 10f, 20f, 30f, 40f, 50f, 59f)
        } else {
            listOf(0f, 4f, 8f, 12f, 16f, 20f, 23f)
        }
    }

    fun slotToXCoord(slot: Int): Float = when {
        granularity == HistoryGranularity.BY_HOURS -> slot.coerceIn(0, 23).toFloat()
        isSingleHourMinuteMode -> slot.coerceIn(0, 59).toFloat()
        else -> slot.coerceIn(0, 1439) / 60f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(198.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-Axis Labels
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .padding(top = if (isSingleSeries) 18.dp else 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yTickValues.reversed().forEach { tick ->
                    Text(
                        text = "${tick.roundToInt()}$yAxisUnit",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            val singleSpotlight = remember(
                lines,
                inspectedSlot,
                isSingleSeries,
                granularity,
                selectedMinuteHour
            ) {
                if (!isSingleSeries) null
                else {
                    val pts = lines.first().series.segments.flatten()
                    if (pts.isEmpty()) null
                    else if (inspectedSlot != null) {
                        when {
                            granularity == HistoryGranularity.BY_HOURS ->
                                pts.lastOrNull { it.hour == inspectedSlot }
                            isSingleHourMinuteMode -> {
                                val target = selectedMinuteHour!!.coerceIn(0, 23) * 60 + inspectedSlot.coerceIn(0, 59)
                                pts.lastOrNull { it.minuteOfDay == target }
                            }
                            else -> pts.lastOrNull { it.minuteOfDay == inspectedSlot }
                        }
                    } else {
                        pts.lastOrNull()
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFAFDFB))
                    .pointerInput(lines, granularity, selectedMinuteHour, inspectedSlot) {
                        detectTapGestures { offset ->
                            val usableW = (size.width - 16.dp.toPx()).coerceAtLeast(1f)
                            val frac = ((offset.x - 8.dp.toPx()) / usableW).coerceIn(0f, 1f)
                            val slot = (frac * maxSlotIndex).roundToInt().coerceIn(0, maxSlotIndex)
                            onInspectSlot(if (inspectedSlot == slot) null else slot)
                        }
                    }
                    .pointerInput(lines, granularity, selectedMinuteHour) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val usableW = (size.width - 16.dp.toPx()).coerceAtLeast(1f)
                            val frac = ((change.position.x - 8.dp.toPx()) / usableW).coerceIn(0f, 1f)
                            val slot = (frac * maxSlotIndex).roundToInt().coerceIn(0, maxSlotIndex)
                            onInspectSlot(slot)
                        }
                    }
            ) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val padH = with(density) { 8.dp.toPx() }
                val padTop = with(density) { if (isSingleSeries) 24.dp.toPx() else 12.dp.toPx() }
                val padBottom = with(density) { 10.dp.toPx() }
                val plotW = (boxW - padH * 2f).coerceAtLeast(1f)
                val plotH = (boxH - padTop - padBottom).coerceAtLeast(1f)

                fun xForCoordPx(xVal: Float): Float =
                    padH + (xVal.coerceIn(0f, xClampMax) / xDomainMax).coerceIn(0f, 1f) * plotW

                fun yForValuePx(v: Float): Float {
                    val norm = ((v - minY) / safeSpanY).coerceIn(0f, 1f)
                    return padTop + plotH - (norm * plotH)
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baselineY = padTop + plotH
                    if (plotW <= 1f || plotH <= 1f) return@Canvas

                    // 1. Horizontal Hairline Grid
                    yTickValues.forEach { tick ->
                        val y = yForValuePx(tick)
                        drawLine(
                            color = HistoryChartColors.GridLine,
                            start = Offset(padH, y),
                            end = Offset(padH + plotW, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Vertical Time Guides
                    verticalGuideCoords.forEach { xVal ->
                        val x = xForCoordPx(xVal)
                        drawLine(
                            color = HistoryChartColors.GridLine.copy(alpha = 0.45f),
                            start = Offset(x, padTop),
                            end = Offset(x, baselineY),
                            strokeWidth = 0.7.dp.toPx()
                        )
                    }

                    // 3. Offline / Missing Gap Bands
                    gaps.forEach { gap ->
                        val x1 = xForCoordPx(gap.startXHour)
                        val x2 = xForCoordPx(gap.endXHour)
                        val bandW = (x2 - x1).coerceAtLeast(2.dp.toPx())
                        if (x2 >= x1) {
                            drawRoundRect(
                                color = HistoryChartColors.OfflineBandBg,
                                topLeft = Offset(x1, padTop),
                                size = Size(bandW, plotH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawLine(
                                color = HistoryChartColors.OfflineBandBorder,
                                start = Offset(x1, padTop),
                                end = Offset(x1, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gapEdgeEffect
                            )
                            if (x2 - x1 > 4.dp.toPx()) {
                                drawLine(
                                    color = HistoryChartColors.OfflineBandBorder,
                                    start = Offset(x2, padTop),
                                    end = Offset(x2, baselineY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = gapEdgeEffect
                                )
                            }
                        }
                    }

                    // 4. Threshold Reference Guides
                    thresholds.forEach { guide ->
                        if (guide.value in minY..maxY) {
                            val y = yForValuePx(guide.value)
                            drawLine(
                                color = guide.color.copy(alpha = 0.55f),
                                start = Offset(padH, y),
                                end = Offset(padH + plotW, y),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }
                    }

                    // 5. Irrigation Markers along baseline
                    irrigationMarkerXCoords.forEach { xCoord ->
                        val x = xForCoordPx(xCoord)
                        drawCircle(
                            color = HomeColors.WaterBlue.copy(alpha = 0.22f),
                            radius = 6.dp.toPx(),
                            center = Offset(x, baselineY - 3.dp.toPx())
                        )
                        drawCircle(
                            color = HomeColors.WaterBlue,
                            radius = 3.dp.toPx(),
                            center = Offset(x, baselineY - 3.dp.toPx())
                        )
                    }

                    // 6. Discontinuous Sensor Line Segments (never bridges missing minutes/hours)
                    val strokePx = if (granularity == HistoryGranularity.BY_MINUTES && !isSingleHourMinuteMode) {
                        2.1.dp.toPx()
                    } else {
                        2.5.dp.toPx()
                    }
                    val singleDotRadius = 3.8.dp.toPx()

                    lines.forEach { spec ->
                        spec.series.segments.forEach { seg ->
                            val offsets = seg.map { pt -> Offset(xForCoordPx(pt.xHour), yForValuePx(pt.value)) }
                            if (offsets.size >= 2) {
                                val topSegY = offsets.minOf { it.y }.coerceIn(padTop, baselineY - 1f)
                                val areaFillAlpha = if (isSingleSeries) 0.26f else 0.08f
                                val fillPath = buildMonotoneAreaFillPath(
                                    points = offsets,
                                    baselineY = baselineY,
                                    minY = padTop,
                                    maxY = baselineY
                                )
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            spec.color.copy(alpha = areaFillAlpha),
                                            spec.color.copy(alpha = areaFillAlpha * 0.25f),
                                            Color.Transparent
                                        ),
                                        startY = topSegY,
                                        endY = baselineY
                                    )
                                )
                                val strokePath = buildSegmentStrokePath(
                                    points = offsets,
                                    minY = padTop,
                                    maxY = baselineY
                                )
                                drawPath(
                                    path = strokePath,
                                    color = spec.color,
                                    style = Stroke(
                                        width = strokePx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                                listOf(offsets.first(), offsets.last()).forEach { endCenter ->
                                    drawCircle(color = Color.White, radius = 3.8.dp.toPx(), center = endCenter)
                                    drawCircle(color = spec.color, radius = 2.5.dp.toPx(), center = endCenter)
                                }
                            } else if (offsets.size == 1) {
                                val center = offsets.first()
                                drawCircle(
                                    color = spec.color.copy(alpha = 0.22f),
                                    radius = singleDotRadius + 3.dp.toPx(),
                                    center = center
                                )
                                drawCircle(color = Color.White, radius = singleDotRadius + 1.dp.toPx(), center = center)
                                drawCircle(color = spec.color, radius = singleDotRadius, center = center)
                            }
                        }
                    }

                    // 7. Interactive Slot Hairline Indicator
                    if (inspectedSlot != null) {
                        val x = xForCoordPx(slotToXCoord(inspectedSlot))
                        drawLine(
                            color = HomeColors.ForestPrimary.copy(alpha = 0.55f),
                            start = Offset(x, padTop),
                            end = Offset(x, baselineY),
                            strokeWidth = 1.3.dp.toPx(),
                            pathEffect = gapEdgeEffect
                        )
                    }

                    // 8. Highlight Glow on Single-Series Spotlight Point
                    if (isSingleSeries && singleSpotlight != null) {
                        val specColor = lines.first().color
                        val center = Offset(xForCoordPx(singleSpotlight.xHour), yForValuePx(singleSpotlight.value))
                        drawCircle(color = specColor.copy(alpha = 0.22f), radius = 8.5.dp.toPx(), center = center)
                        drawCircle(color = Color.White, radius = 5.2.dp.toPx(), center = center)
                        drawCircle(color = specColor, radius = 3.6.dp.toPx(), center = center)
                    }
                }

                // Floating Value Pill Badge for Single-Series Charts
                if (isSingleSeries && singleSpotlight != null) {
                    val sx = xForCoordPx(singleSpotlight.xHour)
                    val sy = yForValuePx(singleSpotlight.value)
                    val pillText = if (yAxisUnit == "°C") {
                        String.format(Locale.US, "%.1f°C", singleSpotlight.value)
                    } else {
                        "${singleSpotlight.value.roundToInt()}$yAxisUnit"
                    }
                    val estW = with(density) { (pillText.length * 6.5f + 20f).dp.toPx() }
                    val pillH = with(density) { 23.dp.toPx() }
                    val clampedX = (sx - estW / 2f).coerceIn(4f, (boxW - estW - 4f).coerceAtLeast(4f))
                    val clampedY = (sy - pillH - with(density) { 8.dp.toPx() })
                        .coerceIn(2f, (boxH - pillH - 4f).coerceAtLeast(2f))
                    Surface(
                        color = HomeColors.ForestPrimary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                            .shadow(
                                elevation = 3.dp,
                                shape = RoundedCornerShape(8.dp),
                                spotColor = HomeColors.ForestPrimary.copy(alpha = 0.24f)
                            )
                    ) {
                        Text(
                            text = pillText,
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Empty State Overlay
                if (!hasAnyPoint) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = HomeColors.TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = emptyMessage,
                                fontFamily = HomeInterFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = HomeColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom X-Axis (12 AM .. 11 PM or :00 .. :59)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xAxisLabels.forEach { label ->
                Text(
                    text = label,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun LegendDotLabel(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.5.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeColors.TextSecondary
        )
    }
}

// ─── 7. 24-Hour Irrigation Activity Strip & Exact Start–End Daily Log ────────

@Composable
private fun IrrigationHistoryCard(
    dateLabel: String,
    irrigations: List<IrrigationHistoryItem>
) {
    val byHour = remember(irrigations) { irrigations.groupBy { it.hour } }
    val totalDurationSec = irrigations.sumOf { it.durationSec }

    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Irrigation Activity Log",
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = if (irrigations.isEmpty()) {
                            "Auto & manual watering runs on $dateLabel"
                        } else {
                            "${irrigations.size} watering ${if (irrigations.size == 1) "cycle" else "cycles"} • ${totalDurationSec}s total pump run"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 11.5.sp,
                        color = HomeColors.TextSecondary
                    )
                }
                Surface(
                    color = HomeColors.WaterBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            tint = HomeColors.WaterBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${irrigations.size}",
                            fontFamily = HomeInterFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.WaterBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 24-Hour Discrete Pulse Timeline Strip (12 AM – 11 PM)
            Surface(
                color = HomeColors.PageBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HomeColors.DividerLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        for (hour in 0..23) {
                            val runs = byHour[hour].orEmpty()
                            val barFraction = when {
                                runs.isEmpty() -> 0.14f
                                runs.size == 1 -> 0.55f
                                runs.size == 2 -> 0.80f
                                else -> 1.0f
                            }
                            val barColor = when {
                                runs.isEmpty() -> HomeColors.CardBorder
                                runs.any { it.isAborted } -> HomeColors.AlertRed
                                runs.size > 1 -> HomeColors.WaterBlue
                                else -> cropAccentColor(runs.first().crop)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(barFraction)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(barColor)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM").forEach { lbl ->
                            Text(
                                text = lbl,
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (irrigations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HomeColors.PageBg)
                        .padding(vertical = 22.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = HomeColors.TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No irrigation activity recorded on this day.",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = HomeColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    irrigations.forEachIndexed { idx, item ->
                        IrrigationRecordRow(item = item)
                        if (idx < irrigations.lastIndex) {
                            HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IrrigationRecordRow(item: IrrigationHistoryItem) {
    val accent = cropAccentColor(item.crop)
    val startEndText = IotDomainLogic.formatPhStartEndTimeRange(item.startedAt, item.endedAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (item.isAborted) HomeColors.AlertRed else accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                    Text(
                        text = "${item.cropDisplayName} Zone",
                        fontFamily = HomeInterFont,
                        fontSize = 13.5.sp,
                        lineHeight = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = if (item.isManual) HomeColors.PageBg else HomeColors.SoftMint,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isManual) Icons.Filled.TouchApp else Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (item.isManual) "Manual" else "Auto",
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.ForestPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = if (item.isAborted) HomeColors.AlertBg else HomeColors.SuccessBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (item.isAborted) "Aborted" else "Completed",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isAborted) HomeColors.AlertRed else HomeColors.SuccessGreen,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Start – End: $startEndText PHT",
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = HomeColors.TextPrimary
            )

            val moistureDetail = when {
                item.startMoisturePct != null && item.endMoisturePct != null ->
                    String.format(Locale.US, " • Soil %.0f%% → %.0f%%", item.startMoisturePct, item.endMoisturePct)
                item.startMoisturePct != null ->
                    String.format(Locale.US, " • Soil %.0f%%", item.startMoisturePct)
                else -> ""
            }
            Text(
                text = "Duration: ${item.durationSec}s (${item.durationMs}ms)$moistureDetail",
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                color = HomeColors.TextSecondary
            )

            if (item.recordedOffline || (item.isAborted && item.reason.isNotBlank())) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (item.recordedOffline) append("Recorded while ESP32 offline • Synced on reconnect")
                        if (item.isAborted && item.reason.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(item.reason)
                        }
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    color = if (item.isAborted) HomeColors.AlertRed else HomeColors.WarningAmber,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun cropAccentColor(cropSlug: String): Color = when (cropSlug.lowercase(Locale.US)) {
    "tomato" -> HistoryChartColors.TomatoLine
    "chili" -> HistoryChartColors.ChiliLine
    "eggplant" -> HistoryChartColors.EggplantLine
    else -> HomeColors.WaterBlue
}
