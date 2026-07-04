package com.example.smartplantcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.smartplantcare.ML.InferencePipeline
import com.example.smartplantcare.navigation.AppNavigation

import com.smartplantcare.ui.theme.SmartPlantCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Pre-initialize ML model before UI composition to prevent crashes
        try {
            android.util.Log.i("MainActivity", "Attempting to initialize ML model...")
            InferencePipeline.getInstance(applicationContext)
            android.util.Log.i("MainActivity", "ML model initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize ML model", e)
            e.printStackTrace()
            // Don't crash the app - let it run with error handling in CameraScreen
            android.util.Log.w("MainActivity", "App will continue without ML model")
        }
        
        setContent {
            SmartPlantCareTheme {
                AppNavigation()
            }
        }
    }
}