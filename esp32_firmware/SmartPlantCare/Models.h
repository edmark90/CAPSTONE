#pragma once
/**
 * @file Models.h
 * @brief Strongly typed state models, sensor measurements, events, and commands.
 */

#include <Arduino.h>
#include <stdint.h>
#include "Config.h"

enum class CropZoneId : uint8_t {
  TOMATO   = 0,
  CHILI    = 1,
  EGGPLANT = 2,
  NONE     = 255
};

inline const char* cropZoneSlug(CropZoneId zone) {
  switch (zone) {
    case CropZoneId::TOMATO:   return "tomato";
    case CropZoneId::CHILI:    return "chili";
    case CropZoneId::EGGPLANT: return "eggplant";
    default:                   return "none";
  }
}

inline const char* cropZoneDisplayName(CropZoneId zone) {
  switch (zone) {
    case CropZoneId::TOMATO:   return "TOMATO";
    case CropZoneId::CHILI:    return "CHILI";
    case CropZoneId::EGGPLANT: return "EGGPLANT";
    default:                   return "NONE";
  }
}

enum class DeviceMode : uint8_t {
  OUTDOOR = 0,
  INDOOR  = 1
};

enum class OwnershipState : uint8_t {
  UNCLAIMED     = 0,
  CLAIM_PENDING = 1,
  OWNED         = 2,
  RESET_PENDING = 3
};

enum class RainSensorState : uint8_t {
  DRY             = 0,
  WET_UNCONFIRMED = 1,
  WET_CONFIRMED   = 2,
  FAULT           = 3
};

enum class ReservoirLevelState : uint8_t {
  NORMAL       = 0,
  LOW_WATER    = 1,
  CRITICAL     = 2,
  EMPTY        = 3,
  SENSOR_FAULT = 4
};

inline const char* reservoirStateString(ReservoirLevelState st) {
  switch (st) {
    case ReservoirLevelState::NORMAL:       return "NORMAL";
    case ReservoirLevelState::LOW_WATER:    return "LOW";
    case ReservoirLevelState::CRITICAL:     return "CRITICAL";
    case ReservoirLevelState::EMPTY:        return "EMPTY";
    case ReservoirLevelState::SENSOR_FAULT: return "SENSOR_ERROR";
    default:                                return "UNKNOWN";
  }
}

enum class IrrigationState : uint8_t {
  BOOT_SAFE              = 0,
  SELF_TEST              = 1,
  IDLE_MONITORING        = 2,
  QUEUED                 = 3,
  OPENING_SELECTED_VALVE = 4,
  PUMP_WATERING          = 5,
  PUMP_STOPPING          = 6,
  CLOSING_VALVE          = 7,
  SOAK_SETTLE            = 8,
  SUSPENDED_RAIN         = 9,
  FAULT_LOCKOUT          = 10
};

inline const char* irrigationStateString(IrrigationState st) {
  switch (st) {
    case IrrigationState::BOOT_SAFE:              return "BOOT_SAFE";
    case IrrigationState::SELF_TEST:              return "SELF_TEST";
    case IrrigationState::IDLE_MONITORING:        return "IDLE";
    case IrrigationState::QUEUED:                 return "QUEUED";
    case IrrigationState::OPENING_SELECTED_VALVE: return "OPEN_VALVE";
    case IrrigationState::PUMP_WATERING:          return "WATERING";
    case IrrigationState::PUMP_STOPPING:          return "STOP_PUMP";
    case IrrigationState::CLOSING_VALVE:          return "CLOSE_VALVE";
    case IrrigationState::SOAK_SETTLE:            return "SOAK_WAIT";
    case IrrigationState::SUSPENDED_RAIN:         return "RAIN_PAUSED";
    case IrrigationState::FAULT_LOCKOUT:          return "FAULT_LOCK";
    default:                                      return "UNKNOWN";
  }
}

enum class EnvBand : uint8_t {
  NORMAL  = 0,
  LOW_VAL = 1,
  HIGH_VAL = 2,
  INVALID = 3
};

inline const char* envBandShort(EnvBand b) {
  switch (b) {
    case EnvBand::NORMAL:   return "NORMAL";
    case EnvBand::LOW_VAL:  return "LOW";
    case EnvBand::HIGH_VAL: return "HIGH";
    default:                return "ERR";
  }
}

