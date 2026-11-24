package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.ML.DetectionResult
import com.example.smartplantcare.ML.PlantPrediction
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeColors
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeInterFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseResultScreen(
    detectionResult: DetectionResult,
    onCaptureAnother: () -> Unit,
    onBack: () -> Unit = onCaptureAnother
) {
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(HomeColors.SurfaceWhite)) {
                CenterAlignedTopAppBar(
                    windowInsets = WindowInsets.statusBars,
                    title = {
                        Text(
                            text = "Detection Result",
                            fontFamily = HomeInterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(HomeColors.SoftMint)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = HomeColors.SurfaceWhite
                    )
                )
                HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
            }
        },
        bottomBar = {
            Surface(color = HomeColors.PageBg.copy(alpha = 0.96f)) {
                Button(
                    onClick = onCaptureAnother,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .heightIn(min = 52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Capture Another Plant",
                        fontFamily = HomeInterFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        containerColor = HomeColors.PageBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (detectionResult) {
                is DetectionResult.PlantRejected -> PlantRejectedContent(detectionResult)
                is DetectionResult.DiseaseRejected -> DiseaseRejectedContent(detectionResult)
                is DetectionResult.DiseaseAccepted -> DiseaseAcceptedContent(detectionResult)
                else -> ErrorScreen(
                    message = "Unexpected result state",
                    onRetry = onCaptureAnother,
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun PlantRejectedContent(result: DetectionResult.PlantRejected) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(HomeColors.AlertBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = HomeColors.AlertRed,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Unable to identify plant",
            fontFamily = HomeInterFont,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = result.message,
            fontFamily = HomeInterFont,
            fontSize = 14.sp,
            color = HomeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        PlantInfoCard(result.plantPrediction)
    }
}

@Composable
private fun DiseaseRejectedContent(result: DetectionResult.DiseaseRejected) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(HomeColors.WarningBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = HomeColors.WarningAmber,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Unable to identify disease",
            fontFamily = HomeInterFont,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = HomeColors.TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = result.message,
            fontFamily = HomeInterFont,
            fontSize = 14.sp,
            color = HomeColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        PlantInfoCard(result.plantPrediction)
    }
}

@Composable
private fun DiseaseAcceptedContent(result: DetectionResult.DiseaseAccepted) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PlantIdentificationBadge(result.plantPrediction)
        DiseaseResultCard(result)
        AdditionalInfoSection(result)
    }
}

@Composable
private fun PlantIdentificationBadge(plantPrediction: PlantPrediction) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PLANT IDENTIFIED",
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextMuted,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plantPrediction.plantType.getDisplayName(),
                    fontFamily = HomeInterFont,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                ConfidenceBadge(
                    confidence = plantPrediction.confidence,
                    label = "Plant Confidence"
                )
            }
        }
    }
}

@Composable
private fun DiseaseResultCard(result: DetectionResult.DiseaseAccepted) {
    val isHealthy = result.diseasePrediction.className in setOf(
        "Tomato_healthy",
        "Chili_Healthy_Leaf",
        "Eggplant_Healthy Leaf"
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHealthy) HomeColors.SuccessBg else HomeColors.SurfaceWhite
        ),
        border = BorderStroke(1.dp, if (isHealthy) HomeColors.SoftMintStrong else HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "DISEASE DETECTION",
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextMuted,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = result.diseasePrediction.className.replace("_", " "),
                fontFamily = HomeInterFont,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHealthy) HomeColors.ForestPrimary else HomeColors.AlertRed
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConfidenceBadge(
                confidence = result.diseaseConfidence,
                label = "Disease Confidence",
                alignEnd = false
            )
        }
    }
}

@Composable
private fun PlantInfoCard(plantPrediction: PlantPrediction) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DETECTED PLANT",
                fontFamily = HomeInterFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextMuted,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plantPrediction.plantType.getDisplayName(),
                    fontFamily = HomeInterFont,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                ConfidenceBadge(
                    confidence = plantPrediction.confidence,
                    label = "Confidence"
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(
    confidence: Float,
    label: String = "Confidence",
    alignEnd: Boolean = true
) {
    val percent = (confidence * 100).toInt()
    val color = when {
        percent >= 80 -> HomeColors.SuccessGreen
        percent >= 60 -> HomeColors.WarningAmber
        else -> HomeColors.AlertRed
    }

    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = "$percent%",
            fontFamily = HomeInterFont,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = HomeColors.TextMuted,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AdditionalInfoSection(result: DetectionResult.DiseaseAccepted) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Detection Details",
                fontFamily = HomeInterFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                label = "Plant Type",
                value = result.plantPrediction.plantType.getDisplayName()
            )
            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(
                label = "Disease Class",
                value = result.diseasePrediction.className.replace("_", " ")
            )
            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(
                label = "Class Index",
                value = result.diseasePrediction.classIndex.toString()
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontFamily = HomeInterFont,
            fontSize = 13.sp,
            color = HomeColors.TextSecondary,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            fontFamily = HomeInterFont,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
