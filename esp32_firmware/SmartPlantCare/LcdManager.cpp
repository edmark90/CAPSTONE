#include "LcdManager.h"
#include <math.h>
#include <stdio.h>
#include <string.h>

bool LcdManager::begin() {
  Wire.begin(Pins::LCD_SDA, Pins::LCD_SCL, Config::I2C_CLOCK_HZ);
  detectedI2cAddr_ = scanI2cBus();

  if (detectedI2cAddr_ == 0x00) {
    Serial.println("[LCD] WARNING: No I2C device responded on SDA=21 / SCL=22.");
    lcdReady_ = false;
    return false;
  }

#if defined(USE_LIQUIDCRYSTAL_I2C_FALLBACK)
  lcd_ = LiquidCrystal_I2C(detectedI2cAddr_, Config::LCD_COLS, Config::LCD_ROWS);
  lcd_.init();
  lcd_.backlight();
  lcdReady_ = true;
#else
  const int status = lcd_.begin(Config::LCD_COLS, Config::LCD_ROWS);
  if (status != 0) {
    Serial.printf("[LCD] hd44780_I2Cexp begin(20,4) failed with status=%d (addr=0x%02X)\n",
                  status, detectedI2cAddr_);
    lcdReady_ = false;
    return false;
  }
  lcd_.backlight();
  lcdReady_ = true;
#endif

  for (int r = 0; r < 4; ++r) {
    memset(displayedRows_[r], 0, sizeof(displayedRows_[r]));
  }

  char bootScreen[4][21];
  formatPaddedRow(bootScreen[0], "SMART PLANT CARE");
  formatPaddedRow(bootScreen[1], "I2C SDA:21 SCL:22");
  char addrLine[21];
  snprintf(addrLine, sizeof(addrLine), "LCD ADDR: 0x%02X", detectedI2cAddr_);
  formatPaddedRow(bootScreen[2], addrLine);
  formatPaddedRow(bootScreen[3], "LOADS: SAFE OFF");
  renderLinesIfChanged(bootScreen);

  lastPageSwitchAtMs_ = millis();
  return true;
}

uint8_t LcdManager::scanI2cBus() {
  uint8_t foundAddr = 0x00;
  Serial.println("[I2C] Scanning bus on SDA=GPIO21, SCL=GPIO22...");
  for (uint8_t addr = 0x08; addr < 0x78; ++addr) {
    Wire.beginTransmission(addr);
    const uint8_t err = Wire.endTransmission();
    if (err == 0) {
      Serial.printf("[I2C] Found device at address 0x%02X\n", addr);
      // Prefer common PCF8574 / PCF8574A LCD addresses (0x20-0x27, 0x38-0x3F)
      if (foundAddr == 0x00 ||
          (addr >= 0x20 && addr <= 0x27) ||
          (addr >= 0x38 && addr <= 0x3F)) {
        foundAddr = addr;
      }
    }
  }
  return foundAddr;
}

void LcdManager::triggerTemporaryOverlay(const char* line0,
                                         const char* line1,
                                         const char* line2,
                                         const char* line3,
                                         uint32_t durationMs) {
  formatPaddedRow(tempOverlayRows_[0], line0 ? line0 : "");
  formatPaddedRow(tempOverlayRows_[1], line1 ? line1 : "");
  formatPaddedRow(tempOverlayRows_[2], line2 ? line2 : "");
  formatPaddedRow(tempOverlayRows_[3], line3 ? line3 : "");
  tempOverlayActive_  = true;
  tempOverlayUntilMs_ = millis() + durationMs;
}

