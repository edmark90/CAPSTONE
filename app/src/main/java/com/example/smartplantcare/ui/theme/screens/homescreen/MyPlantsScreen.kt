package com.example.smartplantcare.ui.theme.screens.homescreen

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min

// NOTE: Using local color constants here since this screen defines its own palette.
// If you already have DarkGreen defined in com.example.smartplantcare.ui.theme,
// either delete the line below and import it instead, OR keep this local one
// and do NOT import theme.DarkGreen — pick one, not both (that was the redeclaration bug).
private val DarkGreen = Color(0xFF1B4D3E)
private val DarkGreenLight = Color(0xFF23604E)
private val RingOuter = Color(0xFFEAF3EE)
private val RingMid = Color(0xFFD3E7DC)
private val RingInner = Color(0xFFB7D9C8)
private val TitleColor = Color(0xFF14261F)
private val SubtitleColor = Color(0xFF8A9791)
private val CardBorder = Color(0xFFE3E8E5)

@Composable
fun MyPlantsScreen(
    onBackClick: () -> Unit = {},
    onStartScanning: () -> Unit = {},
    isScanning: Boolean = true,
    isAuthenticated: Boolean = true,
    isGuest: Boolean = false,
    onNavigateToLogin: () -> Unit = {}
) {
    var showAuthDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect\nSmartPlantCare Device",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TitleColor,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Search nearby SmartPlantCare devices to begin setup.",
            fontSize = 14.sp,
            color = SubtitleColor,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        RadarScanArea(
            isScanning = isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                if (isAuthenticated && !isGuest) {
                    onStartScanning()
                } else {
                    showAuthDialog = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
        ) {
            Text(
                text = "Start Scanning",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Auth Dialog
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
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Static concentric rings (outer -> inner)
            drawCircle(color = RingOuter, radius = maxRadius, center = center)
            drawCircle(color = RingMid, radius = maxRadius * 0.72f, center = center)
            drawCircle(color = RingInner, radius = maxRadius * 0.48f, center = center)

            // Faint crosshair / compass lines
            val lineColor = Color.White.copy(alpha = 0.6f)
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

            // Animated expanding scan rings
            if (isScanning) {
                for (phaseOffset in listOf(0f, 0.5f)) {
                    val phase = (pulse + phaseOffset) % 1f
                    val radius = maxRadius * 0.3f + phase * maxRadius * 0.7f
                    val alpha = (1f - phase).coerceIn(0f, 1f) * 0.5f
                    drawCircle(
                        color = DarkGreen.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Device dots
            val dotColor = DarkGreen
            drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(center.x - maxRadius * 0.62f, center.y - maxRadius * 0.42f))
            drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(center.x + maxRadius * 0.72f, center.y - maxRadius * 0.05f))
            drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(center.x - maxRadius * 0.5f, center.y + maxRadius * 0.58f))
            drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(center.x - maxRadius * 0.08f, center.y + maxRadius * 0.72f))
        }

        // Center dark green circle with leaf icon
        Box(
            modifier = Modifier
                .fillMaxSize(0.34f)
                .clip(CircleShape)
                .background(DarkGreen),
            contentAlignment = Alignment.Center
        ) {
            LeafIcon(modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}

/**
 * Trifoliate leaf icon — 3 pointed, slightly asymmetric lobes meeting at a shared
 * center point, with a wavy stem underneath. Modeled after a classic clover/trefoil
 * leaf silhouette (tips pointed, not rounded like a shamrock).
 */
@Composable
private fun LeafIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.48f)
        // "u" is the base unit — coordinates below were traced from the
        // reference artwork at a 230-unit scale, then scaled by u to fit the canvas.
        val u = (min(w, h) * 0.5f) / 230f
        val leafColor = Color.White.copy(alpha = 0.95f)
        val veinColor = DarkGreenLight

        // Single leaf — pointed tip at top, rounded/tapered base at bottom
        val leaf = Path().apply {
            moveTo(0f, -230f * u)
            cubicTo(110f * u, -190f * u, 165f * u, -90f * u, 150f * u, 10f * u)
            cubicTo(138f * u, 95f * u, 70f * u, 150f * u, 0f, 160f * u)
            cubicTo(-70f * u, 150f * u, -138f * u, 95f * u, -150f * u, 10f * u)
            cubicTo(-165f * u, -90f * u, -110f * u, -190f * u, 0f, -230f * u)
            close()
        }

        // Central vein
        val mainVein = Path().apply {
            moveTo(0f, -210f * u)
            cubicTo(8f * u, -120f * u, 8f * u, -20f * u, 0f, 140f * u)
        }
        // Side veins branching off the main vein
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

        // Short curved stem below the leaf
        val stem = Path().apply {
            moveTo(0f, 155f * u)
            cubicTo(20f * u, 200f * u, -15f * u, 225f * u, 8f * u, 270f * u)
        }

        val veinStrokeThin = 3f * u
        val veinStrokeMain = 5f * u
        val stemStroke = 7f * u

        withTransform({ translate(center.x, center.y) }) {
            drawPath(leaf, color = leafColor)

            drawPath(mainVein, color = veinColor, style = Stroke(width = veinStrokeMain, cap = StrokeCap.Round))
            drawPath(veinLeftTop, color = veinColor, style = Stroke(width = veinStrokeThin, cap = StrokeCap.Round))
            drawPath(veinRightTop, color = veinColor, style = Stroke(width = veinStrokeThin, cap = StrokeCap.Round))
            drawPath(veinLeftBottom, color = veinColor, style = Stroke(width = veinStrokeThin, cap = StrokeCap.Round))
            drawPath(veinRightBottom, color = veinColor, style = Stroke(width = veinStrokeThin, cap = StrokeCap.Round))

            drawPath(stem, color = leafColor, style = Stroke(width = stemStroke, cap = StrokeCap.Round))
        }
    }
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
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = DarkGreen,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Sign In Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Device scanning requires a registered account.\n\nPlease sign in or create an account to continue.",
                    fontSize = 14.sp,
                    color = SubtitleColor,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, DarkGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkGreen)
                ) {
                    Text("Create Account", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SubtitleColor)
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640, device = "id:pixel_9"
)
@Composable
fun PreviewMyPlantsScreen() {
    MaterialTheme {
        MyPlantsScreen()
    }
}