struct EnvironmentReading {
  float    temperatureC      = NAN;
  float    humidityRh        = NAN;
  bool     dhtValid          = false;
  uint32_t dhtSampledAtMs    = 0;
  uint8_t  dhtFailStreak     = 0;
  EnvBand  tempBand          = EnvBand::INVALID;
  EnvBand  humidityBand      = EnvBand::INVALID;

  int      ldrRaw            = 0;
  float    brightnessPct     = NAN;  // Relative brightness index %, NOT lux
  bool     lightValid        = false;
  uint32_t lightSampledAtMs  = 0;
  uint8_t  lightFailStreak   = 0;
  EnvBand  lightBand         = EnvBand::INVALID;
};

struct SoilZoneConfig {
  bool     enabled           = true;
  int      dryRaw            = Config::DEFAULT_SOIL_DRY_RAW;
  int      wetRaw            = Config::DEFAULT_SOIL_WET_RAW;
  float    startThresholdPct = 35.0f;
  float    stopThresholdPct  = 55.0f;
  uint32_t pulseDurationMs   = Config::DEFAULT_WATER_PULSE_MS;
  uint32_t maxPumpOnMs       = Config::DEFAULT_MAX_ZONE_PUMP_ON_MS;
  uint32_t soakSettleMs      = Config::DEFAULT_SOAK_SETTLE_MS;
  uint32_t cooldownMs        = Config::DEFAULT_ZONE_COOLDOWN_MS;
};

struct SoilZoneStatus {
  int      rawAdc            = 0;
  float    moisturePct       = NAN;
  bool     valid             = false;
  bool     needsWater        = false;
  uint32_t sampledAtMs       = 0;
  uint32_t lastWateredAtMs   = 0;
  uint8_t  faultStreak       = 0;
};

struct ReservoirReading {
  float               airGapInches  = NAN;  // Measured distance from sensor face to water
  float               waterLevelPct = NAN;  // 0-100% computed from calibrated empty/full gaps
  bool                valid         = false;
  ReservoirLevelState state         = ReservoirLevelState::SENSOR_FAULT;
  uint32_t            sampledAtMs   = 0;
  uint8_t             failStreak    = 0;
};

struct WeatherSnapshot {
  bool     valid             = false;
  bool     fresh             = false;
  int      probabilityPct    = -1;
  uint64_t fetchedAtEpochMs  = 0;
  uint64_t validUntilEpochMs = 0;
  uint32_t receivedAtUptimeMs = 0;
  char     windowLabel[24]   = "NEXT 3 H";
};

struct RainGateStatus {
  bool            rawWet                  = false;
  bool            sensorValid             = true;
  RainSensorState sensorState             = RainSensorState::DRY;
  uint32_t        wetDurationMs           = 0;
  uint32_t        dryDurationMs           = 0;
  bool            wetConfirmed            = false;
  bool            dryRecovered            = true;
  bool            rainSuspensionLatched   = false;
  bool            forecastAdvisoryActive  = false;
  bool            weatherUnavailableFault = false;
};

