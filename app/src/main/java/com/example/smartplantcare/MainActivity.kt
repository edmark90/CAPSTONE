package com.example.smartplantcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.smartplantcare.navigation.AppNavigation
import com.example.smartplantcare.ui.theme.SmartPlantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SmartPlantTheme {
                AppNavigation()
            }
        }
    }
}