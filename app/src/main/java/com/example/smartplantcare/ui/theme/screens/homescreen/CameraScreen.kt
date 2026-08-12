package com.example.smartplantcare.ui.theme.screens.homescreen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.example.smartplantcare.ML.DiseaseRepository
import com.example.smartplantcare.ML.ImageProcessor
import com.example.smartplantcare.ML.InferencePipeline
import com.example.smartplantcare.data.ClassificationResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.smartplantcare.ui.theme.DarkGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    onBackClick: () -> Unit = {},
    onAnalyzePhoto: suspend (
        Bitmap,
        Int,
        com.example.smartplantcare.data.PredictionResult.PredictionResult?,
        com.example.smartplantcare.data.DiseaseResult?,
        ClassificationResult?
    ) -> Boolean = { _, _, _, _, _ -> true }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var scanError by remember { mutableStateOf<String?>(null) }
    var scanningBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showScanningAnimation by remember { mutableStateOf(false) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    fun processAndNavigate(bitmap: Bitmap) {
        scanningBitmap = bitmap
        showScanningAnimation = true
        coroutineScope.launch {
            try {
                android.util.Log.d("CameraScreen", "Running pipeline")
                val pipeline = InferencePipeline.getInstance(context)
                val pipelineResult = pipeline.runPipeline(bitmap, rotationDegrees = 0)
                android.util.Log.d("CameraScreen", "Pipeline completed: ${pipelineResult.isSuccess}")

                handlePipelineResult(
                    pipelineResult = pipelineResult,
                    bitmap = bitmap,
                    rotationDegrees = 0,
                    onAnalyzePhoto = onAnalyzePhoto,
                    onError = { scanError = it },
                    onFinishScanning = {
                        showScanningAnimation = false
                        scanningBitmap = null
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("CameraScreen", "Processing error", e)
                scanError = "Error: ${e.message}"
                showScanningAnimation = false
                scanningBitmap = null
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            coroutineScope.launch {
                try {
                    val bitmap = ImageProcessor.loadBitmapFromUri(context, selectedUri)
                    processAndNavigate(bitmap)
                } catch (e: Exception) {
                    scanError = "Error loading image: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Show scanning animation screen immediately when user taps capture
    if (showScanningAnimation) {
        DiseaseScanningAnimationScreen(
            bitmap = scanningBitmap,
            onScanComplete = {
                // Navigation handled by processAndNavigate
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1. Live Camera Preview Background
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lifecycleOwner = lifecycleOwner,
                imageCapture = imageCapture,
                onCameraBound = { control -> cameraControl = control }
            )
        }

        // 2. Standard cinematic dimming overlay habang nag-aabang ng live viewfinder target
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.45f), Color.Transparent, Color.Black.copy(0.55f))
                    )
                )
        )

        // Top-Left Close Navigation Control Handle
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(20.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.35f))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Central Processing Architecture Block (Ang Box kung saan nakapaloob ang layout square container)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val configuration = LocalConfiguration.current
            val boxSize = if (configuration.screenHeightDp < 640) 200.dp else 260.dp
            Box(
                modifier = Modifier.size(boxSize),
                contentAlignment = Alignment.Center
            ) {
                // Static viewfinder corners — scan animation handled by DiseaseScanningAnimationScreen
                ScanFrame(
                    modifier = Modifier.matchParentSize(),
                    isScanning = false,
                    hasImage = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Note below the box with a semi-transparent background for readability
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan only Tomato, Chili, or Eggplant leaves for accurate disease detection",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hold phone 10–15 cm away for best results",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }


        }

        // Bottom Dashboard Core Interaction Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Gallery Button
            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !showScanningAnimation,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.15f))
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White
                )
            }

            // Capture Trigger Shutter Module
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable(enabled = !showScanningAnimation) {
                        imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val rawBitmap = image.toBitmap()
                                    image.close()

                                    if (rawBitmap == null) {
                                        scanError = "Failed to capture image"
                                        return
                                    }

                                    val bitmap = rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    if (bitmap.width == 0 || bitmap.height == 0) {
                                        scanError = "Invalid image dimensions"
                                        return
                                    }

                                    android.util.Log.d("CameraScreen", "Bitmap captured: ${bitmap.width}x${bitmap.height}")

                                    // Set bitmap BEFORE showing animation so image appears immediately
                                    scanningBitmap = bitmap
                                    processAndNavigate(bitmap)

                                } catch (e: Exception) {
                                    image.close()
                                    android.util.Log.e("CameraScreen", "Error processing capture", e)
                                    scanError = "Failed to process capture"
                                }
                            }

                            override fun onError(e: ImageCaptureException) {
                                android.util.Log.e("CameraScreen", "Capture error", e)
                                scanError = "Capture failed: ${e.message}"
                                showScanningAnimation = false
                            }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(DarkGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = "Capture",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Flashlight Toggle System
            IconButton(
                onClick = {
                    isFlashOn = !isFlashOn
                    cameraControl?.enableTorch(isFlashOn)
                },
                enabled = !showScanningAnimation,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.15f))
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (isFlashOn) Color(0xFFFFEB3B) else Color.White
                )
            }
        }
    }

    if (scanError != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                scanError = null
                scanningBitmap = null
                showScanningAnimation = false
            },
            title = { Text("Analysis Failed") },
            text = { Text(scanError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    scanError = null
                    scanningBitmap = null
                    showScanningAnimation = false
                }) {
                    Text("Try Again")
                }
            }
        )
    }
}

