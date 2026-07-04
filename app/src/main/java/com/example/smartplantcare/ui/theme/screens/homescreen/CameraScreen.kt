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
import com.example.smartplantcare.ML.InferencePipeline
import com.example.smartplantcare.ML.DiseaseRepository
import com.example.smartplantcare.ML.ImageProcessor
import com.example.smartplantcare.data.ClassificationResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.smartplantcare.ui.theme.DarkGreen
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
    
    // Use singleton pattern - initialize once globally, not during composition
    val isPipelineReady = remember { InferencePipeline.isInitialized() }

    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        android.util.Log.d("CameraScreen", "Gallery launcher called with URI: $uri")
        uri?.let { selectedUri ->
            android.util.Log.d("CameraScreen", "Selected URI: $selectedUri")
            if (!isPipelineReady) {
                android.util.Log.e("CameraScreen", "Pipeline not ready")
                scanError = "ML model not initialized. Please restart the app."
                return@let
            }
            coroutineScope.launch {
                try {
                    android.util.Log.d("CameraScreen", "Starting gallery upload")
                    isScanning = true
                    android.util.Log.d("CameraScreen", "Loading bitmap from URI")
                    val bitmap = ImageProcessor.loadBitmapFromUri(context, selectedUri)
                    android.util.Log.d("CameraScreen", "Bitmap loaded: ${bitmap.width}x${bitmap.height}")
                    android.util.Log.d("CameraScreen", "Running pipeline")
                    val pipelineResult = InferencePipeline.getInstance(context).runPipeline(bitmap, rotationDegrees = 0)
                    android.util.Log.d("CameraScreen", "Pipeline completed: ${pipelineResult.isSuccess}")
                    handlePipelineResult(
                        pipelineResult = pipelineResult,
                        bitmap = bitmap,
                        rotationDegrees = 0,
                        onAnalyzePhoto = onAnalyzePhoto,
                        onError = { scanError = it },
                        onFinishScanning = { isScanning = false }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CameraScreen", "Gallery upload error", e)
                    e.printStackTrace()
                    scanError = "Error: ${e.message}"
                    isScanning = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreview(modifier = Modifier.fillMaxSize(), lifecycleOwner = lifecycleOwner, imageCapture = imageCapture)
        }

        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.45f), Color.Transparent, Color.Black.copy(0.55f)))))

        IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.TopStart).padding(20.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(0.35f))) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            ScanFrame(modifier = Modifier.size(260.dp), isScanning = isScanning)
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = if (isScanning) "Analyzing leaf..." else "Place the plant in focus", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {

            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !isScanning,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.15f))
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
            }

            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape).background(if (isScanning) Color.Gray else Color.White).padding(5.dp).clip(CircleShape).background(if (isScanning) Color.DarkGray else DarkGreen).clickable(enabled = !isScanning) {
                    android.util.Log.d("CameraScreen", "Camera button clicked")
                    if (!isPipelineReady) {
                        android.util.Log.e("CameraScreen", "Pipeline not ready")
                        scanError = "ML model not initialized. Please restart the app."
                        return@clickable
                    }
                    android.util.Log.d("CameraScreen", "Starting camera capture")
                    isScanning = true
                    imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            android.util.Log.d("CameraScreen", "Image captured successfully")
                            try {
                                // ImageCapture delivers JPEG — toBitmap() decodes safely (YUV path crashed).
                                val rawBitmap = image.toBitmap()
                                image.close()
                                
                                android.util.Log.d("CameraScreen", "Bitmap decoded: ${rawBitmap?.width}x${rawBitmap?.height}")
                                
                                if (rawBitmap == null) {
                                    android.util.Log.e("CameraScreen", "Bitmap is null")
                                    scanError = "Failed to capture image"
                                    isScanning = false
                                    return
                                }
                                
                                val bitmap = try {
                                    rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                } catch (e: Exception) {
                                    android.util.Log.e("CameraScreen", "Failed to copy bitmap", e)
                                    e.printStackTrace()
                                    scanError = "Failed to process image"
                                    isScanning = false
                                    return
                                }
                                
                                android.util.Log.d("CameraScreen", "Bitmap copied: ${bitmap.width}x${bitmap.height}")
                                
                                if (bitmap.width == 0 || bitmap.height == 0) {
                                    android.util.Log.e("CameraScreen", "Invalid bitmap dimensions")
                                    scanError = "Invalid image dimensions"
                                    isScanning = false
                                    return
                                }
                                
                                coroutineScope.launch {
                                    try {
                                        android.util.Log.d("CameraScreen", "Running inference pipeline")
                                        val pipelineResult = InferencePipeline.getInstance(context).runPipeline(bitmap, rotationDegrees = 0)
                                        android.util.Log.d("CameraScreen", "Pipeline result: ${pipelineResult.isSuccess}")
                                        handlePipelineResult(
                                            pipelineResult = pipelineResult,
                                            bitmap = bitmap,
                                            rotationDegrees = 0,
                                            onAnalyzePhoto = onAnalyzePhoto,
                                            onError = { scanError = it },
                                            onFinishScanning = { isScanning = false }
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("CameraScreen", "Camera capture error", e)
                                        e.printStackTrace()
                                        scanError = "Error: ${e.message}"
                                        isScanning = false
                                    }
                                }
                            } catch (e: Exception) {
                                image.close()
                                android.util.Log.e("CameraScreen", "Failed to decode capture", e)
                                e.printStackTrace()
                                scanError = "Error: ${e.message}"
                                isScanning = false
                            }
                        }

                        override fun onError(e: ImageCaptureException) {
                            android.util.Log.e("CameraScreen", "Capture failed", e)
                            e.printStackTrace()
                            scanError = "Capture failed: ${e.message}"
                            isScanning = false
                        }
                    })
                },
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                else Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.White)
            }

            IconButton(onClick = {}, enabled = !isScanning, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.15f))) {
                Icon(Icons.Default.FlashOn, contentDescription = "Flash", tint = Color.White)
            }
        }
    }

    if (scanError != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Analysis Failed") },
            text = { Text(scanError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { scanError = null }) {
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
    android.util.Log.d("CameraScreen", "Pipeline: ${pipelineResult.message}")
    android.util.Log.d("CameraScreen", "Debug: ${pipelineResult.debugInfo}")

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
private fun ScanFrame(modifier: Modifier, isScanning: Boolean) {
    val alpha by rememberInfiniteTransition(label = "").animateFloat(0.55f, 1f, infiniteRepeatable(tween(if(isScanning) 500 else 1100), RepeatMode.Reverse))
    Canvas(modifier = modifier) {
        val c = Color.White.copy(alpha = alpha)
        drawLine(c, Offset(0f, 0f), Offset(28.dp.toPx(), 0f), 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(c, Offset(0f, 0f), Offset(0f, 28.dp.toPx()), 4.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CameraPreview(modifier: Modifier = Modifier, lifecycleOwner: LifecycleOwner, imageCapture: ImageCapture) {
    AndroidView(modifier = modifier, factory = { ctx ->
        PreviewView(ctx).also { previewView ->
            ProcessCameraProvider.getInstance(ctx).addListener({
                val provider = ProcessCameraProvider.getInstance(ctx).get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }, ContextCompat.getMainExecutor(ctx))
        }
    })
}
