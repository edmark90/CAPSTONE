package com.example.smartplantcare.ui.theme.screens.homescreen

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartplantcare.auth.AuthViewModel
import com.example.smartplantcare.calibration.CalibrationConfig
import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.data.DiseaseResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult
import com.example.smartplantcare.ML.DiseaseRepository
import com.example.smartplantcare.ui.theme.screens.SystemUiController
import com.example.smartplantcare.ui.theme.screens.resultscreen.ResultScreen

// ─── Color Palette ────────────────────────────────────────────────────────────
private val ColorBg              = Color(0xFFF2F5F3)
private val ColorSurface         = Color(0xFFFFFFFF)
private val ColorPrimary         = Color(0xFF1D4B34)
private val ColorPrimaryLight    = Color(0xFF2D6E4E)
private val ColorPrimaryGlow     = Color(0xFF3A8F65)
private val ColorPrimaryChip     = Color(0xFFE8F2EC)
private val ColorInactive        = Color(0xFFB0BDB6)
private val ColorNavBar          = Color(0xFFFDFEFD)

// ─── Tab Routes ───────────────────────────────────────────────────────────────
sealed class BottomTab(
    val route          : String,
    val label          : String,
    val selectedIcon   : ImageVector,
    val unselectedIcon : ImageVector,
    val isCamera       : Boolean = false
) {
    object Home     : BottomTab("tab_home",     "Home",     Icons.Filled.Home,           Icons.Outlined.Home)
    object MyPlants : BottomTab("tab_plants",   "Plants",   Icons.Filled.Yard,           Icons.Outlined.Yard)
    object Camera   : BottomTab("tab_camera",   "Scan",     Icons.Filled.CameraAlt,      Icons.Outlined.CameraAlt, true)
    object AIDoctor : BottomTab("tab_doctor",   "Doctor",   Icons.Filled.MedicalServices,Icons.Outlined.MedicalServices)
    object Settings : BottomTab("tab_settings", "Settings", Icons.Filled.Settings,       Icons.Outlined.Settings)
    object Result   : BottomTab("tab_result",   "Result",   Icons.Filled.Assignment,     Icons.Outlined.Assignment)
}

private val allTabs = listOf(
    BottomTab.Home,
    BottomTab.MyPlants,
    BottomTab.Camera,
    BottomTab.AIDoctor,
    BottomTab.Settings
)

