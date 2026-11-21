#pragma once
/**
 * @file SoilMoistureManager.h
 * @brief Independent 3-crop capacitive soil moisture sensing on ADC1
 *        (Tomato = GPIO34, Chili = GPIO35, Eggplant = GPIO33).
 *
 * SAFETY RULES:
 * - Each crop has independent dryRaw/wetRaw calibration and start/stop thresholds.
 * - Disconnected/saturated/jumping ADC readings mark only that crop valid=false.
 * - An invalid sensor NEVER reports 0% dry to trigger false automatic watering.
 */

#include <Arduino.h>
#include "Config.h"
#include "EventNotificationManager.h"
#include "Models.h"
#include "Pins.h"

class SoilMoistureManager {
 public:
  void begin();
  void sampleAllZones(uint32_t nowMs,
                      const RuntimeConfig& cfg,
                      EventNotificationManager& events);

  const SoilZoneStatus& zoneStatus(CropZoneId zone) const;
  const SoilZoneStatus* allStatuses() const { return zones_; }

  void recordZoneWatered(CropZoneId zone, uint32_t nowMs);

 private:
  void sampleSingleZone(uint8_t idx,
                        int pin,
                        uint32_t nowMs,
                        const SoilZoneConfig& zcfg,
                        EventNotificationManager& events);
  int  readMedianAdc7(int pin) const;

  SoilZoneStatus zones_[3];
  bool           faultEventSent_[3]   = {false, false, false};
  bool           lowSoilEventSent_[3] = {false, false, false};
  uint8_t        validStreak_[3]      = {0, 0, 0};
  uint32_t       lastFaultEventMs_[3] = {0, 0, 0};
};
