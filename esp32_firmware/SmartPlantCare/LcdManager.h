#pragma once
/**
 * @file LcdManager.h
 * @brief 20x4 I2C LCD2004 controller (SDA = GPIO21, SCL = GPIO22) with I2C scanner,
 *        flicker-free 20-char row diffing, 3s page rotation, and priority fault overlays.
 *
 * AUTHORITATIVE V3.1 LCD RULES:
 * - Uses ONLY I2C SDA=GPIO21, SCL=GPIO22 (parallel RS/E/D4/D5/D6/D7 completely removed).
 * - Uses hd44780 + hd44780_I2Cexp auto-detection by default.
 *   (Can optionally fall back to LiquidCrystal_I2C by defining USE_LIQUIDCRYSTAL_I2C_FALLBACK).
 * - Pads every line to exactly 20 characters and writes only changed rows to eliminate flicker.
 * - Critical priority overlays override normal page rotation.
 */

#include <Arduino.h>
#include <Wire.h>
#include "Config.h"
#include "IrrigationController.h"
#include "Models.h"
#include "Pins.h"

#if defined(USE_LIQUIDCRYSTAL_I2C_FALLBACK)
#include <LiquidCrystal_I2C.h>
#else
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>
#endif

class LcdManager {
 public:
  // Scans I2C bus on SDA=21/SCL=22 and initializes 20x4 LCD
  bool begin();

  // Nonblocking 750ms display refresh and 3000ms normal page rotation
  void update(uint32_t nowMs,
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
              const IrrigationController& irrigation);

  // Trigger a temporary 5-second priority overlay (e.g. DEVICE PAIRED)
  void triggerTemporaryOverlay(const char* line0,
                               const char* line1,
                               const char* line2,
                               const char* line3,
                               uint32_t durationMs = 5000);

  uint8_t detectedAddress() const { return detectedI2cAddr_; }
  bool    isAvailable() const { return lcdReady_; }

 private:
  uint8_t scanI2cBus();
  void    renderLinesIfChanged(const char lines[4][21]);
  void    formatPaddedRow(char out[21], const char* text) const;

  void buildPageOverview(char lines[4][21],
                         const RuntimeConfig& cfg,
                         bool wifiConnected,
                         bool firebaseSynced,
                         const IrrigationController& irrigation) const;
  void buildPageEnvironment(char lines[4][21], const EnvironmentReading& env) const;
  void buildPageSoil(char lines[4][21], const SoilZoneStatus soil[3]) const;
  void buildPageReservoir(char lines[4][21], const ReservoirReading& reservoir) const;
  void buildPageWeatherRain(char lines[4][21],
                            const RuntimeConfig& cfg,
                            const RainGateStatus& rain,
                            const WeatherSnapshot& weather) const;
  void buildPageActiveWatering(char lines[4][21],
                               uint32_t nowMs,
                               const IrrigationController& irrigation) const;
  void buildPageSetupMode(char lines[4][21], bool wifiConnected) const;
  void buildPageOffline(char lines[4][21],
                        uint32_t nowMs,
                        uint32_t lastSyncUptimeMs,
                        const IrrigationController& irrigation) const;

  bool buildCriticalOverlay(char lines[4][21],
                            uint32_t nowMs,
                            const RuntimeConfig& cfg,
                            const EnvironmentReading& env,
                            const SoilZoneStatus soil[3],
                            const ReservoirReading& reservoir,
                            const RainGateStatus& rain,
                            const WeatherSnapshot& weather,
                            const IrrigationController& irrigation);

#if defined(USE_LIQUIDCRYSTAL_I2C_FALLBACK)
  LiquidCrystal_I2C lcd_{0x27, Config::LCD_COLS, Config::LCD_ROWS};
#else
  hd44780_I2Cexp lcd_;
#endif

  bool     lcdReady_            = false;
  uint8_t  detectedI2cAddr_     = 0x00;
  uint8_t  currentPage_         = 0;
  uint32_t lastPageSwitchAtMs_  = 0;
  char     displayedRows_[4][21] = {{0}};

  bool     tempOverlayActive_   = false;
  uint32_t tempOverlayUntilMs_  = 0;
  char     tempOverlayRows_[4][21] = {{0}};
};
