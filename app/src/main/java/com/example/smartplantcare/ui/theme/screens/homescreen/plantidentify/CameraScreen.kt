package com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.platform.LocalConfiguration
import com.example.smartplantcare.plantnetnetwork.PlantIdentificationUsageRepository.Companion.DAILY_LIMIT

private val PrimaryDarkGreen = Color(0xFF2D6A4F)
private val SemiTransparentBlack = Color.Black.copy(alpha = 0.35f)
private val BgTop = Color(0xFF13221A)
private val AccentGreen = Color(0xFF34D399)

@Composable
fun PlantIdentifyScreen(
    onBack             : () -> Unit = {},
    isGuest            : Boolean    = false,
    onNavigateToLogin  : () -> Unit = {},
    onNavigateToSignUp : () -> Unit = {}

) {
    val context = LocalContext.current.applicationContext
    val viewModel: PlantIdentifyViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PlantIdentifyViewModel::class.java)) {
                    return PlantIdentifyViewModel(context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    // ── Auth gate ────────────────────────────────────────────────────────────
    var showAuthDialog by remember(isGuest) { mutableStateOf(isGuest) }

    if (showAuthDialog) {
        PlantIdentificationAuthDialog(
            onSignIn = {
                onNavigateToLogin()
            },
            onCreateAccount = {
                onNavigateToSignUp()
            },
            onDismiss = {
                onBack()   // Return to previous screen; camera never opens
            }
        )
        return   // Do not render the camera behind the dialog
    }
    // ── /Auth gate ───────────────────────────────────────────────────────────

    val uiState          = viewModel.uiState
    val usage            = viewModel.usage
    var lastCapturedFile by remember { mutableStateOf<File?>(null) }

    when (uiState) {
        is IdentifyUiState.Idle -> {
            CameraScreen(
                onImageReady = { file ->
                    lastCapturedFile = file
                    viewModel.identifyPlant(file)
                },
                onBack     = onBack,
                usage      = usage,
                dailyLimit = DAILY_LIMIT
            )
        }
        is IdentifyUiState.Scanning -> {
            ScanningAnimationScreen(imageFile = lastCapturedFile)
        }
        is IdentifyUiState.Result -> {
            PlantResultScreen(
                topMatch         = uiState.topMatch,
                allResults       = uiState.allResults,
                onCaptureAnother = { viewModel.resetToIdle() },
                usage            = usage,
                dailyLimit       = DAILY_LIMIT
            )
        }
        is IdentifyUiState.Error -> {
            ErrorScreen(message = uiState.message, onRetry = { viewModel.resetToIdle() })
        }
    }
}



@Composable
fun CameraScreen(onImageReady : (File) -> Unit,
                 onBack       : () -> Unit,
                 usage        : com.example.smartplantcare.plantnetnetwork.PlantIdentificationUsage? = null,
                 dailyLimit   : Int = 20
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        permissionRequested = true
        if (!isGranted) {
            // Check if permanently denied
            val isPermanentlyDenied = activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(activity!!, Manifest.permission.CAMERA)
            if (isPermanentlyDenied) {
                showSettingsDialog = true
            } else {
                // Permission denied but not permanently - navigate back
                onBack()
            }
        }
    }
    
    // Auto-request permission on screen entry
    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val file = uriToFile(context, it)
                if (file != null) withContext(Dispatchers.Main) { onImageReady(file) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }
    
    // Show settings dialog for permanent denial
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Camera Permission Required") },
            text = { Text("This feature requires Camera permission to identify plants. Please enable Camera access from your device settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSettingsDialog = false
                    onBack()
                }) {
                    Text("Cancel")
                }
            }
        )
        return
    }
    
    // If permission is denied (not permanently), navigate back immediately
    if (!hasCameraPermission && !showSettingsDialog) {
        LaunchedEffect(Unit) {
            if (permissionRequested) {
                onBack()
            }
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidViewCameraPreview(
            onCameraReady = { cam, capture -> camera = cam; imageCapture = capture },
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor
        )
        ScanViewfinderOverlay(modifier = Modifier.align(Alignment.Center))

        usage?.let { u ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 60.dp, start = 16.dp, end = 16.dp)
            ) {
                UsageBanner(
                    usedCount  = u.identifyCount.toInt(),
                    dailyLimit = dailyLimit
                )
            }
        }
