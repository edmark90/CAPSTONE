package com.example.smartplantcare.ui.theme.screens

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.R
import kotlinx.coroutines.launch
import kotlin.math.abs


private val DarkGreen = Color(0xFF1A3C2E)
private val MintCircle = Color(0xFFE8F5E9)
private val TitleColor = Color(0xFF1A1A1A)
private val SubtitleColor = Color(0xFF8A9490)
private val DotActive = Color(0xFF1A3C2E)
private val DotInactive = Color(0xFFD8E4DC)
private val White = Color.White

data class OnboardingPage(
    val imageRes: Int,
    val title: String,
    val description: String
)

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {

    val activity = LocalContext.current as Activity

    SideEffect {
        SystemUiController.hideSystemBars(activity)
    }

    val pages = listOf(
        OnboardingPage(
            R.drawable.s1,
            "Detect Plant Disease",
            "Use your camera to scan your plant and identify possible diseases instantly."
        ),
        OnboardingPage(
            R.drawable.s2,
            "Monitor Plant Health",
            "Track water, light, and temperature levels to keep your plant healthy."
        ),
        OnboardingPage(
            R.drawable.s3,
            "Smart Plant Care",
            "Get care tips and recommendations to treat diseases and improve plant growth."
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex


    val infiniteTransition = rememberInfiniteTransition(label = "float_rotate")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )


    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animatedRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF4FBF6),
                        Color.White
                    )
                )
            )
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // PAGER
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->

                val data = pages[page]
                val pageOffset =
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp)
                        .graphicsLayer {

                            alpha = 1f - (absOffset * 0.35f)
                            scaleX = 1f - (absOffset * 0.05f)
                            scaleY = 1f - (absOffset * 0.05f)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Spacer(Modifier.height(40.dp))


                    Card(
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.size(290.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {

                            // BACKGROUND CIRCLE (Parallax Effect - moves faster)
                            Box(
                                modifier = Modifier
                                    .size(230.dp)
                                    .graphicsLayer {
                                        translationX = pageOffset * 200f
                                    }
                                    .clip(CircleShape)
                                    .background(MintCircle)
                            )

                            // PLANT IMAGE (Float + Parallax Effect - moves slower)
                            Image(
                                painter = painterResource(data.imageRes),
                                contentDescription = data.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(250.dp)
                                    .graphicsLayer {
                                        translationX = pageOffset * -50f
                                        translationY = floatY
                                        rotationZ = animatedRotation // Fixed naming collision here
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                    }
                            )
                        }
                    }

                    Spacer(Modifier.height(40.dp))

                    // TITLE (Slide & Fade)
                    AnimatedContent(
                        targetState = data.title,
                        transitionSpec = {
                            (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }) togetherWith
                                    (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
                        },
                        label = "title_animation"
                    ) { title ->
                        Text(
                            text = title,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TitleColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // DESCRIPTION (Slide & Fade delayed slightly)
                    AnimatedContent(
                        targetState = data.description,
                        transitionSpec = {
                            (fadeIn(tween(400, delayMillis = 100)) + slideInVertically(
                                tween(
                                    400,
                                    delayMillis = 100
                                )
                            ) { it / 3 }) togetherWith
                                    (fadeOut(tween(200)))
                        },
                        label = "desc_animation"
                    ) { description ->
                        Text(
                            text = description,
                            fontSize = 14.sp,
                            color = SubtitleColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            // DOT INDICATORS
            Row(
                modifier = Modifier.padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val isActive = index == pagerState.currentPage

                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot_width"
                    )

                    val color by animateColorAsState(
                        targetValue = if (isActive) DotActive else DotInactive,
                        animationSpec = tween(300),
                        label = "dot_color"
                    )

                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // BUTTON
            val scaleBtn by animateFloatAsState(
                targetValue = if (isLastPage) 1.02f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "btn_scale"
            )

            Button(
                onClick = {
                    scope.launch {
                        if (isLastPage) onGetStarted()
                        else pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .height(56.dp)
                    .scale(scaleBtn),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
            ) {
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "btn_content"
                ) { last ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (last) "GET STARTED" else "NEXT",
                            color = White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )

                        // Animated Icon for the final page
                        AnimatedVisibility(
                            visible = last,
                            enter = slideInHorizontally { it / 2 } + fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = White,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}