void LcdManager::update(uint32_t nowMs,
                        const RuntimeConfig& cfg,
                        OwnershipState ownershipState,
                        bool wifiConnected,
                        bool firebaseSynced,
                        uint32_t lastSyncUptimeMs,
                        const EnvironmentReading& env,
                        const SoilZoneStatus soil[3],
                        const ReservoirReading& reservoir,
                        const RainGateStatus& rain,
                        const WeatherSnapshot& weather,
                        const IrrigationController& irrigation) {
  if (!lcdReady_) return;

  char nextLines[4][21];

  // 1. Check critical priority overlays first (preempts normal rotation)
  if (buildCriticalOverlay(nextLines, nowMs, cfg, env, soil, reservoir,
                           rain, weather, irrigation)) {
    renderLinesIfChanged(nextLines);
    return;
  }

  // 2. Check temporary event overlay (e.g. DEVICE PAIRED)
  if (tempOverlayActive_) {
    if (int32_t(tempOverlayUntilMs_ - nowMs) > 0) {
      renderLinesIfChanged(tempOverlayRows_);
      return;
    }
    tempOverlayActive_ = false;
  }

  // 3. If actively watering, dedicated Page F stays visible
  if (irrigation.state() == IrrigationState::OPENING_SELECTED_VALVE ||
      irrigation.state() == IrrigationState::PUMP_WATERING ||
      irrigation.state() == IrrigationState::PUMP_STOPPING) {
    buildPageActiveWatering(nextLines, nowMs, irrigation);
    renderLinesIfChanged(nextLines);
    return;
  }

  // 4. If unclaimed / setup mode, show setup screen interleaved with sensor screens
  const uint8_t totalPages = (ownershipState != OwnershipState::OWNED) ? 6
                           : (!wifiConnected || !firebaseSynced)       ? 6
                                                                       : 5;

  if (uint32_t(nowMs - lastPageSwitchAtMs_) >= Config::INTERVAL_LCD_PAGE_MS) {
    lastPageSwitchAtMs_ = nowMs;
    currentPage_        = (currentPage_ + 1) % totalPages;
  }

  switch (currentPage_) {
    case 0:
      buildPageOverview(nextLines, cfg, wifiConnected, firebaseSynced, irrigation);
      break;
    case 1:
      buildPageEnvironment(nextLines, env);
      break;
    case 2:
      buildPageSoil(nextLines, soil);
      break;
    case 3:
      buildPageReservoir(nextLines, reservoir);
      break;
    case 4:
      buildPageWeatherRain(nextLines, cfg, rain, weather);
      break;
    case 5:
    default:
      if (ownershipState != OwnershipState::OWNED) {
        buildPageSetupMode(nextLines, wifiConnected);
      } else {
        buildPageOffline(nextLines, nowMs, lastSyncUptimeMs, irrigation);
      }
      break;
  }

  renderLinesIfChanged(nextLines);
}

bool LcdManager::buildCriticalOverlay(char lines[4][21],
                                      uint32_t nowMs,
                                      const RuntimeConfig& cfg,
                                      const EnvironmentReading& env,
                                      const SoilZoneStatus soil[3],
                                      const ReservoirReading& reservoir,
                                      const RainGateStatus& rain,
                                      const WeatherSnapshot& weather,
                                      const IrrigationController& irrigation) {
  (void)nowMs;
  (void)cfg;
  (void)env;
  (void)soil;

  // Priority 1: Emergency Stop / Hardware Fault Lockout
  if (irrigation.isEmergencyStopped() || irrigation.isFaultLocked()) {
    formatPaddedRow(lines[0], "! EMERGENCY STOP !");
    formatPaddedRow(lines[1], "ALL OUTPUTS: OFF");
    formatPaddedRow(lines[2], irrigation.lastAbortReason());
    formatPaddedRow(lines[3], "CHECK HARDWARE/APP");
    return true;
  }

  // Priority 2: Reservoir Sensor Error / Critical / Empty
  if (!reservoir.valid || reservoir.state == ReservoirLevelState::SENSOR_FAULT) {
    formatPaddedRow(lines[0], "SENSOR ERROR");
    formatPaddedRow(lines[1], "RESERVOIR: -- %");
    formatPaddedRow(lines[2], "AIR GAP: UNKNOWN");
    formatPaddedRow(lines[3], "PUMP: DISABLED");
    return true;
  }

  if (reservoir.state == ReservoirLevelState::EMPTY ||
      reservoir.state == ReservoirLevelState::CRITICAL) {
    char l1[21], l2[21];
    snprintf(l1, sizeof(l1), "LEVEL: %.0f %% (%s)",
             reservoir.waterLevelPct,
             reservoir.state == ReservoirLevelState::EMPTY ? "EMPTY" : "CRIT");
    snprintf(l2, sizeof(l2), "AIR GAP: %.2f IN", reservoir.airGapInches);
    formatPaddedRow(lines[0], "! WATER CRITICAL !");
    formatPaddedRow(lines[1], l1);
    formatPaddedRow(lines[2], l2);
    formatPaddedRow(lines[3], "PUMP: OFF (REFILL)");
    return true;
  }

  // Priority 3: Confirmed Rain Suspension (briefly alternates with normal pages)
  if (rain.rainSuspensionLatched && ((nowMs / 3000) % 3 == 0)) {
    char l2[21];
    if (weather.valid && weather.probabilityPct >= 0) {
      snprintf(l2, sizeof(l2), "FORECAST: %d %%", weather.probabilityPct);
    } else {
      snprintf(l2, sizeof(l2), "FORECAST: -- %%");
    }
    formatPaddedRow(lines[0], "RAIN CONFIRMED");
    formatPaddedRow(lines[1], "RAIN - AUTO PAUSED");
    formatPaddedRow(lines[2], l2);
    formatPaddedRow(lines[3], "PUMP & VALVES: OFF");
    return true;
  }

  return false;
}

