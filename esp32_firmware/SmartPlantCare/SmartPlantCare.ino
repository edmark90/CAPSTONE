/**
 * @file SmartPlantCare.ino
 * @brief Complete Production ESP32 Firmware for Smart Plant Care V3.1 (I2C 20x4 Edition)
 *
 * AUTHORITATIVE GPIO ASSIGNMENT TABLE (PHASE 2 — DO NOT MODIFY):
 * - 20x4 I2C LCD SDA          : GPIO21 (via 3.3V<->5V bidirectional I2C level shifter)
 * - 20x4 I2C LCD SCL          : GPIO22 (via 3.3V<->5V bidirectional I2C level shifter)
 * - Rain Sensor DO            : GPIO32 (3.3V logic input)
 * - Buzzer                    : GPIO18 (NPN/MOSFET driver recommended)
 * - LDR Light Sensor          : GPIO36 (ADC1_CH0 input-only, 12-bit 0..4095)
 * - DHT22 Temp & Humidity     : GPIO4  (3.3V pull-up)
 * - Tomato Soil Moisture      : GPIO34 (ADC1_CH6 input-only)
 * - Chili Soil Moisture       : GPIO35 (ADC1_CH7 input-only)
 * - Eggplant Soil Moisture    : GPIO33 (ADC1_CH5)
 * - Ultrasonic TRIG           : GPIO16 (Output)
 * - Ultrasonic ECHO           : GPIO17 (Input — MUST use 5V->3.3V divider if 5V HC-SR04)
 * - Tomato Solenoid Relay IN1 : GPIO25 (Output — COM/NO, normally OFF)
 * - Chili Solenoid Relay IN2  : GPIO26 (Output — COM/NO, normally OFF)
 * - Eggplant Solenoid Relay IN3: GPIO27 (Output — COM/NO, normally OFF)
 * - Water Pump Relay IN4      : GPIO23 (Output — COM/NO, normally OFF)
 *
 * CRITICAL HARDWARE SAFETY:
 * - Config::ENABLE_PHYSICAL_LOAD_OUTPUTS defaults to false in Config.h so initial upload
 *   NEVER energizes physical valves or the water pump until COM/NO wiring and active
 *   polarity are verified on the bench with disconnected loads.
 */

#include <Arduino.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "DiagnosticsManager.h"
#include "EnvironmentalSensors.h"
#include "EventNotificationManager.h"
#include "FirebaseClientManager.h"
#include "IrrigationController.h"
#include "LcdManager.h"
#include "Models.h"
#include "Pins.h"
#include "ProvisioningPairingManager.h"
#include "RainWeatherManager.h"
#include "RelayController.h"
#include "ReservoirManager.h"
#include "SecretsTemplate.h"
#include "SoilMoistureManager.h"

namespace {

RuntimeConfig              gRuntimeConfig;
RelayController            gRelays;
BuzzerManager              gBuzzer;
EventNotificationManager   gEvents;
ProvisioningPairingManager gProvisioning;
EnvironmentalSensors       gEnvSensors;
SoilMoistureManager        gSoil;
RainWeatherManager         gRainWeather;
ReservoirManager           gReservoir;
IrrigationController       gIrrigation;
LcdManager                 gLcd;
FirebaseClientManager      gFirebase;
DiagnosticsManager         gDiagnostics;

// Nonblocking scheduler timestamps
uint32_t gLastBuzzerMs      = 0;
uint32_t gLastSafetyMs      = 0;
uint32_t gLastIrrigationMs  = 0;
uint32_t gLastRainMs        = 0;
uint32_t gLastLcdMs         = 0;
uint32_t gLastDhtMs         = 0;
uint32_t gLastReservoirMs   = 0;
uint32_t gLastSoilMs        = 0;
uint32_t gLastLdrMs         = 0;
uint32_t gLastTelemetryMs   = 0;
uint32_t gLastCommandPollMs = 0;
uint32_t gLastConfigPollMs  = 0;
uint32_t gLastMetaPollMs    = 0;
uint32_t gLastWeatherPollMs = 0;
uint32_t gLastHistoryMs     = 0;
uint32_t gLastDiagnosticsMs = 0;

IrrigationState gPrevPublishedIrrState      = IrrigationState::BOOT_SAFE;
bool            gPrevPublishedRainRawWet    = false;
bool            gPrevPublishedRainConfirmed = false;
bool            gPrevPublishedRainAccWet    = false;
bool            gPrevPublishedRainSuspended = false;

}  // namespace

