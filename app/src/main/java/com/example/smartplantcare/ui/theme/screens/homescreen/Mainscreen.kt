package com.example.smartplantcare.ui.theme.screens.homescreen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

// ─── Design Tokens ────────────────────────────────────────────────────────────
private val GreenDeep      = Color(0xFF1B4332)
private val GreenMid       = Color(0xFF2D6A4F)
private val GreenAccent    = Color(0xFF52B788)
private val GreenSoft      = Color(0xFFD8F3DC)
private val GreenGlow      = Color(0xFF74C69D)
private val SurfaceWhite   = Color(0xFFFAFDFA)
private val NavBarBg       = Color(0xFFFFFFFF)
private val TextOnDark     = Color(0xFFFFFFFF)
private val TextMuted      = Color(0xFF8FAF9A)

private val TopBarGradient = Brush.linearGradient(
    colors    = listOf(GreenDeep, GreenMid),
    start     = Offset(0f, 0f),
    end       = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// ─── Tab Routes ───────────────────────────────────────────────────────────────
sealed class BottomTab(
    val route          : String,
    val label          : String,
    val selectedIcon   : ImageVector,
    val unselectedIcon : ImageVector,
    val isCamera       : Boolean = false
) {
    object Home     : BottomTab("tab_home",    "Home",      Icons.Filled.Home,           Icons.Outlined.Home)
    object MyPlants : BottomTab("tab_plants",  "My Plants", Icons.Filled.Yard,           Icons.Outlined.Yard)
    object Camera   : BottomTab("tab_camera",  "Scan",      Icons.Filled.CameraAlt,      Icons.Filled.CameraAlt,     isCamera = true)
    object AIDoctor : BottomTab("tab_doctor",  "AI Doctor", Icons.Filled.MedicalServices, Icons.Outlined.MedicalServices)
    object Settings : BottomTab("tab_settings","Settings",  Icons.Filled.Settings,       Icons.Outlined.Settings)
}

private val allTabs = listOf(
    BottomTab.Home,
    BottomTab.MyPlants,
    BottomTab.Camera,
    BottomTab.AIDoctor,
    BottomTab.Settings
)

// ─── Main Screen Shell ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(authViewModel: AuthViewModel) {
    val userProfile       by authViewModel.userProfile.collectAsState()
    val tabNavController  = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route

    Scaffold(
        modifier  = Modifier
            .fillMaxSize()
            .background(SurfaceWhite),
        topBar    = {
            // Gradient top bar with a frosted-leaf feel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TopBarGradient)
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .height(64.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Subtle leaf-vein decorative circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .offset(x = (-30).dp, y = (-20).dp)
                        .background(
                            color = GreenAccent.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = (-10).dp)
                        .background(
                            color = GreenGlow.copy(alpha = 0.10f),
                            shape = CircleShape
                        )
                )
                Text(
                    text       = screenTitle(currentRoute),
                    fontSize   = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextOnDark,
                    letterSpacing = 0.3.sp,
                    modifier   = Modifier.padding(start = 20.dp)
                )
            }
        },
        bottomBar = { BottomBar(navController = tabNavController) },
        containerColor = SurfaceWhite
    ) { innerPadding ->
        NavHost(
            navController    = tabNavController,
            startDestination = BottomTab.Home.route,
            modifier         = Modifier
                .padding(innerPadding)
                .background(SurfaceWhite)
        ) {
            composable(BottomTab.Home.route)     {
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
    }
}

// ─── Bottom Bar ───────────────────────────────────────────────────────────────
@Composable
private fun BottomBar(navController: NavController) {
    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Elevated card-style nav bar with rounded top corners
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation        = 24.dp,
                shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ambientColor     = GreenMid.copy(alpha = 0.18f),
                spotColor        = GreenDeep.copy(alpha = 0.22f)
            ),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = NavBarBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            allTabs.forEach { tab ->
                val isSelected = currentDestination
                    ?.hierarchy
                    ?.any { it.route == tab.route } == true

                if (tab.isCamera) {
                    CameraNavItem(
                        isSelected = isSelected,
                        onClick    = { goToTab(navController, tab.route) },
                        tab        = tab,
                        modifier   = Modifier.weight(1f)
                    )
                } else {
                    RegularNavItem(
                        tab        = tab,
                        isSelected = isSelected,
                        onClick    = { goToTab(navController, tab.route) },
                        modifier   = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─── Regular Nav Item ─────────────────────────────────────────────────────────
@Composable
private fun RegularNavItem(
    tab       : BottomTab,
    isSelected: Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue  = if (isSelected) GreenDeep else TextMuted,
        animationSpec = tween(250),
        label        = "iconColor"
    )
    val labelColor by animateColorAsState(
        targetValue  = if (isSelected) GreenDeep else TextMuted,
        animationSpec = tween(250),
        label        = "labelColor"
    )
    val pillWidth: Dp by animateDpAsState(
        targetValue   = if (isSelected) 52.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "pillWidth"
    )

    Column(
        modifier             = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.spacedBy(3.dp)
    ) {
        // Pill indicator above icon
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(pillWidth)
                .clip(CircleShape)
                .background(if (isSelected) GreenAccent else Color.Transparent)
        )

        IconButton(
            onClick  = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Box(
                modifier          = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isSelected) GreenSoft else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment  = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint               = iconColor,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }

        Text(
            text      = tab.label,
            fontSize  = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color     = labelColor,
            letterSpacing = 0.2.sp
        )
    }
}

// ─── Camera Nav Item (Center FAB) ─────────────────────────────────────────────
@Composable
private fun CameraNavItem(
    isSelected: Boolean,
    onClick   : () -> Unit,
    tab       : BottomTab,
    modifier  : Modifier = Modifier
) {
    val fabSize: Dp by animateDpAsState(
        targetValue   = if (isSelected) 60.dp else 54.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "fabSize"
    )
    val glowAlpha by animateColorAsState(
        targetValue  = if (isSelected) GreenAccent.copy(alpha = 0.35f) else GreenAccent.copy(alpha = 0.15f),
        animationSpec = tween(300),
        label        = "glowAlpha"
    )

    Column(
        modifier            = modifier
            .wrapContentSize()
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glow ring
            Box(
                modifier = Modifier
                    .size(fabSize + 10.dp)
                    .background(color = glowAlpha, shape = CircleShape)
            )
            // FAB button
            FloatingActionButton(
                onClick        = onClick,
                shape          = CircleShape,
                containerColor = GreenDeep,
                contentColor   = Color.White,
                elevation      = FloatingActionButtonDefaults.elevation(
                    defaultElevation  = if (isSelected) 10.dp else 6.dp,
                    pressedElevation  = 14.dp
                ),
                modifier       = Modifier.size(fabSize)
            ) {
                Icon(
                    imageVector        = tab.selectedIcon,
                    contentDescription = tab.label,
                    modifier           = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text         = tab.label,
            fontSize     = 10.sp,
            fontWeight   = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color        = if (isSelected) GreenDeep else TextMuted,
            letterSpacing = 0.2.sp
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun goToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}

private fun screenTitle(route: String?): String = when (route) {
    BottomTab.Home.route     -> "🌿 Smart Plant Care"
    BottomTab.MyPlants.route -> "🪴 My Plants"
    BottomTab.Camera.route   -> "📷 Scan Plant"
    BottomTab.AIDoctor.route -> "🤖 AI Doctor"
    BottomTab.Settings.route -> "⚙️ Settings"
    else                     -> "Smart Plant Care"
}