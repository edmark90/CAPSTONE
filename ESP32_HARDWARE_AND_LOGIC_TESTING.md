# ESP32 HARDWARE AND LOGIC TESTING GUIDE — Smart Plant Care (V3.1 I2C Edition)

This guide provides the complete electrical safety inspection, sensor calibration workflow, and subsystem-by-subsystem logic test matrix for the **Smart Plant Care V3.1 (20×4 I2C LCD)** system.

---

## 1. Authoritative V3.1 GPIO & Electrical Verification Table

| Component | ESP32 GPIO | Direction | Logic Level & Mandatory Protection |
|---|---|---|---|
| **20×4 I2C LCD SDA** | `GPIO21` | Bidirectional | **3.3V only on ESP32 pin.** If LCD backpack uses 5V pull-ups, route `SDA` through a **3.3V ↔ 5V bidirectional I2C level shifter**. |
| **20×4 I2C LCD SCL** | `GPIO22` | Output/Open-Drain | **3.3V only on ESP32 pin.** Route `SCL` through the **3.3V ↔ 5V bidirectional I2C level shifter**. |
| **Rain Sensor DO** | `GPIO32` | Digital Input | Power comparator board from **3.3V** (or level-shift `DO` to `<= 3.3V`). |
| **Buzzer** | `GPIO18` | Digital/PWM Out | Drive via NPN (`2N2222`/`S8050` + `1 kΩ` base resistor) or logic MOSFET if draw `> 12 mA`. |
| **LDR Light Sensor** | `GPIO36` (`VP`) | `ADC1_CH0` Input | Input-only pin (no internal pull-up/down). Wire `3.3V -> LDR -> GPIO36 -> 10 kΩ -> GND`. |
| **DHT22 Sensor** | `GPIO4` | 1-Wire Digital | Power from `3.3V` with `4.7 kΩ–10 kΩ` pull-up resistor from `DATA` (`GPIO4`) to `3.3V`. |
| **Tomato Soil Moisture** | `GPIO34` | `ADC1_CH6` Input | Input-only pin. Power capacitive sensor from `3.3V` so `AOUT <= 3.3V`. |
| **Chili Soil Moisture** | `GPIO35` | `ADC1_CH7` Input | Input-only pin. Power capacitive sensor from `3.3V` so `AOUT <= 3.3V`. |
| **Eggplant Soil Moisture** | `GPIO33` | `ADC1_CH5` Input | Power capacitive sensor from `3.3V` so `AOUT <= 3.3V`. |
| **Ultrasonic TRIG** | `GPIO16` | Digital Output | 3.3V CMOS output (`10 µs` trigger pulse). |
| **Ultrasonic ECHO** | `GPIO17` | Digital Input | **MANDATORY 5V → 3.3V VOLTAGE DIVIDER** (`ECHO -> 1 kΩ -> GPIO17 -> 2 kΩ -> GND`) if using a 5V HC-SR04. Direct 5V `ECHO` will damage `GPIO17`. |
| **Tomato Solenoid Relay IN1** | `GPIO25` | Digital Output | Wire load across **COM** and **NO** (Normally Open). Verify active-LOW vs active-HIGH polarity. |
| **Chili Solenoid Relay IN2** | `GPIO26` | Digital Output | Wire load across **COM** and **NO** (Normally Open). |
| **Eggplant Solenoid Relay IN3**| `GPIO27` | Digital Output | Wire load across **COM** and **NO** (Normally Open). |
| **Water Pump Relay IN4** | `GPIO23` | Digital Output | Wire load across **COM** and **NO** (Normally Open). |

---

## 2. Stage 1 — Unpowered Multimeter Continuity & Voltage Check

Before connecting USB or the 5V power bank:
1. **Disconnect all 3 solenoid valves and the water pump** from the relay screw terminals.
2. Set your multimeter to **Continuity / Resistance ($\Omega$)**.
3. Measure between **COM** and **NO** on Relay Channels 1, 2, 3, and 4:
   - **PASS:** All 4 channels read **OPEN (`OL` / $\infty\,\Omega$)** when unpowered.
   - **FAIL:** If any channel reads `0 Ω` closed while unpowered, that wire is landed on **NC (Normally Closed)** instead of **NO**. Move it to **NO** immediately.
4. Confirm across each 5V solenoid valve and the 5V water pump terminals that a **flyback diode (`1N4007` or `1N5819`, cathode band toward `+5V`)** is installed to suppress inductive kickback.

---

## 3. Stage 2 — Isolated Bench Sketch Verification

### Test 3.1 — 20×4 I2C LCD Bus Scan & Display (`01_i2c_lcd_scanner_and_test.ino`)
1. Upload [`01_i2c_lcd_scanner_and_test.ino`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/esp32_firmware/bench_tests/01_i2c_lcd_scanner_and_test/01_i2c_lcd_scanner_and_test.ino).
2. Open Serial Monitor at `115200` baud:
   - Confirm `[I2C] Found device at 0x27` (or `0x3F`).
   - Confirm all 4 rows of 20 characters render crisply on the 20×4 LCD (adjust the blue potentiometer on the PCF8574 backpack if blocks or blank characters appear).

### Test 3.2 — Relay Polarity & Single-Channel Verifier (`02_safe_relay_off_verifier.ino`)
1. Keep the 5V valves and pump **disconnected**.
2. Upload [`02_safe_relay_off_verifier.ino`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/esp32_firmware/bench_tests/02_safe_relay_off_verifier/02_safe_relay_off_verifier.ino).
3. Confirm that on boot, all 4 relay indicator LEDs are **OFF** and `COM -> NO` is **OPEN**.
   - *(If all 4 relays click ON at boot, your board is active-HIGH: set `RELAY_ACTIVE_LOW = false` in `Config.h`.)*
