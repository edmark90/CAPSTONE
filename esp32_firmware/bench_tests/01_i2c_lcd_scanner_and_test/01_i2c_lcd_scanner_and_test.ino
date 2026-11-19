/**
 * @file 01_i2c_lcd_scanner_and_test.ino
 * @brief Safe Bench Test 1: Scans I2C bus on SDA=GPIO21 / SCL=GPIO22 (with all 4 relays locked OFF)
 *        and tests the 20x4 I2C LCD using hd44780_I2Cexp.
 *
 * ELECTRICAL REMINDER:
 * - Power the 20x4 LCD + PCF8574 backpack from 5V.
 * - Route SDA (GPIO21) and SCL (GPIO22) through a 3.3V<->5V bidirectional I2C logic level shifter
 *   if the PCF8574 backpack has onboard pull-ups to 5V.
 */

#include <Arduino.h>
#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

constexpr int PIN_LCD_SDA    = 21;
constexpr int PIN_LCD_SCL    = 22;
constexpr int PIN_VALVE_1    = 25;
constexpr int PIN_VALVE_2    = 26;
constexpr int PIN_VALVE_3    = 27;
constexpr int PIN_WATER_PUMP = 23;

hd44780_I2Cexp lcd;

void lockAllRelaysOffActiveLow() {
  const int pins[4] = {PIN_VALVE_1, PIN_VALVE_2, PIN_VALVE_3, PIN_WATER_PUMP};
  for (int p : pins) {
    digitalWrite(p, HIGH);
    pinMode(p, OUTPUT);
    digitalWrite(p, HIGH);
  }
}

void setup() {
  lockAllRelaysOffActiveLow();
  Serial.begin(115200);
  delay(200);

  Serial.println(F("\n=== BENCH TEST 01: 20x4 I2C LCD SCANNER & DISPLAY TEST ==="));
  Wire.begin(PIN_LCD_SDA, PIN_LCD_SCL, 100000);

  uint8_t foundAddr = 0;
  for (uint8_t addr = 0x08; addr <= 0x77; ++addr) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      Serial.printf("[I2C] Found device at 0x%02X\n", addr);
      if (foundAddr == 0) foundAddr = addr;
    }
  }

  if (foundAddr == 0) {
    Serial.println(F("[ERROR] No I2C device found on SDA=GPIO21, SCL=GPIO22!"));
    return;
  }

  int status = lcd.begin(20, 4);
  if (status != 0) {
    Serial.printf("[ERROR] hd44780_I2Cexp begin(20,4) failed with status=%d\n", status);
    return;
  }

  lcd.backlight();
  lcd.setCursor(0, 0);
  lcd.print("SMART PLANT CARE    ");
  lcd.setCursor(0, 1);
  lcd.print("20x4 I2C LCD OK     ");
  lcd.setCursor(0, 2);
  char line2[21];
  snprintf(line2, sizeof(line2), "ADDR: 0x%02X SDA21/22 ", foundAddr);
  lcd.print(line2);
  lcd.setCursor(0, 3);
  lcd.print("RELAYS LOCKED OFF   ");
  Serial.println(F("[OK] 20x4 LCD initialized and test text rendered."));
}

void loop() {
  delay(1000);
}
