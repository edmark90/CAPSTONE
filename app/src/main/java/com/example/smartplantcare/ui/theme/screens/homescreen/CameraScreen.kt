package com.example.smartplantcare.ui.theme.screens.homescreen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smartplantcare.ML.DiseaseRepository
import com.example.smartplantcare.ML.ImageProcessor
import com.example.smartplantcare.ML.InferencePipeline
import com.example.smartplantcare.data.ClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

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
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var scanError by remember { mutableStateOf<String?>(null) }
    var scanningBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showScanningAnimation by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraControl?.enableTorch(false)
            } catch (_: Exception) {
            }
            cameraExecutor.shutdown()
        }
    }

    fun processAndNavigate(bitmap: Bitmap) {
        scanningBitmap = bitmap
        showScanningAnimation = true
        coroutineScope.launch {
            try {
                val pipelineResult = withContext(Dispatchers.Default) {
                    val pipeline = InferencePipeline.getInstance(context)
                    pipeline.runPipeline(bitmap, rotationDegrees = 0)
                }

                handlePipelineResult(
                    pipelineResult = pipelineResult,
                    bitmap = bitmap,
                    rotationDegrees = 0,
                    onAnalyzePhoto = onAnalyzePhoto,
                    onError = { scanError = it },
                    onFinishScanning = {
                        showScanningAnimation = false
                        scanningBitmap = null
                        isCapturing = false
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("CameraScreen", "Processing error", e)
                scanError = "Error: ${e.message}"
                showScanningAnimation = false
                scanningBitmap = null
                isCapturing = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isCapturing = true
            coroutineScope.launch {
                try {
                    val cropped = withContext(Dispatchers.IO) {
                        val loaded = ImageProcessor.loadBitmapFromUri(context, selectedUri)
                        val sq = ImageProcessor.centerCropSquare(loaded)
                        downscaleForDisplay(sq, 1080)
                    }
                    processAndNavigate(cropped)
                } catch (e: Exception) {
                    isCapturing = false
                    scanError = "Error loading image: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showScanningAnimation) {
        DiseaseScanningAnimationScreen(bitmap = scanningBitmap)
        return
    }

    val configuration = LocalConfiguration.current
    val boxSize = if (configuration.screenHeightDp < 640) 210.dp else 260.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lifecycleOwner = lifecycleOwner,
                imageCapture = imageCapture,
                onCameraBound = { camera ->
                    cameraControl = camera.cameraControl
                    hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                }
            )
            // Centered cutout overlay framing the exact square scan zone
            ViewfinderCutoutOverlay(boxSize = boxSize)
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Camera Access Needed",
                    fontFamily = HomeInterFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Allow camera permission or pick a leaf photo from your gallery.",
                    fontFamily = HomeInterFont,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomeColors.ForestMedium,
                        contentColor = Color.White
                    )
                ) {
                    Text("Allow Camera", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Top & Bottom subtle gradient scrims so HUD controls stay readable in bright sunlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
        )

        // Centered Scan Brackets — aligned 1:1 with ViewfinderCutoutOverlay(boxSize)
        if (hasCameraPermission) {
            StaticScanFrame(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(boxSize)
            )
        }

        // Top Bar: Close + AI Mode Pill + Supported Crop Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.52f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(HomeColors.MintGlow)
                        )
                        Text(
                            text = "AI Leaf Classifier",
                            fontFamily = HomeInterFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.size(44.dp))
            }

            // Supported Crop Pills
            Surface(
                color = Color.Black.copy(alpha = 0.42f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Eco,
                        contentDescription = null,
                        tint = HomeColors.MintGlow,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Tomato  •  Chili  •  Eggplant",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
        }

        // Bottom HUD: Guidance Pill + Controls Bar (never shifts the centered viewfinder)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.56f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Center a single leaf inside the square",
                        fontFamily = HomeInterFont,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hold steady 10–15 cm away  •  Pinch to zoom",
                        fontFamily = HomeInterFont,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !showScanningAnimation && !isCapturing,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.48f))
                            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Gallery",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                // Shutter CTA
                val shutterInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(2.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .clickable(
                            interactionSource = shutterInteraction,
                            indication = null,
                            enabled = hasCameraPermission && !showScanningAnimation && !isCapturing
                        ) {
                            isCapturing = true
                            imageCapture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        coroutineScope.launch {
                                            try {
                                                val prepared = withContext(Dispatchers.Default) {
                                                    val rotationDegrees = image.imageInfo.rotationDegrees
                                                    val rawBitmap = image.toBitmap()
                                                    image.close()
                                                    val argb = rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                                    if (argb.width == 0 || argb.height == 0) {
                                                        throw IllegalStateException("Invalid image dimensions")
                                                    }
                                                    val oriented = ImageProcessor.orientBitmap(argb, rotationDegrees)
                                                    val cropped = ImageProcessor.centerCropSquare(oriented)
                                                    downscaleForDisplay(cropped, 1080)
                                                }
                                                processAndNavigate(prepared)
                                            } catch (e: Exception) {
                                                try {
                                                    image.close()
                                                } catch (_: Exception) {
                                                }
                                                android.util.Log.e("CameraScreen", "Error processing capture", e)
                                                scanError = "Failed to process capture"
                                                isCapturing = false
                                            }
                                        }
                                    }

                                    override fun onError(e: ImageCaptureException) {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            android.util.Log.e("CameraScreen", "Capture error", e)
                                            scanError = "Capture failed: ${e.message}"
                                            showScanningAnimation = false
                                            isCapturing = false
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(HomeColors.ForestMedium, HomeColors.ForestPrimary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Capture",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (hasFlashUnit) {
                                isFlashOn = !isFlashOn
                                cameraControl?.enableTorch(isFlashOn)
                            }
                        },
                        enabled = hasFlashUnit && !showScanningAnimation && !isCapturing,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFlashOn) Color(0xFFFFEB3B).copy(alpha = 0.22f)
                                else Color.Black.copy(alpha = 0.48f)
                            )
                            .border(
                                1.dp,
                                if (isFlashOn) Color(0xFFFFEB3B).copy(alpha = 0.7f)
                                else Color.White.copy(alpha = 0.24f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = when {
                                !hasFlashUnit -> Color.White.copy(alpha = 0.35f)
                                isFlashOn -> Color(0xFFFFEB3B)
                                else -> Color.White
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = if (isFlashOn) "Flash On" else "Flash",
                        fontFamily = HomeInterFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isFlashOn) Color(0xFFFFEB3B) else Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }

    if (scanError != null) {
        AlertDialog(
            onDismissRequest = {
                scanError = null
                scanningBitmap = null
                showScanningAnimation = false
                isCapturing = false
            },
            title = {
                Text(
                    text = "Analysis Failed",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(scanError.orEmpty(), fontFamily = HomeInterFont) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scanError = null
                        scanningBitmap = null
                        showScanningAnimation = false
                        isCapturing = false
                    }
                ) {
                    Text("Try Again", color = HomeColors.ForestPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(22.dp)
        )
    }
}

private fun downscaleForDisplay(bitmap: Bitmap, maxSize: Int): Bitmap {
    if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap
    val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
    val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
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
        val diseaseInfo = withContext(Dispatchers.IO) {
            DiseaseRepository.getDiseaseInfo(prediction.className)
        }
        onAnalyzePhoto(bitmap, rotationDegrees, prediction, diseaseInfo, classification)
        onFinishScanning()
    } else {
        onError(pipelineResult.message)
        onFinishScanning()
    }
}

@Composable
private fun ViewfinderCutoutOverlay(boxSize: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boxPx = boxSize.toPx()
        val left = (size.width - boxPx) / 2f
        val top = (size.height - boxPx) / 2f
        val cornerPx = 22.dp.toPx()

        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + boxPx, top + boxPx),
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
            )
        }
        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun StaticScanFrame(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val cornerLength = 34.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val cornerColor = HomeColors.MintGlow
        val w = size.width
        val h = size.height

        drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth, cap = StrokeCap.Round)

        drawLine(cornerColor, Offset(w, 0f), Offset(w - cornerLength, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(w, 0f), Offset(w, cornerLength), strokeWidth, cap = StrokeCap.Round)

        drawLine(cornerColor, Offset(0f, h), Offset(cornerLength, h), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, h), Offset(0f, h - cornerLength), strokeWidth, cap = StrokeCap.Round)

        drawLine(cornerColor, Offset(w, h), Offset(w - cornerLength, h), strokeWidth, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(w, h), Offset(w, h - cornerLength), strokeWidth, cap = StrokeCap.Round)
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    imageCapture: ImageCapture,
    onCameraBound: (Camera) -> Unit
) {
    val context = LocalContext.current
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )

                        val scaleDetector = ScaleGestureDetector(
                            ctx,
                            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                override fun onScale(detector: ScaleGestureDetector): Boolean {
                                    val ratio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                                    camera.cameraControl.setZoomRatio(ratio * detector.scaleFactor)
                                    return true
                                }
                            }
                        )
                        previewView.setOnTouchListener { v, event ->
                            scaleDetector.onTouchEvent(event)
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                v.performClick()
                            }
                            true
                        }
                        onCameraBound(camera)
                    } catch (e: Exception) {
                        android.util.Log.e("CameraPreview", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

private val DiseaseBgTop = Color(0xFF13221A)
private val DiseaseBgBottom = Color(0xFF0A130E)
private val DiseaseFramePanel = Color(0xFF1B3324)
private val DiseaseAccentGreen = Color(0xFF34D399)
private val DiseaseAccentGreenDim = Color(0xFF16A34A)

@Composable
private fun DiseaseScanningAnimationScreen(
    bitmap: Bitmap?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disease_scan_transition")

    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "disease_scan_line"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
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
    val messages = remember {
        listOf(
            "Analyzing leaf...",
            "Detecting disease...",
            "Checking symptoms...",
            "Almost done..."
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(DiseaseBgTop, DiseaseBgBottom))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(290.dp)
                        .graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                        }
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

                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DiseaseFramePanel),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        key(bitmap) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Plant to Analyze",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.15f))
                        )
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cornerRadiusPx = 24.dp.toPx()
                        val bracketLen = size.width * 0.14f
                        val strokeW = 3.dp.toPx()

                        drawRoundRect(
                            color = DiseaseAccentGreen.copy(alpha = 0.2f),
                            cornerRadius = CornerRadius(cornerRadiusPx),
                            style = Stroke(width = strokeW)
                        )

                        val c = DiseaseAccentGreen.copy(alpha = pulseAlpha)
                        val w = size.width
                        val h = size.height
                        val inset = strokeW / 2

                        drawLine(c, Offset(inset, inset + bracketLen), Offset(inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, inset), Offset(inset + bracketLen, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset - bracketLen, inset), Offset(w - inset, inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, inset), Offset(w - inset, inset + bracketLen), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, h - inset - bracketLen), Offset(inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(inset, h - inset), Offset(inset + bracketLen, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset - bracketLen, h - inset), Offset(w - inset, h - inset), strokeWidth = strokeW)
                        drawLine(c, Offset(w - inset, h - inset - bracketLen), Offset(w - inset, h - inset), strokeWidth = strokeW)

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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = messages[messageIndex],
                    fontFamily = HomeInterFont,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Please wait while we analyze your plant",
                    fontFamily = HomeInterFont,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.indices.forEach { index ->
                    val active = index == messageIndex
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) DiseaseAccentGreen else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}
