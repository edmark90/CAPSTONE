package com.example.smartplantcare.iot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun generateSevenDayWindowPht_returnsExactlySevenDaysEndingTodayInPhilippineTime() {
        // 2026-11-22T22:15:00+08:00 (2026-11-22T14:15:00Z) -> epoch ms = 1795356900000L
        val nowMs = 1_795_356_900_000L
        val window = IotDomainLogic.generateSevenDayWindowPht(nowMs = nowMs)

        assertEquals(7, window.size)
        assertEquals("2026-11-16", window.first().dateKey)
        assertEquals("2026-11-22", window.last().dateKey)
        assertEquals(true, window.last().isToday)
        assertEquals(false, window.first().isToday)
        assertEquals("2026-11-16", IotDomainLogic.sevenDayCutoffDateKeyPht(nowMs))
    }

    @Test
    fun buildSegmentedHourlySeries_breaksLineOnOfflineHoursBoundaryOutagesAndNullSensors_whilePreservingZero() {
        // Base hour 08:00 PHT on 2026-11-22 -> 2026-11-22T00:00:00Z = 1795305600000L
        val h08Start = 1_795_305_600_000L
        val h09Start = h08Start + 3_600_000L
        val h10Start = h08Start + 2 * 3_600_000L
        val h13Start = h08Start + 5 * 3_600_000L
        val h14Start = h08Start + 6 * 3_600_000L

        val day = DailyHistorySnapshot(
            deviceId = "spc-unit-0001",
            dateKey = "2026-11-22",
            hours = mapOf(
                // Hours 8 & 9 continuous online (including legitimate 0.0% light value at hour 8!)
                8 to HistoricalHourRecord(
                    hour = 8,
                    firstSeenAt = h08Start + 5_000L,
                    lastSeenAt = h08Start + 3_590_000L,
                    sampleCount = 500,
                    lightPct = 0.0f
                ),
                9 to HistoricalHourRecord(
                    hour = 9,
                    firstSeenAt = h09Start + 5_000L,
                    lastSeenAt = h09Start + 300_000L, // went offline at 9:05 AM!
                    sampleCount = 40,
                    lightPct = 42.5f
                ),
                // Hour 10 reconnected at 10:50 AM (> 15 min boundary gap after 9:05 AM!)
                10 to HistoricalHourRecord(
                    hour = 10,
                    firstSeenAt = h10Start + 3_000_000L,
                    lastSeenAt = h10Start + 3_590_000L,
                    sampleCount = 60,
                    lightPct = 70.0f
                ),
                // Hours 11 & 12 completely offline (missing)
                // Hour 13 has two segments separated by 25-min intra-hour outage
                13 to HistoricalHourRecord(
                    hour = 13,
                    firstSeenAt = h13Start + 60_000L,
                    lastSeenAt = h13Start + 3_300_000L,
                    sampleCount = 120,
                    gapCount = 1,
                    lightPct = 80.0f,
                    segments = listOf(
                        HistoricalHourSegment("s0", h13Start + 60_000L, h13Start + 600_000L, 40, lightPct = 78.0f),
                        HistoricalHourSegment("s1", h13Start + 2_100_000L, h13Start + 3_300_000L, 80, lightPct = 82.0f)
                    )
                ),
                // Hour 14 sensor fault (lightPct = null) must break and NOT plot 0
                14 to HistoricalHourRecord(
                    hour = 14,
                    firstSeenAt = h14Start + 10_000L,
                    lastSeenAt = h14Start + 3_590_000L,
                    sampleCount = 500,
                    lightPct = null
                )
            )
        )

        val result = IotDomainLogic.buildSegmentedHourlySeries(day = day, isToday = false) { rec, seg ->
            seg?.lightPct ?: rec.lightPct
        }

        // Expect 4 disconnected segments:
        // [0]: hours 8 (0.0f) & 9 (42.5f)
        // [1]: hour 10 (70.0f) — broken from hour 9 due to 1h45m boundary outage
        // [2]: hour 13 s0 (78.0f) — broken from hour 10 due to missing hours 11-12
        // [3]: hour 13 s1 (82.0f) — broken from hour 13 s0 due to 25m intra-hour outage
        // Hour 14 (null) is omitted completely!
        assertEquals(4, result.segments.size)
        assertEquals(2, result.segments[0].size)
        assertEquals(0.0f, result.segments[0][0].value)
        assertEquals(42.5f, result.segments[0][1].value)
        assertEquals(1, result.segments[1].size)
        assertEquals(70.0f, result.segments[1][0].value)
        assertEquals(1, result.segments[2].size)
        assertEquals(78.0f, result.segments[2][0].value)
        assertEquals(1, result.segments[3].size)
        assertEquals(82.0f, result.segments[3][0].value)
        assertEquals(3, result.gaps.size)
    }

    @Test
    fun averageValidSoilMoisture_ignoresNullsAndReturnsNullWhenAllInvalid() {
        assertEquals(30.0f, IotDomainLogic.averageValidSoilMoisture(28f, 30f, 32f)!!, 0.01f)
        assertEquals(30.0f, IotDomainLogic.averageValidSoilMoisture(28f, null, 32f)!!, 0.01f)
        assertEquals(0.0f, IotDomainLogic.averageValidSoilMoisture(0f, null, null)!!, 0.01f)
        assertNull(IotDomainLogic.averageValidSoilMoisture(null, null, null))
    }

    @Test
    fun appendLivePointIfFresh_neverBridgesAcrossOfflineOutageAndIgnoresOffline() {
        // 08:00 PHT on 2026-11-22 -> 1795305600000L
        val h08Start = 1_795_305_600_000L
        val base = SegmentedChartSeries(
            segments = listOf(
                listOf(
                    ChartDataPoint(xHour = 8.0f, value = 30.0f, epochMs = h08Start + 60_000L, hour = 8)
                )
            ),
            gaps = emptyList()
        )

        // When device is offline, series must stay unchanged
        val offlineRes = IotDomainLogic.appendLivePointIfFresh(
            base = base,
            liveValue = 31.2f,
            liveEpochMs = h08Start + 300_000L,
            isToday = true,
            isDeviceOnline = false
        )
        assertEquals(1, offlineRes.segments.size)
        assertEquals(1, offlineRes.segments[0].size)

        // When online within continuity window (5 min later), appends smoothly to same segment
        val continuousRes = IotDomainLogic.appendLivePointIfFresh(
            base = base,
            liveValue = 31.2f,
            liveEpochMs = h08Start + 360_000L, // 8:06 AM
            isToday = true,
            isDeviceOnline = true
        )
        assertEquals(1, continuousRes.segments.size)
        assertEquals(2, continuousRes.segments[0].size)
        assertEquals(31.2f, continuousRes.segments[0].last().value, 0.01f)

        // When online AFTER a 2-hour outage (10:15 AM), starts a NEW disconnected segment + adds gap
        val reconnectedRes = IotDomainLogic.appendLivePointIfFresh(
            base = base,
            liveValue = 32.5f,
            liveEpochMs = h08Start + 2 * 3_600_000L + 900_000L,
            isToday = true,
            isDeviceOnline = true
        )
        assertEquals(2, reconnectedRes.segments.size)
        assertEquals(1, reconnectedRes.segments[1].size)
        assertEquals(32.5f, reconnectedRes.segments[1][0].value, 0.01f)
        assertEquals(1, reconnectedRes.gaps.size)

        // Cached snapshot must NEVER be appended as a live point
        val cachedIgnored = IotDomainLogic.appendLivePointIfFresh(
            base = base,
            liveValue = 33.0f,
            liveEpochMs = h08Start + 180_000L,
            isToday = true,
            isDeviceOnline = true,
            isFromLocalCache = true
        )
        assertEquals(1, cachedIgnored.segments.size)
        assertEquals(1, cachedIgnored.segments[0].size)
    }

    @Test
    fun buildSegmentedMinuteSeries_breaksImmediatelyOnSingleMissingMinute_andPreservesZero() {
        // 2026-11-22 02:00 PHT -> 2026-11-21T18:00:00Z = 1795284000000L
        val m120Start = 1_795_284_000_000L
        val day = DailyHistorySnapshot(
            deviceId = "spc-unit-0001",
            dateKey = "2026-11-22",
            minutes = mapOf(
                120 to HistoricalMinuteRecord(
                    minuteOfDay = 120,
                    firstSeenAt = m120Start + 1_000L,
                    lastSeenAt = m120Start + 55_000L,
                    sampleCount = 9,
                    tomatoPct = 44.0f,
                    lightPct = 0.0f // legitimate zero must be preserved!
                ),
                121 to HistoricalMinuteRecord(
                    minuteOfDay = 121,
                    firstSeenAt = m120Start + 61_000L,
                    lastSeenAt = m120Start + 115_000L,
                    sampleCount = 9,
                    tomatoPct = 43.5f,
                    lightPct = 0.0f
                ),
                // Minute 122 (02:02 AM) is MISSING — line MUST break immediately without waiting 3 minutes!
                123 to HistoricalMinuteRecord(
                    minuteOfDay = 123,
                    firstSeenAt = m120Start + 181_000L,
                    lastSeenAt = m120Start + 235_000L,
                    sampleCount = 9,
                    tomatoPct = 42.0f,
                    lightPct = 5.0f
                ),
                124 to HistoricalMinuteRecord(
                    minuteOfDay = 124,
                    firstSeenAt = m120Start + 241_000L,
                    lastSeenAt = m120Start + 295_000L,
                    sampleCount = 9,
                    tomatoPct = null, // sensor fault: breaks tomato line, never plots 0!
                    lightPct = 6.0f
                )
            )
        )

        // 1) All Day minute view (selectedHour = null)
        val allDayLight = IotDomainLogic.buildSegmentedMinuteSeries(
            day = day,
            selectedHour = null,
            isToday = false
        ) { it.lightPct }
        assertEquals(2, allDayLight.segments.size)
        assertEquals(2, allDayLight.segments[0].size)
        assertEquals(0.0f, allDayLight.segments[0][0].value, 0.001f)
        assertEquals(2, allDayLight.segments[1].size)
        assertEquals(1, allDayLight.gaps.size)

        // 2) Single-hour (:00–:59) minute view for 2 AM (selectedHour = 2) on Tomato (with minute 124 sensor fault)
        val hour2Tomato = IotDomainLogic.buildSegmentedMinuteSeries(
            day = day,
            selectedHour = 2,
            isToday = false
        ) { it.tomatoPct }
        assertEquals(2, hour2Tomato.segments.size)
        assertEquals(listOf(0f, 1f), hour2Tomato.segments[0].map { it.xHour })
        assertEquals(listOf(3f), hour2Tomato.segments[1].map { it.xHour })
        assertEquals(1, hour2Tomato.gaps.size)
    }

    @Test
    fun appendLiveMinutePointIfFresh_rejectsCacheAndBreaksOnMissedMinute() {
        // 2026-11-22 02:10 PHT -> mOfDay = 130
        val m130Epoch = 1_795_284_000_000L + 10 * 60_000L + 20_000L
        val base = SegmentedChartSeries(
            segments = listOf(
                listOf(
                    ChartDataPoint(
                        xHour = 10f,
                        value = 50.0f,
                        epochMs = m130Epoch,
                        hour = 2,
                        minuteOfDay = 130
                    )
                )
            ),
            gaps = emptyList()
        )

        // Local cache snapshot must never append onto minute chart
        val cachedRes = IotDomainLogic.appendLiveMinutePointIfFresh(
            base = base,
            selectedHour = 2,
            liveValue = 51.0f,
            liveEpochMs = m130Epoch + 60_000L,
            isToday = true,
            isDeviceOnline = true,
            isFromLocalCache = true
        )
        assertEquals(1, cachedRes.segments.size)
        assertEquals(1, cachedRes.segments[0].size)

        // Consecutive minute (:11 -> mOfDay = 131) extends current segment
        val consecutiveRes = IotDomainLogic.appendLiveMinutePointIfFresh(
            base = base,
            selectedHour = 2,
            liveValue = 51.0f,
            liveEpochMs = m130Epoch + 60_000L,
            isToday = true,
            isDeviceOnline = true,
            isFromLocalCache = false
        )
        assertEquals(1, consecutiveRes.segments.size)
        assertEquals(2, consecutiveRes.segments[0].size)

        // Skipping 1 minute (:12 -> mOfDay = 132, :11 missing) MUST break into a 2nd segment!
        val gappedRes = IotDomainLogic.appendLiveMinutePointIfFresh(
            base = base,
            selectedHour = 2,
            liveValue = 52.0f,
            liveEpochMs = m130Epoch + 120_000L,
            isToday = true,
            isDeviceOnline = true,
            isFromLocalCache = false
        )
        assertEquals(2, gappedRes.segments.size)
        assertEquals(1, gappedRes.gaps.size)
    }

    @Test
    fun classifyMinuteInspectionStatus_distinguishesFutureSensorFaultLegacyPhoneOfflineAndDeviceOffline() {
        // 2026-11-22 10:30 PHT -> mOfDay = 630
        // 2026-11-22 00:00 PHT = 2026-11-21T16:00:00Z = 1795276800000L
        val nowMs = 1_795_276_800_000L + 630 * 60_000L + 15_000L
        val day = DailyHistorySnapshot(
            deviceId = "spc-unit-0001",
            dateKey = "2026-11-22",
            hours = mapOf(
                9 to HistoricalHourRecord(hour = 9, sampleCount = 300, temperatureC = 30.2f)
            ),
            minutes = mapOf(
                610 to HistoricalMinuteRecord(minuteOfDay = 610, sampleCount = 8, temperatureC = null)
            )
        )

        // Future minute (> 630 on Today)
        assertEquals(
            PointInspectionStatus.FUTURE_TIME,
            IotDomainLogic.classifyMinuteInspectionStatus(day, 645, false, isToday = true, isPhoneOnline = true, nowMs = nowMs)
        )
        // Sensor fault (ESP32 uploaded minute 610 with sampleCount > 0, but temperatureC == null)
        assertEquals(
            PointInspectionStatus.SENSOR_FAULT,
            IotDomainLogic.classifyMinuteInspectionStatus(day, 610, false, isToday = true, isPhoneOnline = true, nowMs = nowMs)
        )
        // Legacy hourly-only (hour 9 has hourly data, zero minute records exist for hour 9)
        assertEquals(
            PointInspectionStatus.LEGACY_HOURLY_ONLY,
            IotDomainLogic.classifyMinuteInspectionStatus(day, 9 * 60 + 15, false, isToday = true, isPhoneOnline = true, nowMs = nowMs)
        )
        // Phone offline on Today when minute missing
        assertEquals(
            PointInspectionStatus.PHONE_OFFLINE,
            IotDomainLogic.classifyMinuteInspectionStatus(day, 620, false, isToday = true, isPhoneOnline = false, nowMs = nowMs)
        )
        // Device offline when phone is online and minute missing
        assertEquals(
            PointInspectionStatus.DEVICE_OFFLINE,
            IotDomainLogic.classifyMinuteInspectionStatus(day, 620, false, isToday = true, isPhoneOnline = true, nowMs = nowMs)
        )
    }

    @Test
    fun generateDeterministicCommandId_isIdempotentAndFitsEsp32Buffer() {
        val t0 = 1_795_300_001_200L
        val idA = IotDomainLogic.generateDeterministicCommandId(
            uid = "user_abc_123456",
            nowMs = t0,
            randomNonce = 0x4A7F
        )
        val idSameInvocation = IotDomainLogic.generateDeterministicCommandId(
            uid = "user_abc_123456",
            nowMs = t0,
            randomNonce = 0x4A7F
        )
        val idDifferentNonce = IotDomainLogic.generateDeterministicCommandId(
            uid = "user_abc_123456",
            nowMs = t0,
            randomNonce = 0x9B12
        )

        assertTrue("cmdId length (${idA.length}) must fit ESP32 40-char NVS buffer", idA.length <= 36)
        assertEquals(idA, idSameInvocation)
        assertNotEquals(idA, idDifferentNonce)
    }

    @Test
    fun formatPhStartEndTimeRange_formatsExactStartAndEndInPhilippineTime() {
        // 2026-11-22 14:05:10 PHT -> 2026-11-22T06:05:10Z = 1795327510000L
        val startMs = 1_795_327_510_000L
        val endMs = startMs + 4_000L
        val formatted = IotDomainLogic.formatPhStartEndTimeRange(startMs, endMs)
        assertEquals("2:05:10 PM – 2:05:14 PM", formatted)
    }
}
