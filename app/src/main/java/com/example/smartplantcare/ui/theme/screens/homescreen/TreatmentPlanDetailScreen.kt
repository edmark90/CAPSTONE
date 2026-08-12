package com.example.smartplantcare.ui.theme.screens.homescreen

import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.smartplantcare.ML.DiseaseRepository
import com.example.smartplantcare.R
import com.example.smartplantcare.ui.theme.LightGray
import com.example.smartplantcare.ui.theme.MediumGreen
import com.example.smartplantcare.ui.theme.PrimaryGreen
import com.example.smartplantcare.ui.theme.SoftGreen
import com.example.smartplantcare.ui.theme.TextDark
import com.example.smartplantcare.ui.theme.TextLight
import com.example.smartplantcare.ui.theme.TextMedium
import java.io.File
import java.util.Locale

// ─── Design Tokens (mirrors AIDoctorScreen / SettingsScreen) ────────────────
private val PageBg        = Color(0xFFF8FAF9)
private val SurfaceWhite  = Color(0xFFFFFFFF)
private val CardBorder    = Color(0xFFE8EFEA)
private val SuccessGreen  = Color(0xFF10B981)
private val SuccessBg     = Color(0xFFECFDF5)
private val AlertRed      = Color(0xFFEF4444)
private val AlertBg       = Color(0xFFFEF2F2)
private val PlaceholderBg = Color(0xFFEFF2F1)
private val MutedIcon     = Color(0xFF9CA3AF)

