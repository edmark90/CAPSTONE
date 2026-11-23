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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.IrrigationHistoryItem
import com.example.smartplantcare.iot.LiveDeviceSnapshot
import com.example.smartplantcare.iot.OfflineGapWindow
import com.example.smartplantcare.iot.SegmentedChartSeries
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
    RAIN("Rain", "%", Icons.Filled.Cloud, HistoryChartColors.RainLine)
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
    isLoadingHistory: Boolean,
    onSelectDate: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val selectedOption = remember(historyDaysWindow, selectedDateKey) {
        historyDaysWindow.firstOrNull { it.dateKey == selectedDateKey }
            ?: historyDaysWindow.lastOrNull()
    }
    val selectedDay = historyByDate[selectedDateKey]
    val isToday = selectedOption?.isToday == true
    val nowMs = remember(selectedDay?.updatedAt, snapshot.receivedAtClientMs) {
        System.currentTimeMillis()
    }

    var soilFilter by remember { mutableStateOf(SoilFilterTab.ALL) }
    var envMetric by remember { mutableStateOf(EnvMetricTab.TEMPERATURE) }
    var inspectedSoilHour by remember(selectedDateKey) { mutableStateOf<Int?>(null) }
    var inspectedEnvHour by remember(selectedDateKey, envMetric) { mutableStateOf<Int?>(null) }

    // Build discontinuous hourly series for each crop zone
    val tomatoSeries = remember(selectedDay, isToday, nowMs) {
        IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
            seg?.tomatoPct ?: rec.tomatoPct
        }
    }
    val chiliSeries = remember(selectedDay, isToday, nowMs) {
        IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
            seg?.chiliPct ?: rec.chiliPct
        }
    }
    val eggplantSeries = remember(selectedDay, isToday, nowMs) {
        IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
            seg?.eggplantPct ?: rec.eggplantPct
        }
    }

    // Build discontinuous hourly series for active environmental / reservoir metric
    val envSeries = remember(selectedDay, envMetric, isToday, nowMs) {
        IotDomainLogic.buildSegmentedHourlySeries(selectedDay, isToday, nowMs) { rec, seg ->
            when (envMetric) {
                EnvMetricTab.TEMPERATURE -> seg?.temperatureC ?: rec.temperatureC
                EnvMetricTab.HUMIDITY -> seg?.humidityPct ?: rec.humidityPct
                EnvMetricTab.LIGHT -> seg?.lightPct ?: rec.lightPct
                EnvMetricTab.RESERVOIR -> seg?.reservoirPct ?: rec.reservoirPct
                EnvMetricTab.RAIN -> (seg?.rainProbPct ?: rec.rainProbPct)?.toFloat()
            }
        }
    }

    // Summary stats for selected day
    val hoursRecorded = selectedDay?.hours?.count { it.value.sampleCount > 0 } ?: 0
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
            start = 16.dp,
            end = 16.dp,
            top = 14.dp,
            bottom = HomeDimens.BottomBarClearance + 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Editorial Header with Back & Refresh
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

        // 3. Selected Date Overview & Summary Badges
        item(key = "day_summary") {
            SelectedDayOverviewCard(
                fullDateLabel = selectedOption?.fullDateLabel ?: selectedDateKey,
                isToday = isToday,
                hoursRecorded = hoursRecorded,
                detectedGapCount = detectedGapCount,
                irrigationCount = irrigations.size,
                totalWaterSec = totalWaterSec
            )
        }

        // 4. Card 1: Crop Soil Moisture Hourly Line Graph (0% – 100%)
        item(key = "soil_moisture_graph") {
            SoilMoistureHistoryCard(
                day = selectedDay,
                config = snapshot.config,
                selectedFilter = soilFilter,
                onSelectFilter = { soilFilter = it },
                tomatoSeries = tomatoSeries,
                chiliSeries = chiliSeries,
                eggplantSeries = eggplantSeries,
                irrigations = irrigations,
                inspectedHour = inspectedSoilHour,
                onInspectHour = { inspectedSoilHour = it }
            )
        }

        // 5. Card 2: Environmental & Reservoir Hourly Line Graph (Unit-Accurate Tabs)
        item(key = "env_tank_graph") {
            EnvironmentalHistoryCard(
                day = selectedDay,
                config = snapshot.config,
                selectedMetric = envMetric,
                onSelectMetric = { envMetric = it },
                series = envSeries,
                inspectedHour = inspectedEnvHour,
                onInspectHour = { inspectedEnvHour = it }
            )
        }

        // 6. Card 3: 24-Hour Irrigation Activity Timeline & Daily Records
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
                .size(44.dp)
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
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$boxName • Philippine Time (PHT)",
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                color = HomeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
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
                        modifier = Modifier.size(19.dp)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Date (Last 7 Days)",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Text(
                    text = "12:00 AM – 11:59 PM PHT",
                    fontFamily = HomeInterFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                days.forEach { option ->
                    val selected = option.dateKey == selectedDateKey
                    val bg = if (selected) HomeColors.ForestPrimary else HomeColors.PageBg
                    val border = if (selected) HomeColors.ForestPrimary else HomeColors.CardBorder
                    val dowColor = if (selected) Color.White.copy(alpha = 0.85f) else HomeColors.TextSecondary
                    val numColor = if (selected) Color.White else HomeColors.TextPrimary

                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, border),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectDate(option.dateKey) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (option.isToday) "Now" else option.dayOfWeekShort,
                                fontFamily = HomeInterFont,
                                fontSize = 9.5.sp,
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
            }
        }
    }
}

