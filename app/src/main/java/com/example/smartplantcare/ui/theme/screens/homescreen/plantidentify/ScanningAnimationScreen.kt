package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeInterFont
import kotlinx.coroutines.delay
import java.io.File

private val BgTop = Color(0xFF13221A)
private val BgBottom = Color(0xFF0A130E)
private val FramePanel = Color(0xFF1B3324)
private val AccentGreen = Color(0xFF34D399)
private val AccentGreenDim = Color(0xFF16A34A)

@Composable
fun ScanningAnimationScreen(
    imageFile: File?
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "scan_transition")

    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
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

    var messageIndex by remember { mutableIntStateOf(0) }
    val messages = remember {
        listOf(
            "Identifying plant...",
            "Analyzing leaf structure...",
            "Matching species database...",
            "Almost done..."
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }

    val imageRequest = remember(imageFile) {
        imageFile?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(640)
                .crossfade(false)
                .build()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(BgTop, BgBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(290.dp)
                        .graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                        }
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
                    if (imageRequest != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Plant to Analyze",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.15f))
                        )
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cornerRadiusPx = 24.dp.toPx()
                        val bracketLen = size.width * 0.14f
                        val strokeW = 3.dp.toPx()

                        drawRoundRect(
                            color = AccentGreen.copy(alpha = 0.2f),
                            cornerRadius = CornerRadius(cornerRadiusPx),
                            style = Stroke(width = strokeW)
                        )

                        val c = AccentGreen.copy(alpha = pulseAlpha)
                        val w = size.width
                        val h = size.height
                        val inset = strokeW / 2

                        drawLine(c, Offset(inset, inset + bracketLen), Offset(inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, inset), Offset(inset + bracketLen, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset - bracketLen, inset), Offset(w - inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, inset), Offset(w - inset, inset + bracketLen), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, h - inset - bracketLen), Offset(inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, h - inset), Offset(inset + bracketLen, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset - bracketLen, h - inset), Offset(w - inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, h - inset - bracketLen), Offset(w - inset, h - inset), strokeWidth = strokeW)

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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = messages[messageIndex],
                    fontFamily = HomeInterFont,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Please wait while we identify your plant",
                    fontFamily = HomeInterFont,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.indices.forEach { index ->
                    val active = index == messageIndex
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) AccentGreen else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}
