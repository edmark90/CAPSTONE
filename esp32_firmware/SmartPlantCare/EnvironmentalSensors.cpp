#include "EnvironmentalSensors.h"
#include <math.h>
#include <stdio.h>

EnvironmentalSensors::EnvironmentalSensors() : dht_(Pins::DHT22_DATA, DHT22) {}

void EnvironmentalSensors::begin() {
  dht_.begin();
  pinMode(Pins::LDR_ADC, INPUT);
  analogReadResolution(12);
  analogSetPinAttenuation(Pins::LDR_ADC, ADC_11db);
}

void EnvironmentalSensors::sampleDht22(uint32_t nowMs,
                                       const RuntimeConfig& cfg,
                                       BuzzerManager& buzzer,
                                       EventNotificationManager& events) {
  const float rawTemp = dht_.readTemperature();
  const float rawHum  = dht_.readHumidity();

  const bool plausible = isfinite(rawTemp) && isfinite(rawHum) &&
                         rawTemp >= -10.0f && rawTemp <= 65.0f &&
                         rawHum >= 1.0f && rawHum <= 100.0f;

  if (!plausible) {
    dhtValidStreak_ = 0;
    if (reading_.dhtFailStreak < 250) ++reading_.dhtFailStreak;
    // Allow up to 2 transient missed reads before declaring sensor fault
    if (reading_.dhtFailStreak >= 3) {
      reading_.dhtValid      = false;
      reading_.temperatureC  = NAN;
      reading_.humidityRh    = NAN;
      reading_.tempBand      = EnvBand::INVALID;
      reading_.humidityBand  = EnvBand::INVALID;
      tempAbnormStartMs_     = 0;
      humAbnormStartMs_      = 0;
      const bool cooldownReady = (lastDhtFaultEventMs_ == 0) ||
                                 (uint32_t(nowMs - lastDhtFaultEventMs_) >= 600000UL);
      if (!dhtFaultNotified_ && cooldownReady) {
        dhtFaultNotified_    = true;
        lastDhtFaultEventMs_ = (nowMs == 0) ? 1UL : nowMs;
        events.enqueueEvent("DHT_SENSOR_FAULT",
                            EventSeverity::WARNING,
                            "DHT22 Sensor Error",
                            "Temperature/humidity sensor on GPIO4 returned invalid data.");
      }
    }
    return;
  }

  // Reject single-sample impossible jump (>12C or >35% RH in 2.5s)
  if (reading_.dhtValid && reading_.dhtFailStreak == 0) {
    if (fabsf(rawTemp - reading_.temperatureC) > 12.0f ||
        fabsf(rawHum - reading_.humidityRh) > 35.0f) {
      ++reading_.dhtFailStreak;
      return;
    }
  }

  reading_.dhtFailStreak  = 0;
  if (dhtValidStreak_ < 250) ++dhtValidStreak_;
  if (dhtValidStreak_ >= 3) {
    dhtFaultNotified_ = false;
  }
  reading_.dhtValid       = true;
  reading_.dhtSampledAtMs = nowMs;
  reading_.temperatureC   = rawTemp;
  reading_.humidityRh     = rawHum;

  // ─── 1. Temperature Band & 30s Sustained Confirmation ─────────────────────
  EnvBand candidateTemp = EnvBand::NORMAL;
  if (rawTemp >= cfg.tempHighC) {
    candidateTemp = EnvBand::HIGH_VAL;
  } else if (cfg.enableTempLowAlert && rawTemp <= cfg.tempLowC) {
    candidateTemp = EnvBand::LOW_VAL;
  } else if (reading_.tempBand == EnvBand::HIGH_VAL && rawTemp > cfg.tempHighRearmC) {
    candidateTemp = EnvBand::HIGH_VAL;  // Hold until below rearm hysteresis
  } else if (reading_.tempBand == EnvBand::LOW_VAL && rawTemp < cfg.tempLowRearmC) {
    candidateTemp = EnvBand::LOW_VAL;
  }

  if (reading_.tempBand == EnvBand::INVALID) {
    reading_.tempBand = EnvBand::NORMAL;
  }

  if (candidateTemp != EnvBand::NORMAL) {
    if (pendingTempBand_ != candidateTemp) {
      pendingTempBand_   = candidateTemp;
      tempAbnormStartMs_ = nowMs;
    } else if (reading_.tempBand != candidateTemp &&
               uint32_t(nowMs - tempAbnormStartMs_) >= Config::ENV_ALERT_CONFIRM_MS) {
      reading_.tempBand = candidateTemp;
      buzzer.triggerEventTransition(BuzzerEventId::TEMP_ALERT);

      char msg[112];
      if (candidateTemp == EnvBand::HIGH_VAL) {
        snprintf(msg, sizeof(msg),
                 "Air temperature is %.1f C (limit %.1f C). Check ventilation and heat exposure.",
                 rawTemp, cfg.tempHighC);
        events.enqueueEvent("TEMP_HIGH", EventSeverity::WARNING,
                            "High Temperature Alert", msg, nullptr, rawTemp);
      } else {
        snprintf(msg, sizeof(msg),
                 "Air temperature dropped to %.1f C (limit %.1f C).",
                 rawTemp, cfg.tempLowC);
        events.enqueueEvent("TEMP_LOW", EventSeverity::WARNING,
                            "Low Temperature Alert", msg, nullptr, rawTemp);
      }
    }
  } else {
    pendingTempBand_   = EnvBand::NORMAL;
    tempAbnormStartMs_ = 0;
    if (reading_.tempBand != EnvBand::NORMAL) {
      reading_.tempBand = EnvBand::NORMAL;
      buzzer.clearEventLatch(BuzzerEventId::TEMP_ALERT);
    }
  }

  // ─── 2. Humidity Band & 30s Sustained Confirmation ────────────────────────
  EnvBand candidateHum = EnvBand::NORMAL;
  if (rawHum <= cfg.humLowRh) {
    candidateHum = EnvBand::LOW_VAL;
  } else if (cfg.enableHumHighAlert && rawHum >= cfg.humHighRh) {
    candidateHum = EnvBand::HIGH_VAL;
  } else if (reading_.humidityBand == EnvBand::LOW_VAL && rawHum < cfg.humLowRearmRh) {
    candidateHum = EnvBand::LOW_VAL;
  } else if (reading_.humidityBand == EnvBand::HIGH_VAL && rawHum > cfg.humHighRearmRh) {
    candidateHum = EnvBand::HIGH_VAL;
  }

  if (reading_.humidityBand == EnvBand::INVALID) {
    reading_.humidityBand = EnvBand::NORMAL;
  }

  if (candidateHum != EnvBand::NORMAL) {
    if (pendingHumBand_ != candidateHum) {
      pendingHumBand_   = candidateHum;
      humAbnormStartMs_ = nowMs;
    } else if (reading_.humidityBand != candidateHum &&
               uint32_t(nowMs - humAbnormStartMs_) >= Config::ENV_ALERT_CONFIRM_MS) {
      reading_.humidityBand = candidateHum;
      buzzer.triggerEventTransition(BuzzerEventId::HUMIDITY_ALERT);

      char msg[112];
      if (candidateHum == EnvBand::LOW_VAL) {
        snprintf(msg, sizeof(msg),
                 "Relative humidity dropped to %.0f%% (minimum %.0f%%).",
                 rawHum, cfg.humLowRh);
        events.enqueueEvent("HUMIDITY_LOW", EventSeverity::WARNING,
                            "Low Humidity Alert", msg, nullptr, rawHum);
      } else {
        snprintf(msg, sizeof(msg),
                 "Relative humidity reached %.0f%% (maximum %.0f%%).",
                 rawHum, cfg.humHighRh);
        events.enqueueEvent("HUMIDITY_HIGH", EventSeverity::WARNING,
                            "High Humidity Alert", msg, nullptr, rawHum);
      }
    }
  } else {
    pendingHumBand_   = EnvBand::NORMAL;
    humAbnormStartMs_ = 0;
    if (reading_.humidityBand != EnvBand::NORMAL) {
      reading_.humidityBand = EnvBand::NORMAL;
      buzzer.clearEventLatch(BuzzerEventId::HUMIDITY_ALERT);
    }
  }
}

