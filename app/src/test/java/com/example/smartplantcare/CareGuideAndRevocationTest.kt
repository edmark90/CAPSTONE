package com.example.smartplantcare

import com.example.smartplantcare.ML.DiseaseRepository
import com.example.smartplantcare.iot.AccessRevocationEvent
import com.example.smartplantcare.iot.DeviceRole
import com.example.smartplantcare.iot.RevocationEventCoordinator
import com.example.smartplantcare.iot.UserDeviceLink
import com.example.smartplantcare.notification.AlertPreferences
import com.example.smartplantcare.notification.IotAlertCategory
import com.example.smartplantcare.ui.theme.screens.homescreen.resolveDiseaseRepositoryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareGuideAndRevocationTest {

    @Test
    fun all22SupportedClassesHaveSafeConsolidatedCareTips() {
        val keys = DiseaseRepository.getAllSupportedKeys()
        assertEquals("Expected all 22 plant/disease classes", 22, keys.size)

        val dayLabelRegex = Regex("""(?i)\bDay\s+[1-7]\b""")
        val forbiddenTerms = listOf(
            "7-day",
            "tank mix",
            "15g/L",
            "1:4",
            "1:9",
            "FRAC",
            "burning",
            "52°C",
            "Acramite",
            "Agri-Mek",
            "14-14-14",
            "20-10-10",
            "10-52-10"
        )

        for (key in keys) {
            val info = DiseaseRepository.getDiseaseInfo(key)
            assertTrue("Missing description for $key", info.description.isNotBlank())
            assertTrue("Missing causes for $key", info.causes.isNotBlank())
            assertTrue("Missing careTips for $key", info.careTips.isNotBlank())
            assertTrue("Missing treatment for $key", info.treatment.isNotBlank())
            assertTrue("Missing preventionTips for $key", info.preventionTips.isNotBlank())

            assertFalse("Day label found in careTips for $key", dayLabelRegex.containsMatchIn(info.careTips))
            assertFalse("Day label found in treatment for $key", dayLabelRegex.containsMatchIn(info.treatment))

            for (term in forbiddenTerms) {
                assertFalse("Forbidden term '$term' in careTips for $key", info.careTips.contains(term, ignoreCase = true))
                assertFalse("Forbidden term '$term' in treatment for $key", info.treatment.contains(term, ignoreCase = true))
                assertFalse("Forbidden term '$term' in preventionTips for $key", info.preventionTips.contains(term, ignoreCase = true))
            }
        }
    }

    @Test
    fun healthyClassesDoNotContainDiseaseTreatmentInstructions() {
        val healthyKeys = listOf("Chili_Healthy_Leaf", "Eggplant_Healthy Leaf", "Tomato_healthy")
        for (key in healthyKeys) {
            val info = DiseaseRepository.getDiseaseInfo(key)
            assertFalse(info.careTips.contains("fungicide", ignoreCase = true))
            assertFalse(info.careTips.contains("bactericide", ignoreCase = true))
            assertFalse(info.careTips.contains("uproot", ignoreCase = true))
        }
    }

    @Test
    fun legacyDiseaseNameFallbackResolvesAll22ClassesIncludingTomatoMosaic() {
        for (key in DiseaseRepository.getAllSupportedKeys()) {
            val info = DiseaseRepository.getDiseaseInfo(key)
            val isHealthy = key.contains("healthy", ignoreCase = true)

            // Exact className lookup
            assertEquals(key, resolveDiseaseRepositoryKey(key, info.diseaseName, isHealthy))
            // Legacy saved record without className
            assertEquals(key, resolveDiseaseRepositoryKey("", info.diseaseName, isHealthy))
        }
    }

    @Test
    fun delayedRevocationEventDoesNotSupersedeNewerAuthorizedAccess() {
        val olderRevocation = AccessRevocationEvent(
            eventId = "rev_1000_aaa",
            deviceId = "spc-unit-0001",
            deviceName = "Backyard Box",
            ownerUid = "owner_1",
            ownerName = "Alice",
            revokedAt = 1_700_000_010_000L
        )
        val newerLink = UserDeviceLink(
            deviceId = "spc-unit-0001",
            role = DeviceRole.VIEWER,
            ownershipEpoch = 1L,
            addedAt = 1_700_000_020_000L
        )
        val olderLink = UserDeviceLink(
            deviceId = "spc-unit-0001",
            role = DeviceRole.VIEWER,
            ownershipEpoch = 1L,
            addedAt = 1_700_000_005_000L
        )

        assertTrue(RevocationEventCoordinator.isSupersededByNewerAccess(olderRevocation, listOf(newerLink)))
        assertFalse(RevocationEventCoordinator.isSupersededByNewerAccess(olderRevocation, listOf(olderLink)))
        assertFalse(RevocationEventCoordinator.isSupersededByNewerAccess(olderRevocation, emptyList()))
    }

    @Test
    fun repeatedRevocationEventsGenerateDistinctNotificationHistoryIds() {
        val id1 = RevocationEventCoordinator.notificationHistoryId("rev_1000_first")
        val id2 = RevocationEventCoordinator.notificationHistoryId("rev_2000_second")
        assertNotEquals(id1, id2)
        assertEquals("revocation_rev_1000_first", id1)
    }

    @Test
    fun alertPreferencesClassifiesIotEventsIntoExpectedCategories() {
        // Water Tank / Reservoir alerts
        assertEquals(IotAlertCategory.WATER_TANK, AlertPreferences.classifyEventCategory("RESERVOIR_LOW"))
        assertEquals(IotAlertCategory.WATER_TANK, AlertPreferences.classifyEventCategory("RESERVOIR_EMPTY"))
        assertEquals(IotAlertCategory.WATER_TANK, AlertPreferences.classifyEventCategory("tank_refill_needed"))

        // Irrigation & Rain alerts
        assertEquals(IotAlertCategory.IRRIGATION_RAIN, AlertPreferences.classifyEventCategory("RAIN_DETECTED"))
        assertEquals(IotAlertCategory.IRRIGATION_RAIN, AlertPreferences.classifyEventCategory("WEATHER_PAUSE"))
        assertEquals(IotAlertCategory.IRRIGATION_RAIN, AlertPreferences.classifyEventCategory("IRRIGATION_STARTED"))
        assertEquals(IotAlertCategory.IRRIGATION_RAIN, AlertPreferences.classifyEventCategory("WATERING_COMPLETE"))
        assertEquals(IotAlertCategory.IRRIGATION_RAIN, AlertPreferences.classifyEventCategory("VALVE_OPEN"))

        // Device & Sensor Health alerts (and fallback)
        assertEquals(IotAlertCategory.DEVICE_HEALTH, AlertPreferences.classifyEventCategory("DEVICE_OFFLINE"))
        assertEquals(IotAlertCategory.DEVICE_HEALTH, AlertPreferences.classifyEventCategory("SENSOR_FAULT"))
        assertEquals(IotAlertCategory.DEVICE_HEALTH, AlertPreferences.classifyEventCategory("HIGH_TEMP_ALERT"))
        assertEquals(IotAlertCategory.DEVICE_HEALTH, AlertPreferences.classifyEventCategory("UNKNOWN_EVENT"))
    }
}
