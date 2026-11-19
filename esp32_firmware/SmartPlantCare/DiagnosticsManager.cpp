/**
 * @file DiagnosticsManager.cpp
 * @brief Implementation of runtime health diagnostics and interactive Serial bench CLI.
 */

#include "DiagnosticsManager.h"
#include <esp_system.h>
#include <stdlib.h>
#include <string.h>

void DiagnosticsManager::begin() {
  metrics_.freeHeapBytes    = ESP.getFreeHeap();
  metrics_.minFreeHeapBytes = ESP.getMinFreeHeap();
  metrics_.lastSafetyCheckMs = millis();

  esp_reset_reason_t reason = esp_reset_reason();
  Serial.printf("[DIAG] Boot reset reason code=%d, FreeHeap=%lu B, MinFreeHeap=%lu B\n",
                static_cast<int>(reason),
                static_cast<unsigned long>(metrics_.freeHeapBytes),
                static_cast<unsigned long>(metrics_.minFreeHeapBytes));
  Serial.println(F("[DIAG] Type 'help' in Serial Monitor (115200) for bench test commands."));
}

void DiagnosticsManager::recordSafetyLoopExecution(uint32_t nowMs) {
  if (metrics_.lastSafetyCheckMs != 0) {
    uint32_t delta = nowMs - metrics_.lastSafetyCheckMs;
    if (delta > metrics_.maxSafetyLoopDeltaMs) {
      metrics_.maxSafetyLoopDeltaMs = delta;
    }
  }
  metrics_.lastSafetyCheckMs = nowMs;
}

void DiagnosticsManager::updatePeriodicMetrics(uint32_t nowMs,
                                               const EventNotificationManager& events) {
  (void)nowMs;
  metrics_.freeHeapBytes     = ESP.getFreeHeap();
  metrics_.minFreeHeapBytes  = ESP.getMinFreeHeap();
  metrics_.droppedEventCount = events.droppedCount();
}

void DiagnosticsManager::printStatusReport(
    uint32_t nowMs,
    const RuntimeConfig& cfg,
    const ProvisioningPairingManager& provisioning,
    const EnvironmentReading& env,
    const SoilZoneStatus soil[3],
    const ReservoirReading& reservoir,
    const RainGateStatus& rain,
    const WeatherSnapshot& weather,
    const RelayController& relays,
    const IrrigationController& irrigation,
    const LcdManager& lcd) const {
  Serial.println(F("==================== SMART PLANT CARE V3.1 STATUS ===================="));
  Serial.printf("Uptime: %lu s | Device: %s | BootId: %s | State: %s (Epoch %lu)\n",
                static_cast<unsigned long>(nowMs / 1000UL),
                provisioning.deviceId(),
                provisioning.bootId(),
                ProvisioningPairingManager::ownershipStateToString(provisioning.ownershipState()),
                static_cast<unsigned long>(provisioning.ownershipEpoch()));
  Serial.printf("Mode: %s | LoadsEnabled: %s | LCD(0x%02X): %s | Heap: %lu B (min %lu B) | MaxSafetyDelta: %lu ms\n",
                (cfg.mode == DeviceMode::OUTDOOR) ? "OUTDOOR" : "INDOOR",
                relays.isPhysicalLoadsEnabled() ? "YES" : "NO (BENCH_SAFE)",
                lcd.detectedAddress(),
                lcd.isAvailable() ? "OK" : "NOT_FOUND",
                static_cast<unsigned long>(metrics_.freeHeapBytes),
                static_cast<unsigned long>(metrics_.minFreeHeapBytes),
                static_cast<unsigned long>(metrics_.maxSafetyLoopDeltaMs));
  Serial.printf("DHT22(GPIO4): valid=%s Temp=%.1fC(%s) Hum=%.1f%%(%s) | LDR(GPIO36): raw=%d Brt=%.1f%%(%s)\n",
                env.dhtValid ? "Y" : "N",
                env.temperatureC,
                envBandShort(env.tempBand),
                env.humidityRh,
                envBandShort(env.humidityBand),
                env.ldrRaw,
                env.brightnessPct,
                envBandShort(env.lightBand));
  Serial.printf("Soil Tomato(34): raw=%d %.1f%% valid=%s dry=%s | Chili(35): raw=%d %.1f%% valid=%s dry=%s | Eggpl(33): raw=%d %.1f%% valid=%s dry=%s\n",
                soil[0].rawAdc, soil[0].moisturePct, soil[0].valid ? "Y" : "N", soil[0].needsWater ? "Y" : "N",
                soil[1].rawAdc, soil[1].moisturePct, soil[1].valid ? "Y" : "N", soil[1].needsWater ? "Y" : "N",
                soil[2].rawAdc, soil[2].moisturePct, soil[2].valid ? "Y" : "N", soil[2].needsWater ? "Y" : "N");
  Serial.printf("Reservoir(16/17): valid=%s AirGap=%.2f in Level=%.1f%% State=%s\n",
                reservoir.valid ? "Y" : "N",
                reservoir.airGapInches,
                reservoir.waterLevelPct,
                reservoirStateString(reservoir.state));
  Serial.printf("Rain(32) & Weather: rawWet=%s wetConf=%s(wetMs=%lu,dryMs=%lu) Forecast=%d%%(valid=%s,fresh=%s) Latch=%s Advisory=%s\n",
                rain.rawWet ? "WET" : "DRY",
                rain.wetConfirmed ? "YES" : "NO",
                static_cast<unsigned long>(rain.wetDurationMs),
                static_cast<unsigned long>(rain.dryDurationMs),
                weather.probabilityPct,
                weather.valid ? "Y" : "N",
                weather.fresh ? "Y" : "N",
                rain.rainSuspensionLatched ? "PAUSED" : "CLEAR",
                rain.forecastAdvisoryActive ? "WARN" : "NONE");
  Serial.printf("Irrigation: State=%s ActiveZone=%s Valve=%s Pump=%s EStop=%s FaultLock=%s LastAbort=%s\n",
                irrigationStateString(irrigation.state()),
                cropZoneDisplayName(irrigation.activeZone()),
                cropZoneDisplayName(relays.activeValveZone()),
                relays.isPumpOn() ? "ON" : "OFF",
                irrigation.isEmergencyStopped() ? "YES" : "NO",
                irrigation.isFaultLocked() ? "YES" : "NO",
                irrigation.lastAbortReason());
  Serial.println(F("======================================================================"));
}

