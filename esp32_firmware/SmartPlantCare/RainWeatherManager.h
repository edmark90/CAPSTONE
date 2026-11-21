#pragma once
/**
 * @file RainWeatherManager.h
 * @brief Rain Sensor (GPIO32 DO) + Open-Meteo Weather Snapshot + Strict AND-Condition Gate.
 *
 * AUTHORITATIVE RAIN SUSPENSION RULE (WEATHER-FIRST SEQUENCE):
 *   outdoorMode
 *   && weatherDataValid
 *   && weatherDataFresh
 *   && rainProbability >= rainThresholdPct (MUST be true BEFORE sensor gets wet!)
 *   && rainSensorWetConfirmed
 *   && !accidentalWetLatched
 *
 * - Forecast >= threshold with dry sensor: weather armed + warning advisory only, SILENT buzzer, NO suspension.
 * - Wet sensor BEFORE weather condition is met (or forecast < threshold): treated as ACCIDENTAL WET,
 *   SILENT buzzer, NO suspension, emits RAIN_SENSOR_WET notification explaining threshold not met.
 * - Weather condition met FIRST, THEN sensor wet (sustained): latch rain suspension, stop pump/valves,
 *   2 short beeps, emit single RAIN_SUSPENSION_STARTED event.
 * - Recovery: requires BOTH continuous dry >= 120s AND fresh valid forecast < threshold.
 * - Unavailable/stale weather: handled as separate WEATHER_UNAVAILABLE fault, NEVER fake rain.
 */

#include <Arduino.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EventNotificationManager.h"
#include "Models.h"
#include "Pins.h"

class RainWeatherManager {
 public:
  void begin(bool wetIsLow = Config::RAIN_WET_IS_LOW);

  // Sample GPIO32 every 200ms and update sustained wet/dry confirmation timers
  void sampleRainSensor(uint32_t nowMs, const RuntimeConfig& cfg);

  // Update cached Open-Meteo snapshot received from Firebase RTDB /weather or direct fetch
  void updateWeatherSnapshot(bool valid,
                             int probabilityPct,
                             uint64_t fetchedAtEpochMs,
                             uint64_t validUntilEpochMs,
                             uint32_t nowMs,
                             const char* windowLabel = "NEXT 3 H",
                             const char* locationName = nullptr);

  // Evaluate the strict AND-condition and recovery latch.
  // Returns true if a NEW rain suspension just triggered on this cycle (caller stops pump immediately).
  bool evaluateRainGate(uint32_t nowMs,
                        const RuntimeConfig& cfg,
                        BuzzerManager& buzzer,
                        EventNotificationManager& events);

  const RainGateStatus&  rainStatus() const { return status_; }
  const WeatherSnapshot& weather() const { return weather_; }

  // True if automatic irrigation is allowed by the weather/rain subsystem
  bool isAutoWateringAllowedByWeather(const RuntimeConfig& cfg) const;

 private:
  bool            wetIsLow_                = Config::RAIN_WET_IS_LOW;
  uint32_t        lastSampleAtMs_          = 0;
  RainGateStatus  status_;
  WeatherSnapshot weather_;

  bool            prevEvaluatedRawWet_     = false;
  bool            weatherMetBeforeWet_     = false;
  bool            accidentalWetLatched_    = false;
  bool            forecastAdvisorySent_    = false;
  bool            wetOnlyStatusSent_       = false;
  bool            weatherFaultSent_        = false;
  uint32_t        lastWeatherFaultEventMs_ = 0;
  uint32_t        lastAdvisoryEventMs_     = 0;
  DeviceMode      lastEvaluatedMode_       = DeviceMode::OUTDOOR;
};
