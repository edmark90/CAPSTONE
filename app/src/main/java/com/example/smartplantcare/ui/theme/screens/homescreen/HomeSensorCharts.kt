package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.iot.ChartDataPoint
import com.example.smartplantcare.iot.HistoryGranularity
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.OfflineGapWindow
import com.example.smartplantcare.iot.PointInspectionStatus
import com.example.smartplantcare.iot.SegmentedChartSeries
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Builds a fast polyline path for dense 1-minute All Day segments (>90 points) while keeping
 * monotone cubic splines for hourly and single-hour (:00–:59) segments.
 */
internal fun buildSegmentStrokePath(
    points: List<Offset>,
    minY: Float,
    maxY: Float
): Path {
    if (points.size > 90) {
        return Path().apply {
            moveTo(points[0].x, points[0].y.coerceIn(minY, maxY))
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y.coerceIn(minY, maxY))
            }
        }
    }
    return buildMonotoneCubicPath(points, minY, maxY)
}

/**
 * Computes a Fritsch-Carlson monotone cubic Hermite spline path through [points].
 * Guarantees smooth curves with ZERO vertical overshoot past local extrema.
 */
internal fun buildMonotoneCubicPath(
    points: List<Offset>,
    minY: Float,
    maxY: Float
): Path {
    val path = Path()
    val n = points.size
    if (n == 0) return path
    path.moveTo(points[0].x, points[0].y)
    if (n == 1) return path
    if (n == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    val h = FloatArray(n - 1)
    val d = FloatArray(n - 1)
    for (i in 0 until n - 1) {
        val dx = (points[i + 1].x - points[i].x).coerceAtLeast(0.001f)
        h[i] = dx
        d[i] = (points[i + 1].y - points[i].y) / dx
    }

    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) {
        m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) * 0.5f
    }

    // Fritsch-Carlson monotonicity clamping
    for (i in 0 until n - 1) {
        if (abs(d[i]) < 1e-6f) {
            m[i] = 0f
            m[i + 1] = 0f
        } else {
            val alpha = m[i] / d[i]
            val beta = m[i + 1] / d[i]
            val radius = hypot(alpha, beta)
            if (radius > 3f) {
                val tau = 3f / radius
                m[i] = tau * alpha * d[i]
                m[i + 1] = tau * beta * d[i]
            }
        }
    }

    for (i in 0 until n - 1) {
        val segH = h[i]
        val cx1 = points[i].x + segH / 3f
        val cy1 = (points[i].y + m[i] * segH / 3f).coerceIn(minY, maxY)
        val cx2 = points[i + 1].x - segH / 3f
        val cy2 = (points[i + 1].y - m[i + 1] * segH / 3f).coerceIn(minY, maxY)
        path.cubicTo(cx1, cy1, cx2, cy2, points[i + 1].x, points[i + 1].y)
    }
    return path
}

/**
 * Builds the closed vertical fill area directly under a single continuous segment.
 * Because each segment is filled independently, missing/offline gaps remain completely clear.
 */
internal fun buildMonotoneAreaFillPath(
    points: List<Offset>,
    baselineY: Float,
    minY: Float,
    maxY: Float
): Path {
    val strokePath = buildSegmentStrokePath(points, minY, maxY)
    if (points.size < 2) return strokePath
    return Path().apply {
        addPath(strokePath)
        lineTo(points.last().x, baselineY)
        lineTo(points.first().x, baselineY)
        close()
    }
}

/**
 * Discontinuous sensor trend line chart supporting:
 * - `BY_HOURS`: 12 AM – 11 PM PHT (slots `0..23`)
 * - `BY_MINUTES` All Day: 12:00 AM – 11:59 PM PHT (slots `0..1439`, breaks on 1 missing minute)
 * - `BY_MINUTES` Single Hour: `:00 – :59` within selected hour (slots `0..59`, breaks on 1 missing minute)
 *
 * Strictly breaks line segments and area fill across any missing expected slot or sensor fault —
 * never connects across offline gaps and never substitutes fake zeros.
 */
