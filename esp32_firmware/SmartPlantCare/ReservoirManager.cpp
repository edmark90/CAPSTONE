#include "ReservoirManager.h"
#include <math.h>
#include <stdio.h>

void ReservoirManager::begin() {
  pinMode(Pins::ULTRASONIC_TRIG, OUTPUT);
  digitalWrite(Pins::ULTRASONIC_TRIG, LOW);
  pinMode(Pins::ULTRASONIC_ECHO, INPUT);
}

bool ReservoirManager::sampleAndEvaluate(uint32_t nowMs,
                                         const RuntimeConfig& cfg,
                                         BuzzerManager& buzzer,
                                         EventNotificationManager& events) {
  const float gapIn = measureMedianAirGapInches();

  // Validate physical reading and calibration geometry
  const bool calValid = isfinite(cfg.reservoirFullGapIn) &&
                        isfinite(cfg.reservoirEmptyGapIn) &&
                        (cfg.reservoirEmptyGapIn > cfg.reservoirFullGapIn);
  const bool gapValid = isfinite(gapIn) &&
                        gapIn >= Config::DEFAULT_RESERVOIR_MIN_VALID_IN &&
                        gapIn <= Config::DEFAULT_RESERVOIR_MAX_VALID_IN;

  if (!calValid || !gapValid) {
    if (reading_.failStreak < 250) ++reading_.failStreak;
    // Immediately block pump on invalid distance; emit fault alert after 3 failed cycles
    criticalLockout_ = true;
    if (reading_.failStreak >= 3) {
      reading_.valid         = false;
      reading_.airGapInches  = NAN;
      reading_.waterLevelPct = NAN;
      reading_.state         = ReservoirLevelState::SENSOR_FAULT;

      if (lastNotifiedState_ != ReservoirLevelState::SENSOR_FAULT) {
        lastNotifiedState_ = ReservoirLevelState::SENSOR_FAULT;
        buzzer.triggerEventTransition(BuzzerEventId::RESERVOIR_CRITICAL_EMPTY);
        events.enqueueEvent("RESERVOIR_SENSOR_FAULT",
                            EventSeverity::CRITICAL,
                            "Reservoir Sensor Error",
                            "Ultrasonic distance sensor returned no valid echo. Water level UNKNOWN; pump disabled.");
      }
    }
    return true;
  }

  reading_.failStreak    = 0;
  reading_.valid         = true;
  reading_.sampledAtMs   = nowMs;
  reading_.airGapInches  = gapIn;
  reading_.waterLevelPct = computeWaterPercent(gapIn, cfg.reservoirFullGapIn, cfg.reservoirEmptyGapIn);

  const float pct = reading_.waterLevelPct;

  // ─── Evaluate Severity Hierarchy: EMPTY -> CRITICAL -> LOW -> NORMAL ──────
  if (gapIn >= cfg.reservoirEmptyGapIn || pct <= 0.5f) {
    reading_.waterLevelPct = 0.0f;
    reading_.state         = ReservoirLevelState::EMPTY;
    criticalLockout_       = true;
    lowSinceMs_            = 0;

    if (lastNotifiedState_ != ReservoirLevelState::EMPTY) {
      lastNotifiedState_ = ReservoirLevelState::EMPTY;
      buzzer.triggerEventTransition(BuzzerEventId::RESERVOIR_CRITICAL_EMPTY);
      char msg[112];
      snprintf(msg, sizeof(msg),
               "7-inch reservoir is EMPTY (air gap %.2f in, 0%%). Pump disabled.",
               gapIn);
      events.enqueueEvent("RESERVOIR_EMPTY",
                          EventSeverity::CRITICAL,
                          "Water Reservoir Empty",
                          msg,
                          nullptr,
                          0.0f);
    }
    return true;
  }

  if (pct <= cfg.reservoirCriticalPct) {
    reading_.state   = ReservoirLevelState::CRITICAL;
    criticalLockout_ = true;
    lowSinceMs_      = 0;

    if (lastNotifiedState_ != ReservoirLevelState::CRITICAL &&
        lastNotifiedState_ != ReservoirLevelState::EMPTY) {
      lastNotifiedState_ = ReservoirLevelState::CRITICAL;
      buzzer.triggerEventTransition(BuzzerEventId::RESERVOIR_CRITICAL_EMPTY);
      char msg[112];
      snprintf(msg, sizeof(msg),
               "Water level critically low at %.0f%% (air gap %.2f in). Pump stopped to prevent dry running.",
               pct, gapIn);
      events.enqueueEvent("RESERVOIR_CRITICAL",
                          EventSeverity::CRITICAL,
                          "Critical Water Level",
                          msg,
                          nullptr,
                          pct);
    }
    return true;
  }

  // If previously locked out by CRITICAL/EMPTY/FAULT, require refilling >= reservoirRecoverPct (25%)
  if (criticalLockout_) {
    if (pct >= cfg.reservoirRecoverPct) {
      criticalLockout_ = false;
      buzzer.clearEventLatch(BuzzerEventId::RESERVOIR_CRITICAL_EMPTY);
    } else {
      reading_.state = ReservoirLevelState::CRITICAL;
      return true;
    }
  }

  if (pct <= cfg.reservoirLowPct) {
    if (lowSinceMs_ == 0) {
      lowSinceMs_ = nowMs;
    }
    if (uint32_t(nowMs - lowSinceMs_) >= Config::RESERVOIR_LOW_CONFIRM_MS) {
      reading_.state = ReservoirLevelState::LOW_WATER;
      if (lastNotifiedState_ != ReservoirLevelState::LOW_WATER) {
        lastNotifiedState_ = ReservoirLevelState::LOW_WATER;
        buzzer.triggerEventTransition(BuzzerEventId::RESERVOIR_LOW);
        char msg[112];
        snprintf(msg, sizeof(msg),
                 "Reservoir water level is low at %.0f%% (air gap %.2f in). Refill the 7-inch tank soon.",
                 pct, gapIn);
        events.enqueueEvent("RESERVOIR_LOW",
                            EventSeverity::WARNING,
                            "Low Water Reservoir",
                            msg,
                            nullptr,
                            pct);
      }
    }
    return false;  // Low warning allows current run above 10% cutoff
  }

  // Normal level (>20%, or >=25% after low/critical hysteresis)
  if (reading_.state == ReservoirLevelState::LOW_WATER && pct < cfg.reservoirRecoverPct) {
    return false;  // Hold LOW_WATER state until recovery hysteresis is crossed
  }

  lowSinceMs_        = 0;
  reading_.state     = ReservoirLevelState::NORMAL;
  lastNotifiedState_ = ReservoirLevelState::NORMAL;
  buzzer.clearEventLatch(BuzzerEventId::RESERVOIR_LOW);
  buzzer.clearEventLatch(BuzzerEventId::RESERVOIR_CRITICAL_EMPTY);
  return false;
}

