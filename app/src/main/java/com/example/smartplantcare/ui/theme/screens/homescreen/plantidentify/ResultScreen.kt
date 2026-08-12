package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.smartplantcare.plantnetnetwork.PlantIdentificationSummary

// Palette - Emerald/Forest Green design system
private val EmeraldGreen = Color(0xFF22C55E)
private val ForestGreen = Color(0xFF166534)
private val DeepGreen = Color(0xFF0F3D22)
private val SoftBackground = Color(0xFFF7FAF8)
private val CardBackground = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF6A6A6A)
private val TextMuted = Color(0xFF8A8A8A)

@Composable
fun PlantResultScreen(
    topMatch         : PlantIdentificationSummary,
    allResults       : List<PlantIdentificationSummary>,
    onCaptureAnother : () -> Unit,
    usage            : com.example.smartplantcare.plantnetnetwork.PlantIdentificationUsage? = null,
    dailyLimit       : Int = 20

) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp) // space for the floating button below
        ) {

            // ---- Hero image (first related image, if present) ----
            HeroImageSection(topMatch)

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                Spacer(modifier = Modifier.height(20.dp))

                ConfidenceBadge(confidence = topMatch.confidenceScore)
                usage?.let { u ->
                    Spacer(modifier = Modifier.height(12.dp))
                    UsageBanner(
                        usedCount  = u.identifyCount.toInt(),
                        dailyLimit = dailyLimit,
                        modifier   = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }



                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = topMatch.plantName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = topMatch.scientificName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ForestGreen,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ---- Info cards: Species + Family ----

                val configuration = LocalConfiguration.current
                if (configuration.screenWidthDp < 360) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCard(label = "Species", value = topMatch.species, modifier =
                            Modifier.fillMaxWidth())
                        InfoCard(label = "Family", value = topMatch.family, modifier =
                            Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier =
                        Modifier.fillMaxWidth()) {
                        InfoCard(label = "Species", value = topMatch.species, modifier =
                            Modifier.weight(1f))
                        InfoCard(label = "Family", value = topMatch.family, modifier =
                            Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                SectionLabel(text = "Description")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = topMatch.description,
                        fontSize = 14.sp,
                        color = Color(0xFF334155),
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---- Related images gallery ----
                if (topMatch.relatedImageUrls.isNotEmpty()) {
                    SectionLabel(text = "Related images")
                    Spacer(modifier = Modifier.height(8.dp))
                    RelatedImagesRow(imageUrls = topMatch.relatedImageUrls)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ---- Other possible matches ----
                if (allResults.size > 1) {
                    SectionLabel(text = "Other possible matches")
                    Spacer(modifier = Modifier.height(8.dp))
                    allResults.drop(1).take(3).forEach { alt ->
                        OtherMatchRow(alt)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // ---- Floating "Capture another plant" button ----
        Button(
            onClick = onCaptureAnother,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Capture another plant",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
}

@Composable
private fun HeroImageSection(topMatch: PlantIdentificationSummary) {
    val heroImageUrl = topMatch.relatedImageUrls.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .height(280.dp)
            .background(ForestGreen.copy(alpha = 0.1f))
    ) {
        if (heroImageUrl != null) {
            AsyncImage(
                model = heroImageUrl,
                contentDescription = topMatch.plantName,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = ForestGreen.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Double) {
    val percent = (confidence * 100).toInt()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFDCFCE7))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$percent% match confidence",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF15803D)
        )
    }
}

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun RelatedImagesRow(imageUrls: List<String>) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(imageUrls.take(10)) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ForestGreen.copy(alpha = 0.08f))
            )
        }
    }
}

@Composable
private fun OtherMatchRow(match: PlantIdentificationSummary) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = match.plantName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = match.scientificName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF15803D),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                )
            }
            Text(
                text = "${(match.confidenceScore * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF15803D)
            )
        }
    }
}

/**
 * Error screen for a failed identification (no result, network error, etc.)
 */
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "There was an problem",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Try again", color = Color.White)
            }
        }
    }
}


@Composable
fun UsageBanner(
    usedCount  : Int,
    dailyLimit : Int,
    modifier   : Modifier = Modifier
) {
    val remaining   = (dailyLimit - usedCount).coerceAtLeast(0)
    val isNearLimit = remaining <= 5
    val bgColor     = if (isNearLimit) Color(0xFFFEF2F2) else Color(0xFFDCFCE7)
    val textColor   = if (isNearLimit) Color(0xFFB91C1C) else Color(0xFF166534)

    Surface(
        modifier       = modifier,
        shape          = RoundedCornerShape(24.dp),
        color          = bgColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = "Today's Usage: $usedCount / $dailyLimit",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text  = if (remaining == 0) "Limit reached · Resets at 8:00 AM"
                else "$remaining scan${if (remaining == 1) "" else "s"} remaining · Resets at 8:00 AM",
                fontSize = 11.sp,
                color    = textColor.copy(alpha = 0.8f)
            )
        }
    }
}