void DiagnosticsManager::pollSerialCli(
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    ProvisioningPairingManager& provisioning,
    EnvironmentalSensors& envSensors,
    SoilMoistureManager& soil,
    ReservoirManager& reservoir,
    RainWeatherManager& rainWeather,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  while (Serial.available() > 0) {
    char c = static_cast<char>(Serial.read());
    if (c == '\r') continue;
    if (c == '\n') {
      cliBuffer_[cliLen_] = '\0';
      if (cliLen_ > 0) {
        processCliLine(cliBuffer_,
                       nowMs,
                       ioConfig,
                       provisioning,
                       envSensors,
                       soil,
                       reservoir,
                       rainWeather,
                       relays,
                       irrigation,
                       buzzer,
                       events,
                       lcd);
      }
      cliLen_ = 0;
    } else if (cliLen_ + 1 < sizeof(cliBuffer_)) {
      cliBuffer_[cliLen_++] = c;
    }
  }
}

void DiagnosticsManager::processCliLine(
    char* line,
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    ProvisioningPairingManager& provisioning,
    EnvironmentalSensors& envSensors,
    SoilMoistureManager& soil,
    ReservoirManager& reservoir,
    RainWeatherManager& rainWeather,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  char* cmd = strtok(line, " ");
  if (!cmd) return;

  if (strcmp(cmd, "help") == 0) {
    printCliHelp();
  } else if (strcmp(cmd, "status") == 0) {
    printStatusReport(nowMs,
                      ioConfig,
                      provisioning,
                      envSensors.reading(),
                      soil.allStatuses(),
                      reservoir.reading(),
                      rainWeather.rainStatus(),
                      rainWeather.weather(),
                      relays,
                      irrigation,
                      lcd);
  } else if (strcmp(cmd, "wifi") == 0) {
    char* ssid = strtok(nullptr, " ");
    char* pass = strtok(nullptr, "");
    if (ssid) {
      provisioning.saveWifiCredentials(ssid, pass ? pass : "");
    } else {
      Serial.println(F("Usage: wifi <ssid> <password>"));
    }
  } else if (strcmp(cmd, "claimstate") == 0) {
    char* stStr    = strtok(nullptr, " ");
    char* epochStr = strtok(nullptr, " ");
    uint32_t ep    = epochStr ? static_cast<uint32_t>(strtoul(epochStr, nullptr, 10))
                              : (provisioning.ownershipEpoch() + 1);
    if (stStr) {
      provisioning.handleCloudMetaSnapshot(
          stStr, ep, strcmp(stStr, "RESET_PENDING") == 0, ioConfig, relays, irrigation, buzzer, events, lcd);
    } else {
      Serial.println(F("Usage: claimstate <UNCLAIMED|OWNED|RESET_PENDING> [epoch]"));
    }
  } else if (strcmp(cmd, "mode") == 0) {
    char* m = strtok(nullptr, " ");
    if (m && strcmp(m, "INDOOR") == 0) {
      ioConfig.mode = DeviceMode::INDOOR;
      Serial.println(F("[CLI] Mode set to INDOOR."));
    } else {
      ioConfig.mode = DeviceMode::OUTDOOR;
      Serial.println(F("[CLI] Mode set to OUTDOOR."));
    }
  } else if (strcmp(cmd, "simweather") == 0) {
    char* pStr = strtok(nullptr, " ");
    int prob   = pStr ? atoi(pStr) : -1;
    bool valid = (prob >= 0 && prob <= 100);
    rainWeather.updateWeatherSnapshot(valid, prob, nowMs, nowMs + 3600000UL, nowMs, "NEXT 3 H");
    Serial.printf("[CLI] Injected simulated Open-Meteo weather: valid=%s prob=%d%%\n",
                  valid ? "YES" : "NO", prob);
  } else if (strcmp(cmd, "enableloads") == 0) {
    ioConfig.physicalLoadsEnabled = true;
    relays.setPhysicalLoadsEnabled(true);
    Serial.println(F("[CLI] Physical relay loads ENABLED. Verify COM/NO wiring!"));
  } else if (strcmp(cmd, "disableloads") == 0) {
    ioConfig.physicalLoadsEnabled = false;
    relays.setPhysicalLoadsEnabled(false);
    Serial.println(F("[CLI] Physical relay loads DISABLED (safe bench mode)."));
  } else if (strcmp(cmd, "water") == 0) {
    char* cropStr = strtok(nullptr, " ");
    char* durStr  = strtok(nullptr, " ");
    CommandRequest req;
    req.valid = true;
    snprintf(req.commandId, sizeof(req.commandId), "cli-%lu", static_cast<unsigned long>(nowMs));
    req.type = CommandType::WATER_CROP;
    if (cropStr && strcmp(cropStr, "tomato") == 0) req.crop = CropZoneId::TOMATO;
    else if (cropStr && strcmp(cropStr, "chili") == 0) req.crop = CropZoneId::CHILI;
    else if (cropStr && strcmp(cropStr, "eggplant") == 0) req.crop = CropZoneId::EGGPLANT;
    else req.crop = CropZoneId::NONE;
    req.durationMs     = durStr ? static_cast<uint32_t>(strtoul(durStr, nullptr, 10))
                                : Config::DEFAULT_WATER_PULSE_MS;
    req.ownershipEpoch = provisioning.ownershipEpoch();
    req.createdAtMs    = nowMs;
    req.expiresAtMs    = nowMs + 30000UL;
    irrigation.handleCommand(
        req, nowMs, nowMs, provisioning.ownershipEpoch(), ioConfig, provisioning.ownershipState());
  } else if (strcmp(cmd, "estop") == 0) {
    irrigation.triggerEmergencyStop("CLI_EMERGENCY_STOP");
    Serial.println(F("[CLI] Emergency stop triggered!"));
  } else if (strcmp(cmd, "clearfault") == 0) {
    irrigation.clearEmergencyStop();
    Serial.println(F("[CLI] Emergency stop / fault lockout cleared."));
  } else if (strcmp(cmd, "mute") == 0) {
    buzzer.setMuted(true);
    Serial.println(F("[CLI] Buzzer muted."));
  } else if (strcmp(cmd, "unmute") == 0) {
    buzzer.setMuted(false);
    Serial.println(F("[CLI] Buzzer unmuted."));
  } else if (strcmp(cmd, "factoryreset") == 0) {
    provisioning.initiateResetPendingLifecycle(
        provisioning.ownershipEpoch() + 1, relays, irrigation, buzzer, events);
    provisioning.finalizeLocalUnclaimAndEnterSetup(ioConfig, false);
  } else {
    Serial.printf("[CLI] Unknown command '%s'. Type 'help'.\n", cmd);
  }
}

void DiagnosticsManager::printCliHelp() const {
  Serial.println(F("─── Smart Plant Care V3.1 Bench CLI Commands ───────────────────────────"));
  Serial.println(F("  status                                  : Print full sensor & state report"));
  Serial.println(F("  wifi <ssid> <password>                  : Save Wi-Fi credentials to NVS"));
  Serial.println(F("  claimstate <UNCLAIMED|OWNED|RESET_PENDING> [epoch] : Set ownership state"));
  Serial.println(F("  mode <OUTDOOR|INDOOR>                   : Switch outdoor/indoor mode"));
  Serial.println(F("  simweather <0..100 | -1>                : Simulate Open-Meteo rain prob %"));
  Serial.println(F("  enableloads / disableloads              : Enable/disable physical relay coils"));
  Serial.println(F("  water <tomato|chili|eggplant> [ms]      : Test manual watering state machine"));
  Serial.println(F("  estop / clearfault                      : Trigger or clear emergency lockout"));
  Serial.println(F("  mute / unmute                           : Mute or unmute GPIO18 buzzer"));
  Serial.println(F("  factoryreset                            : Run local RESET_PENDING unclaim"));
  Serial.println(F("────────────────────────────────────────────────────────────────────────"));
}