// ─── 3. Selected Date Overview Strip ─────────────────────────────────────────

@Composable
private fun SelectedDayOverviewCard(
    fullDateLabel: String,
    isToday: Boolean,
    hoursRecorded: Int,
    detectedGapCount: Int,
    irrigationCount: Int,
    totalWaterSec: Int
) {
    Surface(
        color = HomeColors.SurfaceWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isToday) "$fullDateLabel (Today)" else fullDateLabel,
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = HomeColors.SoftMint,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "7-Day Auto-Pruned",
                        fontFamily = HomeInterFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                    value = "$hoursRecorded/24 hrs",
                    bg = if (hoursRecorded > 0) HomeColors.SuccessBg else HomeColors.PageBg,
                    fg = if (hoursRecorded > 0) HomeColors.SuccessGreen else HomeColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricChip(
                    label = "Offline Gaps",
                    value = when {
                        hoursRecorded == 0 -> "No data"
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = HomeColors.TextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── 4. Card 1: Crop Soil Moisture Line Graph (0% – 100%) ────────────────────

@Composable
private fun SoilMoistureHistoryCard(
    day: DailyHistorySnapshot?,
    config: DeviceConfig,
    selectedFilter: SoilFilterTab,
    onSelectFilter: (SoilFilterTab) -> Unit,
    tomatoSeries: SegmentedChartSeries,
    chiliSeries: SegmentedChartSeries,
    eggplantSeries: SegmentedChartSeries,
    irrigations: List<IrrigationHistoryItem>,
    inspectedHour: Int?,
    onInspectHour: (Int?) -> Unit
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

    val activeIrrigationHours = remember(selectedFilter, irrigations) {
        irrigations
            .filter { selectedFilter == SoilFilterTab.ALL || it.crop.equals(selectedFilter.slug, true) }
            .map { it.hour }
            .toSet()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Crop Soil Moisture",
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = "Hourly readings (0% – 100%) • Breaks on ESP32 offline",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary
                    )
                }
            }

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
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (chosen) Color.White else HomeColors.TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Inspector Callout Strip
            SoilInspectionBanner(
                day = day,
                inspectedHour = inspectedHour,
                selectedFilter = selectedFilter,
                activeIrrigationHours = activeIrrigationHours
            )

            Spacer(modifier = Modifier.height(8.dp))

            DiscontinuousHourlyLineChart(
                lines = activeLines,
                gaps = unionGaps,
                minY = 0f,
                maxY = 100f,
                yAxisUnit = "%",
                yTickValues = listOf(0f, 25f, 50f, 75f, 100f),
                thresholds = thresholds,
                irrigationHours = activeIrrigationHours,
                inspectedHour = inspectedHour,
                onInspectHour = onInspectHour,
                emptyMessage = "No soil moisture readings recorded on this date."
            )
        }
    }
}