// ── /Daily Usage Banner ─────────────────────────────────────────────────────

        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
        }
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPaddingCompat().padding(bottom = 40.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(icon = Icons.Filled.PhotoLibrary, contentDescription = "Upload from gallery", size = 56.dp, onClick = { galleryLauncher.launch("image/*") })
            ShutterButton(onClick = { captureImage(context, imageCapture, cameraExecutor) { file -> onImageReady(file) } })
            CircleIconButton(
                icon = if (isFlashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Toggle flash", size = 56.dp,
                onClick = { isFlashOn = !isFlashOn; camera?.cameraControl?.enableTorch(isFlashOn) }
            )
        }
        Text(
            text = "Center the leaf or plant inside the frame", color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
        )
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).clickableNoRipple(onClick), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(PrimaryDarkGreen), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = "Capture Photo", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun CircleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, size: androidx.compose.ui.unit.Dp = 44.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(size).clip(CircleShape).background(SemiTransparentBlack)) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun ScanViewfinderOverlay(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val overlaySize = if (configuration.screenHeightDp < 640 ) 200.dp else 260.dp
    Canvas(modifier = modifier.size(overlaySize)) {
        val cornerLength = 32.dp.toPx(); val strokeWidth = 5.dp.toPx(); val w = size.width; val h = size.height
        drawLine(PrimaryDarkGreen, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth)
        drawLine(PrimaryDarkGreen, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth)
        drawLine(PrimaryDarkGreen, Offset(w, 0f), Offset(w - cornerLength, 0f), strokeWidth)
        drawLine(PrimaryDarkGreen, Offset(w, 0f), Offset(w, cornerLength), strokeWidth)
        drawLine(PrimaryDarkGreen, Offset(0f, h), Offset(cornerLength, h), strokeWidth)
        drawLine(PrimaryDarkGreen, Offset(0f, h), Offset(0f, h - cornerLength), strokeWidth)
        drawLine(PrimaryDarkGreen, Offset(w, h), Offset(w - cornerLength, h), strokeWidth)
        drawLine(PrimaryDarkGreen, Offset(w, h), Offset(w, h - cornerLength), strokeWidth)
    }
}

@Composable
private fun AndroidViewCameraPreview(onCameraReady: (Camera, ImageCapture) -> Unit, lifecycleOwner: androidx.lifecycle.LifecycleOwner, cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        val previewView = PreviewView(ctx)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val zoomState = cam.cameraInfo.zoomState.value
                        cam.cameraControl.setZoomRatio((zoomState?.zoomRatio ?: 1f) * detector.scaleFactor)
                        return true
                    }
                })
                previewView.setOnTouchListener { _, event -> scaleGestureDetector.onTouchEvent(event); true }
                onCameraReady(cam, imageCapture)
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(ctx))
        previewView
    })
}

private fun captureImage(context: Context, imageCapture: ImageCapture?, executor: ExecutorService, onImageSaved: (File) -> Unit) {
    val capture = imageCapture ?: return
    val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + "_plant.jpg"
    val outputFile = File(context.cacheDir, fileName)
    android.util.Log.d("PlantCamera", "Capturing to: ${outputFile.absolutePath}")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    capture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            android.util.Log.d("PlantCamera", "Image saved successfully: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
            onImageSaved(outputFile)
        }
        override fun onError(exception: ImageCaptureException) {
            android.util.Log.e("PlantCamera", "Image capture error", exception)
            exception.printStackTrace()
        }
    })
}

private suspend fun uriToFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "upload_" + System.currentTimeMillis() + "_plant.jpg"
        val outputFile = File(context.cacheDir, fileName)
        FileOutputStream(outputFile).use { output -> inputStream.copyTo(output) }
        inputStream.close()
        android.util.Log.d("PlantCamera", "Upload image saved: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
        outputFile
    } catch (e: Exception) {
        android.util.Log.e("PlantCamera", "Error converting URI to file", e)
        null
    }
}

@Composable
private fun Modifier.navigationBarsPaddingCompat(): Modifier = this
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interactionSource, indication = null, onClick = onClick))
}