void LcdManager::buildPageOverview(char lines[4][21],
                                   const RuntimeConfig& cfg,
                                   bool wifiConnected,
                                   bool firebaseSynced,
                                   const IrrigationController& irrigation) const {
  char l1[21], l2[21], l3[21];
  snprintf(l1, sizeof(l1), "MODE: %s",
           cfg.mode == DeviceMode::OUTDOOR ? "OUTDOOR" : "INDOOR");
  snprintf(l2, sizeof(l2), "STATUS: %s",
           (wifiConnected && firebaseSynced) ? "ONLINE"
           : wifiConnected                   ? "WIFI ONLY"
                                             : "OFFLINE");
  snprintf(l3, sizeof(l3), "IRRIGATION: %s",
           irrigationStateString(irrigation.state()));

  formatPaddedRow(lines[0], "SMART PLANT CARE");
  formatPaddedRow(lines[1], l1);
  formatPaddedRow(lines[2], l2);
  formatPaddedRow(lines[3], l3);
}

void LcdManager::buildPageEnvironment(char lines[4][21],
                                      const EnvironmentReading& env) const {
  char l0[21], l1[21], l2[21], l3[21];

  if (env.dhtValid && isfinite(env.temperatureC)) {
    snprintf(l0, sizeof(l0), "TEMP: %.1f C %s",
             env.temperatureC, envBandShort(env.tempBand));
  } else {
    snprintf(l0, sizeof(l0), "TEMP: --.- C ERR");
  }

  if (env.dhtValid && isfinite(env.humidityRh)) {
    snprintf(l1, sizeof(l1), "HUM:  %.0f %% %s",
             env.humidityRh, envBandShort(env.humidityBand));
  } else {
    snprintf(l1, sizeof(l1), "HUM:  -- %% ERR");
  }

  if (env.lightValid && isfinite(env.brightnessPct)) {
    snprintf(l2, sizeof(l2), "LIGHT: %.0f %% %s",
             env.brightnessPct, envBandShort(env.lightBand));
  } else {
    snprintf(l2, sizeof(l2), "LIGHT: -- %% ERR");
  }

  const bool allOk = env.dhtValid && env.lightValid &&
                     env.tempBand == EnvBand::NORMAL &&
                     env.humidityBand == EnvBand::NORMAL &&
                     env.lightBand == EnvBand::NORMAL;
  if (!env.dhtValid || !env.lightValid) {
    snprintf(l3, sizeof(l3), "ENV: SENSOR FAULT");
  } else if (allOk) {
    snprintf(l3, sizeof(l3), "ENV: ALL NORMAL");
  } else {
    snprintf(l3, sizeof(l3), "ENV: CHECK ALERTS");
  }

  formatPaddedRow(lines[0], l0);
  formatPaddedRow(lines[1], l1);
  formatPaddedRow(lines[2], l2);
  formatPaddedRow(lines[3], l3);
}