@Composable
private fun SoilInspectionBanner(
    day: DailyHistorySnapshot?,
    inspectedHour: Int?,
    selectedFilter: SoilFilterTab,
    activeIrrigationHours: Set<Int>
) {
    Surface(
        color = HomeColors.PageBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HomeColors.DividerLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (inspectedHour == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap or drag graph to inspect any hour",
                    fontFamily = HomeInterFont,
                    fontSize = 10.5.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegendDotLabel(color = HistoryChartColors.OfflineBandBorder, label = "Offline Gap")
                    LegendDotLabel(color = HomeColors.WaterBlue, label = "Watered")
                }
            }
        } else {
            val rec = day?.hours?.get(inspectedHour)
            val hourLabel = IotDomainLogic.formatHourRangeLabel(inspectedHour)
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$hourLabel PHT",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary
                    )
                    if (inspectedHour in activeIrrigationHours) {
                        Text(
                            text = "• Watered this hour",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.WaterBlue
                        )
                    } else if (rec == null || rec.sampleCount <= 0) {
                        Text(
                            text = "ESP32 Offline",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.AlertRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                if (rec == null || rec.sampleCount <= 0) {
                    Text(
                        text = "No readings transmitted — device offline or disconnected.",
                        fontFamily = HomeInterFont,
                        fontSize = 10.5.sp,
                        color = HomeColors.TextSecondary
                    )
                } else {
                    val summary = buildString {
                        if (selectedFilter == SoilFilterTab.ALL || selectedFilter == SoilFilterTab.TOMATO) {
                            append("Tomato: ")
                            append(rec.tomatoPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "No data")
                        }
                        if (selectedFilter == SoilFilterTab.ALL || selectedFilter == SoilFilterTab.CHILI) {
                            if (isNotEmpty()) append("  •  ")
                            append("Chili: ")
                            append(rec.chiliPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "No data")
                        }
                        if (selectedFilter == SoilFilterTab.ALL || selectedFilter == SoilFilterTab.EGGPLANT) {
                            if (isNotEmpty()) append("  •  ")
                            append("Eggplant: ")
                            append(rec.eggplantPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "No data")
                        }
                    }
                    Text(
                        text = summary,
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextPrimary
                    )
                }
            }
        }
    }
}

// ─── 5. Card 2: Environmental & Reservoir Trends (Single-Unit Tabs) ──────────

@Composable
private fun EnvironmentalHistoryCard(
    day: DailyHistorySnapshot?,
    config: DeviceConfig,
    selectedMetric: EnvMetricTab,
    onSelectMetric: (EnvMetricTab) -> Unit,
    series: SegmentedChartSeries,
    inspectedHour: Int?,
    onInspectHour: (Int?) -> Unit
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
                text = "Environment & Water Tank",
                fontFamily = HomeInterFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
            Text(
                text = when (selectedMetric) {
                    EnvMetricTab.TEMPERATURE -> "DHT22 Air Temperature (°C)"
                    EnvMetricTab.HUMIDITY -> "DHT22 Relative Humidity (% RH)"
                    EnvMetricTab.LIGHT -> "LDR Relative Light Brightness Index (%)"
                    EnvMetricTab.RESERVOIR -> "7-Inch Ultrasonic Water Tank Level (%)"
                    EnvMetricTab.RAIN -> "Open-Meteo 3-Hour Rain Probability (%)"
                },
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
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
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (chosen) Color.White else tab.color,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = tab.label,
                                fontFamily = HomeInterFont,
                                fontSize = 11.5.sp,
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
                inspectedHour = inspectedHour,
                metric = selectedMetric
            )

            Spacer(modifier = Modifier.height(8.dp))

            DiscontinuousHourlyLineChart(
                lines = lines,
                gaps = series.gaps,
                minY = minY,
                maxY = maxY,
                yAxisUnit = selectedMetric.unit,
                yTickValues = ticks,
                thresholds = thresholds,
                irrigationHours = emptySet(),
                inspectedHour = inspectedHour,
                onInspectHour = onInspectHour,
                emptyMessage = "No ${selectedMetric.label.lowercase(Locale.US)} readings recorded on this date."
            )
        }
    }
}