void setup() {
  // 1. FIRST MICROSECOND SAFETY: Preload inactive relay latches BEFORE any other init
  gRelays.begin(Config::RELAY_ACTIVE_LOW, Config::ENABLE_PHYSICAL_LOAD_OUTPUTS);

  Serial.begin(115200);
  delay(50);
  Serial.println();
  Serial.println(F("======================================================================"));
  Serial.printf(" Smart Plant Care Firmware %s (%s)\n",
                Config::FIRMWARE_VERSION,
                Config::HARDWARE_REVISION);
  Serial.printf(" Physical Load Outputs Default: %s\n",
                Config::ENABLE_PHYSICAL_LOAD_OUTPUTS ? "ENABLED" : "DISABLED (BENCH SAFE)");
  Serial.println(F("======================================================================"));

  // 2. Initialize Buzzer (silent on boot unless chirp requested after self-test)
  gBuzzer.begin(Config::BUZZER_IS_PASSIVE);

  // 3. Load NVS Provisioning, Ownership State/Epoch, and Persisted Calibration
  gProvisioning.begin(gRuntimeConfig);
  gRelays.setPhysicalLoadsEnabled(gRuntimeConfig.physicalLoadsEnabled);
  gEvents.begin(gProvisioning.deviceId(),
                gProvisioning.bootId(),
                gProvisioning.ownershipEpoch());

  // 4. Initialize 20x4 I2C LCD on GPIO21 (SDA) / GPIO22 (SCL) with Bus Scanner
  gLcd.begin();

  // 5. Initialize All Sensor Managers
  gEnvSensors.begin();
  gSoil.begin();
  gRainWeather.begin(Config::RAIN_WET_IS_LOW);
  gReservoir.begin();

  // 6. Initialize Irrigation Controller & Cloud / Diagnostics Managers
  gIrrigation.begin(&gRelays, &gSoil, &gReservoir, &gRainWeather, &gBuzzer, &gEvents);
  gFirebase.begin(&gProvisioning);
  gDiagnostics.begin();

  // 7. Initial synchronous baseline sensor sample before entering loop()
  uint32_t nowMs = millis();
  gRainWeather.sampleRainSensor(nowMs, gRuntimeConfig);
  gReservoir.sampleAndEvaluate(nowMs, gRuntimeConfig, gBuzzer, gEvents);
  gSoil.sampleAllZones(nowMs, gRuntimeConfig, gEvents);
  gEnvSensors.sampleLdr(nowMs, gRuntimeConfig, gBuzzer, gEvents);

  // Short 100ms boot ready chirp
  gBuzzer.triggerEventTransition(BuzzerEventId::BOOT_READY_CHIRP);
}

