/**
 * @file 03_sensors_dry_bench_test.ino
 * @brief Safe Bench Test 3: Reads DHT22 (GPIO4), LDR (GPIO36), 3 Soil Sensors (GPIO34/35/33),
 *        Rain DO (GPIO32), and Ultrasonic 7-inch Reservoir Air Gap (TRIG=16/ECHO=17)
 *        while keeping all 4 relays strictly locked OFF.
 */

#include <Arduino.h>
#include <DHT.h>

constexpr int PIN_DHT            = 4;
constexpr int PIN_LDR            = 36;
constexpr int PIN_RAIN_DO        = 32;
constexpr int PIN_SOIL_TOMATO    = 34;
constexpr int PIN_SOIL_CHILI     = 35;
constexpr int PIN_SOIL_EGGPLANT  = 33;
constexpr int PIN_ULTRA_TRIG     = 16;
constexpr int PIN_ULTRA_ECHO     = 17;

DHT dht(PIN_DHT, DHT22);

float readUltrasonicAirGapInches() {
  digitalWrite(PIN_ULTRA_TRIG, LOW);
  delayMicroseconds(3);
  digitalWrite(PIN_ULTRA_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_ULTRA_TRIG, LOW);
  unsigned long dur = pulseIn(PIN_ULTRA_ECHO, HIGH, 15000UL);
  if (dur == 0) return NAN;  // No echo = UNKNOWN, never 0% or 100%
  return (static_cast<float>(dur) * 0.0343f * 0.5f) / 2.54f;
}

void setup() {
  // Keep all relay pins locked OFF
  const int relayPins[4] = {25, 26, 27, 23};
  for (int p : relayPins) {
    digitalWrite(p, HIGH);
    pinMode(p, OUTPUT);
    digitalWrite(p, HIGH);
  }

  Serial.begin(115200);
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_LDR, ADC_11db);
  analogSetPinAttenuation(PIN_SOIL_TOMATO, ADC_11db);
  analogSetPinAttenuation(PIN_SOIL_CHILI, ADC_11db);
  analogSetPinAttenuation(PIN_SOIL_EGGPLANT, ADC_11db);
  pinMode(PIN_RAIN_DO, INPUT);
  pinMode(PIN_ULTRA_TRIG, OUTPUT);
  digitalWrite(PIN_ULTRA_TRIG, LOW);
  pinMode(PIN_ULTRA_ECHO, INPUT);
  dht.begin();

  Serial.println(F("\n=== BENCH TEST 03: DRY SENSOR CALIBRATION & VERIFICATION ==="));
}

void loop() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  int ldrRaw = analogRead(PIN_LDR);
  int soilT  = analogRead(PIN_SOIL_TOMATO);
  int soilC  = analogRead(PIN_SOIL_CHILI);
  int soilE  = analogRead(PIN_SOIL_EGGPLANT);
  int rainDo = digitalRead(PIN_RAIN_DO);
  float gapIn = readUltrasonicAirGapInches();

  Serial.printf("DHT22: T=%.1fC H=%.1f%% | LDR(36)=%d | RainDO(32)=%d | Soil Raw T(34)=%d C(35)=%d E(33)=%d | TankGap=%.2f in\n",
                t, h, ldrRaw, rainDo, soilT, soilC, soilE, gapIn);
  delay(2500);
}
