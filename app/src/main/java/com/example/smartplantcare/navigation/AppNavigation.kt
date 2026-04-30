package com.example.smartplantcare.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartplantcare.R
import com.example.smartplantcare.auth.AuthViewModel
import com.example.smartplantcare.ui.screens.LoginScreen
import com.example.smartplantcare.ui.theme.screens.OnboardingScreen

import com.example.smartplantcare.ui.screens.SignUpScreen

import com.example.smartplantcare.ui.theme.screens.homescreen.MainScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes


sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login      : Screen("login")
    object SignUp     : Screen("signup")
    object Main       : Screen("main")
}

@Composable
fun AppNavigation() {
    val authViewModel: AuthViewModel = viewModel()
    val uiState by authViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    val googleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    )

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching { task.getResult(ApiException::class.java) }
            .onSuccess { account -> authViewModel.signInWithGoogleAccount(account) }
            .onFailure { throwable ->
                val errorMessage = when (throwable) {
                    is ApiException -> mapGoogleSignInError(throwable.statusCode)
                    else -> "Google sign-in failed. Please try again."
                }
                authViewModel.setError(errorMessage)
            }
    }

    LaunchedEffect(uiState.isAuthenticated, currentRoute) {
        if (uiState.isAuthenticated) {
            val isOnMain = navBackStackEntry
                ?.destination
                ?.hierarchy
                ?.any { it.route == Screen.Main.route } == true
            if (!isOnMain) {
                navController.navigate(Screen.Main.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = if (uiState.isAuthenticated) Screen.Main.route else Screen.Onboarding.route
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
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onSignIn = authViewModel::signInWithEmail,
                onGoogleSignIn = {
                    authViewModel.clearError()
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                onSignUp         = { navController.navigate(Screen.SignUp.route) },
                onForgotPassword = { /* TODO */ },
                onDismissError = authViewModel::clearError
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onBack   = { navController.popBackStack() },
                onSignUp = authViewModel::signUpWithEmail,
                onGoogleSignIn = {
                    authViewModel.clearError()
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                onLogin  = { navController.popBackStack() },
                onDismissError = authViewModel::clearError
            )
        }

        composable(Screen.Main.route) {
            MainScreen(authViewModel = authViewModel)
        }
    }
}

private fun mapGoogleSignInError(statusCode: Int): String {
    return when (statusCode) {
        CommonStatusCodes.CANCELED -> "Google sign-in was cancelled."
        CommonStatusCodes.NETWORK_ERROR -> "Network error during Google sign-in."
        CommonStatusCodes.TIMEOUT -> "Google sign-in timed out. Try again."
        CommonStatusCodes.DEVELOPER_ERROR,
        GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Google sign-in config error. Check SHA-1/SHA-256 and Firebase web client ID."
        CommonStatusCodes.INVALID_ACCOUNT -> "Invalid Google account selected."
        CommonStatusCodes.SIGN_IN_REQUIRED -> "Please choose a Google account to continue."
        else -> "Google sign-in failed (code: $statusCode)."
    }
}