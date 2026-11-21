package com.example.smartplantcare.iot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IotDomainLogicTest {

    @Test
    fun parseNearbyEspDevices_filtersOnlySpcSetupPrefix_andSortsByRssi() {
        val raw = listOf(
            "PLDT_Home_5G" to -40,
            "SPC-SETUP-spc-unit-0002" to -74,
            "\"SPC-SETUP-spc-unit-0001\"" to -52,
            "SPC-SETUP-spc-unit-0001" to -65,
            "Converge_2.4G" to -48
        )

        val result = IotDomainLogic.parseNearbyEspDevices(raw)
        assertEquals(2, result.size)
        assertEquals("spc-unit-0001", result[0].deviceId)
        assertEquals(-52, result[0].rssi)
        assertEquals("spc-unit-0002", result[1].deviceId)
    }

    @Test
    fun normalizeScannedWifi_removesSpcHotspotsAndDeduplicatesStrongest() {
        val raw = listOf(
            ScannedWifiNetwork("SPC-SETUP-spc-unit-0001", -30, true),
            ScannedWifiNetwork("HomeWifi_2.4G", -70, true),
            ScannedWifiNetwork("HomeWifi_2.4G", -55, true),
            ScannedWifiNetwork("Office_2.4G", -62, false),
            ScannedWifiNetwork("   ", -50, false)
        )

        val cleaned = IotDomainLogic.normalizeScannedWifi(raw)
        assertEquals(2, cleaned.size)
        assertEquals("HomeWifi_2.4G", cleaned[0].ssid)
        assertEquals(-55, cleaned[0].rssi)
        assertEquals("Office_2.4G", cleaned[1].ssid)
    }

    @Test
    fun sanitizeWifiInputs_preservesSpacesInPasswordAndStripsQuotesAndNewlines() {
        assertEquals("PLDT_Home_2.4G", IotDomainLogic.sanitizeWifiSsid(" \"PLDT_Home_2.4G\"\n"))
        assertEquals("My Pass 123!", IotDomainLogic.sanitizeWifiPassword("My Pass 123!\r\n"))
    }

    @Test
    fun clampPulseDurationMs_enforcesHardSafetyBounds() {
        assertEquals(1000L, IotDomainLogic.clampPulseDurationMs(200L))
        assertEquals(4000L, IotDomainLogic.clampPulseDurationMs(4000L))
        assertEquals(15000L, IotDomainLogic.clampPulseDurationMs(99000L))
    }

    @Test
    fun manualWaterLockReason_enforcesSafetyAndRoleMatrix() {
        val safeSnapshot = LiveDeviceSnapshot(
            deviceId = "spc-unit-0001",
            status = DeviceStatus(
                online = true,
                mode = "OUTDOOR",
                rainSuspensionActive = false,
                emergencyStop = false,
                faultLockout = false,
                activeValve = "none",
                irrigationState = "IDLE"
            ),
            telemetry = DeviceTelemetry(
                reservoirValid = true,
                reservoirPct = 65f,
                reservoirState = "NORMAL"
            )
        )

        // Allowed for Owner & Operator when safe
        assertNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, safeSnapshot, "tomato"))
        assertNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OPERATOR, safeSnapshot, "chili"))

        // Blocked for Viewer
        assertNotNull(IotDomainLogic.manualWaterLockReason(DeviceRole.VIEWER, safeSnapshot, "tomato"))

        // Manual button bypasses automatic reservoir/rain/zone logic (direct control from app)
        val critRes = safeSnapshot.copy(
            telemetry = safeSnapshot.telemetry.copy(reservoirState = "CRITICAL", reservoirPct = 8f)
        )
        assertNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, critRes, "tomato"))

        val unknownRes = safeSnapshot.copy(
            telemetry = safeSnapshot.telemetry.copy(reservoirValid = false, reservoirState = "SENSOR_ERROR")
        )
        assertNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, unknownRes, "tomato"))

        val rainPaused = safeSnapshot.copy(
            status = safeSnapshot.status.copy(mode = "OUTDOOR", rainSuspensionActive = true)
        )
        assertNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, rainPaused, "eggplant"))

        // Still blocked during Emergency Stop or Fault Lockout
        val estopSnapshot = safeSnapshot.copy(
            status = safeSnapshot.status.copy(emergencyStop = true)
        )
        assertNotNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, estopSnapshot, "tomato"))

        // Still blocked when another valve is currently open (1 valve at a time rule)
        val chiliWatering = safeSnapshot.copy(
            status = safeSnapshot.status.copy(irrigationState = "WATERING", activeValve = "chili")
        )
        assertNotNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, chiliWatering, "tomato"))
    }

    @Test
    fun isDeviceOnlineFresh_handlesServerEpochAndPreNtpBootUptime() {
        val now = 1_795_100_000_000L
        val freshRealEpoch = DeviceStatus(online = true, lastSeenAt = now - 15_000L)
        assertEquals(true, IotDomainLogic.isDeviceOnlineFresh(freshRealEpoch, now, now))

        val staleRealEpoch = DeviceStatus(online = true, lastSeenAt = now - 60_000L)
        assertEquals(false, IotDomainLogic.isDeviceOnlineFresh(staleRealEpoch, now, now))

        val offlineFlag = DeviceStatus(online = false, lastSeenAt = now - 5_000L)
        assertEquals(false, IotDomainLogic.isDeviceOnlineFresh(offlineFlag, now, now))

        // ESP32 rebooted and published millis()=15000 before NTP synced: must NOT falsely report Offline!
        val preNtpReboot = DeviceStatus(online = true, lastSeenAt = 15_000L)
        assertEquals(true, IotDomainLogic.isDeviceOnlineFresh(preNtpReboot, now, now - 5_000L))
    }

    @Test
    fun canRequestDeviceLocationPermission_strictlyOwnerOnly() {
        assertEquals(true, IotDomainLogic.canRequestDeviceLocationPermission(DeviceRole.OWNER))
        assertEquals(false, IotDomainLogic.canRequestDeviceLocationPermission(DeviceRole.OPERATOR))
        assertEquals(false, IotDomainLogic.canRequestDeviceLocationPermission(DeviceRole.VIEWER))
    }

    @Test
    fun shouldDispatchDeviceEventNotification_preventsSensorFaultLoopAndStaleReplay() {
        val now = 1_795_100_000_000L
        val freshSoilFault = DeviceEvent(
            eventId = "spc-unit-0001_boot2_1",
            type = "SOIL_SENSOR_FAULT",
            severity = "warning",
            title = "Soil Sensor Fault",
            message = "Tomato sensor disconnected",
            crop = "tomato",
            createdAt = now - 5_000L
        )

        // First fresh fault passes
        assertEquals(
            true,
            IotDomainLogic.shouldDispatchDeviceEventNotification(
                event = freshSoilFault,
                nowMs = now,
                isAlreadySeen = false,
                lastNotifiedSameTypeMs = null
            )
        )

        // Same fault within 60-min sensor fault cooldown is suppressed even with a brand-new eventId!
        assertEquals(
            false,
            IotDomainLogic.shouldDispatchDeviceEventNotification(
                event = freshSoilFault.copy(eventId = "spc-unit-0001_boot3_1"),
                nowMs = now,
                isAlreadySeen = false,
                lastNotifiedSameTypeMs = now - 10 * 60_000L
            )
        )

        // Allowed again after 60-min cooldown expires
        assertEquals(
            true,
            IotDomainLogic.shouldDispatchDeviceEventNotification(
                event = freshSoilFault.copy(eventId = "spc-unit-0001_boot4_1"),
                nowMs = now,
                isAlreadySeen = false,
                lastNotifiedSameTypeMs = now - 61 * 60_000L
            )
        )

        // Historical event older than 2 minutes is NEVER replayed on sync
        assertEquals(
            false,
            IotDomainLogic.shouldDispatchDeviceEventNotification(
                event = freshSoilFault.copy(createdAt = now - 180_000L),
                nowMs = now,
                isAlreadySeen = false,
                lastNotifiedSameTypeMs = null
            )
        )

        // Stable notification ID matches across different eventIds for the same (device, type, crop)
        assertEquals(
            IotDomainLogic.stableNotificationId("spc-unit-0001", "SOIL_SENSOR_FAULT", "tomato"),
            IotDomainLogic.stableNotificationId("spc-unit-0001", "SOIL_SENSOR_FAULT", "tomato")
        )
    }

    @Test
    fun shouldDispatchDeviceEventNotification_allowsAccidentalRainSensorWetNotification() {
        val now = 1_795_100_000_000L
        val accidentalWetEvent = DeviceEvent(
            eventId = "spc-unit-0001_boot2_9",
            type = "RAIN_SENSOR_WET",
            severity = "info",
            title = "Rain Sensor Wet (Accidental)",
            message = "Rain sensor wet, but weather (20%) did not meet 60% threshold. Accidentally wet — watering continues.",
            crop = null,
            createdAt = now - 3_000L
        )

        // Accidental wet event MUST dispatch a notification (never silently dropped)
        assertEquals(
            true,
            IotDomainLogic.shouldDispatchDeviceEventNotification(
                event = accidentalWetEvent,
                nowMs = now,
                isAlreadySeen = false,
                lastNotifiedSameTypeMs = null
            )
        )

        // Suppressed during 1-minute cooldown so it never spams
        assertEquals(
            false,
            IotDomainLogic.shouldDispatchDeviceEventNotification(
                event = accidentalWetEvent.copy(eventId = "spc-unit-0001_boot2_10"),
                nowMs = now,
                isAlreadySeen = false,
                lastNotifiedSameTypeMs = now - 20_000L
            )
        )
    }
}
