#pragma once
/**
 * @file Pins.h
 * @brief Authoritative V3.1 GPIO pin assignments for Smart Plant Care ESP32 Firmware.
 *
 * HARDWARE REVISION V3.1 (CONFIRMED):
 * - 20x4 I2C LCD2004 via I2C backpack: SDA = GPIO21, SCL = GPIO22
 * - Rain Sensor Digital Output (DO): GPIO32 (exclusively rain DO; parallel LCD removed)
 * - Buzzer Output: GPIO18
 * - LDR Light Sensor Divider (ADC1): GPIO36 (VP)
 * - DHT22 Temperature & Humidity: GPIO4
 * - Tomato Soil Moisture (ADC1): GPIO34
 * - Chili Soil Moisture (ADC1): GPIO35
 * - Eggplant Soil Moisture (ADC1): GPIO33
 * - Ultrasonic TRIG: GPIO16
 * - Ultrasonic ECHO: GPIO17 (MUST be 3.3V-safe via voltage divider / level shifter)
 * - Tomato Solenoid Valve Relay IN1: GPIO25
 * - Chili Solenoid Valve Relay IN2: GPIO26
 * - Eggplant Solenoid Valve Relay IN3: GPIO27
 * - Common Water Pump Relay IN4: GPIO23
 */

#include <stdint.h>

namespace Pins {

// ─── 1. 20x4 I2C LCD2004 (PCF8574 backpack via 3.3V-safe I2C bus) ───────────
constexpr int LCD_SDA            = 21;
constexpr int LCD_SCL            = 22;

// ─── 2. Environmental & Weather Sensors ──────────────────────────────────────
constexpr int RAIN_DO            = 32;  // Digital input (3.3V-safe comparator DO)
constexpr int LDR_ADC            = 36;  // ADC1_CH0 (VP, input-only)
constexpr int DHT22_DATA         = 4;   // Digital 1-Wire (with 3.3V pull-up)

// ─── 3. Three Crop Soil Moisture Sensors (All on ADC1 for Wi-Fi safety) ──────
constexpr int SOIL_TOMATO_ADC    = 34;  // ADC1_CH6 (input-only)
constexpr int SOIL_CHILI_ADC     = 35;  // ADC1_CH7 (input-only)
constexpr int SOIL_EGGPLANT_ADC  = 33;  // ADC1_CH5

// ─── 4. Ultrasonic Water Reservoir Sensor (7-inch tank air-gap) ──────────────
constexpr int ULTRASONIC_TRIG    = 16;  // Digital output
constexpr int ULTRASONIC_ECHO    = 17;  // Digital input (MUST be <= 3.3V!)

// ─── 5. Audible Alert Buzzer ─────────────────────────────────────────────────
constexpr int BUZZER             = 18;  // Digital / PWM output (use transistor driver if >12mA)

// ─── 6. Four-Channel Relay Module (3 Valves + 1 Shared Pump) ─────────────────
constexpr int RELAY_TOMATO_IN1   = 25;  // Solenoid valve 1 (Tomato)
constexpr int RELAY_CHILI_IN2    = 26;  // Solenoid valve 2 (Chili)
constexpr int RELAY_EGGPLANT_IN3 = 27;  // Solenoid valve 3 (Eggplant)
constexpr int RELAY_PUMP_IN4     = 23;  // Shared 5V water pump

// ─── Compile-Time Pin Uniqueness & Constraint Verification ───────────────────
static_assert(LCD_SDA == 21 && LCD_SCL == 22, "LCD must use I2C GPIO21 (SDA) and GPIO22 (SCL)");
static_assert(RAIN_DO == 32, "Rain sensor DO must remain on GPIO32");
static_assert(BUZZER == 18, "Buzzer must remain on GPIO18");
static_assert(LDR_ADC == 36, "LDR sensor must remain on GPIO36 (ADC1)");
static_assert(DHT22_DATA == 4, "DHT22 must remain on GPIO4");
static_assert(SOIL_TOMATO_ADC == 34 && SOIL_CHILI_ADC == 35 && SOIL_EGGPLANT_ADC == 33,
              "Soil moisture sensors must use ADC1 GPIOs 34, 35, 33");
static_assert(ULTRASONIC_TRIG == 16 && ULTRASONIC_ECHO == 17,
              "Ultrasonic sensor must use TRIG=GPIO16 and ECHO=GPIO17");
static_assert(RELAY_TOMATO_IN1 == 25 && RELAY_CHILI_IN2 == 26 &&
              RELAY_EGGPLANT_IN3 == 27 && RELAY_PUMP_IN4 == 23,
              "Relay channels must use GPIOs 25, 26, 27, 23");

}  // namespace Pins
