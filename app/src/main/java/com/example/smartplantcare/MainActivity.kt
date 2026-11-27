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
import com.example.smartplantcare.notification.LegacyTreatmentCleanup
import com.example.smartplantcare.notification.NotificationHelper
import com.example.smartplantcare.ui.theme.SmartPlantCareTheme
import com.example.smartplantcare.ui.theme.screens.homescreen.SettingsViewModel
import com.example.smartplantcare.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    var pendingAccessRevokedSync by mutableStateOf(false)
        private set

    fun consumePendingAccessRevokedSync() {
        pendingAccessRevokedSync = false
        intent?.removeExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE)
        intent?.removeExtra(NotificationHelper.EXTRA_EVENT_ID)
        intent?.removeExtra(NotificationHelper.EXTRA_DEVICE_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNotificationIntent(intent)

        // Apply saved language preference before UI is created
        SettingsViewModel.setAppLanguage(applicationContext)

        // Network monitor for unstable-connection toasts
        NetworkMonitor.init(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Create active notification channels (IoT Alerts, Device Access Alerts)
        NotificationHelper.createChannel(applicationContext)

        // One-time cleanup of any legacy 7-day treatment & daily reminder alarms, channels, and prefs
        CoroutineScope(Dispatchers.IO).launch {
            LegacyTreatmentCleanup.runOnceIfNeeded(applicationContext)
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
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val notifType = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE)
            ?: intent.getStringExtra("type")
        if (notifType == NotificationHelper.TYPE_ACCESS_REVOKED) {
            pendingAccessRevokedSync = true
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
