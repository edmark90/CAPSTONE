package com.example.smartplantcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.smartplantcare.navigation.AppNavigation

import com.smartplantcare.ui.theme.SmartPlantCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SmartPlantCareTheme {
                AppNavigation()
            }
        }
    }
}