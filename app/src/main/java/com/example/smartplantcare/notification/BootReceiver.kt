package com.example.smartplantcare.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appContext = context.applicationContext

                    // Ensure any legacy 7-day treatment or daily reminder alarms from previous versions are cleaned up
                    LegacyTreatmentCleanup.runOnceIfNeeded(appContext)
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Error running legacy notification cleanup on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
