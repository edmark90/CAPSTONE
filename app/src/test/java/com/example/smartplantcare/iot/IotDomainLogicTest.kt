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

        // Blocked when Reservoir is CRITICAL or UNKNOWN
        val critRes = safeSnapshot.copy(
            telemetry = safeSnapshot.telemetry.copy(reservoirState = "CRITICAL", reservoirPct = 8f)
        )
        assertNotNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, critRes, "tomato"))

        val unknownRes = safeSnapshot.copy(
            telemetry = safeSnapshot.telemetry.copy(reservoirValid = false, reservoirState = "SENSOR_ERROR")
        )
        assertNotNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, unknownRes, "tomato"))

        // Blocked when Outdoor Rain Suspension is latched
        val rainPaused = safeSnapshot.copy(
            status = safeSnapshot.status.copy(mode = "OUTDOOR", rainSuspensionActive = true)
        )
        assertNotNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, rainPaused, "eggplant"))

        // Allowed in Indoor Mode even if rainSuspensionActive flag was stale
        val indoorMode = rainPaused.copy(
            status = rainPaused.status.copy(mode = "INDOOR")
        )
        assertNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, indoorMode, "eggplant"))

        // Blocked when another valve is currently open (1 valve at a time rule)
        val chiliWatering = safeSnapshot.copy(
            status = safeSnapshot.status.copy(irrigationState = "WATERING", activeValve = "chili")
        )
        assertNotNull(IotDomainLogic.manualWaterLockReason(DeviceRole.OWNER, chiliWatering, "tomato"))
    }
}
