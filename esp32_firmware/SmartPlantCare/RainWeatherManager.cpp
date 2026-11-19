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
                                               const char* windowLabel) {
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
    lastEvaluatedMode_              = cfg.mode;
    return false;
  }
  lastEvaluatedMode_ = cfg.mode;

  // ─── Exact Variables for the User's Mandatory AND-Condition ────────────────
  const bool outdoorMode            = (cfg.mode == DeviceMode::OUTDOOR);
  const bool weatherDataValid       = weather_.valid;
  const bool weatherDataFresh       = weather_.fresh;
  const int  rainProbability        = weather_.probabilityPct;
  const bool rainSensorWetConfirmed = status_.wetConfirmed;

  // ─── 1. Separate Unavailable/Stale Weather Fault (NEVER fake rain!) ────────
  if (outdoorMode && (!weatherDataValid || !weatherDataFresh)) {
    status_.weatherUnavailableFault = true;
    status_.forecastAdvisoryActive  = false;
    if (!weatherFaultSent_) {
      weatherFaultSent_ = true;
      events.enqueueEvent("WEATHER_UNAVAILABLE",
                          EventSeverity::WARNING,
                          "Weather Data Unavailable",
                          "Open-Meteo forecast is missing or stale. Unattended outdoor auto-watering paused.");
    }
  } else {
    status_.weatherUnavailableFault = false;
    weatherFaultSent_               = false;
  }

  // ─── 2. PRESERVE USER'S EXACT AND-CONDITION FOR AUTOMATIC RAIN SUSPENSION ─
  const bool combinedRainTrigger =
      outdoorMode
      && weatherDataValid
      && weatherDataFresh
      && rainProbability >= cfg.rainThresholdPct
      && rainSensorWetConfirmed;

  bool newlySuspended = false;

  if (combinedRainTrigger && !status_.rainSuspensionLatched) {
    status_.rainSuspensionLatched  = true;
    status_.forecastAdvisoryActive = false;
    newlySuspended                 = true;

    // 2 short beeps once on confirmed combined rain suspension
    buzzer.triggerEventTransition(BuzzerEventId::CONFIRMED_RAIN_SUSPEND);

    char msg[112];
    snprintf(msg, sizeof(msg),
             "Forecast %d%% and rain sensor confirmed wet. Pump stopped and valves closed.",
             rainProbability);
    events.enqueueEvent("RAIN_SUSPENSION_STARTED",
                        EventSeverity::CRITICAL,
                        "Automatic Irrigation Suspended",
                        msg,
                        nullptr,
                        static_cast<float>(rainProbability));
  }

  // ─── 3. Latched Recovery Rule (Dry >= 120s AND Fresh Forecast < 60%) ───────
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

  // ─── 4. Forecast >= 60% WITHOUT Confirmed Wet Sensor (Advisory Only, Silent)
  if (outdoorMode && weatherDataValid && weatherDataFresh &&
      rainProbability >= cfg.rainThresholdPct && !rainSensorWetConfirmed) {
    status_.forecastAdvisoryActive = true;
    if (!forecastAdvisorySent_) {
      forecastAdvisorySent_ = true;
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

  // ─── 5. Wet Sensor WITH Forecast < 60% (Live Status Only, Silent, No Suspend)
  if (outdoorMode && rainSensorWetConfirmed &&
      (!weatherDataValid || !weatherDataFresh || rainProbability < cfg.rainThresholdPct)) {
    if (!wetOnlyStatusSent_) {
      wetOnlyStatusSent_ = true;
      events.enqueueEvent("RAIN_SENSOR_WET",
                          EventSeverity::INFO,
                          "Rain Sensor Wet",
                          "Local rain sensor is wet while forecast is below 60%. Auto-irrigation not suspended by rain rule.");
    }
  } else if (!status_.rawWet) {
    wetOnlyStatusSent_ = false;
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