void EnvironmentalSensors::sampleLdr(uint32_t nowMs,
                                     const RuntimeConfig& cfg,
                                     BuzzerManager& buzzer,
                                     EventNotificationManager& events) {
  const int raw = readLdrMedianAdc();
  reading_.ldrRaw = raw;

  if (raw < Config::LDR_ADC_MIN_PLAUSIBLE || raw > Config::LDR_ADC_MAX_PLAUSIBLE ||
      cfg.ldrBrightRaw == cfg.ldrDarkRaw) {
    lightValidStreak_ = 0;
    if (reading_.lightFailStreak < 250) ++reading_.lightFailStreak;
    if (reading_.lightFailStreak >= 3) {
      reading_.lightValid    = false;
      reading_.brightnessPct = NAN;
      reading_.lightBand     = EnvBand::INVALID;
      lightAbnormStartMs_    = 0;
      const bool cooldownReady = (lastLightFaultEventMs_ == 0) ||
                                 (uint32_t(nowMs - lastLightFaultEventMs_) >= 600000UL);
      if (!lightFaultNotified_ && cooldownReady) {
        lightFaultNotified_    = true;
        lastLightFaultEventMs_ = (nowMs == 0) ? 1UL : nowMs;
        events.enqueueEvent("LIGHT_SENSOR_FAULT",
                            EventSeverity::WARNING,
                            "Light Sensor Fault",
                            "LDR sensor on GPIO36 reading out of valid range.");
      }
    }
    return;
  }

  reading_.lightFailStreak  = 0;
  if (lightValidStreak_ < 250) ++lightValidStreak_;
  if (lightValidStreak_ >= 3) {
    lightFaultNotified_ = false;
  }
  reading_.lightValid       = true;
  reading_.lightSampledAtMs = nowMs;

  // Convert calibrated raw ADC to relative brightness index % (NOT lux)
  const float denom = static_cast<float>(cfg.ldrBrightRaw - cfg.ldrDarkRaw);
  const float pct   = 100.0f * static_cast<float>(raw - cfg.ldrDarkRaw) / denom;
  reading_.brightnessPct = constrain(pct, 0.0f, 100.0f);

  EnvBand candidateLight = EnvBand::NORMAL;
  if (reading_.brightnessPct >= cfg.lightHighPct) {
    candidateLight = EnvBand::HIGH_VAL;
  } else if (reading_.brightnessPct <= cfg.lightLowPct) {
    candidateLight = EnvBand::LOW_VAL;
  } else if (reading_.lightBand == EnvBand::HIGH_VAL &&
             reading_.brightnessPct > cfg.lightHighRearmPct) {
    candidateLight = EnvBand::HIGH_VAL;
  } else if (reading_.lightBand == EnvBand::LOW_VAL &&
             reading_.brightnessPct < cfg.lightLowRearmPct) {
    candidateLight = EnvBand::LOW_VAL;
  }

  if (reading_.lightBand == EnvBand::INVALID) {
    reading_.lightBand = EnvBand::NORMAL;
  }

  if (candidateLight != EnvBand::NORMAL) {
    if (pendingLightBand_ != candidateLight) {
      pendingLightBand_   = candidateLight;
      lightAbnormStartMs_ = nowMs;
    } else if (reading_.lightBand != candidateLight &&
               uint32_t(nowMs - lightAbnormStartMs_) >= Config::ENV_ALERT_CONFIRM_MS) {
      reading_.lightBand = candidateLight;
      buzzer.triggerEventTransition(BuzzerEventId::LIGHT_ALERT);

      char msg[112];
      if (candidateLight == EnvBand::HIGH_VAL) {
        snprintf(msg, sizeof(msg),
                 "Relative brightness index reached %.0f%%. Check intense sunlight exposure.",
                 reading_.brightnessPct);
        events.enqueueEvent("LIGHT_HIGH", EventSeverity::WARNING,
                            "Light Too Bright", msg, nullptr, reading_.brightnessPct);
      } else {
        snprintf(msg, sizeof(msg),
                 "Relative brightness index dropped to %.0f%%. Check plant light exposure.",
                 reading_.brightnessPct);
        events.enqueueEvent("LIGHT_LOW", EventSeverity::WARNING,
                            "Light Too Dark", msg, nullptr, reading_.brightnessPct);
      }
    }
  } else {
    pendingLightBand_   = EnvBand::NORMAL;
    lightAbnormStartMs_ = 0;
    if (reading_.lightBand != EnvBand::NORMAL) {
      reading_.lightBand = EnvBand::NORMAL;
      buzzer.clearEventLatch(BuzzerEventId::LIGHT_ALERT);
    }
  }
}

int EnvironmentalSensors::readLdrMedianAdc() const {
  int samples[5];
  for (int i = 0; i < 5; ++i) {
    samples[i] = analogRead(Pins::LDR_ADC);
  }
  for (int i = 0; i < 4; ++i) {
    for (int j = i + 1; j < 5; ++j) {
      if (samples[j] < samples[i]) {
        const int tmp = samples[i];
        samples[i]    = samples[j];
        samples[j]    = tmp;
      }
    }
  }
  return samples[2];
}
