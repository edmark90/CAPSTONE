package com.example.smartplantcare.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartplantcare.ui.screens.LoginScreen
import com.example.smartplantcare.ui.screens.OnboardingScreen
import com.example.smartplantcare.ui.screens.SignUpScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login      : Screen("login")
    object SignUp     : Screen("signup")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = Screen.Onboarding.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onSignIn         = { /* TODO: navigate to Home */ },
                onSignUp         = { navController.navigate(Screen.SignUp.route) },
                onForgotPassword = { /* TODO: handle forgot password */ }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                onBack   = { navController.popBackStack() },
                onSignUp = { /* TODO: navigate to Home */ },
                onLogin  = { navController.popBackStack() }
            )
        }
    }
}
