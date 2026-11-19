#pragma once
/**
 * @file BuzzerManager.h
 * @brief Nonblocking GPIO18 buzzer scheduler with priority preemption and deduplication.
 *
 * AUTHORITATIVE BUZZER MATRIX (PHASE 5):
 * - Successful device pairing: 1 short beep (150ms)
 * - High/low temperature alert: 1 short beep (150ms)
 * - Low/high humidity alert: 1 short beep (150ms)
 * - Too bright/too dark light alert: 1 short beep (150ms)
 * - Weather forecast warning only (>=60%, dry): SILENT
 * - Rain sensor wet (<60% forecast): SILENT
 * - Confirmed combined rain suspension: 2 short beeps (2 x 150ms)
 * - Reservoir low (<=20%): 2 short beeps (2 x 150ms)
 * - Reservoir critical/empty/invalid: 3 short beeps (3 x 150ms)
 * - Critical sensor or electrical/emergency fault: 3 short beeps (3 x 150ms)
 * - Normal sensor readings & normal irrigation progress: SILENT
 */

#include <Arduino.h>
#include "Config.h"
#include "Pins.h"

enum class BuzzerEventId : uint8_t {
  NONE                     = 0,
  BOOT_READY_CHIRP         = 1,
  PAIRING_SUCCESS          = 2,
  TEMP_ALERT               = 3,
  HUMIDITY_ALERT           = 4,
  LIGHT_ALERT              = 5,
  CONFIRMED_RAIN_SUSPEND   = 6,
  RESERVOIR_LOW            = 7,
  RESERVOIR_CRITICAL_EMPTY = 8,
  CRITICAL_SYSTEM_FAULT    = 9,
  COUNT                    = 10
};

enum class BuzzerPriority : uint8_t {
  LOW_PRIO      = 1,
  ENV_ALERT     = 2,
  MEDIUM_ALERT  = 3,
  CRITICAL_PRIO = 4
};

class BuzzerManager {
 public:
  void begin(bool isPassive = Config::BUZZER_IS_PASSIVE);
  void service(uint32_t nowMs);

  // Trigger a pattern once on state transition; ignored while that event remains latched
  void triggerEventTransition(BuzzerEventId eventId);

  // Rearm a previously triggered event once its underlying condition has recovered
  void clearEventLatch(BuzzerEventId eventId);

  void setMuted(bool muted);
  bool isMuted() const { return muted_; }
  void stopImmediately();

 private:
  struct PatternSpec {
    uint8_t        pulses;
    uint16_t       onMs;
    uint16_t       gapMs;
    BuzzerPriority priority;
  };

  PatternSpec specForEvent(BuzzerEventId eventId) const;
  void        setHardwareOutput(bool on);

  bool           isPassive_                        = Config::BUZZER_IS_PASSIVE;
  bool           muted_                            = false;
  bool           outputOn_                         = false;
  uint8_t        remainingPulses_                  = 0;
  uint16_t       activeOnMs_                       = 0;
  uint16_t       activeGapMs_                      = 0;
  BuzzerPriority activePriority_                   = BuzzerPriority::LOW_PRIO;
  uint32_t       phaseStartedAtMs_                 = 0;
  bool           eventLatched_[static_cast<uint8_t>(BuzzerEventId::COUNT)] = {false};
};
