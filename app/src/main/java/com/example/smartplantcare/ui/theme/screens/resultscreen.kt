package com.example.smartplantcare.ui.theme.screens.resultscreen

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.data.DiseaseResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeColors
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeDimens
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeInterFont
import java.util.Locale

@Composable
fun ResultScreen(
    prediction: PredictionResult,
    diseaseInfo: DiseaseResult,
    leafImage: Bitmap?,
    classification: ClassificationResult,
    captureSessionId: Long,
    onBackClick: () -> Unit,
    onViewTreatmentPlan: () -> Unit,
    onCalibrationLogged: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val isHealthy = prediction.className.contains("healthy", ignoreCase = true)
    val matchPercentage = String.format(Locale.US, "%.1f", (prediction.confidence * 100f).coerceIn(0f, 100f))
    val hostPlantName = prediction.className.split("_").firstOrNull()?.ifBlank { "Plant" } ?: "Plant"

    val pathogenType = when {
        isHealthy -> "Optimal Leaf Health"
        prediction.className.contains("Virus", ignoreCase = true) -> "Viral Pathogen Identification"
        prediction.className.contains("Bacterial", ignoreCase = true) -> "Bacterial Pathogen Identification"
        prediction.className.contains("Deficiency", ignoreCase = true) -> "Nutritional Deficiency Analysis"
        else -> "Fungal Pathogen Identification"
    }

    Scaffold(
        topBar = {
            ResultTopBar(onBackClick = onBackClick)
        },
        bottomBar = {
            Surface(
                color = HomeColors.SurfaceWhite,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onViewTreatmentPlan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HomeColors.ForestPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MedicalServices,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHealthy) "Save Healthy Record & 7-Day Care" else "Save & View 7-Day Treatment Plan",
                                fontFamily = HomeInterFont,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = onBackClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeColors.ForestPrimary),
                            border = BorderStroke(1.5.dp, HomeColors.ForestPrimary.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scan Another Leaf",
                                fontFamily = HomeInterFont,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeColors.ForestPrimary
                            )
                        }
                    }
                }
            }
        },
        containerColor = HomeColors.PageBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Leaf Image & Analysis Status Card
            ImageAnalysisCard(leafImage = leafImage, isHealthy = isHealthy)

            // 2. Metrics & Diagnosis Summary Card
            MainInfoCard(
                diseaseName = diseaseInfo.diseaseName,
                pathogenType = pathogenType,
                matchPercentage = matchPercentage,
                confidence = prediction.confidence,
                hostPlantName = hostPlantName,
                isHealthy = isHealthy,
                description = diseaseInfo.description
            )

            // 3. Key Visual Indicators / Symptoms Card
            SymptomsCard(diseaseName = prediction.className, isHealthy = isHealthy)

            // 4. Immediate Care Actions (properly supports both narrow & standard screens)
            ImmediateActionsCard(
                treatment = diseaseInfo.treatment,
                prevention = diseaseInfo.preventionTips,
                isHealthy = isHealthy
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultTopBar(onBackClick: () -> Unit) {
    Column(modifier = Modifier.background(HomeColors.SurfaceWhite)) {
        CenterAlignedTopAppBar(
            windowInsets = WindowInsets.statusBars,
            title = {
                Text(
                    text = "AI Diagnosis Result",
                    fontFamily = HomeInterFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
            },
            navigationIcon = {
                Surface(
                    color = HomeColors.PageBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBackClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = HomeColors.ForestPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = HomeColors.SurfaceWhite)
        )
        HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)
    }
}

@Composable
private fun ImageAnalysisCard(leafImage: Bitmap?, isHealthy: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(HomeColors.SoftMint)
            .border(1.dp, HomeColors.CardBorder, RoundedCornerShape(22.dp))
    ) {
        if (leafImage != null) {
            Image(
                bitmap = leafImage.asImageBitmap(),
                contentDescription = "Analyzed leaf snapshot",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.48f))
                        )
                    )
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = HomeColors.ForestMedium.copy(alpha = 0.45f),
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Surface(
            color = HomeColors.SurfaceWhite.copy(alpha = 0.96f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, HomeColors.CardBorder),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.HealthAndSafety,
                    contentDescription = "Complete",
                    tint = if (isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = if (isHealthy) "Healthy Leaf Verified" else "AI Analysis Complete",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = "On-device TFLite classification",
                        fontFamily = HomeInterFont,
                        fontSize = 10.sp,
                        color = HomeColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MainInfoCard(
    diseaseName: String,
    pathogenType: String,
    matchPercentage: String,
    confidence: Float,
    hostPlantName: String,
    isHealthy: Boolean,
    description: String
) {
    val (badgeBg, badgeFg, badgeText) = when {
        isHealthy -> Triple(HomeColors.SuccessBg, HomeColors.SuccessGreen, "HEALTHY")
        confidence >= 0.85f -> Triple(HomeColors.AlertBg, HomeColors.AlertRed, "HIGH MATCH")
        else -> Triple(HomeColors.WarningBg, HomeColors.WarningAmber, "MODERATE")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = diseaseName,
                        fontFamily = HomeInterFont,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary,
                        lineHeight = 25.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = pathogenType,
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = HomeColors.TextSecondary
                    )
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = badgeText,
                            fontFamily = HomeInterFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = HomeColors.PageBg,
                    border = BorderStroke(1.dp, HomeColors.CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = matchPercentage,
                                fontFamily = HomeInterFont,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary
                            )
                            Text(
                                text = "%",
                                fontFamily = HomeInterFont,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                            )
                        }
                        Text(
                            text = "CONFIDENCE",
                            fontFamily = HomeInterFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = HomeColors.TextMuted
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isHealthy) HomeColors.SuccessBg else HomeColors.AlertBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.Shield,
                                contentDescription = null,
                                tint = if (isHealthy) HomeColors.SuccessGreen else HomeColors.AlertRed,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Status",
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                color = HomeColors.TextSecondary
                            )
                            Text(
                                text = when {
                                    isHealthy -> "Healthy & Thriving"
                                    confidence >= 0.85f -> "Action Recommended"
                                    else -> "Early Detection"
                                },
                                fontFamily = HomeInterFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HomeColors.SoftMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Eco,
                                contentDescription = null,
                                tint = HomeColors.ForestPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Host Crop",
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                color = HomeColors.TextSecondary
                            )
                            Text(
                                text = hostPlantName,
                                fontFamily = HomeInterFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = HomeColors.DividerLine)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = description,
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = HomeColors.TextSecondary
            )
        }
    }
}

