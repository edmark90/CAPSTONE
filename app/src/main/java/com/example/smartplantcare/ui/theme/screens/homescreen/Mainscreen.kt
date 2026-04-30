package com.example.smartplantcare.ui.theme.screens.homescreen



import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
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

// ─── Tab Routes ───────────────────────────────────────────────────────────────
sealed class BottomTab(
    val route         : String,
    val label         : String,
    val selectedIcon  : ImageVector,
    val unselectedIcon: ImageVector,
    val isCamera      : Boolean = false
) {
    object Home : BottomTab(
        route          = "tab_home",
        label          = "Home",
        selectedIcon   = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    object MyPlants : BottomTab(
        route          = "tab_plants",
        label          = "My Plants",
        selectedIcon   = Icons.Filled.Yard,
        unselectedIcon = Icons.Outlined.Yard
    )
    object Camera : BottomTab(
        route          = "tab_camera",
        label          = "Scan",
        selectedIcon   = Icons.Filled.CameraAlt,
        unselectedIcon = Icons.Filled.CameraAlt,
        isCamera       = true
    )
    object AIDoctor : BottomTab(
        route          = "tab_doctor",
        label          = "AI Doctor",
        selectedIcon   = Icons.Filled.MedicalServices,
        unselectedIcon = Icons.Outlined.MedicalServices
    )
    object Settings : BottomTab(
        route          = "tab_settings",
        label          = "Settings",
        selectedIcon   = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
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
fun MainScreen() {
    val tabNavController  = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route

    Scaffold(
        modifier  = Modifier.fillMaxSize(),
        topBar    = {
            TopAppBar(
                title = {
                    Text(
                        text       = screenTitle(currentRoute),
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BottomBar(navController = tabNavController)
        }
    ) { innerPadding ->
        NavHost(
            navController    = tabNavController,
            startDestination = BottomTab.Home.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(BottomTab.Home.route)     { HomeScreen() }
            composable(BottomTab.MyPlants.route) { MyPlantsScreen() }
            composable(BottomTab.Camera.route)   { CameraScreen() }
            composable(BottomTab.AIDoctor.route) { AIDoctorScreen() }
            composable(BottomTab.Settings.route) { SettingsScreen() }
        }
    }
}

// ─── Bottom Bar ───────────────────────────────────────────────────────────────
@Composable
private fun BottomBar(navController: NavController) {
    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(
        modifier = Modifier.shadow(
            elevation = 12.dp,
            shape     = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            allTabs.forEach { tab ->
                val isSelected = currentDestination
                    ?.hierarchy
                    ?.any { it.route == tab.route } == true

                if (tab.isCamera) {
                    NavigationBarItem(
                        selected = isSelected,
                        onClick  = { goToTab(navController, tab.route) },
                        icon     = {
                            FloatingActionButton(
                                onClick        = { goToTab(navController, tab.route) },
                                shape          = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary,
                                elevation      = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = if (isSelected) 10.dp else 6.dp
                                ),
                                modifier       = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector        = tab.selectedIcon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label  = null,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                } else {
                    NavigationBarItem(
                        selected = isSelected,
                        onClick  = { goToTab(navController, tab.route) },
                        icon     = {
                            Icon(
                                imageVector        = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label  = { Text(text = tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = MaterialTheme.colorScheme.primary,
                            selectedTextColor   = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor      = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
}

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