package com.example.smartplantcare.ui.theme.screens.homescreen

import android.R.attr.scaleX
import android.R.attr.scaleY
import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.smartplantcare.ui.theme.screens.SystemUiController

private val ColorBg        = Color(0xFFF5F7F6)
private val ColorSurface   = Color(0xFFFFFFFF)
private val ColorPrimary   = Color(0xFF1D4B34) // Dark Green
private val ColorPrimaryLight = Color(0xFF2A6D4C) // Lighter Green for gradient
private val ColorInactive  = Color(0xFFAAB8AF)
private val ColorShadow    = Color(0x14000000)

// ─── Tab Routes with Icons ────────────────────────────────────────────────────
sealed class BottomTab(
    val route         : String,
    val label         : String,
    val selectedIcon  : ImageVector,
    val unselectedIcon: ImageVector,
    val isCamera      : Boolean = false
) {
    object Home : BottomTab("tab_home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object MyPlants : BottomTab("tab_plants", "Plants", Icons.Filled.Yard, Icons.Outlined.Yard)
    // IBINALIK SA CameraAlt
    object Camera : BottomTab("tab_camera", "Scan", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt, true)
    object AIDoctor : BottomTab("tab_doctor", "Doctor", Icons.Filled.MedicalServices, Icons.Outlined.MedicalServices)
    object Settings : BottomTab("tab_settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
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

    SideEffect {
        SystemUiController.hideSystemBars(activity)
    }

    val userProfile       by authViewModel.userProfile.collectAsState()
    val tabNavController  = rememberNavController()

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = ColorBg
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // LAYER 1: The Screens
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
                composable(BottomTab.Camera.route)   { CameraScreen() }
                composable(BottomTab.AIDoctor.route) { AIDoctorScreen() }
                composable(BottomTab.Settings.route) {
                    SettingsScreen(
                        userName  = userProfile.name,
                        userEmail = userProfile.email,
                        onSignOut = authViewModel::signOut
                    )
                }
            }

            // LAYER 2: Floating Nav Bar
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                FloatingBottomBar(navController = tabNavController)
            }
        }
    }
}

// ─── Responsive Floating Bottom Bar with Elevated Camera ──────────────────────
@Composable
private fun FloatingBottomBar(navController: NavController) {

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val cameraTab = BottomTab.Camera

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {

        // ─── MAIN NAV BAR (The White Pill) ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .shadow(
                    elevation = 28.dp, // High elevation for better float effect
                    shape = RoundedCornerShape(40.dp),
                    spotColor = Color.Black.copy(alpha = 0.35f),
                    ambientColor = Color.Black.copy(alpha = 0.1f)
                )
                .background(Color.White, RoundedCornerShape(40.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {

            allTabs.forEach { tab ->
                if (tab.isCamera) {
                    Spacer(modifier = Modifier.weight(1.3f))
                } else {
                    val isSelected =
                        currentDestination?.hierarchy?.any { it.route == tab.route } == true

                    ModernNavItem(
                        tab = tab,
                        isSelected = isSelected,
                        onClick = { goToTab(navController, tab.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }


        Box(
            modifier = Modifier
                .size(96.dp)
                .offset(y = (-32).dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.25f)
                )
                .background(Color.White, CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {


            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(ColorPrimaryLight, ColorPrimary)
                        )
                    )
                    .border(2.dp, Color(0xFFE0E0E0), CircleShape) // Subtle silver inner ring
                    .clickable { goToTab(navController, cameraTab.route) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp) // Mas pinalaki nang onti para fit sa circle
                )
            }
        }
    }
}

@Composable
private fun ModernNavItem(
    tab: BottomTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val tint by animateColorAsState(
        targetValue = if (isSelected) ColorPrimary else ColorInactive,
        animationSpec = tween(250),
        label = ""
    )

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = tween(250),
        label = ""
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = tab.label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )

        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(if (isSelected) 6.dp else 0.dp)
                .clip(CircleShape)
                .background(if (isSelected) ColorPrimary else Color.Transparent)
        )
    }
}

private fun goToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}