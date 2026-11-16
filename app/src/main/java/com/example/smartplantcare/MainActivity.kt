package com.example.smartplantcare

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import coil.ImageLoader
import com.example.smartplantcare.navigation.AppNavigation
import com.example.smartplantcare.notification.DailyAlarmScheduler
import com.example.smartplantcare.notification.NotificationHelper
import com.example.smartplantcare.ui.theme.SmartPlantCareTheme
import com.example.smartplantcare.ui.theme.screens.homescreen.SettingsViewModel
import com.example.smartplantcare.util.NetworkMonitor
import java.util.Locale

class MainActivity : ComponentActivity() {

    var pendingPlanId by mutableStateOf<String?>(null)
        private set

    fun consumePendingPlanId() {
        pendingPlanId = null
        intent?.removeExtra("plan_id")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingPlanId = intent?.getStringExtra("plan_id")?.takeIf { it.isNotBlank() }

        // Apply saved language preference before UI is created
        SettingsViewModel.setAppLanguage(applicationContext)

        // Network monitor for unstable-connection toasts
        NetworkMonitor.init(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Create notification channel for treatment reminders
        NotificationHelper.createChannel(applicationContext)

        // Ensure daily reminder alarms respect saved user settings (enabled state and time)
        val prefs = applicationContext.getSharedPreferences("smartplantcare_settings", MODE_PRIVATE)
        if (prefs.getBoolean("morning_reminder", true)) {
            DailyAlarmScheduler.schedule(
                applicationContext,
                "morning_reminder",
                prefs.getInt("morning_hour", 8),
                prefs.getInt("morning_minute", 0),
                "\uD83C\uDF05 Good Morning!",
                "Time to check on your plants and start the day's care routine."
            )
        } else {
            DailyAlarmScheduler.cancel(applicationContext, "morning_reminder")
        }

        if (prefs.getBoolean("evening_reminder", true)) {
            DailyAlarmScheduler.schedule(
                applicationContext,
                "evening_reminder",
                prefs.getInt("evening_hour", 18),
                prefs.getInt("evening_minute", 0),
                "\uD83C\uDF06 Good Evening!",
                "Don't forget to give your plants evening care before the night."
            )
        } else {
            DailyAlarmScheduler.cancel(applicationContext, "evening_reminder")
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("plan_id")?.takeIf { it.isNotBlank() }?.let {
            pendingPlanId = it
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
