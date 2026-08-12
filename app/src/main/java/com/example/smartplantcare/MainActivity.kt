package com.example.smartplantcare

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import coil.ImageLoader
import com.example.smartplantcare.navigation.AppNavigation
import com.example.smartplantcare.notification.NotificationHelper
import com.example.smartplantcare.ui.theme.SmartPlantCareTheme
import com.example.smartplantcare.ui.theme.screens.homescreen.SettingsViewModel
import com.example.smartplantcare.util.NetworkMonitor
import java.util.Localeimport io.sentry.Sentry


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    // waiting for view to draw to better represent a captured error with a screenshot
    findViewById<android.view.View>(android.R.id.content).viewTreeObserver.addOnGlobalLayoutListener {
      try {
        throw Exception("This app uses Sentry! :)")
      } catch (e: Exception) {
        Sentry.captureException(e)
      }
    }


        // Apply saved language preference before UI is created
        SettingsViewModel.setAppLanguage(applicationContext)

        // Network monitor for unstable-connection toasts
        NetworkMonitor.init(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Create notification channel for treatment reminders
        NotificationHelper.createChannel(applicationContext)

        // Ensure daily reminder alarms are always armed at fixed times (8:00 AM and 6:00 PM)
        com.example.smartplantcare.notification.DailyAlarmScheduler.schedule(
            applicationContext,
            "morning_reminder",
            8,
            0,
            "\uD83C\uDF05 Good Morning!",
            "Time to check on your plants and start the day's care routine."
        )
        com.example.smartplantcare.notification.DailyAlarmScheduler.schedule(
            applicationContext,
            "evening_reminder",
            18,
            0,
            "\uD83C\uDF06 Good Evening!",
            "Don't forget to give your plants evening care before the night."
        )

        // Configure Coil ImageLoader with optimized caching
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.2)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .allowHardware(true)
            .build()
        
        // Set the singleton ImageLoader
        coil.Coil.setImageLoader(imageLoader)

        setContent {
            SmartPlantCareTheme {
                AppNavigation()
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        // Apply saved language preference at the base context level
        val prefs = newBase.getSharedPreferences("smartplantcare_settings", MODE_PRIVATE)
        val lang = prefs.getString("language", "en") ?: "en"
        val locale = when (lang) {
            "fil" -> Locale("fil")
            else -> Locale("en")
        }
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}
