package com.example.smartplantcare.ui.theme.screens.homescreen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartplantcare.ML.DiseaseRepository
import java.io.File
import java.util.Locale

private data class WordSpan(
    val word: String,
    val startChar: Int,
    val endChar: Int,
    val globalIndex: Int
)

private data class TtsSpokenSections(
    val fullText: String,
    val descSpans: List<WordSpan>,
    val causeSpans: List<WordSpan>,
    val careTipsSpans: List<WordSpan>,
    val allSpans: List<WordSpan>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentPlanDetailScreen(
    planId: String,
    viewModel: TreatmentPlanViewModel,
    onBackClick: () -> Unit
) {
    val plan by viewModel.selectedPlan.collectAsState()
    val isDetailLoading by viewModel.isDetailLoading.collectAsState()

    LaunchedEffect(planId) {
        if (planId.isNotBlank()) {
            viewModel.loadPlan(planId)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(HomeColors.SurfaceWhite)) {
                CenterAlignedTopAppBar(
                    windowInsets = WindowInsets.statusBars,
                    title = {
                        val currentPlan = plan
                        val isHealthy = currentPlan?.isHealthy == true
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = currentPlan?.plantName?.ifBlank { "Plant" }
                                    ?: if (isDetailLoading) "Loading..." else "Care Guide",
                                fontFamily = HomeInterFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                color = when {
                                    currentPlan == null -> HomeColors.TextPrimary
                                    isHealthy -> HomeColors.ForestMedium
                                    else -> HomeColors.AlertRed
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val badgeColor = when {
                                currentPlan == null -> HomeColors.SoftMint
                                isHealthy -> HomeColors.SuccessBg
                                else -> HomeColors.AlertBg
                            }
                            val badgeTextColor = when {
                                currentPlan == null -> HomeColors.ForestMedium
                                isHealthy -> HomeColors.SuccessGreen
                                else -> HomeColors.AlertRed
                            }
                            val badgeText = when {
                                currentPlan == null -> if (isDetailLoading) "Loading" else "Unavailable"
                                isHealthy -> "Healthy"
                                else -> currentPlan.diseaseName.ifBlank { "Care Guide" }
                            }
                            Surface(color = badgeColor, shape = RoundedCornerShape(50)) {
                                Text(
                                    text = badgeText,
                                    fontFamily = HomeInterFont,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    color = badgeTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
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
        containerColor = HomeColors.PageBg
    ) { innerPadding ->
        when {
            plan != null -> {
                val p = plan!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 16.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { ImageHeaderSection(p.imageUrl, p.confidence) }

                    item {
                        DiseaseInformationSection(
                            className = p.className,
                            diseaseName = p.diseaseName,
                            isHealthy = p.isHealthy
                        )
                    }

                    item { Spacer(modifier = Modifier.navigationBarsPadding()) }
                }
            }

            isDetailLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = HomeColors.ForestPrimary,
                        strokeWidth = 3.5.dp
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Saved plant not found",
                            fontFamily = HomeInterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "This plant record may have been removed or is no longer available.",
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            color = HomeColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HomeColors.ForestPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Go Back", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageHeaderSection(imageUrl: String, confidence: Float) {
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        if (imageUrl.isNotBlank()) {
            val data: Any = if (imageUrl.startsWith("/")) File(imageUrl) else imageUrl
            ImageRequest.Builder(context)
                .data(data)
                .size(1024)
                .crossfade(false)
                .build()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HomeColors.PlaceholderBg),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Plant",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = HomeColors.TextMuted,
                modifier = Modifier.size(44.dp)
            )
        }

        val confidencePercentage = (confidence * 100).toInt()
        if (confidencePercentage > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                color = HomeColors.SurfaceWhite.copy(alpha = 0.95f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, HomeColors.CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = HomeColors.SuccessGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$confidencePercentage% Match",
                        fontFamily = HomeInterFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeColors.TextPrimary
                    )
                }
            }
        }
    }
}

internal fun resolveDiseaseRepositoryKey(
    className: String,
    diseaseName: String,
    isHealthy: Boolean
): String? {
    if (className.isNotBlank() && DiseaseRepository.getAllSupportedKeys().contains(className)) {
        return className
    }
    return when {
        isHealthy -> when {
            diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Healthy_Leaf"
            diseaseName.contains("Eggplant", ignoreCase = true) -> "Eggplant_Healthy Leaf"
            diseaseName.contains("Tomato", ignoreCase = true) -> "Tomato_healthy"
            else -> null
        }
        diseaseName.contains("Bacterial Spot", ignoreCase = true) -> when {
            diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Bacterial_Spot"
            diseaseName.contains("Tomato", ignoreCase = true) -> "Tomato_Bacterial_spot"
            else -> null
        }
        diseaseName.contains("Cercospora", ignoreCase = true) -> when {
            diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Cercospora_Leaf_Spot"
            diseaseName.contains("Eggplant", ignoreCase = true) -> "Eggplant_Cercospora Leaf Spot"
            else -> null
        }
        diseaseName.contains("Leaf Curl", ignoreCase = true) || diseaseName.contains("Curl Virus", ignoreCase = true) -> when {
            diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Curl_Virus"
            diseaseName.contains("Tomato", ignoreCase = true) -> "Tomato__Tomato_YellowLeaf__Curl_Virus"
            else -> null
        }
        diseaseName.contains("Mosaic", ignoreCase = true) -> when {
            diseaseName.contains("Tomato", ignoreCase = true) -> "Tomato__Tomato_mosaic_virus"
            else -> "Eggplant_Mosaic Virus Disease"
        }
        diseaseName.contains("Nutrient", ignoreCase = true) || diseaseName.contains("Deficiency", ignoreCase = true) -> "Chili_Nutrition_Deficiency"
        diseaseName.contains("White Spot", ignoreCase = true) -> "Chili_White_Spot"
        diseaseName.contains("Insect", ignoreCase = true) || diseaseName.contains("Pest", ignoreCase = true) -> "Eggplant_Insect Pest Disease"
        diseaseName.contains("Septoria", ignoreCase = true) -> "Tomato_Septoria_leaf_spot"
        diseaseName.contains("Leaf Spot", ignoreCase = true) -> "Eggplant_Leaf Spot Disease"
        diseaseName.contains("Powdery", ignoreCase = true) -> "Eggplant_Powdery Mildew"
        diseaseName.contains("Early Blight", ignoreCase = true) -> "Tomato_Early_blight"
        diseaseName.contains("Late Blight", ignoreCase = true) -> "Tomato_Late_blight"
        diseaseName.contains("Leaf Mold", ignoreCase = true) -> "Tomato_Leaf_Mold"
        diseaseName.contains("Spider", ignoreCase = true) || diseaseName.contains("Mite", ignoreCase = true) -> "Tomato_Spider_mites_Two_spotted_spider_mite"
        diseaseName.contains("Target Spot", ignoreCase = true) -> "Tomato__Target_Spot"
        else -> null
    }
}

@Composable
fun DiseaseInformationSection(
    className: String = "",
    diseaseName: String,
    isHealthy: Boolean
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var currentWordIndex by remember { mutableIntStateOf(-1) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsInitialized by remember { mutableStateOf(false) }

    val diseaseKey = remember(className, diseaseName, isHealthy) {
        resolveDiseaseRepositoryKey(className, diseaseName, isHealthy)
    }

    val diseaseData = remember(diseaseKey) { DiseaseRepository.getDiseaseInfo(diseaseKey) }
    val description = diseaseData.description
    val causes = diseaseData.causes
    val careTips = diseaseData.careTips

    // Precompute spoken text & word character intervals across Description, Causes, and Care Tips
    val ttsSections = remember(description, causes, careTips) {
        val descPrefix = "Description. "
        val causesPrefix = ". Causes. "
        val careTipsPrefix = ". Care Tips. "
        val fullText = "$descPrefix$description$causesPrefix$causes$careTipsPrefix$careTips"

        val descWords = description.split(" ").filter { it.isNotEmpty() }
        val causesWords = causes.split(" ").filter { it.isNotEmpty() }
        val careTipsWords = careTips.split(" ").filter { it.isNotEmpty() }
        val allSpans = ArrayList<WordSpan>(descWords.size + causesWords.size + careTipsWords.size + 4)

        var cursor = descPrefix.length
        var globalIdx = 1

        val descSpans = ArrayList<WordSpan>(descWords.size)
        for (word in descWords) {
            val start = fullText.indexOf(word, cursor).takeIf { it >= 0 } ?: cursor
            val end = start + word.length
            val span = WordSpan(word, start, end, globalIdx++)
            descSpans.add(span)
            allSpans.add(span)
            cursor = end
        }

        cursor = (descPrefix.length + description.length + causesPrefix.length)
        val causeSpans = ArrayList<WordSpan>(causesWords.size)
        for (word in causesWords) {
            val start = fullText.indexOf(word, cursor).takeIf { it >= 0 } ?: cursor
            val end = start + word.length
            val span = WordSpan(word, start, end, globalIdx++)
            causeSpans.add(span)
            allSpans.add(span)
            cursor = end
        }

        cursor = (descPrefix.length + description.length + causesPrefix.length + causes.length + careTipsPrefix.length)
        val careTipsSpans = ArrayList<WordSpan>(careTipsWords.size)
        for (word in careTipsWords) {
            val start = fullText.indexOf(word, cursor).takeIf { it >= 0 } ?: cursor
            val end = start + word.length
            val span = WordSpan(word, start, end, globalIdx++)
            careTipsSpans.add(span)
            allSpans.add(span)
            cursor = end
        }

        TtsSpokenSections(
            fullText = fullText,
            descSpans = descSpans,
            causeSpans = causeSpans,
            careTipsSpans = careTipsSpans,
            allSpans = allSpans
        )
    }

    DisposableEffect(context, ttsSections.allSpans) {
        lateinit var textToSpeech: TextToSpeech
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                mainHandler.post { ttsInitialized = true }
            }
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    currentWordIndex = 0
                    isSpeaking = true
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    currentWordIndex = -1
                    isSpeaking = false
                }
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    currentWordIndex = -1
                    isSpeaking = false
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val match = ttsSections.allSpans.firstOrNull { start >= it.startChar && start < it.endChar }
                if (match != null) {
                    mainHandler.post { currentWordIndex = match.globalIndex }
                }
            }
        })

        tts = textToSpeech

        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            textToSpeech.stop()
            textToSpeech.shutdown()
            currentWordIndex = -1
            isSpeaking = false
            ttsInitialized = false
        }
    }

    val annotatedDescription = remember(currentWordIndex, ttsSections.descSpans) {
        buildHighlightedText(ttsSections.descSpans, currentWordIndex)
    }

    val annotatedCauses = remember(currentWordIndex, ttsSections.causeSpans) {
        buildHighlightedText(ttsSections.causeSpans, currentWordIndex)
    }

    val annotatedCareTips = remember(currentWordIndex, ttsSections.careTipsSpans) {
        buildHighlightedText(ttsSections.careTipsSpans, currentWordIndex)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header: Icon + Title + Listen/Stop TTS Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isHealthy) HomeColors.SuccessBg else HomeColors.SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHealthy) Icons.Filled.Eco else Icons.Filled.MedicalServices,
                        contentDescription = null,
                        tint = if (isHealthy) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHealthy) "Plant Health Guide" else "Disease Information",
                        fontFamily = HomeInterFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        color = HomeColors.TextPrimary
                    )
                    Text(
                        text = if (isSpeaking) "Reading guide aloud..." else "Summary, causes & care tips",
                        fontFamily = HomeInterFont,
                        fontSize = 11.5.sp,
                        color = if (isSpeaking) HomeColors.ForestMedium else HomeColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = if (isSpeaking) HomeColors.AlertBg else if (ttsInitialized) HomeColors.SoftMint else HomeColors.PlaceholderBg,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isSpeaking -> HomeColors.AlertRed.copy(alpha = 0.25f)
                            ttsInitialized -> HomeColors.SoftMintStrong
                            else -> HomeColors.CardBorder
                        }
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = ttsInitialized) {
                            if (isSpeaking) {
                                tts?.stop()
                                isSpeaking = false
                                currentWordIndex = -1
                            } else {
                                tts?.let { textToSpeech ->
                                    textToSpeech.stop()
                                    currentWordIndex = -1
                                    val params = Bundle()
                                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tts_utterance")
                                    textToSpeech.speak(
                                        ttsSections.fullText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        params,
                                        "tts_utterance"
                                    )
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeaking) "Stop listening" else "Listen",
                            tint = when {
                                isSpeaking -> HomeColors.AlertRed
                                ttsInitialized -> HomeColors.ForestPrimary
                                else -> HomeColors.TextMuted
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isSpeaking) "Stop" else "Listen",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = when {
                                isSpeaking -> HomeColors.AlertRed
                                ttsInitialized -> HomeColors.ForestPrimary
                                else -> HomeColors.TextMuted
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = HomeColors.DividerLine, thickness = 1.dp)

            // 1. Description Panel
            CareGuideSectionPanel(
                icon = Icons.Outlined.Description,
                iconBg = HomeColors.SoftMint,
                iconTint = HomeColors.ForestPrimary,
                title = "Description",
                titleColor = HomeColors.ForestDark,
                containerColor = HomeColors.SubtleSurface,
                borderColor = HomeColors.CardBorder,
                body = annotatedDescription,
                bodyColor = HomeColors.TextSecondary
            )

            // 2. Causes Panel
            CareGuideSectionPanel(
                icon = Icons.Outlined.Biotech,
                iconBg = if (isHealthy) HomeColors.SuccessBg else HomeColors.WarningBg,
                iconTint = if (isHealthy) HomeColors.SuccessGreen else HomeColors.WarningAmber,
                title = "Causes",
                titleColor = HomeColors.ForestDark,
                containerColor = HomeColors.SubtleSurface,
                borderColor = HomeColors.CardBorder,
                body = annotatedCauses,
                bodyColor = HomeColors.TextSecondary
            )

            // 3. Care Tips Panel (Highlighted Actionable Guidance)
            CareGuideSectionPanel(
                icon = Icons.Outlined.Spa,
                iconBg = HomeColors.SurfaceWhite,
                iconTint = HomeColors.ForestPrimary,
                title = "Care Tips",
                titleColor = HomeColors.ForestPrimary,
                containerColor = HomeColors.SoftMint.copy(alpha = 0.65f),
                borderColor = HomeColors.SoftMintStrong,
                body = annotatedCareTips,
                bodyColor = HomeColors.TextPrimary.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun CareGuideSectionPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    titleColor: Color,
    containerColor: Color,
    borderColor: Color,
    body: androidx.compose.ui.text.AnnotatedString,
    bodyColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = titleColor,
                    letterSpacing = (-0.1).sp
                )
            }

            Text(
                text = body,
                fontFamily = HomeInterFont,
                fontSize = 13.5.sp,
                color = bodyColor,
                lineHeight = 22.sp
            )
        }
    }
}

private fun buildHighlightedText(spans: List<WordSpan>, currentWordIndex: Int) =
    buildAnnotatedString {
        spans.forEachIndexed { i, span ->
            if (currentWordIndex == span.globalIndex) {
                withStyle(SpanStyle(background = HomeColors.ForestPrimary, color = Color.White)) {
                    append(span.word)
                }
            } else {
                append(span.word)
            }
            if (i < spans.lastIndex) append(" ")
        }
    }