bool ReservoirManager::isPumpAllowed() const {
  return reading_.valid &&
         !criticalLockout_ &&
         reading_.state != ReservoirLevelState::CRITICAL &&
         reading_.state != ReservoirLevelState::EMPTY &&
         reading_.state != ReservoirLevelState::SENSOR_FAULT;
}

float ReservoirManager::measureSingleAirGapInches() const {
  digitalWrite(Pins::ULTRASONIC_TRIG, LOW);
  delayMicroseconds(3);
  digitalWrite(Pins::ULTRASONIC_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(Pins::ULTRASONIC_TRIG, LOW);

  // Bounded pulseIn (max 15ms) so a disconnected sensor never stalls the loop
  const unsigned long durationUs =
      pulseIn(Pins::ULTRASONIC_ECHO, HIGH, Config::ULTRASONIC_ECHO_TIMEOUT_US);

  // MANDATORY SAFETY RULE: Never convert timeout (0) into 0.0 inches ("100% full")!
  if (durationUs == 0) {
    return NAN;
  }

  // Speed of sound ~0.0343 cm/us -> distance_cm = durationUs * 0.01715; 1 inch = 2.54 cm
  const float distanceInches = (static_cast<float>(durationUs) * 0.01715f) / 2.54f;
  return distanceInches;
}

float ReservoirManager::measureMedianAirGapInches() const {
  float validSamples[5];
  int   validCount = 0;

  for (int i = 0; i < 5; ++i) {
    const float sample = measureSingleAirGapInches();
    if (isfinite(sample) &&
        sample >= Config::DEFAULT_RESERVOIR_MIN_VALID_IN &&
        sample <= Config::DEFAULT_RESERVOIR_MAX_VALID_IN) {
      validSamples[validCount++] = sample;
    }
    delayMicroseconds(200);
  }

  if (validCount < 3) {
    return NAN;
  }

  for (int i = 0; i < validCount - 1; ++i) {
    for (int j = i + 1; j < validCount; ++j) {
      if (validSamples[j] < validSamples[i]) {
        const float tmp = validSamples[i];
        validSamples[i] = validSamples[j];
        validSamples[j] = tmp;
      }
    }
  }
  return validSamples[validCount / 2];
}

float ReservoirManager::computeWaterPercent(float gapIn,
                                            float fullGapIn,
                                            float emptyGapIn) const {
  if (!isfinite(gapIn) || !isfinite(fullGapIn) || !isfinite(emptyGapIn) ||
      emptyGapIn <= fullGapIn) {
    return NAN;
  }
  const float pct = 100.0f * (emptyGapIn - gapIn) / (emptyGapIn - fullGapIn);
  return constrain(pct, 0.0f, 100.0f);
}
