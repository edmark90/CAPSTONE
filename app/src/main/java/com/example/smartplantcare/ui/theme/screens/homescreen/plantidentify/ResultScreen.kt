package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.plantnetnetwork.PlantIdentificationSummary
import com.example.smartplantcare.plantnetnetwork.PlantIdentificationUsage
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeColors
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeInterFont

@Composable
fun PlantResultScreen(
    topMatch: PlantIdentificationSummary,
    allResults: List<PlantIdentificationSummary>,
    onCaptureAnother: () -> Unit,
    onBack: () -> Unit = onCaptureAnother,
    usage: PlantIdentificationUsage? = null,
    dailyLimit: Int = 20
) {
    Scaffold(
        containerColor = HomeColors.PageBg,
        bottomBar = {
            Surface(
                color = HomeColors.SurfaceWhite,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
                    Button(
                        onClick = onCaptureAnother,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomeColors.ForestPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Identify Another Plant",
                            fontFamily = HomeInterFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            HeroImageSection(topMatch = topMatch, onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(18.dp))

                ConfidenceBadge(confidence = topMatch.confidenceScore)

                usage?.let { u ->
                    Spacer(modifier = Modifier.height(12.dp))
                    UsageBanner(
                        usedCount = u.identifyCount.toInt(),
                        dailyLimit = dailyLimit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = topMatch.plantName,
                    fontFamily = HomeInterFont,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = topMatch.scientificName,
                    fontFamily = HomeInterFont,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = HomeColors.ForestMedium
                )

                Spacer(modifier = Modifier.height(18.dp))

                val configuration = LocalConfiguration.current
                val isCompactInfo = configuration.screenWidthDp < 380 || LocalDensity.current.fontScale > 1.15f
                if (isCompactInfo) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoCard(label = "Species", value = topMatch.species, modifier = Modifier.fillMaxWidth())
                        InfoCard(label = "Family", value = topMatch.family, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                    ) {
                        InfoCard(label = "Species", value = topMatch.species, modifier = Modifier.weight(1f).fillMaxHeight())
                        InfoCard(label = "Family", value = topMatch.family, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                SectionLabel(text = "Description")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = topMatch.description,
                        fontFamily = HomeInterFont,
                        fontSize = 14.sp,
                        color = HomeColors.TextSecondary,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (topMatch.relatedImageUrls.isNotEmpty()) {
                    SectionLabel(text = "Related Images")
                    Spacer(modifier = Modifier.height(8.dp))
                    RelatedImagesRow(imageUrls = topMatch.relatedImageUrls)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (allResults.size > 1) {
                    SectionLabel(text = "Other Possible Matches")
                    Spacer(modifier = Modifier.height(8.dp))
                    allResults.drop(1).take(3).forEach { alt ->
                        OtherMatchRow(alt)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = HomeInterFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = HomeColors.TextPrimary
    )
}

@Composable
private fun HeroImageSection(
    topMatch: PlantIdentificationSummary,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val heroImageUrl = topMatch.relatedImageUrls.firstOrNull()
    val heroRequest = remember(heroImageUrl) {
        heroImageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(1080)
                .crossfade(false)
                .build()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(270.dp)
            .background(HomeColors.SoftMint)
    ) {
        if (heroRequest != null) {
            AsyncImage(
                model = heroRequest,
                contentDescription = topMatch.plantName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = HomeColors.ForestMedium.copy(alpha = 0.4f),
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Double) {
    val percent = (confidence * 100).toInt()
    Surface(
        shape = RoundedCornerShape(50),
        color = HomeColors.SuccessBg
    ) {
        Text(
            text = "$percent% match confidence",
            fontFamily = HomeInterFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.SuccessGreen,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                fontFamily = HomeInterFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextMuted,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                fontFamily = HomeInterFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
        }
    }
}

@Composable
private fun RelatedImagesRow(imageUrls: List<String>) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(imageUrls.take(10), key = { it }) { url ->
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .size(300)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(108.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HomeColors.SoftMint)
            )
        }
    }
}

@Composable
private fun OtherMatchRow(match: PlantIdentificationSummary) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.plantName,
                    fontFamily = HomeInterFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = match.scientificName,
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = HomeColors.ForestMedium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(color = HomeColors.SoftMint, shape = RoundedCornerShape(50)) {
                Text(
                    text = "${(match.confidenceScore * 100).toInt()}%",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.ForestPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeColors.PageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(HomeColors.SurfaceWhite)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HomeColors.TextPrimary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (onBack != null) 52.dp else 0.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(HomeColors.AlertBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = HomeColors.AlertRed,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = "Identification Failed",
                fontFamily = HomeInterFont,
                fontSize = 19.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                fontFamily = HomeInterFont,
                fontSize = 14.sp,
                color = HomeColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomeColors.ForestPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Try Again", fontFamily = HomeInterFont, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center)
            }
            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = "Go Back", fontFamily = HomeInterFont, fontWeight = FontWeight.Medium, color = HomeColors.TextSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun UsageBanner(
    usedCount: Int,
    dailyLimit: Int,
    modifier: Modifier = Modifier
) {
    val remaining = (dailyLimit - usedCount).coerceAtLeast(0)
    val isNearLimit = remaining <= 5
    val bgColor = if (isNearLimit) HomeColors.AlertBg else HomeColors.SoftMint
    val textColor = if (isNearLimit) HomeColors.AlertRed else HomeColors.ForestPrimary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Today's Usage: $usedCount / $dailyLimit",
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (remaining == 0) "Limit reached · Resets at 8:00 AM"
                else "$remaining scan${if (remaining == 1) "" else "s"} remaining · Resets at 8:00 AM",
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = textColor.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}
