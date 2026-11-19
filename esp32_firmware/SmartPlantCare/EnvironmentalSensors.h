#pragma once
/**
 * @file EnvironmentalSensors.h
 * @brief Nonblocking DHT22 (GPIO4) and LDR (GPIO36 ADC1) monitoring with sustained
 *        threshold confirmation, recovery hysteresis, buzzer triggers, and event emission.
 */

#include <Arduino.h>
#include <DHT.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EventNotificationManager.h"
#include "Models.h"
#include "Pins.h"

class EnvironmentalSensors {
 public:
  EnvironmentalSensors();

  void begin();
  void sampleDht22(uint32_t nowMs,
                   const RuntimeConfig& cfg,
                   BuzzerManager& buzzer,
                   EventNotificationManager& events);

  void sampleLdr(uint32_t nowMs,
                 const RuntimeConfig& cfg,
                 BuzzerManager& buzzer,
                 EventNotificationManager& events);

  const EnvironmentReading& reading() const { return reading_; }

 private:
  int readLdrMedianAdc() const;

  DHT                dht_;
  EnvironmentReading reading_;

  uint32_t tempAbnormStartMs_  = 0;
  EnvBand  pendingTempBand_    = EnvBand::NORMAL;
  bool     dhtFaultNotified_   = false;

  uint32_t humAbnormStartMs_   = 0;
  EnvBand  pendingHumBand_     = EnvBand::NORMAL;

  uint32_t lightAbnormStartMs_ = 0;
  EnvBand  pendingLightBand_   = EnvBand::NORMAL;
  bool     lightFaultNotified_ = false;
};
