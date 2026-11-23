package com.example.smartplantcare.ui.theme.screens.homescreen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartplantcare.R
import com.example.smartplantcare.auth.AuthViewModel
import com.example.smartplantcare.iot.MyPlantsSubPage
import com.example.smartplantcare.iot.MyPlantsViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.smartplantcare.data.ClassificationResult
import com.example.smartplantcare.ui.theme.screens.homescreen.plantidentify.PlantIdentifyScreen
import com.example.smartplantcare.data.DiseaseResult
import com.example.smartplantcare.data.PredictionResult.PredictionResult
import com.example.smartplantcare.data.TreatmentPlan
import com.example.smartplantcare.data.ImageUtils
import com.example.smartplantcare.data.TreatmentPlanRepository
import com.example.smartplantcare.notification.NotificationScheduler
import com.example.smartplantcare.ML.DiseaseRepository
import com.example.smartplantcare.ui.theme.screens.SystemUiController
import com.example.smartplantcare.ui.theme.screens.resultscreen.ResultScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Color Palette ────────────────────────────────────────────────────────────
private val ColorBg              = HomeColors.PageBg
private val ColorSurface         = HomeColors.SurfaceWhite
private val ColorPrimary         = HomeColors.ForestPrimary
private val ColorPrimaryLight    = HomeColors.ForestMedium
private val ColorPrimaryGlow     = HomeColors.MintGlow
private val ColorPrimaryChip     = HomeColors.SoftMint
private val ColorInactive        = HomeColors.NavInactive
private val ColorNavBar          = HomeColors.SurfaceWhite

// ─── Tab Routes ───────────────────────────────────────────────────────────────
sealed class BottomTab(
    val route          : String,
    @StringRes val labelRes : Int,
    val selectedIcon   : ImageVector,
    val unselectedIcon : ImageVector,
    val isCamera       : Boolean = false
) {
    object Home     : BottomTab("tab_home",     R.string.nav_home,     Icons.Filled.Home,           Icons.Outlined.Home)
    object MyPlants : BottomTab("tab_plants",   R.string.nav_plants,   Icons.Filled.Yard,           Icons.Outlined.Yard)
    object Camera   : BottomTab("tab_camera",   R.string.nav_scan,     Icons.Filled.CameraAlt,      Icons.Outlined.CameraAlt, true)
    object AIDoctor : BottomTab("tab_doctor",   R.string.nav_doctor,   Icons.Filled.MedicalServices,Icons.Outlined.MedicalServices)
    object Settings : BottomTab("tab_settings", R.string.nav_settings, Icons.Filled.Settings,       Icons.Outlined.Settings)
    object Result   : BottomTab("tab_result",   R.string.nav_scan,     Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment)
    object PlantIdentify : BottomTab("plant_identify", R.string.nav_plants, Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt)
}

private val allTabs = listOf(
    BottomTab.Home,
    BottomTab.MyPlants,
    BottomTab.Camera,
    BottomTab.AIDoctor,
    BottomTab.Settings
)

// ─── Treatment Detail Route ───────────────────────────────────────────────────
private const val ROUTE_TREATMENT_DETAIL = "treatment_detail/{planId}"
private fun treatmentDetailRoute(planId: String) = "treatment_detail/$planId"

// ─── Notification History Route ───────────────────────────────────────────────
private const val ROUTE_NOTIFICATION_HISTORY = "notification_history"

