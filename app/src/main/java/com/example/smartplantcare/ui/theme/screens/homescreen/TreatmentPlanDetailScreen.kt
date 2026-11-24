package com.example.smartplantcare.ui.theme.screens.homescreen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.StrokeCap
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
                                    ?: if (isDetailLoading) "Loading..." else "Treatment Plan",
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
                                currentPlan.completedCount >= 7 -> "Completed"
                                else -> "In Care"
                            }
                            Surface(color = badgeColor, shape = RoundedCornerShape(50)) {
                                Text(
                                    text = badgeText,
                                    fontFamily = HomeInterFont,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    color = badgeTextColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
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
                val currentDayIndex = p.computedCurrentDayIndex
                val tasks = remember(p.sevenDayPlan) {
                    if (p.sevenDayPlan.size >= 7) p.sevenDayPlan.take(7)
                    else p.sevenDayPlan + List(7 - p.sevenDayPlan.size) { "Upcoming treatment" }
                }

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

                    item { ProgressSection(p.completedCount, currentDayIndex) }

                    item { DiseaseInformationSection(p.diseaseName, p.isHealthy) }

                    item {
                        DoctorNotesSection(
                            isHealthy = p.isHealthy,
                            isCompleted = p.completedCount >= 7
                        )
                    }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Treatment Schedule",
                                fontFamily = HomeInterFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = HomeColors.TextPrimary,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(color = HomeColors.SoftMint, shape = RoundedCornerShape(50)) {
                                Text(
                                    text = "7 Days",
                                    fontFamily = HomeInterFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HomeColors.ForestMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(tasks) { index, task ->
                        val dayNum = index + 1
                        val isCompleted = p.completedDays.getOrElse(index) { false }
                        val isCurrent = index == currentDayIndex
                        val isLocked = index > currentDayIndex
                        val isLast = index == tasks.lastIndex
                        val displayTask = if (isCompleted || isCurrent) task else "Upcoming treatment"

                        TimelineItem(
                            dayNum = dayNum,
                            task = displayTask,
                            isCompleted = isCompleted,
                            isCurrent = isCurrent,
                            isLocked = isLocked,
                            isLast = isLast,
                            onMarkDone = { viewModel.toggleDayComplete(planId, index) },
                            onUndo = { viewModel.undoDayComplete(planId, index) }
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
                            text = "Treatment plan not found",
                            fontFamily = HomeInterFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "This plan may have been removed or is no longer available.",
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

@Composable
fun ProgressSection(completedCount: Int, currentDayIndex: Int) {
    val progress = (completedCount / 7f).coerceIn(0f, 1f)
    val percent = ((completedCount / 7f) * 100).toInt()
    val isComplete = completedCount >= 7

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (isComplete) HomeColors.SuccessGreen else HomeColors.ForestPrimary,
                    trackColor = HomeColors.SoftMint,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = "$percent%",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isComplete) "Plan Completed" else "Day ${currentDayIndex + 1} of 7",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HomeColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isComplete) "Great job! Your 7-day plan is finished."
                    else "$completedCount of 7 treatment days completed",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = HomeColors.TextSecondary,
                    lineHeight = 18.sp
                )
            }
            if (isComplete) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(HomeColors.SuccessBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = HomeColors.SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DoctorNotesSection(
    isHealthy: Boolean = false,
    isCompleted: Boolean = false
) {
    val noteBody = when {
        isHealthy -> "Your plant leaf looks healthy! Maintain consistent morning watering, good sunlight, and weekly leaf underside checks."
        isCompleted -> "Your 7-day treatment schedule is finished. Keep monitoring new leaves for spots over the coming week."
        else -> "Follow each daily step in order without skipping. Water cleanly at the soil base and inspect leaves for new spots daily."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SoftMint),
        border = BorderStroke(1.dp, HomeColors.SoftMintStrong),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(HomeColors.SurfaceWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = HomeColors.ForestMedium,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Care Guidance",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = HomeColors.ForestPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = noteBody,
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                color = HomeColors.TextSecondary,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
fun DiseaseInformationSection(diseaseName: String, isHealthy: Boolean) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var currentWordIndex by remember { mutableIntStateOf(-1) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsInitialized by remember { mutableStateOf(false) }

    val diseaseKey = remember(diseaseName, isHealthy) {
        when {
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
            diseaseName.contains("Nutrient", ignoreCase = true) || diseaseName.contains("Deficiency", ignoreCase = true) -> "Chili_Nutrition_Deficiency"
            diseaseName.contains("White Spot", ignoreCase = true) -> "Chili_White_Spot"
            diseaseName.contains("Insect", ignoreCase = true) || diseaseName.contains("Pest", ignoreCase = true) -> "Eggplant_Insect Pest Disease"
            diseaseName.contains("Leaf Spot", ignoreCase = true) -> "Eggplant_Leaf Spot Disease"
            diseaseName.contains("Mosaic", ignoreCase = true) -> "Eggplant_Mosaic Virus Disease"
            diseaseName.contains("Powdery", ignoreCase = true) -> "Eggplant_Powdery Mildew"
            diseaseName.contains("Early Blight", ignoreCase = true) -> "Tomato_Early_blight"
            diseaseName.contains("Late Blight", ignoreCase = true) -> "Tomato_Late_blight"
            diseaseName.contains("Leaf Mold", ignoreCase = true) -> "Tomato_Leaf_Mold"
            diseaseName.contains("Septoria", ignoreCase = true) -> "Tomato_Septoria_leaf_spot"
            diseaseName.contains("Spider", ignoreCase = true) || diseaseName.contains("Mite", ignoreCase = true) -> "Tomato_Spider_mites_Two_spotted_spider_mite"
            diseaseName.contains("Target Spot", ignoreCase = true) -> "Tomato__Target_Spot"
            else -> null
        }
    }

    val diseaseData = remember(diseaseKey) { DiseaseRepository.getDiseaseInfo(diseaseKey) }
    val description = diseaseData.description
    val causes = diseaseData.causes

    // Precompute spoken text & word character intervals once when text changes (zero per-word allocations)
    val ttsData = remember(description, causes) {
        val descPrefix = "Description. "
        val causesPrefix = ". Causes. "
        val fullText = "$descPrefix$description$causesPrefix$causes"

        val descWords = description.split(" ")
        val causesWords = causes.split(" ")
        val spans = ArrayList<WordSpan>(descWords.size + causesWords.size + 4)

        var cursor = descPrefix.length
        var globalIdx = 1
        val descSpans = ArrayList<WordSpan>(descWords.size)
        for (word in descWords) {
            val end = cursor + word.length
            val span = WordSpan(word, cursor, end, globalIdx++)
            descSpans.add(span)
            spans.add(span)
            cursor = end + 1
        }

        cursor += causesPrefix.length - 1
        val causeSpans = ArrayList<WordSpan>(causesWords.size)
        for (word in causesWords) {
            val end = cursor + word.length
            val span = WordSpan(word, cursor, end, globalIdx++)
            causeSpans.add(span)
            spans.add(span)
            cursor = end + 1
        }

        Triple(fullText, descSpans, causeSpans) to spans
    }

    val speakText = ttsData.first.first
    val descSpans = ttsData.first.second
    val causeSpans = ttsData.first.third
    val allSpans = ttsData.second

    DisposableEffect(context, allSpans) {
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
                val match = allSpans.firstOrNull { start >= it.startChar && start < it.endChar }
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

    val annotatedDescription = remember(currentWordIndex, descSpans) {
        buildAnnotatedString {
            descSpans.forEachIndexed { i, span ->
                if (currentWordIndex == span.globalIndex) {
                    withStyle(SpanStyle(background = HomeColors.ForestPrimary, color = Color.White)) {
                        append(span.word)
                    }
                } else {
                    append(span.word)
                }
                if (i < descSpans.lastIndex) append(" ")
            }
        }
    }

    val annotatedCauses = remember(currentWordIndex, causeSpans) {
        buildAnnotatedString {
            causeSpans.forEachIndexed { i, span ->
                if (currentWordIndex == span.globalIndex) {
                    withStyle(SpanStyle(background = HomeColors.ForestPrimary, color = Color.White)) {
                        append(span.word)
                    }
                } else {
                    append(span.word)
                }
                if (i < causeSpans.lastIndex) append(" ")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
        border = BorderStroke(1.dp, HomeColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isHealthy) "Plant Health" else "Disease Information",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HomeColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = if (isSpeaking) HomeColors.AlertBg else if (ttsInitialized) HomeColors.SoftMint else HomeColors.PlaceholderBg,
                    shape = RoundedCornerShape(50),
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
                                    textToSpeech.speak(speakText, TextToSpeech.QUEUE_FLUSH, params, "tts_utterance")
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Description",
                fontFamily = HomeInterFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = HomeColors.ForestMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = annotatedDescription,
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                color = HomeColors.TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Causes",
                fontFamily = HomeInterFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = HomeColors.ForestMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = annotatedCauses,
                fontFamily = HomeInterFont,
                fontSize = 13.sp,
                color = HomeColors.TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun TimelineItem(
    dayNum: Int,
    task: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
    isLocked: Boolean,
    isLast: Boolean,
    onMarkDone: () -> Unit,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> HomeColors.ForestPrimary
                            isLocked -> HomeColors.PlaceholderBg
                            else -> HomeColors.SurfaceWhite
                        }
                    )
                    .border(
                        width = if (isCurrent || isCompleted) 2.dp else 1.dp,
                        color = if (isCurrent || isCompleted) HomeColors.ForestPrimary else HomeColors.CardBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCompleted -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    isLocked -> Icon(Icons.Default.Lock, contentDescription = null, tint = HomeColors.TextMuted, modifier = Modifier.size(15.dp))
                    isCurrent -> Text("$dayNum", fontFamily = HomeInterFont, fontWeight = FontWeight.Bold, color = HomeColors.ForestPrimary, fontSize = 13.sp)
                    else -> Text("$dayNum", fontFamily = HomeInterFont, fontWeight = FontWeight.Bold, color = HomeColors.TextMuted, fontSize = 13.sp)
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(
                            if (isCompleted) HomeColors.ForestPrimary.copy(alpha = 0.3f)
                            else HomeColors.DividerLine
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 14.dp)
        ) {
            val context = LocalContext.current
            if (isCurrent) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = HomeColors.SurfaceWhite),
                    border = BorderStroke(1.5.dp, HomeColors.ForestPrimary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Surface(color = HomeColors.SoftMint, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "Day $dayNum • Today",
                                fontFamily = HomeInterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HomeColors.ForestPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = task,
                            fontFamily = HomeInterFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                            color = HomeColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                onMarkDone()
                                Toast.makeText(
                                    context,
                                    "Day $dayNum marked as completed.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HomeColors.ForestPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mark as Done",
                                color = Color.White,
                                fontFamily = HomeInterFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLocked) Color(0xFFF3F6F4) else HomeColors.SurfaceWhite
                    ),
                    border = BorderStroke(1.dp, HomeColors.CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Day $dayNum",
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    color = if (isLocked) HomeColors.TextMuted else HomeColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (isCompleted) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(color = HomeColors.SuccessBg, shape = RoundedCornerShape(50)) {
                                        Text(
                                            text = "Completed",
                                            fontFamily = HomeInterFont,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = HomeColors.SuccessGreen,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (isCompleted) {
                                Text(
                                    text = "Undo",
                                    fontFamily = HomeInterFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeColors.AlertRed,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onUndo() }
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = task,
                            fontFamily = HomeInterFont,
                            fontSize = 13.sp,
                            color = if (isLocked) HomeColors.TextMuted else HomeColors.TextPrimary,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}