struct RuntimeConfig {
  DeviceMode     mode                    = DeviceMode::OUTDOOR;
  int            rainThresholdPct        = Config::DEFAULT_RAIN_PROB_THRESHOLD_PCT;
  uint32_t       wetConfirmMs            = Config::DEFAULT_RAIN_WET_CONFIRM_MS;
  uint32_t       dryRecoverMs            = Config::DEFAULT_RAIN_DRY_RECOVER_MS;
  float          reservoirFullGapIn      = Config::DEFAULT_RESERVOIR_FULL_GAP_IN;
  float          reservoirEmptyGapIn     = Config::DEFAULT_RESERVOIR_EMPTY_GAP_IN;
  float          reservoirLowPct         = Config::DEFAULT_RESERVOIR_LOW_PCT;
  float          reservoirCriticalPct    = Config::DEFAULT_RESERVOIR_CRITICAL_PCT;
  float          reservoirRecoverPct     = Config::DEFAULT_RESERVOIR_RECOVER_PCT;
  float          tempHighC               = Config::DEFAULT_TEMP_HIGH_C;
  float          tempHighRearmC          = Config::DEFAULT_TEMP_HIGH_REARM_C;
  float          tempLowC                = Config::DEFAULT_TEMP_LOW_C;
  float          tempLowRearmC           = Config::DEFAULT_TEMP_LOW_REARM_C;
  bool           enableTempLowAlert      = Config::DEFAULT_ENABLE_TEMP_LOW_ALERT;
  float          humLowRh                = Config::DEFAULT_HUM_LOW_RH;
  float          humLowRearmRh           = Config::DEFAULT_HUM_LOW_REARM_RH;
  float          humHighRh               = Config::DEFAULT_HUM_HIGH_RH;
  float          humHighRearmRh          = Config::DEFAULT_HUM_HIGH_REARM_RH;
  bool           enableHumHighAlert      = Config::DEFAULT_ENABLE_HUM_HIGH_ALERT;
  int            ldrDarkRaw              = Config::DEFAULT_LDR_DARK_RAW;
  int            ldrBrightRaw            = Config::DEFAULT_LDR_BRIGHT_RAW;
  float          lightHighPct            = Config::DEFAULT_LIGHT_HIGH_PCT;
  float          lightHighRearmPct       = Config::DEFAULT_LIGHT_HIGH_REARM_PCT;
  float          lightLowPct             = Config::DEFAULT_LIGHT_LOW_PCT;
  float          lightLowRearmPct        = Config::DEFAULT_LIGHT_LOW_REARM_PCT;
  bool           buzzerMuted             = false;
  bool           physicalLoadsEnabled    = Config::ENABLE_PHYSICAL_LOAD_OUTPUTS;
  uint32_t       configVersion           = 1;
  SoilZoneConfig zones[3];

  RuntimeConfig() {
    zones[0].startThresholdPct = Config::DEFAULT_TOMATO_START_PCT;
    zones[0].stopThresholdPct  = Config::DEFAULT_TOMATO_STOP_PCT;
    zones[1].startThresholdPct = Config::DEFAULT_CHILI_START_PCT;
    zones[1].stopThresholdPct  = Config::DEFAULT_CHILI_STOP_PCT;
    zones[2].startThresholdPct = Config::DEFAULT_EGGPLANT_START_PCT;
    zones[2].stopThresholdPct  = Config::DEFAULT_EGGPLANT_STOP_PCT;
  }
};

enum class EventSeverity : uint8_t {
  INFO     = 0,
  WARNING  = 1,
  CRITICAL = 2
};

inline const char* severityToString(EventSeverity sev) {
  switch (sev) {
    case EventSeverity::INFO:     return "info";
    case EventSeverity::WARNING:  return "warning";
    case EventSeverity::CRITICAL: return "critical";
    default:                      return "info";
  }
}

struct DeviceEventRecord {
  bool          occupied       = false;
  char          eventId[48]    = {0};
  char          type[32]       = {0};
  EventSeverity severity       = EventSeverity::INFO;
  char          title[48]      = {0};
  char          message[112]   = {0};
  char          crop[12]       = {0};
  char          dedupeKey[56]  = {0};
  float         primaryValue   = NAN;
  uint32_t      createdAtMs    = 0;
  uint32_t      ownershipEpoch = 0;
};

enum class CommandType : uint8_t {
  NONE            = 0,
  WATER_CROP      = 1,
  STOP_WATERING   = 2,
  CLEAR_EMERGENCY = 3,
  MUTE_BUZZER     = 4
};

struct CommandRequest {
  bool        valid          = false;
  char        commandId[40]  = {0};
  CommandType type           = CommandType::NONE;
  CropZoneId  crop           = CropZoneId::NONE;
  uint32_t    durationMs     = Config::DEFAULT_WATER_PULSE_MS;
  uint32_t    ownershipEpoch = 0;
  uint64_t    createdAtMs    = 0;
  uint64_t    expiresAtMs    = 0;
};

struct DiagnosticsMetrics {
  uint32_t freeHeapBytes          = 0;
  uint32_t minFreeHeapBytes       = 0;
  uint32_t maxSafetyLoopDeltaMs   = 0;
  uint32_t lastSafetyCheckMs      = 0;
  uint32_t wifiReconnectCount     = 0;
  uint32_t firebaseSyncSuccessCnt = 0;
  uint32_t firebaseSyncFailCnt    = 0;
  uint32_t droppedEventCount      = 0;
  int8_t   wifiRssiDbm            = 0;
};
