#pragma once
/**
 * @file Config.h
 * @brief Default timing cadences, safety thresholds, and hardware polarity configuration.
 *
 * IMPORTANT HARDWARE SAFETY NOTICE:
 * - Set ENABLE_PHYSICAL_LOAD_OUTPUTS = false during initial bench upload and sensor verification
 *   so the firmware NEVER energizes the physical relay coils until COM/NO wiring and active
 *   polarity have been verified with a multimeter and disconnected loads.
 * - Once relay COM/NO wiring and active polarity are physically verified, set
 *   ENABLE_PHYSICAL_LOAD_OUTPUTS = true (or enable via authenticated config after self-test).
 */

#include <stdint.h>

namespace Config {

// ─── Firmware Metadata ───────────────────────────────────────────────────────
constexpr const char* FIRMWARE_VERSION  = "3.1.0-i2c";
constexpr const char* HARDWARE_REVISION = "ESP32-WROOM32-V3.1-I2C";
constexpr const char* MODEL_NAME        = "SmartPlantCare-3Zone";

// ─── CRITICAL BENCH SAFETY GATE ──────────────────────────────────────────────
// Keep false for initial upload/sensor testing so relays stay locked in safe-OFF.
// Change to true ONLY after verifying relay COM/NO contacts and active polarity!
constexpr bool ENABLE_PHYSICAL_LOAD_OUTPUTS = false;

// ─── Hardware Electrical Polarity (VERIFY ON BENCH BEFORE ENABLING LOADS) ────
// Most 4-channel optocoupled 5V relay boards are active-LOW (IN = LOW energizes coil).
constexpr bool RELAY_ACTIVE_LOW        = true;
// Many LM393 rain comparator modules pull DO LOW when the sensing plate is wet.
constexpr bool RAIN_WET_IS_LOW         = true;
// Set true if using a passive piezo buzzer (uses LEDC tone); false for active DC buzzer.
constexpr bool BUZZER_IS_PASSIVE       = false;
constexpr int  BUZZER_ACTIVE_HIGH      = 1;
constexpr uint32_t BUZZER_PASSIVE_FREQ = 2400;  // Hz when BUZZER_IS_PASSIVE == true

// ─── I2C LCD2004 Configuration ───────────────────────────────────────────────
// Address 0x00 tells hd44780_I2Cexp to auto-detect backpack address (0x27, 0x3F, etc.)
constexpr uint8_t  LCD_I2C_ADDR_AUTO   = 0x00;
constexpr uint8_t  LCD_COLS            = 20;
constexpr uint8_t  LCD_ROWS            = 4;
constexpr uint32_t I2C_CLOCK_HZ        = 100000;  // Standard 100 kHz for stable long-run I2C

// ─── Cooperative Scheduler Cadences (milliseconds) ───────────────────────────
constexpr uint32_t INTERVAL_SAFETY_MS       = 25;     // 25 ms emergency/actuator check
constexpr uint32_t INTERVAL_IRRIGATION_MS   = 50;     // 50 ms irrigation state machine
constexpr uint32_t INTERVAL_BUZZER_MS       = 15;     // 15 ms nonblocking buzzer slices
constexpr uint32_t INTERVAL_RAIN_MS         = 200;    // 200 ms rain DO sampling & debounce
constexpr uint32_t INTERVAL_DHT_MS          = 2500;   // 2.5 s DHT22 sampling (>=2s spec)
constexpr uint32_t INTERVAL_SOIL_MS         = 3000;   // 3.0 s 3-zone ADC1 soil sampling
constexpr uint32_t INTERVAL_RESERVOIR_MS    = 2500;   // 2.5 s ultrasonic air-gap sampling
constexpr uint32_t INTERVAL_LDR_MS          = 5000;   // 5.0 s LDR ADC1 brightness sampling
constexpr uint32_t INTERVAL_LCD_REFRESH_MS  = 750;    // 750 ms LCD row refresh
constexpr uint32_t INTERVAL_LCD_PAGE_MS     = 3000;   // 3.0 s normal LCD page rotation
constexpr uint32_t INTERVAL_TELEMETRY_MS            = 15000;  // 15.0 s Firebase telemetry snapshot
constexpr uint32_t INTERVAL_COMMAND_POLL_MS         = 2500;   // 2.5 s fast manual/emergency command poll
constexpr uint32_t INTERVAL_CONFIG_POLL_MS          = 15000;  // 15.0 s Firebase meta/weather/config sync
constexpr uint32_t INTERVAL_DIAGNOSTICS_MS          = 60000;  // 60.0 s heap/uptime diagnostics log
constexpr uint32_t WIFI_PROVISION_VERIFY_TIMEOUT_MS = 35000;  // Re-open AP if Wi-Fi join fails

// ─── Rain Sensor & Strict Weather AND-Condition Defaults ─────────────────────
constexpr int      DEFAULT_RAIN_PROB_THRESHOLD_PCT = 60;      // CONFIRMED: >= 60%
constexpr uint32_t DEFAULT_RAIN_WET_CONFIRM_MS     = 30000;   // 30 s sustained wet
constexpr uint32_t DEFAULT_RAIN_DRY_RECOVER_MS     = 120000;  // 120 s sustained dry
constexpr uint32_t DEFAULT_WEATHER_MAX_AGE_MS      = 3600000; // 60 min weather freshness TTL
constexpr bool     BLOCK_OUTDOOR_AUTO_ON_STALE_WEATHER = true;
constexpr bool     BLOCK_OUTDOOR_AUTO_ON_RAIN_FAULT    = true;

// ─── 7-Inch Water Reservoir Calibration & Thresholds ─────────────────────────
// CONFIRMED: Reservoir is ~7.0 inches high; ultrasonic measures air gap from top.
// Smaller valid air gap = fuller tank; >= 7.0 in = 0% EMPTY.
constexpr float    DEFAULT_RESERVOIR_FULL_GAP_IN     = 1.0f;  // Calibrate after mounting
constexpr float    DEFAULT_RESERVOIR_EMPTY_GAP_IN    = 7.0f;  // CONFIRMED 7-inch nominal tank
constexpr float    DEFAULT_RESERVOIR_MIN_VALID_IN    = 0.6f;  // Below sensor dead-zone = invalid
constexpr float    DEFAULT_RESERVOIR_MAX_VALID_IN    = 12.0f; // Above plausible mount = invalid
constexpr float    DEFAULT_RESERVOIR_LOW_PCT         = 20.0f; // <= 20% low water warning (2 beeps)
constexpr float    DEFAULT_RESERVOIR_CRITICAL_PCT    = 10.0f; // <= 10% critical pump cutoff (3 beeps)
constexpr float    DEFAULT_RESERVOIR_RECOVER_PCT     = 25.0f; // Hysteresis recovery threshold
constexpr uint32_t RESERVOIR_LOW_CONFIRM_MS          = 10000; // 10 s stable low before warning
constexpr uint32_t RESERVOIR_FAULT_CONFIRM_MS        = 7500;  // 3 consecutive failed pings (~7.5s)
constexpr uint32_t ULTRASONIC_ECHO_TIMEOUT_US        = 15000; // 15 ms (~250 cm max) nonblocking bound

// ─── Soil Moisture Default Calibration & Per-Crop Thresholds ─────────────────
// Capacitive soil sensors on 3.3V typically read ~3100-3400 in dry air and ~1200-1500 in water.
// MUST be calibrated per physical sensor in soil.
constexpr int      DEFAULT_SOIL_DRY_RAW              = 3250;
constexpr int      DEFAULT_SOIL_WET_RAW              = 1350;
constexpr int      SOIL_ADC_MIN_PLAUSIBLE            = 150;   // Short-to-GND fault threshold
constexpr int      SOIL_ADC_MAX_PLAUSIBLE            = 4020;  // Open-wire / rail fault threshold
constexpr float    SOIL_MAX_STEP_DELTA_PCT           = 45.0f; // Single-sample spike rejection

constexpr float    DEFAULT_TOMATO_START_PCT          = 35.0f;
constexpr float    DEFAULT_TOMATO_STOP_PCT           = 55.0f;
constexpr float    DEFAULT_CHILI_START_PCT           = 32.0f;
constexpr float    DEFAULT_CHILI_STOP_PCT            = 52.0f;
constexpr float    DEFAULT_EGGPLANT_START_PCT        = 38.0f;
constexpr float    DEFAULT_EGGPLANT_STOP_PCT         = 58.0f;

// ─── Irrigation Valve, Pump, Pulse & Cooldown Timings ────────────────────────
constexpr uint32_t VALVE_OPEN_SETTLE_MS              = 400;    // Valve ON -> wait 400ms -> Pump ON
constexpr uint32_t PUMP_STOP_SETTLE_MS               = 250;    // Pump OFF -> wait 250ms -> Valve OFF
constexpr uint32_t DEFAULT_WATER_PULSE_MS            = 4000;   // 4 s supervised pulse per cycle
constexpr uint32_t DEFAULT_MAX_ZONE_PUMP_ON_MS       = 15000;  // 15 s hard max runtime per session
constexpr uint32_t GLOBAL_HARD_MAX_PUMP_ON_MS        = 30000;  // 30 s absolute hardware safety cap
constexpr uint32_t DEFAULT_SOAK_SETTLE_MS            = 25000;  // 25 s soak settle before re-evaluating
constexpr uint32_t DEFAULT_ZONE_COOLDOWN_MS          = 180000; // 3 min minimum between cycles on same zone
constexpr uint32_t MANUAL_CMD_MAX_TTL_MS             = 60000;  // Reject commands older than 60 s

// ─── Environmental (DHT22 & LDR) Defaults ────────────────────────────────────
constexpr float    DEFAULT_TEMP_HIGH_C               = 35.0f;
constexpr float    DEFAULT_TEMP_HIGH_REARM_C         = 33.0f;
constexpr float    DEFAULT_TEMP_LOW_C                = 12.0f;
constexpr float    DEFAULT_TEMP_LOW_REARM_C          = 14.0f;
constexpr bool     DEFAULT_ENABLE_TEMP_LOW_ALERT     = false;

constexpr float    DEFAULT_HUM_LOW_RH                = 40.0f;
constexpr float    DEFAULT_HUM_LOW_REARM_RH          = 45.0f;
constexpr float    DEFAULT_HUM_HIGH_RH               = 88.0f;
constexpr float    DEFAULT_HUM_HIGH_REARM_RH         = 82.0f;
constexpr bool     DEFAULT_ENABLE_HUM_HIGH_ALERT     = false;

// 3.3V -> LDR -> GPIO36 -> 10k -> GND: brighter light increases ADC raw value.
constexpr int      DEFAULT_LDR_DARK_RAW              = 250;
constexpr int      DEFAULT_LDR_BRIGHT_RAW            = 3600;
constexpr int      LDR_ADC_MIN_PLAUSIBLE             = 10;
constexpr int      LDR_ADC_MAX_PLAUSIBLE             = 4085;
constexpr float    DEFAULT_LIGHT_HIGH_PCT            = 85.0f;
constexpr float    DEFAULT_LIGHT_HIGH_REARM_PCT      = 78.0f;
constexpr float    DEFAULT_LIGHT_LOW_PCT             = 20.0f;
constexpr float    DEFAULT_LIGHT_LOW_REARM_PCT       = 28.0f;

constexpr uint32_t ENV_ALERT_CONFIRM_MS              = 30000;  // 30 s sustained before alert

// ─── Buzzer Pattern Timings ──────────────────────────────────────────────────
constexpr uint16_t BEEP_SHORT_ON_MS                  = 150;
constexpr uint16_t BEEP_SHORT_GAP_MS                 = 200;
constexpr uint16_t BEEP_BOOT_ON_MS                   = 100;

// ─── Bounded Queues & Network Backoff ────────────────────────────────────────
constexpr size_t   EVENT_QUEUE_CAPACITY              = 16;
constexpr size_t   PROCESSED_CMD_RING_SIZE           = 12;
constexpr uint32_t WIFI_RECONNECT_MIN_MS             = 5000;
constexpr uint32_t WIFI_RECONNECT_MAX_MS             = 60000;
constexpr uint32_t HTTP_TIMEOUT_MS                   = 4500;

}  // namespace Config