@Composable
private fun EnvInspectionBanner(
    day: DailyHistorySnapshot?,
    inspectedHour: Int?,
    metric: EnvMetricTab
) {
    Surface(
        color = HomeColors.PageBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HomeColors.DividerLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (inspectedHour == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap or drag graph to inspect hourly ${metric.label.lowercase(Locale.US)}",
                    fontFamily = HomeInterFont,
                    fontSize = 10.5.sp,
                    color = HomeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LegendDotLabel(color = HistoryChartColors.OfflineBandBorder, label = "Offline Gap")
            }
        } else {
            val rec = day?.hours?.get(inspectedHour)
            val hourLabel = IotDomainLogic.formatHourRangeLabel(inspectedHour)
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$hourLabel PHT",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.ForestPrimary
                    )
                    if (rec == null || rec.sampleCount <= 0) {
                        Text(
                            text = "ESP32 Offline",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.AlertRed
                        )
                    } else if (rec.gapCount > 0) {
                        Text(
                            text = "Reconnected mid-hour",
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.WarningAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                if (rec == null || rec.sampleCount <= 0) {
                    Text(
                        text = "No readings transmitted — device offline or disconnected.",
                        fontFamily = HomeInterFont,
                        fontSize = 10.5.sp,
                        color = HomeColors.TextSecondary
                    )
                } else {
                    val valueText = when (metric) {
                        EnvMetricTab.TEMPERATURE ->
                            rec.temperatureC?.let { String.format(Locale.US, "Temperature: %.1f°C", it) }
                                ?: "Temperature: Sensor invalid (no reading)"
                        EnvMetricTab.HUMIDITY ->
                            rec.humidityPct?.let { String.format(Locale.US, "Humidity: %.1f%% RH", it) }
                                ?: "Humidity: Sensor invalid (no reading)"
                        EnvMetricTab.LIGHT ->
                            rec.lightPct?.let { String.format(Locale.US, "Light Index: %.1f%%", it) }
                                ?: "Light: Sensor invalid (no reading)"
                        EnvMetricTab.RESERVOIR -> {
                            val pct = rec.reservoirPct
                            val gap = rec.reservoirGapIn
                            if (pct != null) {
                                val gapSuffix = if (gap != null) String.format(Locale.US, " (%.2f in air gap)", gap) else ""
                                String.format(Locale.US, "Water Tank: %.1f%%%s", pct, gapSuffix)
                            } else {
                                "Water Tank: Ultrasonic fault (no reading)"
                            }
                        }
                        EnvMetricTab.RAIN -> {
                            val prob = rec.rainProbPct
                            val wetSuffix = if (rec.rainWetSamples > 0) " • Rain plate wet detected" else " • Plate dry"
                            if (prob != null) "Rain Forecast: $prob%$wetSuffix"
                            else "Rain Forecast: Unavailable$wetSuffix"
                        }
                    }
                    Text(
                        text = valueText,
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextPrimary
                    )
                }
            }
        }
    }
}

// ─── Reusable Discontinuous 24-Hour Compose Canvas Line Chart ────────────────