void LcdManager::buildPageSoil(char lines[4][21],
                               const SoilZoneStatus soil[3]) const {
  char l1[21], l2[21], l3[21];

  if (soil[0].valid && isfinite(soil[0].moisturePct)) {
    snprintf(l1, sizeof(l1), "TOMATO:    %3.0f %%", soil[0].moisturePct);
  } else {
    snprintf(l1, sizeof(l1), "TOMATO:    ERR");
  }

  if (soil[1].valid && isfinite(soil[1].moisturePct)) {
    snprintf(l2, sizeof(l2), "CHILI:     %3.0f %%", soil[1].moisturePct);
  } else {
    snprintf(l2, sizeof(l2), "CHILI:     ERR");
  }

  if (soil[2].valid && isfinite(soil[2].moisturePct)) {
    snprintf(l3, sizeof(l3), "EGGPLANT:  %3.0f %%", soil[2].moisturePct);
  } else {
    snprintf(l3, sizeof(l3), "EGGPLANT:  ERR");
  }

  formatPaddedRow(lines[0], "SOIL MOISTURE");
  formatPaddedRow(lines[1], l1);
  formatPaddedRow(lines[2], l2);
  formatPaddedRow(lines[3], l3);
}

void LcdManager::buildPageReservoir(char lines[4][21],
                                    const ReservoirReading& reservoir) const {
  char l1[21], l2[21], l3[21];

  if (reservoir.valid && isfinite(reservoir.waterLevelPct) &&
      isfinite(reservoir.airGapInches)) {
    snprintf(l1, sizeof(l1), "LEVEL:  %.0f %%", reservoir.waterLevelPct);
    snprintf(l2, sizeof(l2), "AIR GAP: %.2f IN", reservoir.airGapInches);
  } else {
    snprintf(l1, sizeof(l1), "LEVEL:  -- %%");
    snprintf(l2, sizeof(l2), "AIR GAP: UNKNOWN");
  }
  snprintf(l3, sizeof(l3), "STATUS: %s", reservoirStateString(reservoir.state));

  formatPaddedRow(lines[0], "WATER RESERVOIR");
  formatPaddedRow(lines[1], l1);
  formatPaddedRow(lines[2], l2);
  formatPaddedRow(lines[3], l3);
}

void LcdManager::buildPageWeatherRain(char lines[4][21],
                                      const RuntimeConfig& cfg,
                                      const RainGateStatus& rain,
                                      const WeatherSnapshot& weather) const {
  char l0[21], l1[21], l2[21], l3[21];

  if (weather.valid && weather.fresh && weather.probabilityPct >= 0) {
    snprintf(l0, sizeof(l0), "FORECAST: %d %%", weather.probabilityPct);
  } else {
    snprintf(l0, sizeof(l0), "FORECAST: -- %%");
  }

  snprintf(l1, sizeof(l1), "WINDOW: %s", weather.windowLabel);

  const char* rainStr = (rain.sensorState == RainSensorState::WET_CONFIRMED)   ? "WET"
                      : (rain.sensorState == RainSensorState::WET_UNCONFIRMED) ? "WET?"
                      : (rain.sensorState == RainSensorState::DRY)             ? "DRY"
                                                                               : "ERR";
  snprintf(l2, sizeof(l2), "RAIN SENSOR: %s", rainStr);

  if (cfg.mode == DeviceMode::INDOOR) {
    snprintf(l3, sizeof(l3), "AUTO WATER: INDOOR");
  } else if (rain.rainSuspensionLatched) {
    snprintf(l3, sizeof(l3), "AUTO WATER: PAUSED");
  } else if (rain.weatherUnavailableFault) {
    snprintf(l3, sizeof(l3), "AUTO WATER: NO API");
  } else {
    snprintf(l3, sizeof(l3), "AUTO WATER: ALLOWED");
  }

  formatPaddedRow(lines[0], l0);
  formatPaddedRow(lines[1], l1);
  formatPaddedRow(lines[2], l2);
  formatPaddedRow(lines[3], l3);
}

