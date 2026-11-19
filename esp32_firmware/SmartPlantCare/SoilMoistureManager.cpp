#include "SoilMoistureManager.h"
#include <math.h>
#include <stdio.h>

void SoilMoistureManager::begin() {
  const int pins[3] = {
      Pins::SOIL_TOMATO_ADC,
      Pins::SOIL_CHILI_ADC,
      Pins::SOIL_EGGPLANT_ADC
  };
  analogReadResolution(12);
  for (int pin : pins) {
    pinMode(pin, INPUT);
    analogSetPinAttenuation(pin, ADC_11db);
  }
}

void SoilMoistureManager::sampleAllZones(uint32_t nowMs,
                                         const RuntimeConfig& cfg,
                                         EventNotificationManager& events) {
  sampleSingleZone(0, Pins::SOIL_TOMATO_ADC,   nowMs, cfg.zones[0], events);
  sampleSingleZone(1, Pins::SOIL_CHILI_ADC,    nowMs, cfg.zones[1], events);
  sampleSingleZone(2, Pins::SOIL_EGGPLANT_ADC, nowMs, cfg.zones[2], events);
}

const SoilZoneStatus& SoilMoistureManager::zoneStatus(CropZoneId zone) const {
  const uint8_t idx = static_cast<uint8_t>(zone);
  return zones_[idx < 3 ? idx : 0];
}

void SoilMoistureManager::recordZoneWatered(CropZoneId zone, uint32_t nowMs) {
  const uint8_t idx = static_cast<uint8_t>(zone);
  if (idx < 3) {
    zones_[idx].lastWateredAtMs = nowMs;
  }
}

void SoilMoistureManager::sampleSingleZone(uint8_t idx,
                                           int pin,
                                           uint32_t nowMs,
                                           const SoilZoneConfig& zcfg,
                                           EventNotificationManager& events) {
  SoilZoneStatus& st = zones_[idx];
  const CropZoneId cropId = static_cast<CropZoneId>(idx);
  const int raw = readMedianAdc7(pin);
  st.rawAdc = raw;

  // Validate wiring & calibration range
  const bool rawPlausible = (raw >= Config::SOIL_ADC_MIN_PLAUSIBLE &&
                             raw <= Config::SOIL_ADC_MAX_PLAUSIBLE &&
                             zcfg.dryRaw != zcfg.wetRaw &&
                             zcfg.stopThresholdPct > zcfg.startThresholdPct);

  if (!rawPlausible) {
    if (st.faultStreak < 250) ++st.faultStreak;
    if (st.faultStreak >= 3) {
      st.valid       = false;
      st.moisturePct = NAN;
      st.needsWater  = false;
      if (!faultEventSent_[idx]) {
        faultEventSent_[idx] = true;
        char msg[112];
        snprintf(msg, sizeof(msg),
                 "%s soil sensor (ADC raw=%d) is disconnected or out of calibrated range. Auto-watering blocked for %s.",
                 cropZoneDisplayName(cropId), raw, cropZoneDisplayName(cropId));
        events.enqueueEvent("SOIL_SENSOR_FAULT",
                            EventSeverity::WARNING,
                            "Soil Sensor Fault",
                            msg,
                            cropZoneSlug(cropId),
                            static_cast<float>(raw));
      }
    }
    return;
  }

  // Formula: soilPercent = clamp(100 * (raw - dryRaw) / (wetRaw - dryRaw), 0, 100)
  const float denom = static_cast<float>(zcfg.wetRaw - zcfg.dryRaw);
  const float rawPct = 100.0f * static_cast<float>(raw - zcfg.dryRaw) / denom;
  const float clampedPct = constrain(rawPct, 0.0f, 100.0f);

  // Reject impossible single-step jump unless recovering from invalid state
  if (st.valid && st.faultStreak == 0 &&
      fabsf(clampedPct - st.moisturePct) > Config::SOIL_MAX_STEP_DELTA_PCT) {
    ++st.faultStreak;
    return;
  }

  // Smooth valid reading with lightweight EMA (70% new, 30% previous)
  st.moisturePct = (st.valid && isfinite(st.moisturePct))
                       ? (0.7f * clampedPct + 0.3f * st.moisturePct)
                       : clampedPct;
  st.valid       = true;
  st.faultStreak = 0;
  st.sampledAtMs = nowMs;
  faultEventSent_[idx] = false;

  // Hysteresis evaluation:
  // - Needs water when moisture <= startThresholdPct
  // - Satisfied when moisture >= stopThresholdPct
  if (st.moisturePct <= zcfg.startThresholdPct) {
    st.needsWater = true;
    if (!lowSoilEventSent_[idx]) {
      lowSoilEventSent_[idx] = true;
      const char* evType = (idx == 0) ? "SOIL_LOW_TOMATO"
                         : (idx == 1) ? "SOIL_LOW_CHILI"
                                      : "SOIL_LOW_EGGPLANT";
      char title[48];
      char msg[112];
      snprintf(title, sizeof(title), "%s Soil Dry", cropZoneDisplayName(cropId));
      snprintf(msg, sizeof(msg), "%s soil moisture is %.0f%% (start threshold %.0f%%).",
               cropZoneDisplayName(cropId), st.moisturePct, zcfg.startThresholdPct);
      events.enqueueEvent(evType, EventSeverity::INFO, title, msg,
                          cropZoneSlug(cropId), st.moisturePct);
    }
  } else if (st.moisturePct >= zcfg.stopThresholdPct) {
    st.needsWater          = false;
    lowSoilEventSent_[idx] = false;
  }
}

int SoilMoistureManager::readMedianAdc7(int pin) const {
  int s[7];
  for (int i = 0; i < 7; ++i) {
    s[i] = analogRead(pin);
  }
  for (int i = 0; i < 6; ++i) {
    for (int j = i + 1; j < 7; ++j) {
      if (s[j] < s[i]) {
        const int tmp = s[i];
        s[i]          = s[j];
        s[j]          = tmp;
      }
    }
  }
  return s[3];
}