@Composable
private fun DiscontinuousHourlyLineChart(
    lines: List<ChartLineSpec>,
    gaps: List<OfflineGapWindow>,
    minY: Float,
    maxY: Float,
    yAxisUnit: String,
    yTickValues: List<Float>,
    thresholds: List<ThresholdGuide>,
    irrigationHours: Set<Int>,
    inspectedHour: Int?,
    onInspectHour: (Int?) -> Unit,
    emptyMessage: String
) {
    val hasAnyPoint = lines.any { !it.series.isEmpty }
    val isSingleSeries = lines.size == 1
    val safeSpanY = (maxY - minY).coerceAtLeast(1f)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }
    val gapEdgeEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-Axis Labels (aligned with horizontal gridlines)
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .padding(top = if (isSingleSeries) 18.dp else 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yTickValues.reversed().forEach { tick ->
                    Text(
                        text = "${tick.roundToInt()}$yAxisUnit",
                        fontFamily = HomeInterFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.TextMuted,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Main Plot Canvas with Smooth Monotone Curves, Soft Gradient Fill & Floating Callout
            val singleSpotlight = remember(lines, inspectedHour, isSingleSeries) {
                if (!isSingleSeries) null
                else {
                    val pts = lines.first().series.segments.flatten()
                    if (pts.isEmpty()) null
                    else if (inspectedHour != null) pts.filter { it.hour == inspectedHour }.lastOrNull()
                    else pts.lastOrNull()
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFAFDFB))
                    .pointerInput(lines) {
                        detectTapGestures { offset ->
                            val usableW = (size.width - 16.dp.toPx()).coerceAtLeast(1f)
                            val frac = ((offset.x - 8.dp.toPx()) / usableW).coerceIn(0f, 1f)
                            val hour = (frac * 23f).roundToInt().coerceIn(0, 23)
                            onInspectHour(if (inspectedHour == hour) null else hour)
                        }
                    }
                    .pointerInput(lines) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val usableW = (size.width - 16.dp.toPx()).coerceAtLeast(1f)
                            val frac = ((change.position.x - 8.dp.toPx()) / usableW).coerceIn(0f, 1f)
                            val hour = (frac * 23f).roundToInt().coerceIn(0, 23)
                            onInspectHour(hour)
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

                fun xForHourPx(xHour: Float): Float =
                    padH + (xHour.coerceIn(0f, 23.98f) / 23.5f).coerceIn(0f, 1f) * plotW

                fun yForValuePx(v: Float): Float {
                    val norm = ((v - minY) / safeSpanY).coerceIn(0f, 1f)
                    return padTop + plotH - (norm * plotH)
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baselineY = padTop + plotH
                    if (plotW <= 1f || plotH <= 1f) return@Canvas

                    // 1. Clean Horizontal Hairline Grid
                    yTickValues.forEach { tick ->
                        val y = yForValuePx(tick)
                        drawLine(
                            color = HistoryChartColors.GridLine,
                            start = Offset(padH, y),
                            end = Offset(padH + plotW, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Subtle Vertical 4-hour guides
                    listOf(0f, 4f, 8f, 12f, 16f, 20f, 23f).forEach { hr ->
                        val x = xForHourPx(hr)
                        drawLine(
                            color = HistoryChartColors.GridLine.copy(alpha = 0.45f),
                            start = Offset(x, padTop),
                            end = Offset(x, baselineY),
                            strokeWidth = 0.7.dp.toPx()
                        )
                    }

                    // 3. Offline Gap Bands (visually highlighting ESP32 disconnections)
                    gaps.forEach { gap ->
                        val x1 = xForHourPx(gap.startXHour)
                        val x2 = xForHourPx(gap.endXHour)
                        if (x2 > x1 + 2f) {
                            drawRoundRect(
                                color = HistoryChartColors.OfflineBandBg,
                                topLeft = Offset(x1, padTop),
                                size = Size(x2 - x1, plotH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawLine(
                                color = HistoryChartColors.OfflineBandBorder,
                                start = Offset(x1, padTop),
                                end = Offset(x1, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gapEdgeEffect
                            )
                            drawLine(
                                color = HistoryChartColors.OfflineBandBorder,
                                start = Offset(x2, padTop),
                                end = Offset(x2, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gapEdgeEffect
                            )
                        }
                    }

                    // 4. Optional Threshold Reference Guides
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

                    // 5. Irrigation Markers along the bottom timeline
                    irrigationHours.forEach { hr ->
                        val x = xForHourPx(hr.toFloat())
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

                    // 6. Smooth Area-Filled Discontinuous Sensor Line Segments (NEVER connects across gaps!)
                    val strokePx = 2.5.dp.toPx()
                    val singleDotRadius = 4.2.dp.toPx()

                    lines.forEach { spec ->
                        spec.series.segments.forEach { seg ->
                            val offsets = seg.map { pt -> Offset(xForHourPx(pt.xHour), yForValuePx(pt.value)) }
                            if (offsets.size >= 2) {
                                val topSegY = offsets.minOf { it.y }.coerceIn(padTop, baselineY - 1f)
                                val areaFillAlpha = if (isSingleSeries) 0.28f else 0.08f
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
                                val strokePath = buildMonotoneCubicPath(
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
                                // Crisp endpoint markers so segment starts/stops around gaps stay unmistakable
                                listOf(offsets.first(), offsets.last()).forEach { endCenter ->
                                    drawCircle(color = Color.White, radius = 4.2.dp.toPx(), center = endCenter)
                                    drawCircle(color = spec.color, radius = 2.8.dp.toPx(), center = endCenter)
                                }
                            } else if (offsets.size == 1) {
                                val center = offsets.first()
                                drawCircle(
                                    color = spec.color.copy(alpha = 0.22f),
                                    radius = singleDotRadius + 3.5.dp.toPx(),
                                    center = center
                                )
                                drawCircle(color = Color.White, radius = singleDotRadius + 1.2.dp.toPx(), center = center)
                                drawCircle(color = spec.color, radius = singleDotRadius, center = center)
                            }
                        }
                    }

                    // 7. Interactive Hour Hairline Indicator
                    if (inspectedHour != null) {
                        val x = xForHourPx(inspectedHour.toFloat())
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
                        val center = Offset(xForHourPx(singleSpotlight.xHour), yForValuePx(singleSpotlight.value))
                        drawCircle(color = specColor.copy(alpha = 0.22f), radius = 8.5.dp.toPx(), center = center)
                        drawCircle(color = Color.White, radius = 5.2.dp.toPx(), center = center)
                        drawCircle(color = specColor, radius = 3.6.dp.toPx(), center = center)
                    }
                }

                // Floating Dark Value Pill Badge for Single-Series Charts
                if (isSingleSeries && singleSpotlight != null) {
                    val sx = xForHourPx(singleSpotlight.xHour)
                    val sy = yForValuePx(singleSpotlight.value)
                    val pillText = if (yAxisUnit == "°C") {
                        String.format(Locale.US, "%.1f°C", singleSpotlight.value)
                    } else {
                        "${singleSpotlight.value.roundToInt()}$yAxisUnit"
                    }
                    val estW = with(density) { (pillText.length * 6.5f + 20f).dp.toPx() }
                    val pillH = with(density) { 22.dp.toPx() }
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
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Clean Empty State Overlay when selected day has no recorded data
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = HomeColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Bottom 24-Hour Time Axis (12:00 AM .. 11:59 PM PHT)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 46.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12AM", "4AM", "8AM", "12PM", "4PM", "8PM", "11PM").forEach { label ->
                Text(
                    text = label,
                    fontFamily = HomeInterFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeColors.TextMuted
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
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeColors.TextSecondary
        )
    }
}

// ─── 6. Card 3: 24-Hour Irrigation Activity Strip & Daily Log ────────────────

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
                            "Actual watering pulses on $dateLabel"
                        } else {
                            "${irrigations.size} watering ${if (irrigations.size == 1) "cycle" else "cycles"} • ${totalDurationSec}s total pump run"
                        },
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        color = HomeColors.TextSecondary
                    )
                }
                Surface(
                    color = HomeColors.WaterBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            tint = HomeColors.WaterBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${irrigations.size}",
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.WaterBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 24-Hour Discrete Pulse Timeline Strip (12AM – 11PM)
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
                        listOf("12AM", "6AM", "12PM", "6PM", "11PM").forEach { lbl ->
                            Text(
                                text = lbl,
                                fontFamily = HomeInterFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.TextMuted
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
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
    val timeText = IotDomainLogic.formatPhTimeLabel(
        if (item.startedAt >= 1_700_000_000_000L) item.startedAt else item.endedAt
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(50))
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${item.cropDisplayName} Zone",
                        fontFamily = HomeInterFont,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                    Surface(
                        color = if (item.isManual) HomeColors.PageBg else HomeColors.SoftMint,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isManual) Icons.Filled.TouchApp else Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (item.isManual) "Manual" else "Auto",
                                fontFamily = HomeInterFont,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.ForestPrimary
                            )
                        }
                    }
                }

                Surface(
                    color = if (item.isAborted) HomeColors.AlertBg else HomeColors.SuccessBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (item.isAborted) "Aborted" else "Completed",
                        fontFamily = HomeInterFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isAborted) HomeColors.AlertRed else HomeColors.SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            val moistureDetail = when {
                item.startMoisturePct != null && item.endMoisturePct != null ->
                    String.format(Locale.US, " • Soil %.0f%% → %.0f%%", item.startMoisturePct, item.endMoisturePct)
                item.startMoisturePct != null ->
                    String.format(Locale.US, " • Soil %.0f%%", item.startMoisturePct)
                else -> ""
            }
            Text(
                text = "$timeText PHT • ${item.durationSec}s pulse$moistureDetail",
                fontFamily = HomeInterFont,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = HomeColors.TextSecondary
            )

            if (item.recordedOffline || (item.isAborted && item.reason.isNotBlank())) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (item.recordedOffline) append("Offline fallback • Synced on reconnect")
                        if (item.isAborted && item.reason.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(item.reason)
                        }
                    },
                    fontFamily = HomeInterFont,
                    fontSize = 10.sp,
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
