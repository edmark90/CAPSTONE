/**
 * @file 02_safe_relay_off_verifier.ino
 * @brief Safe Bench Test 2: Verifies 4-channel relay active-LOW / active-HIGH polarity
 *        and single-channel sequencing ONLY on explicit Serial confirmation with
 *        5V pump and solenoid valves PHYSICALLY DISCONNECTED.
 */

#include <Arduino.h>

constexpr int PIN_RELAY_TOMATO   = 25;
constexpr int PIN_RELAY_CHILI    = 26;
constexpr int PIN_RELAY_EGGPLANT = 27;
constexpr int PIN_RELAY_PUMP     = 23;
constexpr bool RELAY_ACTIVE_LOW  = true;

void setRelayOff(int pin) {
  digitalWrite(pin, RELAY_ACTIVE_LOW ? HIGH : LOW);
}

void setRelayOn(int pin) {
  digitalWrite(pin, RELAY_ACTIVE_LOW ? LOW : HIGH);
}

void allRelaysOff() {
  const int pins[4] = {PIN_RELAY_TOMATO, PIN_RELAY_CHILI, PIN_RELAY_EGGPLANT, PIN_RELAY_PUMP};
  for (int p : pins) {
    setRelayOff(p);
    pinMode(p, OUTPUT);
    setRelayOff(p);
  }
}

void setup() {
  allRelaysOff();
  Serial.begin(115200);
  delay(200);
  Serial.println(F("\n=== BENCH TEST 02: SAFE RELAY POLARITY & COM/NO VERIFIER ==="));
  Serial.println(F("WARNING: Disconnect all 5V solenoid valves and water pump BEFORE running!"));
  Serial.println(F("All 4 relay outputs (GPIO25, 26, 27, 23) are currently INACTIVE (OFF)."));
  Serial.println(F("Verify with a multimeter that COM->NO is OPEN on all 4 channels."));
  Serial.println(F("Type 'step1', 'step2', 'step3', 'step4' to pulse one channel for 1s, or 'off'."));
}

void loop() {
  if (Serial.available() > 0) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    allRelaysOff();

    int targetPin = -1;
    const char* label = "";
    if (cmd == "step1") { targetPin = PIN_RELAY_TOMATO; label = "IN1 Tomato Valve (GPIO25)"; }
    else if (cmd == "step2") { targetPin = PIN_RELAY_CHILI; label = "IN2 Chili Valve (GPIO26)"; }
    else if (cmd == "step3") { targetPin = PIN_RELAY_EGGPLANT; label = "IN3 Eggplant Valve (GPIO27)"; }
    else if (cmd == "step4") { targetPin = PIN_RELAY_PUMP; label = "IN4 Water Pump (GPIO23)"; }

    if (targetPin != -1) {
      Serial.printf("[TEST] Energizing ONLY %s for 1000 ms...\n", label);
      setRelayOn(targetPin);
      delay(1000);
      allRelaysOff();
      Serial.println(F("[TEST] Returned to ALL OFF."));
    } else {
      Serial.println(F("[SAFE] All relays OFF."));
    }
  }
}
