#include "RelayController.h"
#include <driver/gpio.h>

void RelayController::begin(bool activeLow, bool loadsEnabled) {
  activeLow_            = activeLow;
  physicalLoadsEnabled_ = loadsEnabled;

  const int inactiveLevel = activeLow_ ? HIGH : LOW;
  const int pins[4] = {
      Pins::RELAY_TOMATO_IN1,
      Pins::RELAY_CHILI_IN2,
      Pins::RELAY_EGGPLANT_IN3,
      Pins::RELAY_PUMP_IN4
  };

  // Detach GPIO25/26/27 from DAC/RTC/ADC2 muxing, set max push-pull drive strength (40mA),
  // and preload inactive latch BEFORE switching to OUTPUT so active-LOW relays stay OFF at boot.
  for (int pin : pins) {
    gpio_reset_pin(static_cast<gpio_num_t>(pin));
    digitalWrite(pin, inactiveLevel);
    pinMode(pin, OUTPUT);
    gpio_set_drive_capability(static_cast<gpio_num_t>(pin), GPIO_DRIVE_CAP_3);
    digitalWrite(pin, inactiveLevel);
  }

  pumpCommandedOn_ = false;
  valveOn_[0]      = false;
  valveOn_[1]      = false;
  valveOn_[2]      = false;
  activeValve_     = CropZoneId::NONE;
}

void RelayController::emergencyAllOff() {
  // Always de-energize pump FIRST, then all three valves
  writeRelayPin(Pins::RELAY_PUMP_IN4, false);
  writeRelayPin(Pins::RELAY_TOMATO_IN1, false);
  writeRelayPin(Pins::RELAY_CHILI_IN2, false);
  writeRelayPin(Pins::RELAY_EGGPLANT_IN3, false);

  pumpCommandedOn_ = false;
  valveOn_[0]      = false;
  valveOn_[1]      = false;
  valveOn_[2]      = false;
  activeValve_     = CropZoneId::NONE;
}

void RelayController::setPhysicalLoadsEnabled(bool enabled) {
  if (!enabled) {
    emergencyAllOff();
  }
  physicalLoadsEnabled_ = enabled;
}

bool RelayController::openSingleValve(CropZoneId zone) {
  if (zone == CropZoneId::NONE) {
    closeAllValves();
    return false;
  }

  const uint8_t idx = static_cast<uint8_t>(zone);
  if (idx > 2) {
    emergencyAllOff();
    return false;
  }

  // If switching zones while pump is somehow on, force pump off first
  if (pumpCommandedOn_ && activeValve_ != zone) {
    writeRelayPin(Pins::RELAY_PUMP_IN4, false);
    pumpCommandedOn_ = false;
  }

  // Ensure all other crop valves are OFF before opening the selected valve
  for (uint8_t i = 0; i < 3; ++i) {
    if (i != idx) {
      writeRelayPin(valvePinForZone(static_cast<CropZoneId>(i)), false);
      valveOn_[i] = false;
    }
  }

  const int valvePin = valvePinForZone(zone);
  writeRelayPin(valvePin, physicalLoadsEnabled_);
  valveOn_[idx] = true;
  activeValve_  = zone;
  Serial.printf("[RELAY] OPEN Valve %s (GPIO%d) -> %s (loadsEnabled=%s)\n",
                cropZoneDisplayName(zone),
                valvePin,
                (physicalLoadsEnabled_ ? (activeLow_ ? "ON [LOW]" : "ON [HIGH]") : "OFF [BENCH_SAFE]"),
                physicalLoadsEnabled_ ? "YES" : "NO");
  return true;
}

void RelayController::closeAllValves() {
  const bool anyWasOpen = (valveOn_[0] || valveOn_[1] || valveOn_[2] || pumpCommandedOn_);
  // Never leave pump running against closed valves
  if (pumpCommandedOn_) {
    writeRelayPin(Pins::RELAY_PUMP_IN4, false);
    pumpCommandedOn_ = false;
  }
  writeRelayPin(Pins::RELAY_TOMATO_IN1, false);
  writeRelayPin(Pins::RELAY_CHILI_IN2, false);
  writeRelayPin(Pins::RELAY_EGGPLANT_IN3, false);

  valveOn_[0]  = false;
  valveOn_[1]  = false;
  valveOn_[2]  = false;
  activeValve_ = CropZoneId::NONE;
  if (anyWasOpen) {
    Serial.println(F("[RELAY] CLOSE All Valves (GPIO25/26/27) & Pump (GPIO23) -> OFF"));
  }
}

bool RelayController::setPump(bool on) {
  if (!on) {
    const bool wasOn = pumpCommandedOn_;
    writeRelayPin(Pins::RELAY_PUMP_IN4, false);
    pumpCommandedOn_ = false;
    if (wasOn) {
      Serial.println(F("[RELAY] STOP Pump IN4 (GPIO23) -> OFF (valve stays open 250ms for pressure relief)"));
    }
    return true;
  }

  // Refuse to start pump unless exactly ONE valve is open
  uint8_t openCount = 0;
  for (uint8_t i = 0; i < 3; ++i) {
    if (valveOn_[i]) ++openCount;
  }
  if (openCount != 1 || activeValve_ == CropZoneId::NONE) {
    emergencyAllOff();
    return false;
  }

  // Explicitly assert BOTH the selected crop solenoid valve AND the shared water pump ON together!
  const int activeValvePin = valvePinForZone(activeValve_);
  writeRelayPin(activeValvePin, physicalLoadsEnabled_);
  writeRelayPin(Pins::RELAY_PUMP_IN4, physicalLoadsEnabled_);
  pumpCommandedOn_ = true;
  Serial.printf("[RELAY] WATERING %s: Valve (GPIO%d)=ON + Pump (GPIO%d)=ON [%s, loadsEnabled=%s]\n",
                cropZoneDisplayName(activeValve_),
                activeValvePin,
                Pins::RELAY_PUMP_IN4,
                activeLow_ ? "LOW" : "HIGH",
                physicalLoadsEnabled_ ? "YES" : "NO");
  return true;
}

