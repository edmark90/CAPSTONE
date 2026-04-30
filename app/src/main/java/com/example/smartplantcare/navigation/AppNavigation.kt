package com.example.smartplantcare.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartplantcare.ui.screens.LoginScreen
import com.example.smartplantcare.ui.theme.screens.OnboardingScreen

import com.example.smartplantcare.ui.screens.SignUpScreen

import com.example.smartplantcare.ui.theme.screens.homescreen.MainScreen


sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login      : Screen("login")
    object SignUp     : Screen("signup")
    object Main       : Screen("main")
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
                onSignIn = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onSignUp         = { navController.navigate(Screen.SignUp.route) },
                onForgotPassword = { /* TODO */ }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                onBack   = { navController.popBackStack() },
                onSignUp = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onLogin  = { navController.popBackStack() }
            )
        }

        composable(Screen.Main.route) {
            MainScreen()
        }
    }
}