// ─── Main Screen Shell ────────────────────────────────────────────────────────
@SuppressLint("ContextCastToActivity")
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: (() -> Unit)? = null,
    onNavigateToSignUp: (() -> Unit)? = null
) {

    val activity = LocalContext.current as Activity
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) { SystemUiController.hideSystemBars(activity) }

    val userProfile      by authViewModel.userProfile.collectAsState()
    val uiState          by authViewModel.uiState.collectAsState()
    val tabNavController = rememberNavController()
    val mainActivity = activity as? com.example.smartplantcare.MainActivity
    val planIdFromIntent = mainActivity?.pendingPlanId ?: activity.intent?.getStringExtra("plan_id")
    LaunchedEffect(planIdFromIntent) {
        if (!planIdFromIntent.isNullOrBlank()) {
            tabNavController.navigate(treatmentDetailRoute(planIdFromIntent)) {
                popUpTo(tabNavController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
            mainActivity?.consumePendingPlanId()
            activity.intent?.removeExtra("plan_id")
        }
    }


    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var predictionResult by remember { mutableStateOf<PredictionResult?>(null) }
    var diseaseResult by remember { mutableStateOf<DiseaseResult?>(null) }
    var classificationResult by remember { mutableStateOf<ClassificationResult?>(null) }
    var captureSessionId by remember { mutableLongStateOf(0L) }

    // ─── Language Switch: observe SharedPreferences & force full recomposition ───
    var refreshKey by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) {
        val prefs = activity.getSharedPreferences("smartplantcare_settings", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "language") {
                // Apply locale using the Activity context so stringResource() picks it up
                SettingsViewModel.applyAppLocale(activity)
                // Force the entire composable tree to recompose
                refreshKey = System.currentTimeMillis()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }


    val treatmentPlanViewModel: TreatmentPlanViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TreatmentPlanViewModel::class.java)) {
                    return TreatmentPlanViewModel(appContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    val notificationHistoryViewModel: NotificationHistoryViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(NotificationHistoryViewModel::class.java)) {
                    return NotificationHistoryViewModel(appContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    return SettingsViewModel(appContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    // Hoisted at MainScreen scope so My Plants keeps its live Firebase state & never reloads on tab switch!
    val myPlantsViewModel: MyPlantsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MyPlantsViewModel::class.java)) {
                    return MyPlantsViewModel(appContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    LaunchedEffect(uiState.isAuthenticated, uiState.isGuest, userProfile.email, userProfile.name) {
        val effectiveUid = if (uiState.isAuthenticated && !uiState.isGuest) {
            FirebaseAuth.getInstance().currentUser?.uid
        } else {
            null
        }
        myPlantsViewModel.bindUser(effectiveUid, userProfile.email, userProfile.name, uiState.isGuest)
    }

    LaunchedEffect(Unit) {
        treatmentPlanViewModel.loadPlans()
        notificationHistoryViewModel.loadUnreadCount()
    }

    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute       = navBackStackEntry?.destination?.route
    val isCameraFocused    = currentRoute == BottomTab.Camera.route
    val isResultFocused    = currentRoute == BottomTab.Result.route
    val isDetailFocused    = currentRoute?.startsWith("treatment_detail/") == true
    val isPlantIdentifyFocused = currentRoute == BottomTab.PlantIdentify.route
    val isNotificationHistoryFocused = currentRoute == ROUTE_NOTIFICATION_HISTORY

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless — WorkManager still schedules */ }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        NavHost(
                navController    = tabNavController,
                startDestination = BottomTab.Home.route,
                enterTransition  = { fadeIn(tween(150)) },
                exitTransition   = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition  = { fadeOut(tween(120)) },
                modifier         = Modifier
                    .fillMaxSize()
                    .background(ColorBg)
            ) {
                composable(BottomTab.Home.route) {
                    key(refreshKey) {
                        val unreadCount by notificationHistoryViewModel.unreadCount.collectAsState()
                        val plans by treatmentPlanViewModel.plans.collectAsState()
                        val myPlantsUiState by myPlantsViewModel.uiState.collectAsState()

                        LaunchedEffect(Unit) {
                            notificationHistoryViewModel.loadUnreadCount()
                        }

                        HomeScreen(
                            userName = userProfile.name,
                            userEmail = userProfile.email,
                            unreadCount = unreadCount,
                            plans = plans,
                            myPlantsUiState = myPlantsUiState,
                            onOpenMyPlants = {
                                myPlantsViewModel.closeSubPage()
                                goToTab(tabNavController, BottomTab.MyPlants.route)
                            },
                            onOpenHistory = {
                                myPlantsViewModel.openSubPage(MyPlantsSubPage.HISTORY_MONITORING)
                                goToTab(tabNavController, BottomTab.MyPlants.route)
                            },
                            onSelectHistoryDate = { dateKey ->
                                myPlantsViewModel.selectHistoryDate(dateKey)
                            },
                            onNotificationClick = {
                                tabNavController.navigate(ROUTE_NOTIFICATION_HISTORY)
                            },
                            onScanClick = {
                                goToTab(tabNavController, BottomTab.Camera.route)
                            },
                            onIdentifyClick = {
                                tabNavController.navigate(BottomTab.PlantIdentify.route)
                            },
                            onDoctorClick = {
                                goToTab(tabNavController, BottomTab.AIDoctor.route)
                            },
                            onPlanClick = { id ->
                                tabNavController.navigate(treatmentDetailRoute(id))
                            }
                        )
                    }
                }
                composable(BottomTab.MyPlants.route) {
                    key(refreshKey) {
                        MyPlantsScreen(
                            isAuthenticated = uiState.isAuthenticated,
                            isGuest = uiState.isGuest,
                            userEmail = userProfile.email,
                            userName = userProfile.name,
                            onNavigateToLogin = { onNavigateToLogin?.invoke() },
                            sharedViewModel = myPlantsViewModel
                        )
                    }
                }
                composable(BottomTab.Camera.route) {
                    key(refreshKey) {
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
                }
                composable(BottomTab.AIDoctor.route) {
                    key(refreshKey) {
                        AIDoctorScreen(
                            viewModel = treatmentPlanViewModel,
                            onPlanClick = { id -> tabNavController.navigate(treatmentDetailRoute(id)) },
                            onIdentifyClick = { tabNavController.navigate(BottomTab.PlantIdentify.route) }
                        )
                    }
                }
                composable(BottomTab.PlantIdentify.route) {
                    key(refreshKey) {
                        PlantIdentifyScreen(
                            onBack             = { tabNavController.popBackStack() },
                            isGuest            = uiState.isGuest,
                            onNavigateToLogin  = { onNavigateToLogin?.invoke() },
                            onNavigateToSignUp = { onNavigateToSignUp?.invoke() }
                        )
                    }
                }

                composable(BottomTab.Result.route) {
                    key(refreshKey) {
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
                                onViewTreatmentPlan = {
                                    val pred = prediction
                                    val dis = disease
                                    val bitmapToSave = capturedBitmap
                                    requestNotificationPermissionIfNeeded()
                                    val newId = "local_${System.currentTimeMillis()}_${(1000..9999).random()}"

                                    tabNavController.navigate(treatmentDetailRoute(newId)) {
                                        popUpTo(BottomTab.Result.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                    if (tabNavController.currentDestination?.route == BottomTab.Result.route) {
                                        tabNavController.popBackStack()
                                        tabNavController.navigate(treatmentDetailRoute(newId)) {
                                            launchSingleTop = true
                                        }
                                    }

                                    coroutineScope.launch {
                                        try {
                                            val plan = withContext(Dispatchers.IO) {
                                                val repository = TreatmentPlanRepository(appContext)
                                                val savedImageUrl = bitmapToSave?.let { bitmap ->
                                                    ImageUtils.saveBitmapToInternalStorage(appContext, bitmap)
                                                } ?: ""
                                                val newPlan = TreatmentPlan(
                                                    id = newId,
                                                    plantName = pred.className.split("_").firstOrNull() ?: "Plant",
                                                    diseaseName = dis.diseaseName,
                                                    className = pred.className,
                                                    imageUrl = savedImageUrl,
                                                    startDateMillis = System.currentTimeMillis(),
                                                    sevenDayPlan = dis.sevenDayPlan,
                                                    currentDayIndex = 0,
                                                    lastNotificationSent = 0L,
                                                    nextScheduledNotification = 0L,
                                                    lastOverdueCheck = 0L,
                                                    confidence = pred.confidence,
                                                    isHealthy = pred.className.contains("Healthy", ignoreCase = true)
                                                )
                                                repository.savePlan(newPlan)
                                                NotificationScheduler.scheduleInitialDay1(appContext, newPlan)
                                                newPlan
                                            }
                                            treatmentPlanViewModel.loadPlan(plan.id)
                                            treatmentPlanViewModel.loadPlans()
                                        } catch (e: Exception) {
                                            android.util.Log.e("ResultScreen", "Failed to save plant", e)
                                            tabNavController.popBackStack()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                composable(BottomTab.Settings.route) {
                    key(refreshKey) {
                        val myPlantsUiState by myPlantsViewModel.uiState.collectAsState()
                        SettingsScreen(
                            userName  = userProfile.name,
                            userEmail = userProfile.email,
                            onSignOut = { onNavigateToLogin?.invoke() },
                            onNavigateToLogin = onNavigateToLogin,
                            onNavigateToSignUp = onNavigateToSignUp,
                            settingsViewModel = settingsViewModel,
                            myPlantsUiState = myPlantsUiState,
                            onOpenMyPlants = {
                                goToTab(tabNavController, BottomTab.MyPlants.route)
                            }
                        )
                    }
                }
                composable(
                    route = ROUTE_TREATMENT_DETAIL,
                    arguments = listOf(navArgument("planId") { type = NavType.StringType })
                ) { backStackEntry ->
                    key(refreshKey) {
                        val planId = backStackEntry.arguments?.getString("planId") ?: ""
                        TreatmentPlanDetailScreen(
                            planId = planId,
                            viewModel = treatmentPlanViewModel,
                            onBackClick = {
                                tabNavController.navigate(BottomTab.AIDoctor.route) {
                                    popUpTo(ROUTE_TREATMENT_DETAIL) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
                composable(ROUTE_NOTIFICATION_HISTORY) {
                    key(refreshKey) {
                        NotificationHistoryScreen(
                            viewModel = notificationHistoryViewModel,
                            onBackClick = {
                                tabNavController.popBackStack()
                            },
                            onPlanClick = { planId ->
                                tabNavController.navigate(treatmentDetailRoute(planId))
                            }
                        )
                    }
                }
            }

        if (!isCameraFocused && !isResultFocused && !isDetailFocused && !isPlantIdentifyFocused && !isNotificationHistoryFocused) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                FloatingBottomBar(
                    navController = tabNavController,
                    currentRoute = currentRoute
                )
            }
        }
    }
}

// ─── Floating Bottom Bar (optimized) ──────────────────────────────────────────
@Composable
private fun FloatingBottomBar(
    navController: NavController,
    currentRoute: String?
) {
    // ─── Camera Button Press Feedback (simple, no bouncy spring) ─────────
    val cameraInteractionSource = remember { MutableInteractionSource() }
    val isCameraPressed by cameraInteractionSource.collectIsPressedAsState()

    val cameraClickScale by animateFloatAsState(
        targetValue = if (isCameraPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "cameraClickScale"
    )

    // Static glow ring — no infinite animation running in the background.
    // This alone removes a continuous recomposition loop that was competing
    // with the nav-graph render on every tab switch.

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = HomeDimens.ScreenHorizontalPadding, vertical = 14.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation    = 6.dp,
                    shape        = RoundedCornerShape(32.dp),
                    spotColor    = ColorPrimary.copy(alpha = 0.14f),
                    ambientColor = Color.Black.copy(alpha = 0.05f)
                )
                .background(ColorNavBar, RoundedCornerShape(32.dp))
                .border(
                    width = 1.dp,
                    color = HomeColors.CardBorder,
                    shape = RoundedCornerShape(32.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allTabs.forEach { tab ->
                if (tab.isCamera) {
                    Spacer(modifier = Modifier.weight(1.25f))
                } else {
                    val isSelected = currentRoute == tab.route
                    NavTabItem(
                        tab        = tab,
                        isSelected = isSelected,
                        onClick    = { goToTab(navController, tab.route) },
                        modifier   = Modifier.weight(1f)
                    )
                }
            }
        }

        // Center Floating Scan Button
        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(64.dp)
                .graphicsLayer {
                    scaleX = cameraClickScale
                    scaleY = cameraClickScale
                }
                .shadow(
                    elevation    = 6.dp,
                    shape        = CircleShape,
                    spotColor    = ColorPrimary.copy(alpha = 0.28f),
                    ambientColor = Color.Black.copy(alpha = 0.08f)
                )
                .background(Color.White, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(ColorPrimaryLight, ColorPrimary)
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
                Icon(
                    imageVector        = Icons.Filled.CameraAlt,
                    contentDescription = stringResource(BottomTab.Camera.labelRes),
                    tint               = Color.White,
                    modifier           = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ─── Individual Nav Tab Item (no color-animation overhead) ───────────────────
@Composable
private fun NavTabItem(
    tab       : BottomTab,
    isSelected: Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val iconTint = if (isSelected) ColorPrimary else ColorInactive
    val label = stringResource(tab.labelRes)

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 30.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isSelected) ColorPrimaryChip else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = label,
                tint               = iconTint,
                modifier           = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text       = label,
            fontFamily = HomeInterFont,
            fontSize   = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color      = iconTint,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
    }
}

// ─── Navigation Helper ────────────────────────────────────────────────────────
private fun goToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}