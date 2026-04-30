package com.example.smartplantcare.ui.theme.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.R
import com.example.smartplantcare.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

// ─── Data model ────────────────────────────────────────────────────────────────

data class OnboardingPageData(
    val imageRes: Int,
    val title: String,
    val description: String,
    val emoji: String,
    val chips: List<String> = emptyList(),
    val cardTint: Color = Color(0xFFE8F5E9),
    val accentColor: Color = Color(0xFF063321)
)

// ─── Feature chip ───────────────────────────────────────────────────────────────

@Composable
private fun FeatureChip(label: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.09f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {

    val pages = listOf(
        OnboardingPageData(
            imageRes = R.drawable.splash1,
            title = "SmartPlant",
            description = "Connect with nature through living tech. Monitor, scan, and nurture your indoor garden with intelligence.",
            emoji = "🪴",
            chips = listOf("🌿 Indoor Plants", "📱 Smart App"),
            cardTint = Color(0xFFEDF7ED),
            accentColor = DarkGreen
        ),
        OnboardingPageData(
            imageRes = R.drawable.splash2,
            title = "Smart Monitoring",
            description = "Track water levels, temperature, and sunlight in real-time. Your plants will never go thirsty again.",
            emoji = "📡",
            chips = listOf("💧 Water", "🌡️ Temp", "☀️ Sunlight"),
            cardTint = Color(0xFFE8F4FD),
            accentColor = PrimaryGreen
        ),
        OnboardingPageData(
            imageRes = R.drawable.splash3,
            title = "Grow Smarter",
            description = "Automated watering schedules and AI health alerts ensure your indoor garden thrives effortlessly.",
            emoji = "🌱",
            chips = listOf("⏰ Auto Schedule", "🤖 AI Alerts"),
            cardTint = Color(0xFFEDF7ED),
            accentColor = DarkGreen
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    val infiniteTransition = rememberInfiniteTransition(label = "inf")

    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6FAF6))
    ) {
        // Decorative background blobs
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .clip(CircleShape)
                .background(Color(0xFF2E7D32).copy(alpha = 0.05f))
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 100.dp)
                .clip(CircleShape)
                .background(Color(0xFF66BB6A).copy(alpha = 0.07f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val data = pages[pageIndex]
                val pageOffset =
                    (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(36.dp))

                    // Image card — emoji badge centered at bottom edge
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val s = 1f - (absOffset * 0.07f)
                                scaleX = s
                                scaleY = s
                                alpha = 1f - (absOffset * 0.25f)
                                rotationY = pageOffset * 3f
                                translationX = pageOffset * -10f
                            }
                    ) {
                        // Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = data.cardTint),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Top-left radial glow
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.65f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(100f, 60f),
                                                radius = 260f
                                            )
                                        )
                                )

                                // Bottom scrim so emoji badge has a clean base
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    data.cardTint.copy(alpha = 0.55f)
                                                ),
                                                startY = 120f
                                            )
                                        )
                                )

                                Image(
                                    painter = painterResource(id = data.imageRes),
                                    contentDescription = data.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.88f)   // Leaves room at bottom for badge
                                        .padding(horizontal = 28.dp)
                                        .padding(top = 20.dp)
                                )
                            }
                        }

                        // Emoji badge — centered, sitting on the bottom edge of the card
                        Box(
                            modifier = Modifier
                                .offset(y = 26.dp)              // Half outside the card
                                .graphicsLayer {
                                    translationY = floatY * -0.5f
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = 1f - (absOffset * 0.45f)
                                }
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Subtle inner shadow ring
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(data.accentColor.copy(alpha = 0.06f))
                            )
                            Text(
                                text = data.emoji,
                                fontSize = 26.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Extra space to clear the overlapping badge
                    Spacer(modifier = Modifier.height(42.dp))

                    // Text + chips block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = absOffset * 36f
                                alpha = 1f - (absOffset * 0.75f)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        AnimatedContent(
                            targetState = data.title,
                            transitionSpec = {
                                (slideInVertically { it / 3 } + fadeIn(tween(380))) togetherWith
                                        (slideOutVertically { -it / 3 } + fadeOut(tween(260)))
                            },
                            label = "titleAnim"
                        ) { title ->
                            Text(
                                text = title,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkGreen,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-0.5).sp,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = data.accentColor.copy(alpha = 0.12f),
                                        offset = Offset(0f, 4f),
                                        blurRadius = 10f
                                    )
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        AnimatedContent(
                            targetState = data.description,
                            transitionSpec = {
                                (fadeIn(tween(480, delayMillis = 80)) +
                                        slideInVertically { it / 5 }) togetherWith
                                        (fadeOut(tween(200)) + slideOutVertically { -it / 5 })
                            },
                            label = "descAnim"
                        ) { desc ->
                            Text(
                                text = desc,
                                fontSize = 14.sp,
                                color = TextMedium,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Feature chips
                        AnimatedContent(
                            targetState = data.chips,
                            transitionSpec = {
                                fadeIn(tween(400, delayMillis = 100)) togetherWith
                                        fadeOut(tween(180))
                            },
                            label = "chipsAnim"
                        ) { chips ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp,
                                    Alignment.CenterHorizontally
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                chips.forEach { chip ->
                                    FeatureChip(label = chip, accentColor = data.accentColor)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Page dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(pages.size) { index ->
                        val isActive = index == pagerState.currentPage

                        val dotWidth by animateDpAsState(
                            targetValue = if (isActive) 28.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
                            label = "dotW"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isActive) DarkGreen else DotInactive,
                            animationSpec = tween(350),
                            label = "dotC"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CTA button
                Button(
                    onClick = {
                        scope.launch {
                            if (isLastPage) {
                                onGetStarted()
                            } else {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = isLastPage,
                            transitionSpec = {
                                (slideInHorizontally { it / 4 } + fadeIn(tween(280))) togetherWith
                                        (slideOutHorizontally { -it / 4 } + fadeOut(tween(240)))
                            },
                            label = "btnTextAnim"
                        ) { lastPage ->
                            Text(
                                text = if (lastPage) "GET STARTED" else "CONTINUE",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "→",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.graphicsLayer {
                                translationX = floatY * 0.5f
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Skip — hidden on last page
                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    TextButton(
                        onClick = onGetStarted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Skip to login",
                            color = TextMedium.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}