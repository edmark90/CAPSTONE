package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.min

private val RingOuter = Color(0xFFEAF3EE)
private val RingMid = Color(0xFFD5E9DE)
private val RingInner = Color(0xFFBBDCCB)

@Composable
fun MyPlantsScreen(
    onBackClick: () -> Unit = {},
    onStartScanning: () -> Unit = {},
    isScanning: Boolean = false,
    isAuthenticated: Boolean = true,
    isGuest: Boolean = false,
    onNavigateToLogin: () -> Unit = {}
) {
    var showAuthDialog by remember { mutableStateOf(false) }
    var activeScan by remember(isScanning) { mutableStateOf(isScanning) }
    var scanTimedOutHint by remember { mutableStateOf(false) }

    LaunchedEffect(activeScan) {
        if (activeScan) {
            scanTimedOutHint = false
            delay(6500L)
            scanTimedOutHint = true
        } else {
            scanTimedOutHint = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = HomeDimens.BottomBarClearance),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = HomeColors.SoftMint,
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = HomeColors.ForestPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "IoT Device Setup",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeColors.ForestPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connect SmartPlantCare\nSensor Device",
            fontFamily = HomeInterFont,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (activeScan) {
                "Searching nearby ESP32 SmartPlantCare sensors..."
            } else {
                "Search nearby SmartPlantCare devices to begin wireless plant monitoring setup."
            },
            fontFamily = HomeInterFont,
            fontSize = 13.sp,
            color = HomeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        RadarScanArea(
            isScanning = activeScan,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(0.78f)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(visible = scanTimedOutHint && activeScan) {
            Surface(
                color = HomeColors.SurfaceWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HomeColors.CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        tint = HomeColors.ForestMedium,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "No device detected yet. Make sure your ESP32 sensor is powered on and within Bluetooth/Wi-Fi range.",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        if (activeScan) {
            OutlinedButton(
                onClick = { activeScan = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, HomeColors.ForestPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeColors.ForestPrimary)
            ) {
                Text(
                    text = "Stop Scanning",
                    fontFamily = HomeInterFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = {
                    if (isAuthenticated && !isGuest) {
                        activeScan = true
                        onStartScanning()
                    } else {
                        showAuthDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomeColors.ForestPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Start Scanning",
                    fontFamily = HomeInterFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showAuthDialog) {
        AuthRequiredDialog(
            onCreateAccount = {
                showAuthDialog = false
                onNavigateToLogin()
            },
            onCancel = {
                showAuthDialog = false
            }
        )
    }
}

@Composable
private fun RadarScanArea(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val pulse: Float = if (isScanning) {
        val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse"
        )
        anim
    } else {
        0f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = RingOuter, radius = maxRadius, center = center)
            drawCircle(color = RingMid, radius = maxRadius * 0.72f, center = center)
            drawCircle(color = RingInner, radius = maxRadius * 0.48f, center = center)

            val lineColor = Color.White.copy(alpha = 0.65f)
            drawLine(
                lineColor,
                Offset(center.x - maxRadius, center.y),
                Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                lineColor,
                Offset(center.x, center.y - maxRadius),
                Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            if (isScanning) {
                for (i in 0..1) {
                    val phase = (pulse + i * 0.5f) % 1f
                    val radius = maxRadius * 0.3f + phase * maxRadius * 0.7f
                    val alpha = (1f - phase).coerceIn(0f, 1f) * 0.5f
                    drawCircle(
                        color = HomeColors.ForestPrimary.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            val dotColor = HomeColors.ForestPrimary.copy(alpha = if (isScanning) 1f else 0.45f)
            val dotRadius = 4.dp.toPx()
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x - maxRadius * 0.62f, center.y - maxRadius * 0.42f))
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x + maxRadius * 0.72f, center.y - maxRadius * 0.05f))
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x - maxRadius * 0.5f, center.y + maxRadius * 0.58f))
            drawCircle(dotColor, radius = dotRadius, center = Offset(center.x - maxRadius * 0.08f, center.y + maxRadius * 0.72f))
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.34f)
                .clip(CircleShape)
                .background(HomeColors.ForestPrimary),
            contentAlignment = Alignment.Center
        ) {
            LeafIcon(modifier = Modifier.fillMaxSize(0.52f))
        }
    }
}

@Composable
private fun LeafIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val center = Offset(w * 0.5f, h * 0.48f)
            val u = (min(w, h) * 0.5f) / 230f
            val leafColor = Color.White.copy(alpha = 0.95f)
            val veinColor = HomeColors.ForestMedium

            val leaf = Path().apply {
                moveTo(0f, -230f * u)
                cubicTo(110f * u, -190f * u, 165f * u, -90f * u, 150f * u, 10f * u)
                cubicTo(138f * u, 95f * u, 70f * u, 150f * u, 0f, 160f * u)
                cubicTo(-70f * u, 150f * u, -138f * u, 95f * u, -150f * u, 10f * u)
                cubicTo(-165f * u, -90f * u, -110f * u, -190f * u, 0f, -230f * u)
                close()
            }
            val mainVein = Path().apply {
                moveTo(0f, -210f * u)
                cubicTo(8f * u, -120f * u, 8f * u, -20f * u, 0f, 140f * u)
            }
            val veinLeftTop = Path().apply {
                moveTo(0f, -100f * u)
                cubicTo(-35f * u, -80f * u, -55f * u, -60f * u, -70f * u, -30f * u)
            }
            val veinRightTop = Path().apply {
                moveTo(0f, -100f * u)
                cubicTo(35f * u, -80f * u, 55f * u, -60f * u, 70f * u, -30f * u)
            }
            val veinLeftBottom = Path().apply {
                moveTo(0f, 0f)
                cubicTo(-25f * u, 15f * u, -35f * u, 35f * u, -50f * u, 55f * u)
            }
            val veinRightBottom = Path().apply {
                moveTo(0f, 0f)
                cubicTo(25f * u, 15f * u, 35f * u, 35f * u, 50f * u, 55f * u)
            }
            val stem = Path().apply {
                moveTo(0f, 155f * u)
                cubicTo(20f * u, 200f * u, -15f * u, 225f * u, 8f * u, 270f * u)
            }

            val veinStrokeThin = Stroke(width = 3f * u, cap = StrokeCap.Round)
            val veinStrokeMain = Stroke(width = 5f * u, cap = StrokeCap.Round)
            val stemStroke = Stroke(width = 7f * u, cap = StrokeCap.Round)

            onDrawBehind {
                withTransform({ translate(center.x, center.y) }) {
                    drawPath(leaf, color = leafColor)
                    drawPath(mainVein, color = veinColor, style = veinStrokeMain)
                    drawPath(veinLeftTop, color = veinColor, style = veinStrokeThin)
                    drawPath(veinRightTop, color = veinColor, style = veinStrokeThin)
                    drawPath(veinLeftBottom, color = veinColor, style = veinStrokeThin)
                    drawPath(veinRightBottom, color = veinColor, style = veinStrokeThin)
                    drawPath(stem, color = leafColor, style = stemStroke)
                }
            }
        }
    )
}

@Composable
private fun AuthRequiredDialog(
    onCreateAccount: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = HomeColors.SurfaceWhite,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(HomeColors.SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = HomeColors.ForestPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sign In Required",
                    fontFamily = HomeInterFont,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Device scanning requires a registered account. Please sign in or create an account to continue.",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Sign In / Create Account",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
fun PreviewMyPlantsScreen() {
    MyPlantsScreen()
}