private suspend fun handlePipelineResult(
    pipelineResult: InferencePipeline.PipelineResult,
    bitmap: Bitmap?,
    rotationDegrees: Int,
    onAnalyzePhoto: suspend (
        Bitmap,
        Int,
        com.example.smartplantcare.data.PredictionResult.PredictionResult?,
        com.example.smartplantcare.data.DiseaseResult?,
        ClassificationResult?
    ) -> Boolean,
    onError: (String) -> Unit,
    onFinishScanning: () -> Unit
) {
    val prediction = pipelineResult.predictionResult
    val classification = pipelineResult.classification
    if (pipelineResult.isSuccess && prediction != null && classification != null && bitmap != null) {
        val diseaseInfo = DiseaseRepository.getDiseaseInfo(prediction.className)
        onAnalyzePhoto(bitmap, rotationDegrees, prediction, diseaseInfo, classification)
        onFinishScanning()
    } else {
        onError(pipelineResult.message)
        onFinishScanning()
    }
}

@Composable
private fun ScanFrame(modifier: Modifier, isScanning: Boolean, hasImage: Boolean) {
    val alpha by rememberInfiniteTransition(label = "frameAlpha").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if(isScanning) 500 else 1100),
            RepeatMode.Reverse
        ),
        label = "frameAlpha"
    )

    val scanLinePosition by rememberInfiniteTransition(label = "scanLine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    Canvas(modifier = modifier) {
        val cornerLength = 36.dp.toPx()
        val strokeWidth = 5.dp.toPx() // Bahagyang nirebisa para mas makapal at kitang-kita

        // Kapag may larawan na, gawing mas matingkad na berde o puti ang corners para umangat sa puting matte background
        val cornerColor = if (hasImage) Color(0xFF1B5E20) else Color.White.copy(alpha = alpha)

        // Top-Left
        drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth, cap = StrokeCap.Round)

        // Top-Right
        drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width - cornerLength, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth, cap = StrokeCap.Round)

        // Bottom-Left
        drawLine(cornerColor, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, size.height), Offset(0f, size.height - cornerLength), strokeWidth, cap = StrokeCap.Round)

        // Bottom-Right
        drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width - cornerLength, size.height), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width, size.height - cornerLength), strokeWidth, cap = StrokeCap.Round)

        if (isScanning) {
            val lineY = size.height * scanLinePosition
            val scanColor = Color(0xFF2E7D32)

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, scanColor.copy(alpha = 0.35f)),
                    startY = maxOf(0f, lineY - 40.dp.toPx()),
                    endY = lineY
                ),
                topLeft = Offset(0f, maxOf(0f, lineY - 40.dp.toPx())),
                size = Size(size.width, minOf(40.dp.toPx(), lineY))
            )

            drawLine(
                color = scanColor,
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    imageCapture: ImageCapture,
    onCameraBound: (CameraControl) -> Unit
) {
    AndroidView(modifier = modifier, factory = { ctx ->
        PreviewView(ctx).also { previewView ->
            ProcessCameraProvider.getInstance(ctx).addListener({
                val provider = ProcessCameraProvider.getInstance(ctx).get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()

                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                onCameraBound(camera.cameraControl)

            }, ContextCompat.getMainExecutor(ctx))
        }
    })
}

