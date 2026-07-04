package com.example.smartplantcare.ui.theme.screens.resultscreen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.calibration.CalibrationConfig
import com.example.smartplantcare.calibration.CalibrationLogger
import com.example.smartplantcare.calibration.CalibrationShare
import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.data.DiseaseResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult
import com.example.smartplantcare.ui.theme.DarkGreen

@Composable
fun ResultScreen(
    prediction: PredictionResult,
    diseaseInfo: DiseaseResult,
    leafImage: Bitmap?,
    classification: ClassificationResult,
    captureSessionId: Long,
    onBackClick: () -> Unit,
    onViewTreatmentPlanClick: () -> Unit,
    onCalibrationLogged: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Helper formatting matching image_0493b5.png logic
    val matchPercentage = String.format("%.1f", prediction.confidence * 100)
    val hostPlantName = prediction.className.split("_").firstOrNull() ?: "Plant"

    // Determine pathogen classification subtext
    val pathogenType = when {
        prediction.className.contains("Virus", ignoreCase = true) -> "Viral Pathogen Identification"
        prediction.className.contains("Bacterial", ignoreCase = true) -> "Bacterial Pathogen Identification"
        prediction.className.contains("Deficiency", ignoreCase = true) -> "Nutritional Status Analysis"
        prediction.className.contains("Healthy", ignoreCase = true) -> "Optimal Health Status"
        else -> "Fungal Pathogen Identification"
    }

    Scaffold(
        topBar = {
            ResultTopBar(onBackClick = onBackClick)
        },
        bottomBar = {
            Button(
                onClick = onViewTreatmentPlanClick,
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(54.dp)
            ) {
                Text(
                    text = "View Full 7-Day Treatment Plan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        containerColor = Color(0xFFF8F9FA) // Soft layout background matching reference image
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Leaf Image & Status Box Card
            ImageAnalysisCard(leafImage = leafImage)

            // 2. Metrics & Summary Card
            MainInfoCard(
                diseaseName = diseaseInfo.diseaseName,
                pathogenType = pathogenType,
                matchPercentage = matchPercentage,
                confidence = prediction.confidence,
                hostPlantName = hostPlantName,
                description = diseaseInfo.description
            )

            // 3. Symptoms Listing Card
            SymptomsCard(diseaseName = prediction.className)

            // 4. Immediate Actions Breakdown Card
            ImmediateActionsCard(treatment = diseaseInfo.treatment, prevention = diseaseInfo.preventionTips)

            // TEMPORARY CALIBRATION CODE
            if (CalibrationConfig.CALIBRATION_MODE) {
                CalibrationFeedbackSection(
                    classification = classification,
                    captureSessionId = captureSessionId,
                    onLogged = onCalibrationLogged
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// TEMPORARY CALIBRATION CODE
@Composable
private fun CalibrationFeedbackSection(
    classification: ClassificationResult,
    captureSessionId: Long,
    onLogged: () -> Unit
) {
    val context = LocalContext.current
    var hasLogged by remember(captureSessionId) { mutableStateOf(false) }
    var feedbackMessage by remember(captureSessionId) { mutableStateOf<String?>(null) }

    LaunchedEffect(captureSessionId) {
        hasLogged = false
        feedbackMessage = null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Prediction Result",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1E)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = classification.top1ClassName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F4C3A)
                )
                Text(
                    text = "Confidence: ${"%.1f".format(classification.top1Confidence * 100)}%",
                    fontSize = 13.sp,
                    color = Color(0xFF495057)
                )
                Text(
                    text = "Top-2: ${classification.top2ClassName} (${"%.1f".format(classification.top2Confidence * 100)}%)",
                    fontSize = 13.sp,
                    color = Color(0xFF495057)
                )
                Text(
                    text = "Margin: ${"%.1f".format(classification.margin * 100)}%",
                    fontSize = 13.sp,
                    color = Color(0xFF495057)
                )
            }

            HorizontalDivider(color = Color(0xFFF1F3F5))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (hasLogged) return@Button
                        CalibrationLogger.log(
                            context = context,
                            className = classification.top1ClassName,
                            confidence = classification.top1Confidence,
                            top2Class = classification.top2ClassName,
                            top2Confidence = classification.top2Confidence,
                            markedCorrect = true
                        )
                        hasLogged = true
                        feedbackMessage = "Logged as correct. Ready for next capture."
                        onLogged()
                    },
                    enabled = !hasLogged,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B8A3E))
                ) {
                    Text("✅ Tama")
                }

                Button(
                    onClick = {
                        if (hasLogged) return@Button
                        CalibrationLogger.log(
                            context = context,
                            className = classification.top1ClassName,
                            confidence = classification.top1Confidence,
                            top2Class = classification.top2ClassName,
                            top2Confidence = classification.top2Confidence,
                            markedCorrect = false
                        )
                        hasLogged = true
                        feedbackMessage = "Logged as incorrect. Ready for next capture."
                        onLogged()
                    },
                    enabled = !hasLogged,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE03131))
                ) {
                    Text("❌ Mali")
                }
            }

            OutlinedButton(
                onClick = { CalibrationShare.shareLog(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Log")
            }

            feedbackMessage?.let { message ->
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = Color(0xFF2B8A3E),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultTopBar(onBackClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Detection Result",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1E)
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F3F5))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Color(0xFF1A1C1E),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFFF8F9FA))
    )
}