4. Type `step1`, `step2`, `step3`, and `step4` one by one in Serial Monitor and confirm each command energizes **only one** relay channel for `1000 ms` and returns to ALL OFF.

### Test 3.3 — Dry Sensor Calibration (`03_sensors_dry_bench_test.ino`)
1. Upload [`03_sensors_dry_bench_test.ino`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/esp32_firmware/bench_tests/03_sensors_dry_bench_test/03_sensors_dry_bench_test.ino).
2. Record raw ADC values for each soil sensor (`GPIO34`, `GPIO35`, `GPIO33`) in **dry air/dry soil** (`dryRaw`) and **saturated wet soil** (`wetRaw`), and update `Config.h` (or `/devices/{deviceId}/config/crops`).
3. Measure the ultrasonic air gap (`TRIG=16`, `ECHO=17`) over your **empty 7-inch reservoir** (`~7.0 in`) and at the **100% full waterline** (`~1.0–1.25 in`).

---

## 4. Stage 3 — Production Firmware Logic Verification Matrix

Upload [`SmartPlantCare.ino`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/esp32_firmware/SmartPlantCare/SmartPlantCare.ino) and open Serial Monitor at `115200` baud.

### Test 4.1 — Strict Rain + Open-Meteo AND-Condition (Phase 3D)
Use the built-in Serial CLI commands (`mode`, `simweather`, `status`) to test all four truth-table quadrants:

| Quadrant | Bench Action | Expected Firmware Behavior |
|---|---|---|
| **1. Forecast ≥ 60%, Sensor DRY** | Run `mode OUTDOOR`, keep rain sensor dry, run `simweather 75` | `Advisory=WARN`, `Latch=CLEAR`, Buzzer **SILENT**, LCD shows `RAIN RISK >=60%` / `SENSOR DRY-NO PAUSE`, automatic watering **NOT** suspended. |
| **2. Forecast < 60%, Sensor WET** | Run `simweather 30`, place wet cloth on rain sensor (`GPIO32`) for `>30 s` | `rawWet=WET`, `wetConf=YES`, `Latch=CLEAR`, Buzzer **SILENT**, no new rain suspension triggered. |
| **3. Forecast ≥ 60% AND Sensor WET ≥ 30 s** | Keep wet cloth on sensor (`>=30 s`) and run `simweather 80` | `Latch=PAUSED`, any active watering stops immediately (`Pump OFF -> Valve OFF`), Buzzer plays **2 short beeps (`2 × 150 ms`) once**, LCD shows `AUTO WATER PAUSED` / `RAIN CONFIRMED`, emits 1 `RAIN_SUSPENSION_STARTED` event. |
| **4. Latch Recovery** | Dry the rain sensor and run `simweather 25` | Remains `Latch=PAUSED` until sensor stays continuously dry for **`120 s`** AND forecast is `< 60%`, then clears latch and emits 1 `RAIN_SUSPENSION_CLEARED` event. |
| **5. Weather Unavailable (`-1`)** | Run `simweather -1` in `OUTDOOR` mode | Reports `WEATHER_UNAVAILABLE` technical fault on LCD/Firebase; **never** fakes confirmed rain (`Latch=CLEAR`). |

### Test 4.2 — 7-Inch Ultrasonic Water Reservoir Safety (Phase 3E)
| Scenario | Air Gap at Sensor | Expected Firmware Behavior |
|---|---|---|
| **Full Tank** | `~1.0 in` | `Level=100.0%`, `State=NORMAL`, pump allowed. |
| **Half Tank** | `~4.0 in` | `Level=50.0%`, `State=NORMAL`, pump allowed. |
| **Low Water (`<= 20%`)** | `~5.9 in` (for `10 s`) | `State=LOW`, Buzzer plays **2 short beeps once**, LCD shows `LOW WATER WARNING`, emits 1 `RESERVOIR_LOW` event. |
| **Critical (`<= 10%`) / Empty (`>= 7.0 in`)** | `>= 6.4 in` | `State=CRITICAL` or `EMPTY`, **immediate pump cutoff**, blocks manual & auto watering, Buzzer plays **3 short beeps once**, LCD shows `RESERVOIR CRITICAL` / `PUMP LOCKED OFF`. |
| **No-Echo / Disconnected (`0` pulse)** | Unplug `ECHO` or point into open room | `valid=N`, `State=SENSOR_ERROR`, displays `UNKNOWN` on LCD (**never `100%` or `0%`**), **immediate pump cutoff**, Buzzer plays **3 short beeps once**. |

### Test 4.3 — Single-Valve + Shared-Pump Sequencing (Phase 4)
1. Ensure reservoir is `NORMAL` (`> 25%`), rain latch is `CLEAR`, and device state is `OWNED` (`claimstate OWNED 1`).
2. Run `water tomato 4000` in Serial Monitor:
   - Observe state transition: `IDLE -> QUEUED -> OPEN_VALVE` (`IN1=GPIO25` ON, Pump OFF for `400 ms`) `-> WATERING` (`IN1=GPIO25` ON + `IN4=GPIO23` ON for `4000 ms`) `-> STOP_PUMP` (`IN4=GPIO23` OFF, `IN1=GPIO25` stays ON for `250 ms` depressurization) `-> CLOSE_VALVE` (`IN1=GPIO25` OFF) `-> SOAK_WAIT` (`25 s`).
   - Confirm `IN2 (Chili)` and `IN3 (Eggplant)` never turn ON during Tomato's cycle.
3. While `WATERING`, run `estop`:
   - Confirm Pump and Valve turn OFF within `25 ms`, Buzzer plays **3 short beeps**, and LCD displays `EMERGENCY STOP`.
