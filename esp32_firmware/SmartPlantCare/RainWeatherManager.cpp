#include "RainWeatherManager.h"
#include <stdio.h>
#include <string.h>

void RainWeatherManager::begin(bool wetIsLow) {
  wetIsLow_ = wetIsLow;
  pinMode(Pins::RAIN_DO, INPUT_PULLUP);
  lastSampleAtMs_ = millis();
}

void RainWeatherManager::sampleRainSensor(uint32_t nowMs, const RuntimeConfig& cfg) {
  const uint32_t dt = (lastSampleAtMs_ == 0) ? 0 : uint32_t(nowMs - lastSampleAtMs_);
  lastSampleAtMs_   = nowMs;

  const int rawLevel = digitalRead(Pins::RAIN_DO);
  const bool isWet   = wetIsLow_ ? (rawLevel == LOW) : (rawLevel == HIGH);

  status_.rawWet      = isWet;
  status_.sensorValid = true;

  if (isWet) {
    status_.wetDurationMs += dt;
    status_.dryDurationMs  = 0;
    status_.dryRecovered   = false;

    if (status_.wetDurationMs >= cfg.wetConfirmMs) {
      status_.wetConfirmed = true;
      status_.sensorState  = RainSensorState::WET_CONFIRMED;
    } else {
      status_.wetConfirmed = false;
      status_.sensorState  = RainSensorState::WET_UNCONFIRMED;
    }
  } else {
    status_.dryDurationMs += dt;
    status_.wetDurationMs  = 0;
    status_.wetConfirmed   = false;
    status_.sensorState    = RainSensorState::DRY;

    if (status_.dryDurationMs >= cfg.dryRecoverMs) {
      status_.dryRecovered = true;
    }
  }
}

void RainWeatherManager::updateWeatherSnapshot(bool valid,
                                               int probabilityPct,
                                               uint64_t fetchedAtEpochMs,
                                               uint64_t validUntilEpochMs,
                                               uint32_t nowMs,
                                               const char* windowLabel,
                                               const char* locationName) {
  const bool plausibleProb = (probabilityPct >= 0 && probabilityPct <= 100);
  weather_.valid              = valid && plausibleProb;
  weather_.probabilityPct     = weather_.valid ? probabilityPct : -1;
  weather_.fetchedAtEpochMs   = fetchedAtEpochMs;
  weather_.validUntilEpochMs  = validUntilEpochMs;
  weather_.receivedAtUptimeMs = nowMs;
  weather_.fresh              = weather_.valid;

  if (windowLabel && windowLabel[0] != '\0') {
    snprintf(weather_.windowLabel, sizeof(weather_.windowLabel), "%s", windowLabel);
  }
  if (locationName && locationName[0] != '\0') {
    snprintf(weather_.locationName, sizeof(weather_.locationName), "%s", locationName);
  }
}