// Palette for disease scanning animation
private val DiseaseBgTop = Color(0xFF13221A)
private val DiseaseBgBottom = Color(0xFF0A130E)
private val DiseaseFramePanel = Color(0xFF1B3324)
private val DiseaseAccentGreen = Color(0xFF34D399)
private val DiseaseAccentGreenDim = Color(0xFF16A34A)

@Composable
private fun DiseaseScanningAnimationScreen(
    bitmap: Bitmap?,
    onScanComplete: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disease_scan_transition")

    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "disease_scan_line"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "disease_pulse"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "disease_glow"
    )

    var messageIndex by remember { mutableStateOf(0) }
    val messages = listOf(
        "Analyzing leaf...",
        "Detecting disease...",
        "Checking symptoms...",
        "Almost done..."
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DiseaseBgTop, DiseaseBgBottom)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {

            // ---- Ambient glow behind the frame ----
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(300.dp)
                        .scale(glowScale)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                DiseaseAccentGreenDim.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }

                // ---- Scan frame with moving line and image render layer ----
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DiseaseFramePanel),
                    contentAlignment = Alignment.Center
                ) {
                    // Render the captured bitmap
                    android.util.Log.d("DiseaseScanningAnimationScreen", "Bitmap check: ${bitmap?.width}x${bitmap?.height}")
                    if (bitmap != null) {
                        key(bitmap) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Plant to Analyze",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // Subtle dark overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.15f))
                        )
                    }

                    // Laser scan line dynamic animation overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cornerRadiusPx = 24.dp.toPx()
                        val bracketLen = size.width * 0.14f
                        val strokeW = 3.dp.toPx()

                        // Soft outer border
                        drawRoundRect(
                            color = DiseaseAccentGreen.copy(alpha = 0.18f),
                            cornerRadius = CornerRadius(cornerRadiusPx),
                            style = Stroke(width = strokeW)
                        )

                        // Four corner brackets, viewfinder style, pulsing
                        val c = DiseaseAccentGreen.copy(alpha = pulseAlpha)
                        val w = size.width
                        val h = size.height
                        val inset = strokeW / 2

                        // top-left
                        drawLine(c, Offset(inset, inset + bracketLen), Offset(inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, inset), Offset(inset + bracketLen, inset), strokeWidth = strokeW)
                        // top-right
                        drawLine(c, Offset(w - inset - bracketLen, inset), Offset(w - inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, inset), Offset(w - inset, inset + bracketLen), strokeWidth = strokeW)
                        // bottom-left
                        drawLine(c, Offset(inset, h - inset - bracketLen), Offset(inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, h - inset), Offset(inset + bracketLen, h - inset), strokeWidth = strokeW)
                        // bottom-right
                        drawLine(c, Offset(w - inset - bracketLen, h - inset), Offset(w - inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, h - inset - bracketLen), Offset(w - inset, h - inset), strokeWidth = strokeW)

                        // Moving sweep line
                        val lineY = size.height * scanLinePosition
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    DiseaseAccentGreen.copy(alpha = 0f),
                                    DiseaseAccentGreen,
                                    DiseaseAccentGreen.copy(alpha = 0f)
                                )
                            ),
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                }
            }

            Text(
                text = messages[messageIndex],
                color = Color.White.copy(alpha = pulseAlpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Please wait while we analyze your plant",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                style = MaterialTheme.typography.bodySmall
            )

            // Progress dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.indices.forEach { index ->
                    val active = index == messageIndex
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) DiseaseAccentGreen else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }
        }
    }
}