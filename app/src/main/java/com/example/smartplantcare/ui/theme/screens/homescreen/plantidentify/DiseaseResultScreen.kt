package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartplantcare.ML.DetectionResult
import com.example.smartplantcare.ML.PlantType

// Palette - Emerald/Forest Green design system
private val EmeraldGreen = Color(0xFF22C55E)
private val ForestGreen = Color(0xFF166534)
private val DeepGreen = Color(0xFF0F3D22)
private val SoftBackground = Color(0xFFF7FAF8)
private val CardBackground = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF6A6A6A)
private val ErrorRed = Color(0xFFDC2626)
private val WarningOrange = Color(0xFFEA580C)

@Composable
fun DiseaseResultScreen(
    detectionResult: DetectionResult,
    onCaptureAnother: () -> Unit
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
            when (detectionResult) {
                is DetectionResult.PlantRejected -> {
                    PlantRejectedContent(detectionResult)
                }
                is DetectionResult.DiseaseRejected -> {
                    DiseaseRejectedContent(detectionResult)
                }
                is DetectionResult.DiseaseAccepted -> {
                    DiseaseAcceptedContent(detectionResult)
                }
                else -> {
                    // Should not happen, but handle gracefully
                    ErrorScreen("Unexpected result state", onCaptureAnother)
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
private fun PlantRejectedContent(result: DetectionResult.PlantRejected) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Unable to identify plant",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = result.message,
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Show detected plant info even if rejected
        PlantInfoCard(result.plantPrediction)
    }
}

@Composable
private fun DiseaseRejectedContent(result: DetectionResult.DiseaseRejected) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Warning icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(WarningOrange.copy(alpha = 0.1f), RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = WarningOrange,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Unable to identify disease",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = result.message,
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Show plant identification result
        PlantInfoCard(result.plantPrediction)
    }
}

@Composable
private fun DiseaseAcceptedContent(result: DetectionResult.DiseaseAccepted) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
        // Plant identification badge
        PlantIdentificationBadge(result.plantPrediction)

        Spacer(modifier = Modifier.height(20.dp))

        // Disease result card
        DiseaseResultCard(result)

        Spacer(modifier = Modifier.height(20.dp))

        // Additional information
        AdditionalInfoSection(result)
    }
}

@Composable
private fun PlantIdentificationBadge(plantPrediction: com.example.smartplantcare.ML.PlantPrediction) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Plant Identified",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plantPrediction.plantType.getDisplayName(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (result.diseasePrediction.className) {
                "Tomato_healthy", "Chili_Healthy_Leaf", "Eggplant_Healthy Leaf" -> Color(0xFFDCFCE7)
                else -> CardBackground
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Disease Detection",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = result.diseasePrediction.className.replace("_", " "),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = when (result.diseasePrediction.className) {
                    "Tomato_healthy", "Chili_Healthy_Leaf", "Eggplant_Healthy Leaf" -> ForestGreen
                    else -> ErrorRed
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ConfidenceBadge(
                confidence = result.diseaseConfidence,
                label = "Disease Confidence"
            )
        }
    }
}

@Composable
private fun PlantInfoCard(plantPrediction: com.example.smartplantcare.ML.PlantPrediction) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Detected Plant",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plantPrediction.plantType.getDisplayName(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
private fun ConfidenceBadge(confidence: Float, label: String = "Confidence") {
    val percent = (confidence * 100).toInt()
    val color = when {
        percent >= 80 -> EmeraldGreen
        percent >= 60 -> WarningOrange
        else -> ErrorRed
    }
    
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "$percent%",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun AdditionalInfoSection(result: DetectionResult.DiseaseAccepted) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Detection Details",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF64748B)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
