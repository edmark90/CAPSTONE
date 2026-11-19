#pragma once
/**
 * @file RelayController.h
 * @brief Safe 4-channel relay driver for 3 crop solenoid valves and 1 shared water pump.
 *
 * SAFETY GUARANTEES:
 * 1. Preloads inactive output latch BEFORE setting pinMode(OUTPUT) to prevent boot glitches.
 * 2. Strictly prohibits opening more than ONE crop solenoid valve at a time.
 * 3. Pump can NEVER be energized unless exactly ONE valve is open AND physicalLoadsEnabled == true.
 * 4. emergencyAllOff() immediately de-energizes Pump + all 3 Valves in a single synchronous call.
 */

#include <Arduino.h>
#include "Config.h"
#include "Models.h"
#include "Pins.h"

class RelayController {
 public:
  void begin(bool activeLow = Config::RELAY_ACTIVE_LOW,
             bool loadsEnabled = Config::ENABLE_PHYSICAL_LOAD_OUTPUTS);

  // Immediately forces Pump OFF and all 3 Valves OFF
  void emergencyAllOff();

  // Enable/disable physical coil actuation (keep false during unloaded bench testing)
  void setPhysicalLoadsEnabled(bool enabled);
  bool isPhysicalLoadsEnabled() const { return physicalLoadsEnabled_; }

  // Open only the specified crop valve (closes any other valve first if different)
  bool openSingleValve(CropZoneId zone);
  void closeAllValves();

  // Energize or de-energize the shared pump (refuses to turn ON if no valve is open)
  bool setPump(bool on);

  // Periodic hardware consistency verification (called every 25ms from safety loop)
  bool verifyHardwareConsistency();

  bool isPumpOn() const { return pumpCommandedOn_; }
  bool isValveOn(CropZoneId zone) const;
  CropZoneId activeValveZone() const { return activeValve_; }

 private:
  void writeRelayPin(int pin, bool energize);
  int  valvePinForZone(CropZoneId zone) const;

  bool       activeLow_            = Config::RELAY_ACTIVE_LOW;
  bool       physicalLoadsEnabled_ = Config::ENABLE_PHYSICAL_LOAD_OUTPUTS;
  bool       pumpCommandedOn_      = false;
  bool       valveOn_[3]           = {false, false, false};
  CropZoneId activeValve_          = CropZoneId::NONE;
};