void RelayController::setActiveLow(bool activeLow) {
  emergencyAllOff();
  activeLow_ = activeLow;
  const int inactiveLevel = activeLow_ ? HIGH : LOW;
  const int pins[4] = {
      Pins::RELAY_TOMATO_IN1,
      Pins::RELAY_CHILI_IN2,
      Pins::RELAY_EGGPLANT_IN3,
      Pins::RELAY_PUMP_IN4
  };
  for (int pin : pins) {
    digitalWrite(pin, inactiveLevel);
  }
  Serial.printf("[RELAY] Polarity set to %s (ON=%s, OFF=%s)\n",
                activeLow_ ? "ACTIVE-LOW" : "ACTIVE-HIGH",
                activeLow_ ? "LOW (0V)" : "HIGH (3.3V)",
                activeLow_ ? "HIGH (3.3V)" : "LOW (0V)");
}

void RelayController::runSequentialPinTest(uint32_t holdMs) {
  emergencyAllOff();
  physicalLoadsEnabled_ = true;
  const int pins[4] = {
      Pins::RELAY_TOMATO_IN1,
      Pins::RELAY_CHILI_IN2,
      Pins::RELAY_EGGPLANT_IN3,
      Pins::RELAY_PUMP_IN4
  };
  const char* labels[4] = {
      "IN1 Tomato Valve (GPIO25)",
      "IN2 Chili Valve (GPIO26)",
      "IN3 Eggplant Valve (GPIO27)",
      "IN4 Water Pump (GPIO23)"
  };
  Serial.printf("[RELAY-TEST] Pulsing all 4 channels one by one (polarity=%s)...\n",
                activeLow_ ? "ACTIVE-LOW" : "ACTIVE-HIGH");
  for (int i = 0; i < 4; ++i) {
    Serial.printf("  -> CH%d: %s ON (%s) for %lu ms\n",
                  i + 1, labels[i], activeLow_ ? "LOW" : "HIGH",
                  static_cast<unsigned long>(holdMs));
    writeRelayPin(pins[i], true);
    delay(holdMs);
    writeRelayPin(pins[i], false);
    delay(300);
  }
  emergencyAllOff();
  Serial.println(F("[RELAY-TEST] Done — all 4 channels OFF."));
}

void RelayController::runZonePairTest(CropZoneId zone, uint32_t holdMs) {
  emergencyAllOff();
  if (zone == CropZoneId::NONE) {
    Serial.println(F("[RELAY-TEST] Usage: testzone <tomato|chili|eggplant>"));
    return;
  }
  physicalLoadsEnabled_ = true;
  const int vPin = valvePinForZone(zone);
  Serial.printf("[RELAY-TEST] Opening %s Valve (GPIO%d) + Pump (GPIO%d) TOGETHER for %lu ms...\n",
                cropZoneDisplayName(zone), vPin, Pins::RELAY_PUMP_IN4,
                static_cast<unsigned long>(holdMs));
  writeRelayPin(vPin, true);
  delay(150);
  writeRelayPin(Pins::RELAY_PUMP_IN4, true);
  delay(holdMs);
  writeRelayPin(Pins::RELAY_PUMP_IN4, false);
  delay(250);
  writeRelayPin(vPin, false);
  emergencyAllOff();
  Serial.println(F("[RELAY-TEST] Pair test complete — Valve & Pump OFF."));
}

bool RelayController::verifyHardwareConsistency() {
  uint8_t openCount = 0;
  for (uint8_t i = 0; i < 3; ++i) {
    if (valveOn_[i]) ++openCount;
  }

  // Violation: more than 1 valve open, or pump on with 0 valves open
  if (openCount > 1 || (pumpCommandedOn_ && openCount != 1)) {
    emergencyAllOff();
    return false;
  }

  // Re-assert desired pin levels in case of EMI glitch
  for (uint8_t i = 0; i < 3; ++i) {
    writeRelayPin(valvePinForZone(static_cast<CropZoneId>(i)),
                  valveOn_[i] && physicalLoadsEnabled_);
  }
  writeRelayPin(Pins::RELAY_PUMP_IN4, pumpCommandedOn_ && physicalLoadsEnabled_);
  return true;
}

bool RelayController::isValveOn(CropZoneId zone) const {
  const uint8_t idx = static_cast<uint8_t>(zone);
  return (idx < 3) ? valveOn_[idx] : false;
}

void RelayController::writeRelayPin(int pin, bool energize) {
  if (pin < 0) return;
  const int activeLevel   = activeLow_ ? LOW : HIGH;
  const int inactiveLevel = activeLow_ ? HIGH : LOW;
  digitalWrite(pin, energize ? activeLevel : inactiveLevel);
}

int RelayController::valvePinForZone(CropZoneId zone) const {
  switch (zone) {
    case CropZoneId::TOMATO:   return Pins::RELAY_TOMATO_IN1;
    case CropZoneId::CHILI:    return Pins::RELAY_CHILI_IN2;
    case CropZoneId::EGGPLANT: return Pins::RELAY_EGGPLANT_IN3;
    default:                   return -1;
  }
}