void LcdManager::buildPageActiveWatering(char lines[4][21],
                                         uint32_t nowMs,
                                         const IrrigationController& irrigation) const {
  char l1[21], l2[21], l3[21];
  const CropZoneId zone = irrigation.activeZone();
  const int valveNum = (zone == CropZoneId::TOMATO)   ? 1
                     : (zone == CropZoneId::CHILI)    ? 2
                     : (zone == CropZoneId::EGGPLANT) ? 3
                                                      : 0;
  const uint32_t elapsedSec = irrigation.activePumpElapsedSec(nowMs);
  const uint32_t targetSec  = irrigation.activeTargetDurationSec();

  snprintf(l1, sizeof(l1), "CROP: %s", cropZoneDisplayName(zone));
  snprintf(l2, sizeof(l2), "PUMP: %s VALVE: %d",
           irrigation.state() == IrrigationState::PUMP_WATERING ? "ON " : "OFF",
           valveNum);
  snprintf(l3, sizeof(l3), "TIME: %02lu:%02lu / %02lu:%02lu",
           static_cast<unsigned long>(elapsedSec / 60),
           static_cast<unsigned long>(elapsedSec % 60),
           static_cast<unsigned long>(targetSec / 60),
           static_cast<unsigned long>(targetSec % 60));

  formatPaddedRow(lines[0], "IRRIGATION ACTIVE");
  formatPaddedRow(lines[1], l1);
  formatPaddedRow(lines[2], l2);
  formatPaddedRow(lines[3], l3);
}

void LcdManager::buildPageSetupMode(char lines[4][21], bool wifiConnected) const {
  formatPaddedRow(lines[0], "DEVICE SETUP MODE");
  formatPaddedRow(lines[1], "PAIR WITH PHONE");
  formatPaddedRow(lines[2], wifiConnected ? "WIFI: CONNECTED" : "WIFI: NOT CONFIGURED");
  formatPaddedRow(lines[3], "PUMP: DISABLED");
}

void LcdManager::buildPageOffline(char lines[4][21],
                                  uint32_t nowMs,
                                  uint32_t lastSyncUptimeMs,
                                  const IrrigationController& irrigation) const {
  char l2[21], l3[21];
  if (lastSyncUptimeMs == 0) {
    snprintf(l2, sizeof(l2), "LAST SYNC: NEVER");
  } else {
    const uint32_t secAgo = uint32_t(nowMs - lastSyncUptimeMs) / 1000;
    snprintf(l2, sizeof(l2), "LAST SYNC: %lus AGO", static_cast<unsigned long>(secAgo));
  }
  snprintf(l3, sizeof(l3), "PUMP: SAFE/%s",
           irrigationStateString(irrigation.state()));

  formatPaddedRow(lines[0], "NETWORK DISCONNECTED");
  formatPaddedRow(lines[1], "SENSORS: ACTIVE");
  formatPaddedRow(lines[2], l2);
  formatPaddedRow(lines[3], l3);
}

void LcdManager::renderLinesIfChanged(const char lines[4][21]) {
  for (uint8_t row = 0; row < 4; ++row) {
    if (strncmp(displayedRows_[row], lines[row], 20) != 0) {
      memcpy(displayedRows_[row], lines[row], 21);
      lcd_.setCursor(0, row);
      lcd_.print(displayedRows_[row]);
    }
  }
}

void LcdManager::formatPaddedRow(char out[21], const char* text) const {
  size_t i = 0;
  if (text) {
    while (text[i] != '\0' && i < 20) {
      out[i] = text[i];
      ++i;
    }
  }
  while (i < 20) {
    out[i++] = ' ';
  }
  out[20] = '\0';
}
