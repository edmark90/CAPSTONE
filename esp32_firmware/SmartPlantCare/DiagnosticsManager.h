#pragma once
/**
 * @file DiagnosticsManager.h
 * @brief Heap/loop health telemetry, reset-reason logging, and nonblocking Serial CLI
 *        for bench hardware verification and logic simulation.
 */

#include <Arduino.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EnvironmentalSensors.h"
#include "EventNotificationManager.h"
#include "IrrigationController.h"
#include "LcdManager.h"
#include "Models.h"
#include "ProvisioningPairingManager.h"
#include "RainWeatherManager.h"
#include "RelayController.h"
#include "ReservoirManager.h"
#include "SoilMoistureManager.h"

class DiagnosticsManager {
 public:
  void begin();
  void recordSafetyLoopExecution(uint32_t nowMs);
  void updatePeriodicMetrics(uint32_t nowMs, const EventNotificationManager& events);

  void printStatusReport(uint32_t nowMs,
                         const RuntimeConfig& cfg,
                         const ProvisioningPairingManager& provisioning,
                         const EnvironmentReading& env,
                         const SoilZoneStatus soil[3],
                         const ReservoirReading& reservoir,
                         const RainGateStatus& rain,
                         const WeatherSnapshot& weather,
                         const RelayController& relays,
                         const IrrigationController& irrigation,
                         const LcdManager& lcd) const;

  void pollSerialCli(uint32_t nowMs,
                     RuntimeConfig& ioConfig,
                     ProvisioningPairingManager& provisioning,
                     EnvironmentalSensors& envSensors,
                     SoilMoistureManager& soil,
                     ReservoirManager& reservoir,
                     RainWeatherManager& rainWeather,
                     RelayController& relays,
                     IrrigationController& irrigation,
                     BuzzerManager& buzzer,
                     EventNotificationManager& events,
                     LcdManager& lcd);

  DiagnosticsMetrics&       metrics() { return metrics_; }
  const DiagnosticsMetrics& metrics() const { return metrics_; }

 private:
  void processCliLine(char* line,
                      uint32_t nowMs,
                      RuntimeConfig& ioConfig,
                      ProvisioningPairingManager& provisioning,
                      EnvironmentalSensors& envSensors,
                      SoilMoistureManager& soil,
                      ReservoirManager& reservoir,
                      RainWeatherManager& rainWeather,
                      RelayController& relays,
                      IrrigationController& irrigation,
                      BuzzerManager& buzzer,
                      EventNotificationManager& events,
                      LcdManager& lcd);
  void printCliHelp() const;

  DiagnosticsMetrics metrics_;
  char               cliBuffer_[128] = {0};
  size_t             cliLen_         = 0;
};
