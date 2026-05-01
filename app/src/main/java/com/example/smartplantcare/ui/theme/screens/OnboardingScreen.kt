package com.example.smartplantcare.ui.theme.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.R
import kotlinx.coroutines.launch
import kotlin.math.abs

// ─── DATA ────────────────────────────────────────────────────────────────
data class OnboardingPageData(
    val imageRes: Int,
    val title: String,
    val description: String,
    val emoji: String,
    val chips: List<String> = emptyList(),
    val cardTint: Color = Color(0xFFE8F5E9),
    val accentColor: Color = Color(0xFF063321)
)

// ─── COLORS ───────────────────────────────────────────────────────────────
private val DarkGreen = Color(0xFF1D4B34)
private val PrimaryGreen = Color(0xFF2E7D32)
private val TextMedium = Color(0xFF6B7A6F)
private val DotInactive = Color(0xFFD0DAD3)

// ─── CHIPS ────────────────────────────────────────────────────────────────
@Composable
private fun FeatureChip(label: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── ONBOARDING SCREEN ────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {

    val pages = listOf(
        OnboardingPageData(
            imageRes = R.drawable.splash1, // Ensure these exist in your res/drawable
            title = "SmartPlant",
            description = "Connect with nature through intelligent plant monitoring.",
            emoji = "🪴",
            chips = listOf("Indoor Plants", "Smart Care"),
            cardTint = Color(0xFFEAF7EA),
            accentColor = DarkGreen
        ),
        OnboardingPageData(
            imageRes = R.drawable.splash2,
            title = "Monitor Plants",
            description = "Track water, sunlight, and temperature in real-time.",
            emoji = "📡",
            chips = listOf("Water", "Light", "Temp"),
            cardTint = Color(0xFFE8F4FD),
            accentColor = PrimaryGreen
        ),
        OnboardingPageData(
            imageRes = R.drawable.splash3,
            title = "Grow Smarter",
            description = "AI-powered plant care suggestions for healthier growth.",
            emoji = "🌱",
            chips = listOf("AI Care", "Auto Alerts"),
            cardTint = Color(0xFFEFF8EF),
            accentColor = DarkGreen
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    // Infinite animations for continuous UI movement
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")

    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsing"
    )

    // Slow rotation for background blobs to make them feel organic
    val blobRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blobRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7FBF7),
                        Color(0xFFEAF5EA),
                        Color(0xFFDFF0E0)
                    )
                )
            )
    ) {
        // --- Animated Background Blobs ---
        Box(
            Modifier
                .size(350.dp)
                .offset((-100).dp, (-100).dp)
                .graphicsLayer { rotationZ = blobRotation }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2E7D32).copy(alpha = 0.08f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(100.dp, 100.dp)
                .graphicsLayer { rotationZ = -blobRotation }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF66BB6A).copy(alpha = 0.1f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->

                val data = pages[page]

                // Calculate offset for parallax effects
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)

                // Parallax Math
                val imageScale = 1f - (absOffset * 0.2f) // Images shrink slightly as they swipe away
                val textTranslationX = pageOffset * 200f // Text slides faster than the page
                val alphaFade = 1f - absOffset // Fade out elements smoothly

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { alpha = alphaFade },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Spacer(Modifier.height(60.dp))

                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier.graphicsLayer {
                            scaleX = imageScale
                            scaleY = imageScale
                        }
                    ) {
                        // Glassmorphism Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            shape = RoundedCornerShape(32.dp),
                            colors = CardDefaults.cardColors(Color.White.copy(0.7f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                data.cardTint.copy(alpha = 0.6f),
                                                Color.White.copy(alpha = 0.3f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(data.imageRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp)
                                        // Slight internal parallax for the image itself
                                        .graphicsLayer { translationX = pageOffset * 50f }
                                )
                            }
                        }

                        // Floating Emoji Badge
                        Box(
                            modifier = Modifier
                                .offset(y = 28.dp)
                                .size(64.dp)
                                .graphicsLayer {
                                    translationY = floatY * -1f
                                    scaleX = pulse
                                    scaleY = pulse
                                    // Make emoji slightly rotate during swipe
                                    rotationZ = pageOffset * 45f
                                }
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, data.cardTint, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(data.emoji, fontSize = 28.sp)
                        }
                    }

                    Spacer(Modifier.height(50.dp))

                    // Animated Text
                    Text(
                        text = data.title,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DarkGreen,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { translationX = textTranslationX }
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = data.description,
                        fontSize = 15.sp,
                        color = TextMedium,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        modifier = Modifier.graphicsLayer { translationX = textTranslationX * 1.2f } // Description slides slightly faster
                    )

                    Spacer(Modifier.height(24.dp))

                    // Animated Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.graphicsLayer { translationX = textTranslationX * 1.4f }
                    ) {
                        data.chips.forEach {
                            FeatureChip(it, data.accentColor)
                        }
                    }
                }
            }

            // --- DOT INDICATORS ---
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val isActive = index == pagerState.currentPage

                    // Spring animation for smooth dot resizing
                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 10.dp,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
                        label = "dotWidth"
                    )

                    val color by animateColorAsState(
                        targetValue = if (isActive) DarkGreen else DotInactive,
                        label = "dotColor"
                    )

                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(10.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // --- ANIMATED BUTTON ---
            Button(
                onClick = {
                    scope.launch {
                        if (isLastPage) onGetStarted()
                        else pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(DarkGreen),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                )
            ) {
                // Smooth transition between "CONTINUE" and "GET STARTED"
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = {
                        (slideInVertically { height -> height } + fadeIn()).togetherWith(
                            slideOutVertically { height -> -height } + fadeOut())
                    },
                    label = "buttonText"
                ) { isLast ->
                    Text(
                        text = if (isLast) "GET STARTED" else "CONTINUE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}