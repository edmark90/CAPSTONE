#include "BuzzerManager.h"

void BuzzerManager::begin(bool isPassive) {
  isPassive_  = isPassive;
  activeHigh_ = (Config::BUZZER_ACTIVE_HIGH != 0);
  pinMode(Pins::BUZZER, OUTPUT);
  setHardwareOutput(false);
  for (uint8_t i = 0; i < static_cast<uint8_t>(BuzzerEventId::COUNT); ++i) {
    eventLatched_[i] = false;
  }
  Serial.printf("[BUZZER] Initialized on GPIO%u (mode=%s)\n",
                static_cast<unsigned int>(Pins::BUZZER),
                isPassive_ ? "PASSIVE_PWM" : (activeHigh_ ? "ACTIVE_HIGH" : "ACTIVE_LOW"));
}

void BuzzerManager::configureDriverMode(bool isPassive, bool activeHigh) {
  stopImmediately();
  isPassive_  = isPassive;
  activeHigh_ = activeHigh;
  pinMode(Pins::BUZZER, OUTPUT);
  setHardwareOutput(false);
  Serial.printf("[BUZZER] Driver mode set on GPIO%u -> %s\n",
                static_cast<unsigned int>(Pins::BUZZER),
                isPassive_ ? "PASSIVE_PWM" : (activeHigh_ ? "ACTIVE_HIGH" : "ACTIVE_LOW"));
}

void BuzzerManager::playDiagnosticBeep() {
  if (muted_) {
    Serial.println(F("[BUZZER] Currently muted. Run 'unmute' first."));
    return;
  }
  activePriority_   = BuzzerPriority::CRITICAL_PRIO;
  activeOnMs_       = 220;
  activeGapMs_      = 180;
  remainingPulses_  = 1;  // 2 clear beeps total (1 now + 1 remaining)
  phaseStartedAtMs_ = millis();
  setHardwareOutput(true);
}

void BuzzerManager::triggerEventTransition(BuzzerEventId eventId) {
  const uint8_t idx = static_cast<uint8_t>(eventId);
  if (idx == 0 || idx >= static_cast<uint8_t>(BuzzerEventId::COUNT)) return;

  // Deduplicate: do not re-beep if this exact condition is already latched active
  if (eventLatched_[idx]) return;
  eventLatched_[idx] = true;

  if (muted_) return;

  const PatternSpec spec = specForEvent(eventId);
  if (spec.pulses == 0) return;

  // Preempt current pattern only if idle or incoming event has strictly higher priority
  const bool isIdle = (remainingPulses_ == 0 && !outputOn_);
  if (isIdle || spec.priority > activePriority_) {
    activePriority_   = spec.priority;
    activeOnMs_       = spec.onMs;
    activeGapMs_      = spec.gapMs;
    remainingPulses_  = spec.pulses - 1;
    phaseStartedAtMs_ = millis();
    setHardwareOutput(true);
  }
}

void BuzzerManager::clearEventLatch(BuzzerEventId eventId) {
  const uint8_t idx = static_cast<uint8_t>(eventId);
  if (idx < static_cast<uint8_t>(BuzzerEventId::COUNT)) {
    eventLatched_[idx] = false;
  }
}

void BuzzerManager::service(uint32_t nowMs) {
  if (muted_) {
    if (outputOn_ || remainingPulses_ > 0) {
      stopImmediately();
    }
    return;
  }

  if (outputOn_) {
    if (uint32_t(nowMs - phaseStartedAtMs_) >= activeOnMs_) {
      setHardwareOutput(false);
      phaseStartedAtMs_ = nowMs;
      if (remainingPulses_ == 0) {
        activePriority_ = BuzzerPriority::LOW_PRIO;
      }
    }
  } else if (remainingPulses_ > 0) {
    if (uint32_t(nowMs - phaseStartedAtMs_) >= activeGapMs_) {
      --remainingPulses_;
      phaseStartedAtMs_ = nowMs;
      setHardwareOutput(true);
    }
  }
}

void BuzzerManager::setMuted(bool muted) {
  muted_ = muted;
  if (muted_) {
    stopImmediately();
  }
}

void BuzzerManager::stopImmediately() {
  setHardwareOutput(false);
  remainingPulses_ = 0;
  activePriority_  = BuzzerPriority::LOW_PRIO;
}

BuzzerManager::PatternSpec BuzzerManager::specForEvent(BuzzerEventId eventId) const {
  switch (eventId) {
    case BuzzerEventId::BOOT_READY_CHIRP:
      return {1, Config::BEEP_BOOT_ON_MS, Config::BEEP_SHORT_GAP_MS, BuzzerPriority::LOW_PRIO};

    case BuzzerEventId::PAIRING_SUCCESS:
      // Clear 1 confirmation beep (250ms) with MEDIUM_ALERT priority so it is never dropped
      return {1, 250, Config::BEEP_SHORT_GAP_MS, BuzzerPriority::MEDIUM_ALERT};

    case BuzzerEventId::WIFI_CONNECTED_SUCCESS:
      // Clear 1 confirmation beep (220ms) whenever Wi-Fi connects successfully
      return {1, 220, Config::BEEP_SHORT_GAP_MS, BuzzerPriority::MEDIUM_ALERT};

    case BuzzerEventId::TEMP_ALERT:
    case BuzzerEventId::HUMIDITY_ALERT:
    case BuzzerEventId::LIGHT_ALERT:
      return {1, Config::BEEP_SHORT_ON_MS, Config::BEEP_SHORT_GAP_MS, BuzzerPriority::ENV_ALERT};

    case BuzzerEventId::IRRIGATION_START:
    case BuzzerEventId::IRRIGATION_DONE:
      // 1 short confirmation beep when watering starts and 1 short beep when watering finishes
      return {1, Config::BEEP_SHORT_ON_MS, Config::BEEP_SHORT_GAP_MS, BuzzerPriority::MEDIUM_ALERT};

    case BuzzerEventId::CONFIRMED_RAIN_SUSPEND:
    case BuzzerEventId::RESERVOIR_LOW:
      return {2, Config::BEEP_SHORT_ON_MS, Config::BEEP_SHORT_GAP_MS, BuzzerPriority::MEDIUM_ALERT};

    case BuzzerEventId::RESERVOIR_CRITICAL_EMPTY:
    case BuzzerEventId::CRITICAL_SYSTEM_FAULT:
      return {3, Config::BEEP_SHORT_ON_MS, Config::BEEP_SHORT_GAP_MS, BuzzerPriority::CRITICAL_PRIO};

    default:
      return {0, 0, 0, BuzzerPriority::LOW_PRIO};
  }
}

void BuzzerManager::setHardwareOutput(bool on) {
  outputOn_ = on;
  if (isPassive_) {
    if (on) {
      tone(Pins::BUZZER, Config::BUZZER_PASSIVE_FREQ);
    } else {
      noTone(Pins::BUZZER);
      digitalWrite(Pins::BUZZER, LOW);
    }
  } else {
    const int activeLevel   = activeHigh_ ? HIGH : LOW;
    const int inactiveLevel = activeHigh_ ? LOW : HIGH;
    digitalWrite(Pins::BUZZER, on ? activeLevel : inactiveLevel);
  }
}
