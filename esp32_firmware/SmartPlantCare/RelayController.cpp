#include "RelayController.h"

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

  // Preload inactive latch state BEFORE switching GPIO direction to OUTPUT
  // so active-LOW relay modules do not pulse ON during boot initialization.
  for (int pin : pins) {
    digitalWrite(pin, inactiveLevel);
    pinMode(pin, OUTPUT);
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

  writeRelayPin(valvePinForZone(zone), physicalLoadsEnabled_);
  valveOn_[idx] = true;
  activeValve_  = zone;
  return true;
}

void RelayController::closeAllValves() {
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
}

bool RelayController::setPump(bool on) {
  if (!on) {
    writeRelayPin(Pins::RELAY_PUMP_IN4, false);
    pumpCommandedOn_ = false;
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

  writeRelayPin(Pins::RELAY_PUMP_IN4, physicalLoadsEnabled_);
  pumpCommandedOn_ = true;
  return true;
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