// ─── Main Screen Shell ────────────────────────────────────────────────────────
@SuppressLint("ContextCastToActivity")
@Composable
fun MainScreen(authViewModel: AuthViewModel) {

    val activity = LocalContext.current as Activity
    val appContext = LocalContext.current.applicationContext
    SideEffect { SystemUiController.hideSystemBars(activity) }

    val userProfile      by authViewModel.userProfile.collectAsState()
    val tabNavController = rememberNavController()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var predictionResult by remember { mutableStateOf<PredictionResult?>(null) }
    var diseaseResult by remember { mutableStateOf<DiseaseResult?>(null) }
    var classificationResult by remember { mutableStateOf<ClassificationResult?>(null) }
    var captureSessionId by remember { mutableLongStateOf(0L) }

    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute       = navBackStackEntry?.destination?.route
    val isCameraFocused    = currentRoute == BottomTab.Camera.route
    val isResultFocused    = currentRoute == BottomTab.Result.route

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = ColorBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isCameraFocused || isResultFocused) Modifier else Modifier.padding(innerPadding))
        ) {
            NavHost(
                navController    = tabNavController,
                startDestination = BottomTab.Home.route,
                modifier         = Modifier
                    .fillMaxSize()
                    .background(ColorBg)
            ) {
                composable(BottomTab.Home.route) {
                    HomeScreen(userName = userProfile.name, userEmail = userProfile.email)
                }
                composable(BottomTab.MyPlants.route) { MyPlantsScreen() }
                composable(BottomTab.Camera.route) {
                    CameraScreen(
                        onBackClick = { goToTab(tabNavController, BottomTab.Home.route) },
                        onAnalyzePhoto = { bitmap, rotationDegrees, prediction, disease, classification ->
                            capturedBitmap = bitmap
                            predictionResult = prediction
                            diseaseResult = disease
                            classificationResult = classification
                            captureSessionId += 1L
                            tabNavController.navigate(BottomTab.Result.route) {
                                popUpTo(BottomTab.Home.route) { saveState = true }
                                launchSingleTop = true
                            }
                            true
                        }
                    )
                }
                composable(BottomTab.AIDoctor.route) { AIDoctorScreen() }
                composable(BottomTab.Result.route) {
                    val prediction = predictionResult
                    val disease = diseaseResult
                    val classification = classificationResult
                    if (prediction != null && disease != null && classification != null) {
                        ResultScreen(
                            prediction = prediction,
                            diseaseInfo = disease,
                            leafImage = capturedBitmap,
                            classification = classification,
                            captureSessionId = captureSessionId,
                            onBackClick = { goToTab(tabNavController, BottomTab.Camera.route) },
                            onViewTreatmentPlanClick = { /* TODO: Navigate to treatment plan */ },
                            onCalibrationLogged = {
                                if (CalibrationConfig.CALIBRATION_MODE) {
                                    goToTab(tabNavController, BottomTab.Camera.route)
                                }
                            }
                        )
                    }
                }
                composable(BottomTab.Settings.route) {
                    SettingsScreen(
                        userName  = userProfile.name,
                        userEmail = userProfile.email,
                        onSignOut = authViewModel::signOut
                    )
                }
            }

            if (!isCameraFocused && !isResultFocused) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    FloatingBottomBar(navController = tabNavController)
                }
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(navController: NavController) {

    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // ─── Camera Button Animation States ──────────────────────────────────
    val cameraInteractionSource = remember { MutableInteractionSource() }
    val isCameraPressed by cameraInteractionSource.collectIsPressedAsState()

    val cameraClickScale by animateFloatAsState(
        targetValue = if (isCameraPressed) 0.93f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "cameraClickScale"
    )

    val clickGlowScale by animateFloatAsState(
        targetValue = if (isCameraPressed) 1.45f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "clickGlowScale"
    )

    val clickGlowAlpha by animateFloatAsState(
        targetValue = if (isCameraPressed) 0.85f else 0.0f,
        animationSpec = tween(durationMillis = 150),
        label = "clickGlowAlpha"
    )

    val pulseTransition = rememberInfiniteTransition(label = "cameraPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.18f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue   = 0.55f,
        targetValue    = 0f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(
                    elevation    = 32.dp,
                    shape        = RoundedCornerShape(36.dp),
                    spotColor    = Color(0xFF1D4B34).copy(alpha = 0.18f),
                    ambientColor = Color.Black.copy(alpha = 0.06f)
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(ColorNavBar, Color(0xFFF8FBF9))
                    ),
                    shape = RoundedCornerShape(36.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFE4EEE8), Color(0xFFEDF3EF))
                    ),
                    shape = RoundedCornerShape(36.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allTabs.forEach { tab ->
                if (tab.isCamera) {
                    Spacer(modifier = Modifier.weight(1.4f))
                } else {
                    val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavTabItem(
                        tab        = tab,
                        isSelected = isSelected,
                        onClick    = { goToTab(navController, tab.route) },
                        modifier   = Modifier.weight(1f)
                    )
                }
            }
        }

        // Floating Camera Button - konting taas lang (8.dp instead of 28.dp)
        Column(
            modifier = Modifier.offset(y = (-8).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Idle Glow Ring
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            alpha  = if (isCameraPressed) 0f else pulseAlpha
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(ColorPrimaryGlow.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )

                // Click Glow Burst
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = clickGlowScale
                            scaleY = clickGlowScale
                            alpha  = clickGlowAlpha
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(ColorPrimaryGlow.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                )

                // Main Button Body
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = cameraClickScale
                            scaleY = cameraClickScale
                        }
                        .shadow(
                            elevation    = 20.dp,
                            shape        = CircleShape,
                            spotColor    = Color(0xFF1D4B34).copy(alpha = 0.3f),
                            ambientColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .background(Color.White, CircleShape)
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(ColorPrimaryLight, ColorPrimary, Color(0xFF152E20))
                                )
                            )
                            .clickable(
                                interactionSource = cameraInteractionSource,
                                indication        = null
                            ) {
                                goToTab(navController, BottomTab.Camera.route)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                                        center = androidx.compose.ui.geometry.Offset(20f, 20f),
                                        radius = 80f
                                    )
                                )
                        )

                        Icon(
                            imageVector        = Icons.Filled.CameraAlt,
                            contentDescription = "Scan Plant",
                            tint               = Color.White,
                            modifier           = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text       = "Scan",
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color      = ColorPrimary
            )
        }
    }
}

// ─── Individual Nav Tab Item - NO animations ──────────────────────────────────
@Composable
private fun NavTabItem(
    tab       : BottomTab,
    isSelected: Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val iconTint by animateColorAsState(
        targetValue   = if (isSelected) ColorPrimary else ColorInactive,
        animationSpec = tween(durationMillis = 200),
        label         = "iconTint"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) ColorPrimaryChip else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text       = tab.label,
            fontSize   = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = iconTint,
            letterSpacing = 0.2.sp
        )
    }
}

// ─── Navigation Helper ────────────────────────────────────────────────────────
private sealed class AnalysisOutcome {
    data class Success(
        val bitmap: Bitmap,
        val prediction: PredictionResult,
        val disease: DiseaseResult
    ) : AnalysisOutcome()

    data class Error(val message: String) : AnalysisOutcome()
}

private fun goToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}