@Composable
private fun ImageAnalysisCard(leafImage: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE9ECEF))
    ) {
        if (leafImage != null) {
            Image(
                bitmap = leafImage.asImageBitmap(),
                contentDescription = "Analyzed leaf snapshot",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Status indicator pill overlay matching image_0493b5.png
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Complete",
                tint = Color(0xFF2B8A3E),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Analysis Complete",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212529)
                )
                Text(
                    text = "Model processed successfully",
                    fontSize = 11.sp,
                    color = Color(0xFF868E96)
                )
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
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diseaseName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F4C3A) // Dark teal-green
                    )
                    Text(
                        text = pathogenType,
                        fontSize = 13.sp,
                        color = Color(0xFF6C757D),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Dynamic Confidence Tag (High / Moderate)
                val badgeColor = if (confidence >= 0.85f) Color(0xFFFFE3E3) else Color(0xFFFFF4E6)
                val badgeTextColor = if (confidence >= 0.85f) Color(0xFFE03131) else Color(0xFFF76707)

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(badgeColor)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = badgeTextColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (confidence >= 0.85f) "HIGH CONFIDENCE" else "MODERATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large Metric Percentage Box
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F3F5))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = matchPercentage,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F4C3A)
                        )
                        Text(
                            text = "%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F4C3A),
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }
                    Text(
                        text = "MATCH RATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF868E96)
                    )
                }

                // Vertical Key-Value Data Rows
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFE3E3)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFE03131),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(text = "Severity", fontSize = 11.sp, color = Color(0xFF868E96))
                            Text(
                                text = if (confidence >= 0.85f) "Severe Case" else "Early Detection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212529)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2F0D9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = null,
                                tint = Color(0xFF385723),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(text = "Host Plant", fontSize = 11.sp, color = Color(0xFF868E96))
                            Text(
                                text = hostPlantName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212529)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF1F3F5))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Color(0xFF495057),
                textAlign = TextAlign.Justify
            )
        }
    }
}

@Composable
private fun SymptomsCard(diseaseName: String) {
    // Generate context-aware symptom tags corresponding to the 22 classes
    val symptomsList = when {
        diseaseName.contains("Bacterial_Spot", ignoreCase = true) -> listOf("Water-soaked tiny leaf lesions", "Dark brown spot cores", "Yellow halos around lesions", "Premature defoliation")
        diseaseName.contains("Blight", ignoreCase = true) -> listOf("Brown concentric target-board spots", "Extensive chlorosis", "Dry, papery margins", "Stem lesion development")
        diseaseName.contains("Virus", ignoreCase = true) -> listOf("Severe upward leaf curling", "Mottled mosaic patterns", "Stunted terminal shoot growth", "Crinkled leaf texture")
        diseaseName.contains("Deficiency", ignoreCase = true) -> listOf("Interveinal leaf yellowing", "Purple or pale edge tints", "Slowed developmental growth", "Brittle stems")
        diseaseName.contains("healthy", ignoreCase = true) -> listOf("Uniform green blade presentation", "Firm turgid stem support", "No microscopic fungal growth")
        else -> listOf("Irregular necrotic leaf spots", "Wilting foliage extensions", "Powdery surfaces", "Debris accumulation")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFF0F4C3A),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Identified Symptoms",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C1E)
                )
            }

            symptomsList.forEach { symptom ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8F9FA))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2B8A3E),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = symptom,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF495057)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmediateActionsCard(treatment: String, prevention: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocalMall,
                contentDescription = null,
                tint = Color(0xFF0F4C3A),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Immediate Actions",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1E)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First Treatment action column item
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Active Control",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE03131)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = treatment,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF495057)
                    )
                }
            }

            // Second Prevention action column item
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Cultural Mitigation",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2B8A3E)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = prevention,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF495057)
                    )
                }
            }
        }
    }
}