@Composable
private fun SymptomsCard(diseaseName: String, isHealthy: Boolean) {
    val symptomsList = when {
        diseaseName.contains("Bacterial_Spot", ignoreCase = true) -> listOf(
            "Water-soaked tiny leaf lesions",
            "Dark brown spot cores",
            "Yellow halos around lesions",
            "Premature defoliation"
        )
        diseaseName.contains("Blight", ignoreCase = true) -> listOf(
            "Brown concentric target-board spots",
            "Extensive chlorosis",
            "Dry, papery margins",
            "Stem lesion development"
        )
        diseaseName.contains("Virus", ignoreCase = true) -> listOf(
            "Severe upward leaf curling",
            "Mottled mosaic patterns",
            "Stunted terminal shoot growth",
            "Crinkled leaf texture"
        )
        diseaseName.contains("Deficiency", ignoreCase = true) -> listOf(
            "Interveinal leaf yellowing",
            "Purple or pale edge tints",
            "Slowed developmental growth",
            "Brittle stems"
        )
        isHealthy -> listOf(
            "Uniform green leaf blade presentation",
            "Firm turgid stem support",
            "No visible fungal or bacterial spots"
        )
        else -> listOf(
            "Irregular necrotic leaf spots",
            "Wilting foliage extensions",
            "Powdery or discolored surfaces",
            "Debris accumulation"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = if (isHealthy) "Healthy Leaf Indicators" else "Identified Symptoms",
                    fontFamily = HomeInterFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary
                )
            }

            symptomsList.forEach { symptom ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HomeColors.PageBg,
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = HomeColors.EmeraldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = symptom,
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = HomeColors.TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmediateActionsCard(
    treatment: String,
    prevention: String,
    isHealthy: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MedicalServices,
                contentDescription = null,
                tint = HomeColors.ForestPrimary,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = if (isHealthy) "Care & Maintenance" else "Immediate Actions",
                fontFamily = HomeInterFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HomeColors.TextPrimary
            )
        }

        val configuration = LocalConfiguration.current
        val isSmallScreen = configuration.screenWidthDp < 360

        if (isSmallScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionAdviceCard(
                    title = if (isHealthy) "Routine Care" else "Active Control",
                    titleColor = if (isHealthy) HomeColors.ForestPrimary else HomeColors.AlertRed,
                    badgeBg = if (isHealthy) HomeColors.SoftMint else HomeColors.AlertBg,
                    body = treatment,
                    modifier = Modifier.fillMaxWidth()
                )
                ActionAdviceCard(
                    title = "Prevention Tips",
                    titleColor = HomeColors.SuccessGreen,
                    badgeBg = HomeColors.SuccessBg,
                    body = prevention,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionAdviceCard(
                    title = if (isHealthy) "Routine Care" else "Active Control",
                    titleColor = if (isHealthy) HomeColors.ForestPrimary else HomeColors.AlertRed,
                    badgeBg = if (isHealthy) HomeColors.SoftMint else HomeColors.AlertBg,
                    body = treatment,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                ActionAdviceCard(
                    title = "Prevention Tips",
                    titleColor = HomeColors.SuccessGreen,
                    badgeBg = HomeColors.SuccessBg,
                    body = prevention,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ActionAdviceCard(
    title: String,
    titleColor: Color,
    badgeBg: Color,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = title,
                    fontFamily = HomeInterFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = body,
                fontFamily = HomeInterFont,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = HomeColors.TextSecondary
            )
        }
    }
}
