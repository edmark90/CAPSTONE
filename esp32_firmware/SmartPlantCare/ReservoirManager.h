#pragma once
/**
 * @file ReservoirManager.h
 * @brief Ultrasonic water reservoir air-gap monitor (TRIG=GPIO16, ECHO=GPIO17)
 *        for the user's 7-inch water reservoir.
 *
 * AUTHORITATIVE 7-INCH RESERVOIR RULES (PHASE 3E):
 * - Ultrasonic sensor is top-mounted facing downward and measures AIR GAP (inches).
 * - Smaller valid distance = fuller reservoir.
 * - Calibrated empty distance (~7.0 inches) = 0% EMPTY.
 * - Formula:
 *     waterPct = clamp(100 * (emptyGapIn - airGapIn) / (emptyGapIn - fullGapIn), 0, 100)
 * - No-echo / timeout (0) or out-of-range reading = UNKNOWN (valid=false), NEVER 100% or 0%.
 * - Thresholds:
 *     <= 20%: LOW_WATER warning (2 short beeps once)
 *     <= 10%: CRITICAL warning and immediate pump cutoff (3 short beeps once)
 *     0%:     EMPTY and immediate pump cutoff (3 short beeps once)
 *     Invalid: SENSOR_FAULT, display UNKNOWN, immediate pump cutoff (3 short beeps once)
 */

#include <Arduino.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EventNotificationManager.h"
#include "Models.h"
#include "Pins.h"

class ReservoirManager {
 public:
  void begin();

  // Sample ultrasonic sensor, filter, and evaluate safety thresholds.
  // Returns true if reservoir is in a pump-blocking state (CRITICAL, EMPTY, or SENSOR_FAULT).
  bool sampleAndEvaluate(uint32_t nowMs,
                         const RuntimeConfig& cfg,
                         BuzzerManager& buzzer,
                         EventNotificationManager& events);

  const ReservoirReading& reading() const { return reading_; }
  bool isPumpAllowed() const;

 private:
  float measureSingleAirGapInches() const;
  float measureMedianAirGapInches() const;
  float computeWaterPercent(float gapIn, float fullGapIn, float emptyGapIn) const;

  ReservoirReading    reading_;
  uint32_t            lowSinceMs_        = 0;
  bool                criticalLockout_   = true;  // Start locked until first valid reading > cutoff
  ReservoirLevelState lastNotifiedState_ = ReservoirLevelState::NORMAL;
};