@Composable
internal fun ModernTrendLineChart(
    series: SegmentedChartSeries,
    granularity: HistoryGranularity = HistoryGranularity.BY_HOURS,
    selectedMinuteHour: Int? = null,
    minY: Float,
    maxY: Float,
    yTicks: List<Float>,
    yAxisUnit: String,
    lineColor: Color,
    fillTopColor: Color,
    pillBgColor: Color,
    formatValue: (Float) -> String,
    inspectedSlot: Int?,
    onInspectSlot: (Int?) -> Unit,
    resolveMissingStatus: ((Int) -> PointInspectionStatus)? = null,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    val allPoints = remember(series) { series.segments.flatten() }
    val hasPoints = allPoints.isNotEmpty()
    val safeSpanY = (maxY - minY).coerceAtLeast(1f)
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

    val spotlightPoint: ChartDataPoint? = remember(
        allPoints,
        inspectedSlot,
        granularity,
        selectedMinuteHour
    ) {
        if (!hasPoints) {
            null
        } else if (inspectedSlot != null) {
            when {
                granularity == HistoryGranularity.BY_HOURS ->
                    allPoints.lastOrNull { it.hour == inspectedSlot }
                isSingleHourMinuteMode -> {
                    val targetMinOfDay = selectedMinuteHour!!.coerceIn(0, 23) * 60 + inspectedSlot.coerceIn(0, 59)
                    allPoints.lastOrNull { it.minuteOfDay == targetMinOfDay }
                }
                else -> allPoints.lastOrNull { it.minuteOfDay == inspectedSlot }
            }
        } else {
            allPoints.lastOrNull()
        }
    }

    val missingSlotStatus: PointInspectionStatus? = remember(
        inspectedSlot,
        spotlightPoint,
        resolveMissingStatus
    ) {
        if (inspectedSlot != null && spotlightPoint == null) {
            resolveMissingStatus?.invoke(inspectedSlot) ?: PointInspectionStatus.DEVICE_OFFLINE
        } else {
            null
        }
    }

    val xAxisLabels = remember(granularity, selectedMinuteHour) {
        if (isSingleHourMinuteMode) {
            listOf(":00", ":15", ":30", ":45", ":59")
        } else {
            listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM")
        }
    }

    fun slotToXCoord(slot: Int): Float = when {
        granularity == HistoryGranularity.BY_HOURS -> slot.coerceIn(0, 23).toFloat()
        isSingleHourMinuteMode -> slot.coerceIn(0, 59).toFloat()
        else -> slot.coerceIn(0, 1439) / 60f
    }

    fun formatSlotLabel(slot: Int): String = when {
        granularity == HistoryGranularity.BY_HOURS -> IotDomainLogic.formatSingleHour12(slot)
        isSingleHourMinuteMode -> {
            val minOfDay = selectedMinuteHour!!.coerceIn(0, 23) * 60 + slot.coerceIn(0, 59)
            IotDomainLogic.formatMinuteOfDay12(minOfDay)
        }
        else -> IotDomainLogic.formatMinuteOfDay12(slot)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-Axis Labels
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .padding(top = 24.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yTicks.reversed().forEach { tick ->
                    Text(
                        text = "${tick.roundToInt()}$yAxisUnit",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextSecondary.copy(alpha = 0.82f),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Interactive Plot Area
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(series, granularity, selectedMinuteHour, inspectedSlot) {
                        detectTapGestures { offset ->
                            val usableW = (size.width - 16.dp.toPx()).coerceAtLeast(1f)
                            val frac = ((offset.x - 8.dp.toPx()) / usableW).coerceIn(0f, 1f)
                            val slot = (frac * maxSlotIndex).roundToInt().coerceIn(0, maxSlotIndex)
                            onInspectSlot(if (inspectedSlot == slot) null else slot)
                        }
                    }
                    .pointerInput(series, granularity, selectedMinuteHour) {
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
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val boxHeightPx = with(density) { maxHeight.toPx() }
                val padLeftPx = with(density) { 8.dp.toPx() }
                val padRightPx = with(density) { 8.dp.toPx() }
                val padTopPx = with(density) { 30.dp.toPx() }
                val padBottomPx = with(density) { 10.dp.toPx() }
                val plotW = (boxWidthPx - padLeftPx - padRightPx).coerceAtLeast(1f)
                val plotH = (boxHeightPx - padTopPx - padBottomPx).coerceAtLeast(1f)

                fun xForCoord(xVal: Float): Float =
                    padLeftPx + (xVal.coerceIn(0f, xClampMax) / xDomainMax).coerceIn(0f, 1f) * plotW

                fun yForValue(v: Float): Float {
                    val norm = ((v - minY) / safeSpanY).coerceIn(0f, 1f)
                    return padTopPx + plotH - (norm * plotH)
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baselineY = padTopPx + plotH

                    // 1. Horizontal Gridlines
                    yTicks.forEach { tick ->
                        val y = yForValue(tick)
                        drawLine(
                            color = Color(0xFFEDF3EF),
                            start = Offset(padLeftPx, y),
                            end = Offset(padLeftPx + plotW, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Disconnected / Offline Gap Bands
                    series.gaps.forEach { gap: OfflineGapWindow ->
                        val x1 = xForCoord(gap.startXHour)
                        val x2 = xForCoord(gap.endXHour)
                        val bandWidth = (x2 - x1).coerceAtLeast(2.dp.toPx())
                        if (x2 >= x1) {
                            drawRoundRect(
                                color = HomeColors.AlertRed.copy(alpha = 0.055f),
                                topLeft = Offset(x1, padTopPx),
                                size = Size(bandWidth, plotH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawLine(
                                color = HomeColors.AlertRed.copy(alpha = 0.28f),
                                start = Offset(x1, padTopPx),
                                end = Offset(x1, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gapEdgeEffect
                            )
                            if (x2 - x1 > 4.dp.toPx()) {
                                drawLine(
                                    color = HomeColors.AlertRed.copy(alpha = 0.28f),
                                    start = Offset(x2, padTopPx),
                                    end = Offset(x2, baselineY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = gapEdgeEffect
                                )
                            }
                        }
                    }

                    // 3. Discontinuous Segments (strictly broken across missing minutes/hours)
                    val strokePx = if (granularity == HistoryGranularity.BY_MINUTES && !isSingleHourMinuteMode) {
                        2.2.dp.toPx()
                    } else {
                        2.6.dp.toPx()
                    }
                    series.segments.forEach { seg ->
                        val offsets = seg.map { pt -> Offset(xForCoord(pt.xHour), yForValue(pt.value)) }
                        if (offsets.size >= 2) {
                            val topOfSeg = offsets.minOf { it.y }.coerceIn(padTopPx, baselineY - 1f)
                            val areaPath = buildMonotoneAreaFillPath(
                                points = offsets,
                                baselineY = baselineY,
                                minY = padTopPx,
                                maxY = baselineY
                            )
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        fillTopColor.copy(alpha = 0.28f),
                                        fillTopColor.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    startY = topOfSeg,
                                    endY = baselineY
                                )
                            )
                            val strokePath = buildSegmentStrokePath(
                                points = offsets,
                                minY = padTopPx,
                                maxY = baselineY
                            )
                            drawPath(
                                path = strokePath,
                                color = lineColor,
                                style = Stroke(
                                    width = strokePx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else if (offsets.size == 1) {
                            val pt = offsets.first()
                            drawCircle(
                                color = lineColor.copy(alpha = 0.18f),
                                radius = 7.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.5.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 3.2.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    // 4. Inspected Slot Hairline
                    if (inspectedSlot != null) {
                        val ix = spotlightPoint?.let { xForCoord(it.xHour) }
                            ?: xForCoord(slotToXCoord(inspectedSlot))
                        val hairlineColor = when (missingSlotStatus) {
                            PointInspectionStatus.DEVICE_OFFLINE -> HomeColors.AlertRed
                            PointInspectionStatus.SENSOR_FAULT,
                            PointInspectionStatus.PHONE_OFFLINE -> HomeColors.WarningAmber
                            PointInspectionStatus.FUTURE_TIME,
                            PointInspectionStatus.LEGACY_HOURLY_ONLY -> HomeColors.TextMuted
                            else -> pillBgColor
                        }
                        drawLine(
                            color = hairlineColor.copy(alpha = 0.45f),
                            start = Offset(ix, padTopPx),
                            end = Offset(ix, baselineY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = gapEdgeEffect
                        )
                    }

                    // 5. Spotlight Marker on Latest / Inspected Valid Point
                    if (spotlightPoint != null) {
                        val sx = xForCoord(spotlightPoint.xHour)
                        val sy = yForValue(spotlightPoint.value)
                        drawCircle(
                            color = lineColor.copy(alpha = 0.22f),
                            radius = 8.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.2.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 3.6.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }

                // 6. Floating Callout Pill Badge
                if (spotlightPoint != null) {
                    val sx = xForCoord(spotlightPoint.xHour)
                    val sy = yForValue(spotlightPoint.value)
                    val labelText = if (inspectedSlot != null) {
                        val timeStr = when {
                            granularity == HistoryGranularity.BY_MINUTES && spotlightPoint.minuteOfDay >= 0 ->
                                IotDomainLogic.formatMinuteOfDay12(spotlightPoint.minuteOfDay)
                            spotlightPoint.epochMs >= 1_700_000_000_000L ->
                                IotDomainLogic.formatPhTimeLabel(spotlightPoint.epochMs)
                            else -> IotDomainLogic.formatSingleHour12(spotlightPoint.hour)
                        }
                        "$timeStr · ${formatValue(spotlightPoint.value)}"
                    } else {
                        formatValue(spotlightPoint.value)
                    }
                    val estPillWidthPx = with(density) { (labelText.length * 6.5f + 22f).dp.toPx() }
                    val pillHeightPx = with(density) { 24.dp.toPx() }
                    val clampedX = (sx - estPillWidthPx / 2f)
                        .coerceIn(4f, (boxWidthPx - estPillWidthPx - 4f).coerceAtLeast(4f))
                    val clampedY = (sy - pillHeightPx - with(density) { 10.dp.toPx() })
                        .coerceIn(0f, (boxHeightPx - pillHeightPx - 4f).coerceAtLeast(0f))

                    Surface(
                        color = pillBgColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                            .shadow(
                                elevation = 3.dp,
                                shape = RoundedCornerShape(8.dp),
                                spotColor = pillBgColor.copy(alpha = 0.22f)
                            )
                    ) {
                        Text(
                            text = labelText,
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                } else if (inspectedSlot != null && hasPoints && missingSlotStatus != null) {
                    val ix = xForCoord(slotToXCoord(inspectedSlot))
                    val (statusText, statusColor) = when (missingSlotStatus) {
                        PointInspectionStatus.FUTURE_TIME -> "Upcoming" to HomeColors.TextSecondary
                        PointInspectionStatus.SENSOR_FAULT -> "Sensor Fault" to HomeColors.WarningAmber
                        PointInspectionStatus.PHONE_OFFLINE -> "No Internet" to HomeColors.WarningAmber
                        PointInspectionStatus.LEGACY_HOURLY_ONLY -> "Hourly Only" to HomeColors.TextSecondary
                        PointInspectionStatus.DEVICE_OFFLINE,
                        PointInspectionStatus.VALID -> "Offline" to HomeColors.AlertRed
                    }
                    val labelText = "${formatSlotLabel(inspectedSlot)} · $statusText"
                    val estPillWidthPx = with(density) { (labelText.length * 6.4f + 22f).dp.toPx() }
                    val clampedX = (ix - estPillWidthPx / 2f)
                        .coerceIn(4f, (boxWidthPx - estPillWidthPx - 4f).coerceAtLeast(4f))
                    Surface(
                        color = statusColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.offset { IntOffset(clampedX.roundToInt(), 2) }
                    ) {
                        Text(
                            text = labelText,
                            fontFamily = HomeInterFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                // 7. Empty State
                if (!hasPoints) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 18.dp, start = 12.dp, end = 12.dp, bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = HomeColors.TextMuted,
                                modifier = Modifier.size(22.dp)
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

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom X-Axis Labels (12 AM .. 11 PM or :00 .. :59)
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
                    color = HomeColors.TextSecondary.copy(alpha = 0.78f),
                    maxLines = 1
                )
            }
        }
    }
}
