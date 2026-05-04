package com.example.smartplantcare.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.ui.theme.screens.SystemUiController
import kotlinx.coroutines.delay


private val PureWhite    = Color(0xFFFFFFFF)
private val DeepGreen    = Color(0xFF0F380F)
private val VibrantGreen = Color(0xFF00A859) // Bright green for leaves
private val LeafGreen    = Color(0xFF2E7D32) // Mid green for stems/accents
private val SoftMint     = Color(0xFFE8F5E9) // Subtle glow


private enum class SplashPhase {
    IDLE,
    S_DROP,
    REVEAL_TEXT,
    REVEAL_LOGO,
    HOLD,
    FADE_OUT
}

@SuppressLint("ContextCastToActivity")
@Composable
fun SmartPlantCareSplashScreen(
    onSplashFinished: () -> Unit = {}
) {

    val activity = LocalContext.current as Activity

    SideEffect {
        SystemUiController.hideSystemBars(activity)
    }
    var phase by remember { mutableStateOf(SplashPhase.IDLE) }


    val textVisible = phase.ordinal >= SplashPhase.REVEAL_TEXT.ordinal
    val logoVisible = phase.ordinal >= SplashPhase.REVEAL_LOGO.ordinal

    // ─── 'S' Zoom Animation ───
    val sAlpha by animateFloatAsState(
        targetValue = if (phase.ordinal >= SplashPhase.S_DROP.ordinal) 1f else 0f,
        animationSpec = tween(500, easing = LinearEasing),
        label = "s_alpha"
    )


    val sScale by animateFloatAsState(
        targetValue = if (phase.ordinal >= SplashPhase.S_DROP.ordinal) 1f else 10f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "s_scale"
    )

    // Full screen fade out
    val screenAlpha by animateFloatAsState(
        targetValue = if (phase == SplashPhase.FADE_OUT) 0f else 1f,
        animationSpec = tween(800),
        label = "screen_alpha"
    )


    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )


    LaunchedEffect(Unit) {
        delay(300)
        phase = SplashPhase.S_DROP
        delay(800)
        phase = SplashPhase.REVEAL_TEXT
        delay(700)
        phase = SplashPhase.REVEAL_LOGO
        delay(2500)
        phase = SplashPhase.HOLD
        delay(100)
        phase = SplashPhase.FADE_OUT
        delay(300)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .alpha(screenAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {

            // ─── Phase 3: Potted Plant Logo Appears ────────────────────────────
            AnimatedVisibility(
                visible = logoVisible,
                enter = expandVertically(
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(tween(800)) + scaleIn(initialScale = 0.5f, animationSpec = spring(0.6f, 200f))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PottedPlantLogo(
                        glowIntensity = glowPulse,
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ─── Phases 1 & 2: S Drop & Text Slide ────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // The Giant 'S' that shrinks perfectly to fit
                Text(
                    text = "S",
                    fontSize = 26.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = DeepGreen,
                    modifier = Modifier.graphicsLayer {
                        scaleX = sScale
                        scaleY = sScale
                        alpha = sAlpha
                    }
                )

                // The sliding "MART PLANT CARE"
                AnimatedVisibility(
                    visible = textVisible,
                    enter = expandHorizontally(
                        animationSpec = tween(800, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Start
                    ) + fadeIn(animationSpec = tween(800))
                ) {
                    Text(
                        text = "MART PLANT CARE",
                        fontSize = 26.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = DeepGreen,
                        maxLines = 1
                    )
                }
            }

            // ─── Phase 3: Slogan Appears at the Bottom ────────────────────────
            AnimatedVisibility(
                visible = logoVisible,
                enter = expandVertically(
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(800))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "GROW SMARTER",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = LeafGreen
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sleek Potted Plant Logo
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PottedPlantLogo(glowIntensity: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Subtle ambient glow behind the plant
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SoftMint.copy(alpha = 0.8f * glowIntensity),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = r * 1.3f
            ),
            radius = r * 1.3f,
            center = Offset(cx, cy)
        )

        // 1. Draw the Pot
        val potTopY = cy + r * 0.3f
        val potBottomY = cy + r * 0.9f
        val potWidthTop = r * 0.7f
        val potWidthBottom = r * 0.45f

        val potPath = Path().apply {
            moveTo(cx - potWidthTop, potTopY)
            lineTo(cx + potWidthTop, potTopY)
            lineTo(cx + potWidthBottom, potBottomY)
            lineTo(cx - potWidthBottom, potBottomY)
            close()
        }

        // Fill Pot
        drawPath(
            path = potPath,
            brush = Brush.verticalGradient(
                colors = listOf(SoftMint, Color.LightGray.copy(alpha = 0.3f)),
                startY = potTopY,
                endY = potBottomY
            )
        )
        // Outline Pot
        drawPath(
            path = potPath,
            color = DeepGreen,
            style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
        )
        // Pot Rim
        drawRoundRect(
            color = DeepGreen,
            topLeft = Offset(cx - potWidthTop - r * 0.1f, potTopY - r * 0.1f),
            size = Size(potWidthTop * 2 + r * 0.2f, r * 0.15f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // 2. Draw the Plant Stems and Leaves
        // Center Stem & Leaf
        val centerLeaf = Path().apply {
            moveTo(cx, potTopY)
            quadraticBezierTo(cx - r * 0.3f, cy - r * 0.4f, cx, cy - r * 0.9f) // Left curve up
            quadraticBezierTo(cx + r * 0.3f, cy - r * 0.4f, cx, potTopY)       // Right curve down
        }

        // Left Stem & Leaf
        val leftLeaf = Path().apply {
            moveTo(cx, potTopY)
            quadraticBezierTo(cx - r * 0.6f, cy - r * 0.1f, cx - r * 0.8f, cy - r * 0.5f)
            quadraticBezierTo(cx - r * 0.2f, cy - r * 0.6f, cx, potTopY)
        }

        // Right Stem & Leaf
        val rightLeaf = Path().apply {
            moveTo(cx, potTopY)
            quadraticBezierTo(cx + r * 0.6f, cy - r * 0.1f, cx + r * 0.8f, cy - r * 0.5f)
            quadraticBezierTo(cx + r * 0.2f, cy - r * 0.6f, cx, potTopY)
        }

        val leafGradient = Brush.verticalGradient(
            colors = listOf(VibrantGreen, LeafGreen),
            startY = cy - r,
            endY = potTopY
        )

        // Draw Leaves
        listOf(leftLeaf, rightLeaf, centerLeaf).forEach { leaf ->
            drawPath(path = leaf, brush = leafGradient)
            drawPath(
                path = leaf,
                color = DeepGreen,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}