package com.example.smartplantcare.notification

import android.content.Context

enum class IotAlertCategory {
    WATER_TANK,
    IRRIGATION_RAIN,
    DEVICE_HEALTH
}

/**
 * Persisted per-category notification preferences for SmartPlantCare ESP32 IoT alerts.
 * Stored in `smartplantcare_settings` SharedPreferences (all enabled by default).
 *
 * Security / account access alerts (e.g. ACCESS_REVOKED) are never muted by these toggles.
 */
object AlertPreferences {

    private const val PREFS_NAME = "smartplantcare_settings"
    const val KEY_WATER_TANK = "alert_water_tank_enabled"
    const val KEY_IRRIGATION_RAIN = "alert_irrigation_rain_enabled"
    const val KEY_DEVICE_HEALTH = "alert_device_health_enabled"

    fun classifyEventCategory(eventType: String): IotAlertCategory {
        val t = eventType.trim().uppercase()
        return when {
            t.contains("RESERVOIR") || t.contains("TANK") -> IotAlertCategory.WATER_TANK
            t.contains("RAIN") ||
                t.contains("WEATHER") ||
                t.contains("FORECAST") ||
                t.contains("IRRIGATION") ||
                t.contains("WATERING") ||
                t.contains("VALVE") ||
                t.contains("PUMP") -> IotAlertCategory.IRRIGATION_RAIN
            else -> IotAlertCategory.DEVICE_HEALTH
        }
    }

    fun isWaterTankEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WATER_TANK, true)

    fun isIrrigationRainEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IRRIGATION_RAIN, true)

    fun isDeviceHealthEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEVICE_HEALTH, true)

    fun isEventAllowed(context: Context, eventType: String): Boolean {
        return when (classifyEventCategory(eventType)) {
            IotAlertCategory.WATER_TANK -> isWaterTankEnabled(context)
            IotAlertCategory.IRRIGATION_RAIN -> isIrrigationRainEnabled(context)
            IotAlertCategory.DEVICE_HEALTH -> isDeviceHealthEnabled(context)
        }
    }
}
