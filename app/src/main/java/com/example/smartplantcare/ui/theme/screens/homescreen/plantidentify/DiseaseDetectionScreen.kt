package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeColors
import com.example.smartplantcare.ui.theme.screens.homescreen.HomeInterFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val SemiTransparentBlack = Color.Black.copy(alpha = 0.48f)

@Composable
fun DiseaseDetectionScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val viewModel: DiseaseDetectionViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DiseaseDetectionViewModel::class.java)) {
                    return DiseaseDetectionViewModel(context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )
    val uiState = viewModel.uiState
    var lastCapturedFile by remember { mutableStateOf<File?>(null) }

    when (uiState) {
        is DiseaseDetectionUiState.Idle -> {
            DiseaseCameraScreen(
                onImageReady = { file ->
                    lastCapturedFile = file
                    viewModel.detectDisease(file)
                },
                onBack = onBack
            )
        }
        is DiseaseDetectionUiState.Scanning -> {
            ScanningAnimationScreen(imageFile = lastCapturedFile)
        }
        is DiseaseDetectionUiState.Result -> {
            DiseaseResultScreen(
                detectionResult = uiState.detectionResult,
                onCaptureAnother = { viewModel.resetToIdle() },
                onBack = onBack
            )
        }
        is DiseaseDetectionUiState.Error -> {
            ErrorScreen(
                message = uiState.message,
                onRetry = { viewModel.resetToIdle() },
                onBack = onBack
            )
        }
    }
}

@Composable
fun DiseaseCameraScreen(onImageReady: (File) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        permissionRequested = true
        if (!isGranted) {
            val isPermanentlyDenied = activity == null ||
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            if (isPermanentlyDenied) {
                showSettingsDialog = true
            } else {
                onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isCapturing = true
            coroutineScope.launch(Dispatchers.IO) {
                val file = uriToFile(context, it)
                withContext(Dispatchers.Main) {
                    isCapturing = false
                    if (file != null) onImageReady(file)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                camera?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {
            }
            cameraExecutor.shutdown()
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Camera Permission Required",
                    fontFamily = HomeInterFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This feature requires Camera permission to identify plants and diseases. Please enable Camera access from your device settings.",
                    fontFamily = HomeInterFont
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HomeColors.ForestPrimary)
                ) {
                    Text("Open Settings", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSettingsDialog = false
                        onBack()
                    }
                ) {
                    Text("Cancel", color = HomeColors.TextSecondary)
                }
            },
            containerColor = HomeColors.SurfaceWhite,
            shape = RoundedCornerShape(22.dp)
        )
        return
    }

    if (!hasCameraPermission && !showSettingsDialog) {
        LaunchedEffect(permissionRequested) {
            if (permissionRequested) {
                onBack()
            }
        }
        return
    }

    val configuration = LocalConfiguration.current
    val overlaySize = if (configuration.screenHeightDp < 640) 210.dp else 260.dp
    val hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidViewCameraPreview(
            onCameraReady = { cam, capture ->
                camera = cam
                imageCapture = capture
            },
            lifecycleOwner = lifecycleOwner
        )

        DiseaseCutoutOverlay(boxSize = overlaySize)
        ScanViewfinderOverlay(
            overlaySize = overlaySize,
            modifier = Modifier.align(Alignment.Center)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack
            )

            Surface(
                color = SemiTransparentBlack,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Leaf Disease Detection",
                    fontFamily = HomeInterFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            Spacer(modifier = Modifier.size(44.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp, start = 28.dp, end = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                color = SemiTransparentBlack,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Center the leaf inside the frame · Pinch to zoom",
                    fontFamily = HomeInterFont,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    icon = Icons.Filled.PhotoLibrary,
                    contentDescription = "Upload from gallery",
                    size = 50.dp,
                    enabled = !isCapturing,
                    onClick = { galleryLauncher.launch("image/*") }
                )

                ShutterButton(
                    isCapturing = isCapturing,
                    onClick = {
                        if (!isCapturing) {
                            isCapturing = true
                            captureImage(
                                context = context,
                                imageCapture = imageCapture,
                                executor = cameraExecutor,
                                onImageSaved = { file ->
                                    isCapturing = false
                                    onImageReady(file)
                                },
                                onCaptureError = {
                                    isCapturing = false
                                }
                            )
                        }
                    }
                )

                CircleIconButton(
                    icon = if (isFlashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Toggle flash",
                    size = 50.dp,
                    enabled = hasFlashUnit && !isCapturing,
                    iconTint = when {
                        !hasFlashUnit -> Color.White.copy(alpha = 0.35f)
                        isFlashOn -> Color(0xFFFFEB3B)
                        else -> Color.White
                    },
                    onClick = {
                        if (hasFlashUnit) {
                            isFlashOn = !isFlashOn
                            camera?.cameraControl?.enableTorch(isFlashOn)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isCapturing,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(HomeColors.ForestPrimary),
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
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Capture Photo",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    iconTint: Color = Color.White,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(SemiTransparentBlack)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = iconTint)
    }
}

@Composable
private fun DiseaseCutoutOverlay(boxSize: Dp) {
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
private fun ScanViewfinderOverlay(
    overlaySize: Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(overlaySize)) {
        val cornerLength = 34.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val color = HomeColors.MintGlow
        val w = size.width
        val h = size.height

        drawLine(color, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(w - cornerLength, 0f), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(w, cornerLength), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, h), Offset(cornerLength, h), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, h), Offset(0f, h - cornerLength), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(w, h), Offset(w - cornerLength, h), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(w, h), Offset(w, h - cornerLength), strokeWidth, cap = StrokeCap.Round)
    }
}

@Composable
private fun AndroidViewCameraPreview(
    onCameraReady: (Camera, ImageCapture) -> Unit,
    lifecycleOwner: LifecycleOwner
) {
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
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                    val scaleGestureDetector = ScaleGestureDetector(
                        ctx,
                        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                val zoomState = cam.cameraInfo.zoomState.value
                                cam.cameraControl.setZoomRatio((zoomState?.zoomRatio ?: 1f) * detector.scaleFactor)
                                return true
                            }
                        }
                    )
                    previewView.setOnTouchListener { v, event ->
                        scaleGestureDetector.onTouchEvent(event)
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        true
                    }
                    onCameraReady(cam, imageCapture)
                } catch (e: Exception) {
                    android.util.Log.e("DiseaseCamera", "Camera init failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    onImageSaved: (File) -> Unit,
    onCaptureError: () -> Unit = {}
) {
    val capture = imageCapture ?: run {
        onCaptureError()
        return
    }
    val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + "_disease.jpg"
    val outputFile = File(context.cacheDir, fileName)
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    capture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                mainExecutor.execute { onImageSaved(outputFile) }
            }

            override fun onError(exception: ImageCaptureException) {
                android.util.Log.e("DiseaseCamera", "Image capture error", exception)
                mainExecutor.execute { onCaptureError() }
            }
        }
    )
}

private suspend fun uriToFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "upload_" + System.currentTimeMillis() + "_disease.jpg"
        val outputFile = File(context.cacheDir, fileName)
        FileOutputStream(outputFile).use { output -> inputStream.copyTo(output) }
        inputStream.close()
        outputFile
    } catch (e: Exception) {
        android.util.Log.e("DiseaseCamera", "Error converting URI to file", e)
        null
    }
}