private val InterFont = FontFamily(
    Font(R.font.inter_black, FontWeight.Black),
    Font(R.font.interbold, FontWeight.Bold),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_24pt_medium, FontWeight.Medium),
    Font(R.font.inter_24pt_regular, FontWeight.Normal)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentPlanDetailScreen(
    planId: String,
    viewModel: TreatmentPlanViewModel,
    onBackClick: () -> Unit
) {
    val plan by viewModel.selectedPlan.collectAsState()
    LaunchedEffect(planId) { viewModel.loadPlan(planId) }

    Scaffold(

        topBar = {

            TopAppBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 17.dp),
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp)
                    ) {
                        val currentPlan = plan
                        val isHealthy = currentPlan?.isHealthy == true
                        Text(
                            currentPlan?.plantName?.ifBlank { "Unknown Plant" } ?: "Loading...",
                            fontFamily = InterFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (currentPlan == null) TextDark else if (isHealthy) MediumGreen else AlertRed
                        )
                        Spacer(Modifier.height(2.dp))
                        val badgeColor = when {
                            currentPlan == null -> SoftGreen
                            isHealthy -> SuccessBg
                            else -> AlertBg
                        }
                        val badgeTextColor = when {
                            currentPlan == null -> MediumGreen
                            isHealthy -> SuccessGreen
                            else -> AlertRed
                        }
                        val badgeText = when {
                            currentPlan == null -> "Loading"
                            isHealthy -> "Healthy"
                            else -> "In Care"
                        }
                        Surface(color = badgeColor, shape = RoundedCornerShape(50)) {
                            Text(
                                badgeText,
                                fontSize = 10.sp,
                                color = badgeTextColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
        containerColor = PageBg
    ) { innerPadding ->
        plan?.let { p ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Image & Match Badge Section
                item { ImageHeaderSection(p.imageUrl, p.confidence) }

                // 2. Progress Overview Section
                item { ProgressSection(p.completedCount, p.computedCurrentDayIndex) }

                // 3. Disease Information Section
                item { DiseaseInformationSection(p.diseaseName, p.isHealthy) }

                // 4. AI Doctor Notes Section
                item { DoctorNotesSection() }

                // 5. Treatment Schedule Header
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Treatment Schedule",
                            fontFamily = InterFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(color = SoftGreen, shape = RoundedCornerShape(50)) {
                            Text(
                                "7 Days",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MediumGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 6. Timeline List
                val currentDayIndex = p.computedCurrentDayIndex
                val tasks = if (p.sevenDayPlan.size >= 7) p.sevenDayPlan.take(7) else p.sevenDayPlan + List(7 - p.sevenDayPlan.size) { "Upcoming treatment" }
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

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        } ?: Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = PrimaryGreen, strokeWidth = 4.dp)
        }
    }
}

@Composable
fun ImageHeaderSection(imageUrl: String, confidence: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PlaceholderBg),
        contentAlignment = Alignment.Center
    ) {
        val imageModel = remember(imageUrl) {
            if (imageUrl.isNotBlank()) {
                val file = File(imageUrl)
                if (file.exists()) Uri.fromFile(file) else imageUrl
            } else null
        }

        if (imageModel != null) {
            AsyncImage(model = imageModel, contentDescription = "Plant", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Image, contentDescription = null, tint = MutedIcon, modifier = Modifier.size(48.dp))
        }

        // Confidence Match Badge
        val confidencePercentage = ((confidence * 100).toInt())
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            color = SurfaceWhite,
            shape = RoundedCornerShape(50),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("$confidencePercentage% Match", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (isComplete) SuccessGreen else PrimaryGreen,
                    trackColor = SoftGreen,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round
                )
                Text("$percent%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isComplete) "Plan Completed" else "Day ${currentDayIndex + 1} of 7",
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isComplete) "Great job! Your treatment plan is complete."
                    else "Treatment Plan Progress",
                    fontSize = 13.sp,
                    color = TextMedium,
                    lineHeight = 18.sp
                )
            }
            if (isComplete) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SuccessBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun DoctorNotesSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SoftGreen),
        border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MediumGreen, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "AI DOCTOR NOTES",
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MediumGreen,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "The disease appears to be improving. Continue following the treatment schedule consistently. Avoid overwatering and monitor for new spots every day.",
                fontSize = 13.sp, color = TextMedium, lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun DiseaseInformationSection(diseaseName: String, isHealthy: Boolean) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var currentWordIndex by remember { mutableStateOf(-1) }
    var speakText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsInitialized by remember { mutableStateOf(false) }

    // Map disease name to repository key
    val diseaseKey = remember(diseaseName, isHealthy) {
        when {
            isHealthy -> {
                when {
                    diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Healthy_Leaf"
                    diseaseName.contains("Eggplant", ignoreCase = true) -> "Eggplant_Healthy Leaf"
                    diseaseName.contains("Tomato", ignoreCase = true) -> "Tomato_healthy"
                    else -> null
                }
            }
            diseaseName.contains("Bacterial Spot", ignoreCase = true) -> {
                when {
                    diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Bacterial_Spot"
                    diseaseName.contains("Tomato", ignoreCase = true) -> "Tomato_Bacterial_spot"
                    else -> null
                }
            }
            diseaseName.contains("Cercospora", ignoreCase = true) -> {
                when {
                    diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Cercospora_Leaf_Spot"
                    diseaseName.contains("Eggplant", ignoreCase = true) -> "Eggplant_Cercospora Leaf Spot"
                    else -> null
                }
            }
            diseaseName.contains("Leaf Curl", ignoreCase = true) || diseaseName.contains("Curl Virus", ignoreCase = true) -> {
                when {
                    diseaseName.contains("Chili", ignoreCase = true) -> "Chili_Curl_Virus"
                    diseaseName.contains("Tomato", ignoreCase = true) -> "Tomato__Tomato_YellowLeaf__Curl_Virus"
                    else -> null
                }
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

    val diseaseData = remember(diseaseKey) {
        DiseaseRepository.getDiseaseInfo(diseaseKey)
    }

    val description = diseaseData.description
    val causes = diseaseData.causes

    // Update speakText when data changes
    LaunchedEffect(description, causes) {
        speakText = "Description. $description. Causes. $causes"
    }

    // Initialize TTS with UtteranceProgressListener
    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsInitialized = true
            }
        }

        // Set up UtteranceProgressListener for word tracking
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                currentWordIndex = 0
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                currentWordIndex = -1
                isSpeaking = false
            }

            override fun onError(utteranceId: String?) {
                currentWordIndex = -1
                isSpeaking = false
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                // Calculate word index based on character position
                val fullText = speakText
                val words = fullText.split(" ")
                var charCount = 0
                var wordIndex = 0
                for (word in words) {
                    if (start >= charCount && start < charCount + word.length) {
                        currentWordIndex = wordIndex
                        break
                    }
                    charCount += word.length + 1 // +1 for space
                    wordIndex++
                }
            }
        })

        tts = textToSpeech

        onDispose {
            tts?.stop()
            tts?.shutdown()
            currentWordIndex = -1
            isSpeaking = false
            ttsInitialized = false
        }
    }

    // Create annotated string with highlighting
    val descriptionWords = description.split(" ")
    val causesWords = causes.split(" ")
    val descriptionWordOffset = 1 // "Description." is word 0

    val annotatedDescription = remember(currentWordIndex, description) {
        buildAnnotatedString {
            descriptionWords.forEachIndexed { index, word ->
                val globalIndex = index + descriptionWordOffset
                val isHighlighted = currentWordIndex == globalIndex
                if (isHighlighted) {
                    withStyle(SpanStyle(background = PrimaryGreen, color = Color.White)) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                if (index < descriptionWords.lastIndex) {
                    append(" ")
                }
            }
        }
    }

    val annotatedCauses = remember(currentWordIndex, causes) {
        buildAnnotatedString {
            causesWords.forEachIndexed { index, word ->
                val globalIndex = index + descriptionWordOffset + descriptionWords.size
                val isHighlighted = currentWordIndex == globalIndex
                if (isHighlighted) {
                    withStyle(SpanStyle(background = PrimaryGreen, color = Color.White)) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                if (index < causesWords.lastIndex) {
                    append(" ")
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isHealthy) "Plant Health" else "Disease Information",
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextDark,
                    modifier = Modifier.weight(1f)
                )

                // Speaker Button
                IconButton(
                    onClick = {
                        if (ttsInitialized && !isSpeaking) {
                            tts?.let { textToSpeech ->
                                textToSpeech.stop()
                                currentWordIndex = -1
                                val params = Bundle()
                                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tts_utterance")
                                textToSpeech.speak(speakText, TextToSpeech.QUEUE_FLUSH, params, "tts_utterance")
                            }
                        }
                    },
                    enabled = ttsInitialized && !isSpeaking,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (ttsInitialized && !isSpeaking) SoftGreen else LightGray,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Listen",
                        tint = if (ttsInitialized && !isSpeaking) PrimaryGreen else MutedIcon,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                "Description",
                fontFamily = InterFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MediumGreen
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                annotatedDescription,
                fontSize = 13.sp,
                color = TextMedium,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Causes
            Text(
                "Causes",
                fontFamily = InterFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MediumGreen
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                annotatedCauses,
                fontSize = 13.sp,
                color = TextMedium,
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
    Row(modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
    ) {
        // Left Column: Icon and Vertical Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> PrimaryGreen
                            isLocked -> Color(0xFFE9EDEB)
                            else -> SurfaceWhite
                        }
                    )
                    .border(
                        when {
                            isCurrent -> BorderStroke(2.dp, PrimaryGreen)
                            isCompleted -> BorderStroke(2.dp, PrimaryGreen)
                            else -> BorderStroke(1.dp, CardBorder)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCompleted -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    isLocked -> Icon(Icons.Default.Lock, contentDescription = null, tint = MutedIcon, modifier = Modifier.size(16.dp))
                    isCurrent -> Text("$dayNum", fontFamily = InterFont, fontWeight = FontWeight.Bold, color = PrimaryGreen, fontSize = 14.sp)
                    else -> Text("$dayNum", fontFamily = InterFont, fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(if (isCompleted) PrimaryGreen.copy(alpha = 0.25f) else Color(0xFFE3EAE6))
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Right Column: The Task Card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
        ) {
            val context = LocalContext.current
            if (isCurrent) {
                // Highlighted Card for Today
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.5.dp, PrimaryGreen),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Surface(color = SoftGreen, shape = RoundedCornerShape(50)) {
                            Text(
                                "Day $dayNum • Today",
                                fontFamily = InterFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            task,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                onMarkDone()
                                Toast.makeText(
                                    context,
                                    "Today's treatment has been marked as completed.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mark as Done", color = Color.White, fontFamily = InterFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isLocked) Color(0xFFF6F8F7) else SurfaceWhite),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Day $dayNum",
                                    fontFamily = InterFont,
                                    fontSize = 12.sp,
                                    color = if (isLocked) TextLight else TextMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isCompleted) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(color = SuccessBg, shape = RoundedCornerShape(50)) {
                                        Text(
                                            "Completed",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Undo Button shows up here when the task is completed
                            if (isCompleted) {
                                Text(
                                    text = "Undo",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertRed,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onUndo() }
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = task,
                            fontSize = 14.sp,
                            color = if (isLocked) TextLight else TextDark,
                            lineHeight = 20.sp,
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
        }
    }
}