bool RainWeatherManager::evaluateRainGate(uint32_t nowMs,
                                          const RuntimeConfig& cfg,
                                          BuzzerManager& buzzer,
                                          EventNotificationManager& events) {
  // Check weather freshness TTL based on monotonic uptime age since snapshot receipt
  if (weather_.valid) {
    const uint32_t ageMs = uint32_t(nowMs - weather_.receivedAtUptimeMs);
    weather_.fresh = (ageMs <= Config::DEFAULT_WEATHER_MAX_AGE_MS);
  } else {
    weather_.fresh = false;
  }

  // Handle explicit mode switch to INDOOR
  if (cfg.mode == DeviceMode::INDOOR) {
    if (lastEvaluatedMode_ == DeviceMode::OUTDOOR && status_.rainSuspensionLatched) {
      status_.rainSuspensionLatched = false;
      buzzer.clearEventLatch(BuzzerEventId::CONFIRMED_RAIN_SUSPEND);
      events.enqueueEvent("RAIN_SUSPENSION_CLEARED",
                          EventSeverity::INFO,
                          "Switched to Indoor Mode",
                          "Outdoor rain suspension cleared after explicit transition to Indoor mode.");
    }
    status_.forecastAdvisoryActive  = false;
    status_.weatherUnavailableFault = false;
    status_.weatherMetBeforeWet     = false;
    status_.accidentalWet           = false;
    weatherMetBeforeWet_            = false;
    accidentalWetLatched_           = false;
    prevEvaluatedRawWet_            = status_.rawWet;
    lastEvaluatedMode_              = cfg.mode;
    return false;
  }
  lastEvaluatedMode_ = cfg.mode;

  // ─── Exact Variables for the Weather-First Strict AND-Condition ────────────
  const bool outdoorMode            = (cfg.mode == DeviceMode::OUTDOOR);
  const bool weatherDataValid       = weather_.valid;
  const bool weatherDataFresh       = weather_.fresh;
  const int  rainProbability        = weather_.probabilityPct;
  const bool rainSensorWetConfirmed = status_.wetConfirmed;
  const bool weatherConditionMet    = outdoorMode &&
                                      weatherDataValid &&
                                      weatherDataFresh &&
                                      (rainProbability >= cfg.rainThresholdPct);

  // ─── Weather-First Sequence Tracker ────────────────────────────────────────
  // Rule: Weather condition MUST be true FIRST (while sensor is dry) BEFORE
  // the rain sensor gets wet. If the rain sensor got wet FIRST while weather
  // did not meet the threshold, latch accidentalWet until the sensor dries out.
  if (!status_.rawWet) {
    accidentalWetLatched_       = false;
    weatherMetBeforeWet_        = weatherConditionMet;
    status_.accidentalWet       = false;
    status_.weatherMetBeforeWet = weatherMetBeforeWet_;
    wetOnlyStatusSent_          = false;
  } else {
    if (!prevEvaluatedRawWet_) {
      // Exact transition DRY -> WET: was weather condition ALREADY true before getting wet?
      accidentalWetLatched_ = !weatherMetBeforeWet_;
    }
    status_.accidentalWet       = accidentalWetLatched_;
    status_.weatherMetBeforeWet = weatherMetBeforeWet_;
  }
  prevEvaluatedRawWet_ = status_.rawWet;

  // ─── 1. Separate Unavailable/Stale Weather Fault (NEVER fake rain!) ────────
  if (outdoorMode && (!weatherDataValid || !weatherDataFresh)) {
    status_.weatherUnavailableFault = true;
    status_.forecastAdvisoryActive  = false;
    const bool faultCooldownReady = (lastWeatherFaultEventMs_ == 0) ||
                                    (uint32_t(nowMs - lastWeatherFaultEventMs_) >= 600000UL);
    if (!weatherFaultSent_ && faultCooldownReady) {
      weatherFaultSent_        = true;
      lastWeatherFaultEventMs_ = (nowMs == 0) ? 1UL : nowMs;
      events.enqueueEvent("WEATHER_UNAVAILABLE",
                          EventSeverity::WARNING,
                          "Weather Data Unavailable",
                          "Open-Meteo forecast is missing or stale. Unattended outdoor auto-watering paused.");
    }
  } else {
    status_.weatherUnavailableFault = false;
    weatherFaultSent_               = false;
  }

  // ─── 2. WEATHER-FIRST STRICT AND-CONDITION FOR AUTOMATIC RAIN SUSPENSION ───
  const bool combinedRainTrigger =
      weatherConditionMet
      && rainSensorWetConfirmed
      && weatherMetBeforeWet_
      && !accidentalWetLatched_;

  bool newlySuspended = false;

  if (combinedRainTrigger && !status_.rainSuspensionLatched) {
    status_.rainSuspensionLatched  = true;
    status_.forecastAdvisoryActive = false;
    newlySuspended                 = true;

    // 2 short beeps once on confirmed combined rain suspension
    buzzer.triggerEventTransition(BuzzerEventId::CONFIRMED_RAIN_SUSPEND);

    char msg[112];
    snprintf(msg, sizeof(msg),
             "Weather (%d%% >= %d%%) met first and rain sensor wet. Raining - watering paused.",
             rainProbability, cfg.rainThresholdPct);
    events.enqueueEvent("RAIN_SUSPENSION_STARTED",
                        EventSeverity::CRITICAL,
                        "Raining - Irrigation Paused",
                        msg,
                        nullptr,
                        static_cast<float>(rainProbability));
  }

  // ─── 3. Latched Recovery Rule (Dry >= 120s AND Fresh Forecast < threshold) ─
  if (status_.rainSuspensionLatched) {
    const bool canRecover =
        status_.dryRecovered
        && weatherDataValid
        && weatherDataFresh
        && rainProbability < cfg.rainThresholdPct;

    if (canRecover) {
      status_.rainSuspensionLatched = false;
      forecastAdvisorySent_         = false;
      buzzer.clearEventLatch(BuzzerEventId::CONFIRMED_RAIN_SUSPEND);

      char msg[112];
      snprintf(msg, sizeof(msg),
               "Rain sensor dry for %lu s and forecast dropped to %d%%. Auto-irrigation resumed.",
               static_cast<unsigned long>(cfg.dryRecoverMs / 1000),
               rainProbability);
      events.enqueueEvent("RAIN_SUSPENSION_CLEARED",
                          EventSeverity::INFO,
                          "Rain Suspension Cleared",
                          msg,
                          nullptr,
                          static_cast<float>(rainProbability));
    }
    return newlySuspended;
  }

  // ─── 4. Forecast >= threshold WITHOUT Confirmed Wet Sensor (Advisory Only, Silent)
  if (weatherConditionMet && !rainSensorWetConfirmed && !accidentalWetLatched_) {
    status_.forecastAdvisoryActive = true;
    const bool advCooldownReady = (lastAdvisoryEventMs_ == 0) ||
                                  (uint32_t(nowMs - lastAdvisoryEventMs_) >= 600000UL);
    if (!forecastAdvisorySent_ && advCooldownReady) {
      forecastAdvisorySent_ = true;
      lastAdvisoryEventMs_  = (nowMs == 0) ? 1UL : nowMs;
      char msg[112];
      snprintf(msg, sizeof(msg),
               "Rain chance is %d%% in %s. Irrigation is NOT suspended yet; rain sensor is dry.",
               rainProbability, weather_.windowLabel);
      events.enqueueEvent("FORECAST_RAIN_RISK",
                          EventSeverity::INFO,
                          "Rain Risk Advisory",
                          msg,
                          nullptr,
                          static_cast<float>(rainProbability));
    }
  } else if (weatherDataValid && weatherDataFresh && rainProbability < cfg.rainThresholdPct) {
    status_.forecastAdvisoryActive = false;
    forecastAdvisorySent_          = false;
  }

  // ─── 5. Accidental Wet Notice (Sensor Wet FIRST or Forecast < threshold) ───
  // Trigger after 3s continuous wet (filters tiny spikes) so user sees immediate
  // notification explaining weather % was not met and it's treated as accidental wet.
  const uint32_t accidentalWetDebounceMs = (cfg.wetConfirmMs < 3000UL) ? cfg.wetConfirmMs : 3000UL;
  const bool sustainedWetForNotice       = status_.rawWet && (status_.wetDurationMs >= accidentalWetDebounceMs);
  if (outdoorMode && sustainedWetForNotice && (accidentalWetLatched_ || !weatherConditionMet)) {
    if (!wetOnlyStatusSent_) {
      wetOnlyStatusSent_ = true;
      char msg[112];
      if (weatherDataValid && weatherDataFresh && rainProbability < cfg.rainThresholdPct) {
        snprintf(msg, sizeof(msg),
                 "Rain sensor wet, weather %d%% < %d%% threshold (not met). Accidentally wet - watering continues.",
                 rainProbability, cfg.rainThresholdPct);
      } else if (accidentalWetLatched_ && weatherDataValid && weatherDataFresh && rainProbability >= cfg.rainThresholdPct) {
        snprintf(msg, sizeof(msg),
                 "Rain sensor got wet before weather reached %d%%. Treated as accidental wet; dry sensor to reset.",
                 cfg.rainThresholdPct);
      } else {
        snprintf(msg, sizeof(msg),
                 "Rain sensor wet, weather unavailable (needs >=%d%% first). Treated as accidental wet.",
                 cfg.rainThresholdPct);
      }
      events.enqueueEvent("RAIN_SENSOR_WET",
                          EventSeverity::INFO,
                          "Rain Sensor Wet (Accidental)",
                          msg,
                          nullptr,
                          weatherDataValid ? static_cast<float>(rainProbability) : NAN);
    }
  }

  return newlySuspended;
}

bool RainWeatherManager::isAutoWateringAllowedByWeather(const RuntimeConfig& cfg) const {
  if (cfg.mode == DeviceMode::INDOOR) {
    return true;  // Indoor mode disables outdoor rain/weather suspension
  }
  if (status_.rainSuspensionLatched) {
    return false;
  }
  if (Config::BLOCK_OUTDOOR_AUTO_ON_STALE_WEATHER && status_.weatherUnavailableFault) {
    return false;
  }
  if (Config::BLOCK_OUTDOOR_AUTO_ON_RAIN_FAULT && !status_.sensorValid) {
    return false;
  }
  return true;
}