void loop() {
  const uint32_t nowMs = millis();

  // ─── 1. Nonblocking Serial CLI, Owner Setup Portal & Wi-Fi Maintenance ────
  gProvisioning.serviceProvisioningPortal(nowMs,
                                          gRuntimeConfig,
                                          gRelays,
                                          gIrrigation,
                                          gBuzzer,
                                          gEvents,
                                          gLcd);
  gDiagnostics.pollSerialCli(nowMs,
                             gRuntimeConfig,
                             gProvisioning,
                             gEnvSensors,
                             gSoil,
                             gReservoir,
                             gRainWeather,
                             gRelays,
                             gIrrigation,
                             gBuzzer,
                             gEvents,
                             gLcd);
  gFirebase.serviceNetworkAndTime(nowMs, gDiagnostics.metrics(), gBuzzer);
  gIrrigation.setNetworkOnline(gFirebase.isWifiConnected());
  gIrrigation.setOwnershipEpoch(gProvisioning.ownershipEpoch());

  // ─── 2. Nonblocking Buzzer Scheduler (15 ms) ───────────────────────────────
  if (nowMs - gLastBuzzerMs >= Config::INTERVAL_BUZZER_MS) {
    gLastBuzzerMs = nowMs;
    gBuzzer.service(nowMs);
  }

  // ─── 3. Fast Hardware Safety & Interlock Loop (25 ms — HIGHEST PRIORITY) ───
  if (nowMs - gLastSafetyMs >= Config::INTERVAL_SAFETY_MS) {
    gLastSafetyMs = nowMs;
    gDiagnostics.recordSafetyLoopExecution(nowMs);
    gIrrigation.serviceSafetyInterlocks(
        nowMs, gRuntimeConfig, gProvisioning.ownershipState());
  }

  // ─── 4. Rain Sensor DO Sampling & Strict AND-Condition Gate (200 ms) ───────
  if (nowMs - gLastRainMs >= Config::INTERVAL_RAIN_MS) {
    gLastRainMs = nowMs;
    gRainWeather.sampleRainSensor(nowMs, gRuntimeConfig);
    bool newlyLatchedRain = gRainWeather.evaluateRainGate(
        nowMs, gRuntimeConfig, gBuzzer, gEvents);
    if (newlyLatchedRain) {
      // Immediately enforce rain suspension in irrigation state machine
      gIrrigation.serviceSafetyInterlocks(
          nowMs, gRuntimeConfig, gProvisioning.ownershipState());
    }
  }

  // ─── 5. Irrigation State Machine Advancement (50 ms) ───────────────────────
  if (nowMs - gLastIrrigationMs >= Config::INTERVAL_IRRIGATION_MS) {
    gLastIrrigationMs = nowMs;
    gIrrigation.advanceStateMachine(
        nowMs, gRuntimeConfig, gProvisioning.ownershipState());
  }

  // ─── 6. Ultrasonic 7-Inch Water Reservoir Air-Gap Monitor (2500 ms) ────────
  // Skip blocking pulseIn/DHT critical sections while live Wi-Fi password test or initial
  // post-reboot Wi-Fi association (first 30s before WL_CONNECTED) is running so the
  // 2.4 GHz WPA2 handshake and DHCP lease never miss a key frame!
  const bool testingWifiNow =
      gProvisioning.isWifiTestInProgress() ||
      (gProvisioning.hasWifiCredentials() && !gFirebase.isWifiConnected() && nowMs < 30000UL);
  if (!testingWifiNow && (nowMs - gLastReservoirMs >= Config::INTERVAL_RESERVOIR_MS)) {
    gLastReservoirMs = nowMs;
    bool pumpBlocked = gReservoir.sampleAndEvaluate(
        nowMs, gRuntimeConfig, gBuzzer, gEvents);
    if (pumpBlocked) {
      gIrrigation.serviceSafetyInterlocks(
          nowMs, gRuntimeConfig, gProvisioning.ownershipState());
    }
  }

  // ─── 7. DHT22 Temperature & Humidity Monitor (2500 ms) ─────────────────────
  if (!testingWifiNow && (nowMs - gLastDhtMs >= Config::INTERVAL_DHT_MS)) {
    gLastDhtMs = nowMs;
    gEnvSensors.sampleDht22(nowMs, gRuntimeConfig, gBuzzer, gEvents);
  }

  // ─── 8. 3-Zone Capacitive Soil Moisture Monitor (3000 ms) ──────────────────
  if (!testingWifiNow && (nowMs - gLastSoilMs >= Config::INTERVAL_SOIL_MS)) {
    gLastSoilMs = nowMs;
    gSoil.sampleAllZones(nowMs, gRuntimeConfig, gEvents);
  }

  // ─── 9. LDR Relative Brightness Monitor (5000 ms) ──────────────────────────
  if (!testingWifiNow && (nowMs - gLastLdrMs >= Config::INTERVAL_LDR_MS)) {
    gLastLdrMs = nowMs;
    gEnvSensors.sampleLdr(nowMs, gRuntimeConfig, gBuzzer, gEvents);
  }

  // ─── 10. 20x4 I2C LCD Flicker-Free Row Refresh & Rotation (750 ms) ─────────
  if (nowMs - gLastLcdMs >= Config::INTERVAL_LCD_REFRESH_MS) {
    gLastLcdMs = nowMs;
    gLcd.update(nowMs,
                gRuntimeConfig,
                gProvisioning.ownershipState(),
                gFirebase.isWifiConnected(),
                gFirebase.isFirebaseAuthenticated(),
                gFirebase.lastSyncUptimeMs(),
                gEnvSensors.reading(),
                gSoil.allStatuses(),
                gReservoir.reading(),
                gRainWeather.rainStatus(),
                gRainWeather.weather(),
                gIrrigation,
                gProvisioning.displayName());
  }

  // ─── 10B-12C. Cooperative Single-Op-Per-Tick Cloud Sync Scheduler ──────────
  // Executes at most ONE cloud operation per loop() pass so 25ms safety interlocks,
  // 50ms irrigation state transitions, and sensor loops are NEVER starved by stacked HTTP calls.
  // While any valve or pump is energized, non-essential background syncs are deferred.
  const IrrigationState curIrrState = gIrrigation.state();
  const bool actuatorActive =
      gRelays.isPumpOn() ||
      (gRelays.activeValveZone() != CropZoneId::NONE) ||
      (curIrrState == IrrigationState::QUEUED ||
       curIrrState == IrrigationState::OPENING_SELECTED_VALVE ||
       curIrrState == IrrigationState::PUMP_WATERING ||
       curIrrState == IrrigationState::PUMP_STOPPING ||
       curIrrState == IrrigationState::CLOSING_VALVE);
  gFirebase.setActuatorBusy(actuatorActive);

  const bool initialBootSyncDone = (gFirebase.lastSyncUptimeMs() > 0) || (nowMs > 20000UL);
  const uint32_t telemetryIntervalMs =
      (gProvisioning.ownershipState() == OwnershipState::OWNED &&
       gFirebase.isWifiConnected() &&
       gFirebase.lastSyncUptimeMs() == 0)
          ? 3000UL
          : Config::INTERVAL_TELEMETRY_MS;
  const RainGateStatus& curRain = gRainWeather.rainStatus();
  const bool irrStateChanged = (curIrrState != gPrevPublishedIrrState);
  const bool rainStateChanged =
      (curRain.rawWet != gPrevPublishedRainRawWet) ||
      (curRain.wetConfirmed != gPrevPublishedRainConfirmed) ||
      (curRain.accidentalWet != gPrevPublishedRainAccWet) ||
      (curRain.rainSuspensionLatched != gPrevPublishedRainSuspended);
  const bool rainPushAllowed = rainStateChanged && (nowMs - gLastTelemetryMs >= 1500UL);
  const bool telemetryDue =
      irrStateChanged || rainPushAllowed || (nowMs - gLastTelemetryMs >= telemetryIntervalMs);

  const uint32_t metaIntervalMs =
      (gFirebase.isWifiConnected() && gFirebase.lastSyncUptimeMs() == 0)
          ? 4000UL
          : Config::INTERVAL_META_POLL_MS;

  if (telemetryDue) {
    // Priority 1: Live status & telemetry snapshot (keeps mobile dashboard immediate)
    gLastTelemetryMs            = nowMs;
    gPrevPublishedIrrState      = curIrrState;
    gPrevPublishedRainRawWet    = curRain.rawWet;
    gPrevPublishedRainConfirmed = curRain.wetConfirmed;
    gPrevPublishedRainAccWet    = curRain.accidentalWet;
    gPrevPublishedRainSuspended = curRain.rainSuspensionLatched;

    gDiagnostics.updatePeriodicMetrics(nowMs, gEvents);
    const bool publishedOk = gFirebase.publishStatusAndTelemetry(nowMs,
                                        gRuntimeConfig,
                                        gEnvSensors.reading(),
                                        gSoil.allStatuses(),
                                        gReservoir.reading(),
                                        gRainWeather.rainStatus(),
                                        gRelays,
                                        gIrrigation,
                                        gLcd.detectedAddress(),
                                        gDiagnostics.metrics());
    if (publishedOk) {
      gFirebase.recordLiveSampleForHistory(nowMs,
                                           gEnvSensors.reading(),
                                           gSoil.allStatuses(),
                                           gReservoir.reading(),
                                           gRainWeather.rainStatus(),
                                           gRainWeather.weather());
    }
  } else if (initialBootSyncDone &&
             gFirebase.hasPendingUrgentWork(gEvents, gIrrigation)) {
    // Priority 2A: Flush queued command execution reports & transition events on their own tick
    gFirebase.flushPendingEventsAndReports(
        nowMs, gEvents, gIrrigation, gDiagnostics.metrics());
  } else if (initialBootSyncDone &&
             (nowMs - gLastCommandPollMs >= Config::INTERVAL_COMMAND_POLL_MS)) {
    // Priority 2B: Fast manual watering & emergency stop command poll (1.8s)
    gLastCommandPollMs = nowMs;
    gFirebase.pollPendingCommandsOnly(nowMs, gRuntimeConfig, gIrrigation, gBuzzer);
  } else if (!actuatorActive && (nowMs - gLastMetaPollMs >= metaIntervalMs)) {
    // Priority 3A: Ownership / reset / box GPS metadata sync
    gLastMetaPollMs = nowMs;
    gFirebase.pollMetaOnly(nowMs,
                           gRuntimeConfig,
                           gRelays,
                           gIrrigation,
                           gBuzzer,
                           gEvents,
                           gLcd);
  } else if (!actuatorActive && initialBootSyncDone &&
             (nowMs - gLastHistoryMs >= Config::INTERVAL_HISTORY_SERVICE_MS) &&
             (nowMs - gLastTelemetryMs >= 1200UL)) {
    // Priority 3B: Authoritative 7-day minute & hourly history + irrigation records + PHT pruning
    gLastHistoryMs = nowMs;
    gFirebase.serviceHistorySyncAndRetention(nowMs, gIrrigation, gDiagnostics.metrics());
  } else if (!actuatorActive && initialBootSyncDone &&
             (nowMs - gLastConfigPollMs >= Config::INTERVAL_CONFIG_POLL_MS) &&
             (nowMs - gLastTelemetryMs >= 1500UL)) {
    // Priority 3C: Staggered /config threshold sync
    gLastConfigPollMs = nowMs;
    gFirebase.pollConfigOnly(nowMs, gRuntimeConfig, gRelays, gBuzzer);
  } else if (!actuatorActive && initialBootSyncDone &&
             (gFirebase.consumeWeatherRefreshRequested() ||
              (!gRainWeather.weather().valid && (nowMs - gLastWeatherPollMs >= 5000UL)) ||
              (nowMs - gLastWeatherPollMs >= Config::INTERVAL_WEATHER_POLL_MS))) {
    // Priority 3D: Staggered Open-Meteo /weather sync
    gLastWeatherPollMs = nowMs;
    gFirebase.pollWeatherOnly(nowMs, gRainWeather);
  }

  // ─── 13. Periodic Heap & Uptime Health Summary (60 s) ──────────────────────
  if (nowMs - gLastDiagnosticsMs >= Config::INTERVAL_DIAGNOSTICS_MS) {
    gLastDiagnosticsMs = nowMs;
    gDiagnostics.updatePeriodicMetrics(nowMs, gEvents);
  }
}
