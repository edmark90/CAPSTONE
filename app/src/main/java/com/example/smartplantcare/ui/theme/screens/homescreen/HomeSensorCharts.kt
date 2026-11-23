package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
import com.example.smartplantcare.iot.IotDomainLogic
import com.example.smartplantcare.iot.OfflineGapWindow
import com.example.smartplantcare.iot.SegmentedChartSeries
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

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
 * Builds the closed vertical fill area directly under a single continuous spline segment.
 * Because each segment is filled independently, ESP32 offline gaps remain completely clear.
 */
internal fun buildMonotoneAreaFillPath(
    points: List<Offset>,
    baselineY: Float,
    minY: Float,
    maxY: Float
): Path {
    val strokePath = buildMonotoneCubicPath(points, minY, maxY)
    if (points.size < 2) return strokePath
    val fill = Path().apply {
        addPath(strokePath)
        lineTo(points.last().x, baselineY)
        lineTo(points.first().x, baselineY)
        close()
    }
    return fill
}

/**
 * Modern smooth area-filled discontinuous 24-hour sensor line chart (12 AM – 11:59 PM PHT).
 * Strictly breaks the curve & area fill across ESP32 offline gaps — never bridges outages or plots 0.
 */
@Composable
internal fun ModernTrendLineChart(
    series: SegmentedChartSeries,
    minY: Float,
    maxY: Float,
    yTicks: List<Float>,
    yAxisUnit: String,
    lineColor: Color,
    fillTopColor: Color,
    pillBgColor: Color,
    formatValue: (Float) -> String,
    inspectedHour: Int?,
    onInspectHour: (Int?) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    val allPoints = remember(series) { series.segments.flatten() }
    val hasPoints = allPoints.isNotEmpty()
    val safeSpanY = (maxY - minY).coerceAtLeast(1f)
    val gapEdgeEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

    // Determine which point to spotlight with the floating pill badge:
    // - If user tapped/dragged an hour: show the point closest to that hour (or offline badge if missing)
    // - Otherwise: highlight the latest valid recorded point (matching mockup)
    val spotlightPoint: ChartDataPoint? = remember(allPoints, inspectedHour) {
        if (!hasPoints) {
            null
        } else if (inspectedHour != null) {
            allPoints.filter { it.hour == inspectedHour }.lastOrNull()
        } else {
            allPoints.lastOrNull()
        }
    }
    val inspectedIsOfflineGap = inspectedHour != null && spotlightPoint == null && hasPoints

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Crisp Left Y-Axis Labels (aligned with horizontal gridlines)
            Column(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .padding(top = 24.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yTicks.reversed().forEach { tick ->
                    Text(
                        text = "${tick.roundToInt()}$yAxisUnit",
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeColors.TextSecondary.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Interactive Plot Area with Floating Pill Callout Overlay
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(series) {
                        detectTapGestures { offset ->
                            val usableW = (size.width - 16.dp.toPx()).coerceAtLeast(1f)
                            val frac = ((offset.x - 8.dp.toPx()) / usableW).coerceIn(0f, 1f)
                            val hr = (frac * 23f).roundToInt().coerceIn(0, 23)
                            onInspectHour(if (inspectedHour == hr) null else hr)
                        }
                    }
                    .pointerInput(series) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val usableW = (size.width - 16.dp.toPx()).coerceAtLeast(1f)
                            val frac = ((change.position.x - 8.dp.toPx()) / usableW).coerceIn(0f, 1f)
                            val hr = (frac * 23f).roundToInt().coerceIn(0, 23)
                            onInspectHour(hr)
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

                fun xForHour(xHour: Float): Float =
                    padLeftPx + (xHour.coerceIn(0f, 23.98f) / 23.5f).coerceIn(0f, 1f) * plotW

                fun yForValue(v: Float): Float {
                    val norm = ((v - minY) / safeSpanY).coerceIn(0f, 1f)
                    return padTopPx + plotH - (norm * plotH)
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baselineY = padTopPx + plotH

                    // 1. Minimal Horizontal Hairlines
                    yTicks.forEach { tick ->
                        val y = yForValue(tick)
                        drawLine(
                            color = Color(0xFFEDF3EF),
                            start = Offset(padLeftPx, y),
                            end = Offset(padLeftPx + plotW, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Subtle ESP32 Offline Gap Bands (visually marking disconnected hours)
                    series.gaps.forEach { gap: OfflineGapWindow ->
                        val x1 = xForHour(gap.startXHour)
                        val x2 = xForHour(gap.endXHour)
                        if (x2 > x1 + 3f) {
                            drawRoundRect(
                                color = HomeColors.AlertRed.copy(alpha = 0.055f),
                                topLeft = Offset(x1, padTopPx),
                                size = Size(x2 - x1, plotH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawLine(
                                color = HomeColors.AlertRed.copy(alpha = 0.26f),
                                start = Offset(x1, padTopPx),
                                end = Offset(x1, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gapEdgeEffect
                            )
                            drawLine(
                                color = HomeColors.AlertRed.copy(alpha = 0.26f),
                                start = Offset(x2, padTopPx),
                                end = Offset(x2, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gapEdgeEffect
                            )
                        }
                    }

                    // 3. Smooth Area-Filled Discontinuous Segments (NEVER bridges across gaps)
                    val strokePx = 2.6.dp.toPx()
                    series.segments.forEach { seg ->
                        val offsets = seg.map { pt -> Offset(xForHour(pt.xHour), yForValue(pt.value)) }
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
                                        fillTopColor.copy(alpha = 0.30f),
                                        fillTopColor.copy(alpha = 0.10f),
                                        Color.Transparent
                                    ),
                                    startY = topOfSeg,
                                    endY = baselineY
                                )
                            )
                            val strokePath = buildMonotoneCubicPath(
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
                            // Single isolated hour reading: crisp point marker
                            val pt = offsets.first()
                            drawCircle(
                                color = lineColor.copy(alpha = 0.18f),
                                radius = 8.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 3.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    // 4. Inspected Hour Hairline (when user taps/drags)
                    if (inspectedHour != null) {
                        val ix = spotlightPoint?.let { xForHour(it.xHour) } ?: xForHour(inspectedHour.toFloat())
                        drawLine(
                            color = (if (inspectedIsOfflineGap) HomeColors.AlertRed else pillBgColor).copy(alpha = 0.40f),
                            start = Offset(ix, padTopPx),
                            end = Offset(ix, baselineY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = gapEdgeEffect
                        )
                    }

                    // 5. Spotlight Glowing Dot on Latest / Inspected Point (matching mockup)
                    if (spotlightPoint != null) {
                        val sx = xForHour(spotlightPoint.xHour)
                        val sy = yForValue(spotlightPoint.value)
                        drawCircle(
                            color = lineColor.copy(alpha = 0.22f),
                            radius = 9.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 3.8.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }

                // 6. Floating Dark Pill Callout Badge above Spotlight Point (clamped to chart bounds)
                if (spotlightPoint != null) {
                    val sx = xForHour(spotlightPoint.xHour)
                    val sy = yForValue(spotlightPoint.value)
                    val labelText = if (inspectedHour != null) {
                        val timeStr = if (spotlightPoint.epochMs >= 1_700_000_000_000L) {
                            IotDomainLogic.formatPhTimeLabel(spotlightPoint.epochMs)
                        } else {
                            IotDomainLogic.formatSingleHour12(spotlightPoint.hour)
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
                                elevation = 4.dp,
                                shape = RoundedCornerShape(8.dp),
                                spotColor = pillBgColor.copy(alpha = 0.25f)
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
                } else if (inspectedHour != null && hasPoints) {
                    val ix = xForHour(inspectedHour.toFloat())
                    val labelText = "${IotDomainLogic.formatSingleHour12(inspectedHour)} · Offline"
                    val estPillWidthPx = with(density) { (labelText.length * 6.2f + 22f).dp.toPx() }
                    val clampedX = (ix - estPillWidthPx / 2f)
                        .coerceIn(4f, (boxWidthPx - estPillWidthPx - 4f).coerceAtLeast(4f))
                    Surface(
                        color = HomeColors.AlertRed,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.offset { IntOffset(clampedX.roundToInt(), 2) }
                    ) {
                        Text(
                            text = labelText,
                            fontFamily = HomeInterFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                // 7. Calm Empty State when no data exists for selected day/metric
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
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = emptyMessage,
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = HomeColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Crisp 3-Hour Bottom X-Axis Labels (matching mockup: 12 AM .. 9 PM)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 46.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12 AM", "3 AM", "6 AM", "9 AM", "12 PM", "3 PM", "6 PM", "9 PM").forEach { label ->
                Text(
                    text = label,
                    fontFamily = HomeInterFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextSecondary.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
        }
    }
}
