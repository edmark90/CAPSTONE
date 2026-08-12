package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import java.io.File
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay

// Palette: layered dark greens instead of one flat color
private val BgTop = Color(0xFF13221A)
private val BgBottom = Color(0xFF0A130E)
private val FramePanel = Color(0xFF1B3324)
private val AccentGreen = Color(0xFF34D399)
private val AccentGreenDim = Color(0xFF16A34A)

@Composable
fun ScanningAnimationScreen(
    imageFile: File?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_transition")

    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    var messageIndex by remember { mutableStateOf(0) }
    val messages = listOf(
        "Identifying plant...",
        "Analyzing leaf structure...",
        "Matching species database...",
        "Almost done..."
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgTop, BgBottom)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {

            // ---- Ambient glow behind the frame ----
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(300.dp)
                        .scale(glowScale)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AccentGreenDim.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }

               val configuration = LocalConfiguration.current
                val frameSize = if (configuration.screenHeightDp < 640) 180.dp else 240.dp
                Box(
                    modifier = Modifier
                        .size(frameSize)
                        .clip(RoundedCornerShape(24.dp))
                        .background(FramePanel),
                    contentAlignment = Alignment.Center
                ) {
                    // Kung may valid file, i-render ito gamit ang proper aspect cropping layout parameters
                    if (imageFile != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageFile),
                            contentDescription = "Plant to Analyze",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Subtle dark overlay so the scan line and corners stay readable over any photo
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.15f))
                        )
                    }

                    // Laser scan line dynamic animation overlay painter canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cornerRadiusPx = 24.dp.toPx()
                        val bracketLen = size.width * 0.14f
                        val strokeW = 3.dp.toPx()

                        // Soft outer border, dim and constant
                        drawRoundRect(
                            color = AccentGreen.copy(alpha = 0.18f),
                            cornerRadius = CornerRadius(cornerRadiusPx),
                            style = Stroke(width = strokeW)
                        )

                        // Four corner brackets, viewfinder style, pulsing
                        val c = AccentGreen.copy(alpha = pulseAlpha)
                        val w = size.width
                        val h = size.height
                        val inset = strokeW / 2

                        // top-left
                        drawLine(c, Offset(inset, inset + bracketLen), Offset(inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, inset), Offset(inset + bracketLen, inset), strokeWidth = strokeW)
                        // top-right
                        drawLine(c, Offset(w - inset - bracketLen, inset), Offset(w - inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, inset), Offset(w - inset, inset + bracketLen), strokeWidth = strokeW)
                        // bottom-left
                        drawLine(c, Offset(inset, h - inset - bracketLen), Offset(inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, h - inset), Offset(inset + bracketLen, h - inset), strokeWidth = strokeW)
                        // bottom-right
                        drawLine(c, Offset(w - inset - bracketLen, h - inset), Offset(w - inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, h - inset - bracketLen), Offset(w - inset, h - inset), strokeWidth = strokeW)

                        // Moving sweep line segment vector
                        val lineY = size.height * scanLinePosition
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    AccentGreen.copy(alpha = 0f),
                                    AccentGreen,
                                    AccentGreen.copy(alpha = 0f)
                                )
                            ),
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                }
            }

            Text(
                text = messages[messageIndex],
                color = Color.White.copy(alpha = pulseAlpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Please wait while we identify your plant",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                style = MaterialTheme.typography.bodySmall
            )

            // Progress dots, one lit per message step
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.indices.forEach { index ->
                    val active = index == messageIndex
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) AccentGreen else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }
        }
    }
}