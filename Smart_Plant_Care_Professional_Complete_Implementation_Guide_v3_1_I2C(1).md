# SMART PLANT CARE
## Professional End-to-End IoT, Firebase & Android Implementation Guide — Version 3.1 (20×4 I2C LCD)

**Project:** Smart Plant Care Mobile Application: An Integrated System for Real-Time Monitoring, AI-Based Disease Detection, and Precision Automated Plant Management  
**Scope of this guide:** Physical prototype, ESP32, sensors, irrigation, 20×4 LCD, buzzer, Open-Meteo, Firebase, Android Kotlin/Jetpack Compose, pairing, sharing, monitoring, notifications, security, safety, and testing.  
**Prepared:** November 17, 2026 · **Revised:** November 18, 2026 (account deletion, firmware budget and alert routing)  
**Status:** Updated implementation specification and test plan; **not a claim that untested wiring or software is already functioning**.  
**Source basis:** The uploaded V3 master/quick-reference documents, supplied ESP32 pin-wiring and system-logic diagrams, and the user's later explicit choice of **20×4 I2C LCD**, **rain DO = GPIO32**, **buzzer = GPIO18**. External engineering recommendations and assumptions are explicitly labeled.

**V3.1 HARDWARE UPDATE — AUTHORITATIVE:** Replace the previous six-wire *parallel LCD* configuration with a **20×4 LCD2004 with I2C backpack**. ESP32 **GPIO21 = SDA**, **GPIO22 = SCL**, **GPIO32 = rain sensor DO**, **GPIO18 = buzzer**, **GPIO36 = LDR analog light sensor (already present in V3)**. GPIO13/14/19/39 are no longer assigned by this mapping. All rain, reservoir, irrigation, notifications, pairing, sharing, and safety rules below otherwise remain unchanged.

> **Read first:** The diagram is a *conceptual data-flow and plumbing drawing*, not an electrically complete circuit diagram. **Never connect pump/valve load current directly through an ESP32 GPIO.** Before applying power, use the hardware verification, wiring, voltage, and fault checklists below. A verified wiring harness, calibrated physical sensors, and tested fail-safe behavior are necessary before enabling unattended irrigation.

### Status labels used throughout

- **[CONFIRMED — DIAGRAM]**: Explicitly shown in the user's diagram.
- **[CONFIRMED — USER]**: Explicitly clarified in conversation; takes precedence over older contradictory notes.
- **[DESIGN DEFAULT]**: Proposed implementation setting; change only after actual calibration/testing.
- **[VERIFY]**: Physical measurement, actual module type, service setup, or a choice absent from the user's diagram.
- **[SAFETY REQUIREMENT]**: Do not skip before pump/valve testing.

---

# 01. The final system in one page

## 1.1 Project purpose

Monitor **tomato, chili, and eggplant** in one prototype using a shared ESP32 controller. Observe three independent soil-moisture readings, air temperature, relative humidity, brightness, local wet/rain state, and a water-reservoir air-gap measurement. Control **three 5 V solenoid valves and one common 5 V water pump through a four-channel relay**. Send meaningful status and alerts to a Kotlin/Jetpack Compose Android application over Firebase. The app also contains its existing **plant-identification and disease-detection AI**; that AI functionality is not moved into the ESP32.

## 1.2 Ownership of responsibilities

```text
  Tomato soil     Chili soil      Eggplant soil      DHT22 / LDR
        \             |              /                  /
         \            |             /            Rain / ultrasonic
                      v                        /
                  ESP32 Controller <----------+
                 - sensor acquisition and validation
                 - crop-by-crop irrigation state machine
                 - hard safety limits and fail-safe shutdown
                 - local buzzer and 20x4 LCD
                 - Wi-Fi + small Firebase updates/events
                          | Wi-Fi / TLS
                          v
                   FIREBASE RTDB
          telemetry / status / configs / commands / events
                   /                       \
                  v                         v
      ANDROID KOTLIN APP               TRUSTED BACKEND
      - live monitoring                - device claim/ownership
      - crop controls/settings         - credentials / permissions
      - onboarding/pairing             - fetch/cache Open-Meteo
      - sharing & history              - new-event -> FCM push
      - existing AI features                     |
                  ^                               v
                  +----- Android push notification
```

The device must remain safe **even if the mobile app is closed, Firebase is unreachable, or the ESP32 reconnects**. The ESP32 is the final authority over pump/valve safety; the app submits *requests*, never bypasses the hardware interlocks.

## 1.3 Non-negotiable project behavior

1. **One crop/valve at a time** while using one shared pump. Tomato, chili, and eggplant have independently calibrated moisture settings.
2. **Outdoor rain suspension = OUTDOOR mode AND fresh weather probability >= 60% AND locally confirmed wet rain sensor.** A single condition never creates a new rain suspension. [CONFIRMED — USER]
3. Rain wet confirmation uses a **continuous-duration filter** to avoid triggering from one splash. [DESIGN DEFAULT: 30 s wet; 120 s dry for recovery].
4. **Seven-inch reservoir:** ultrasonic sensor measures **distance from sensor face to water surface (air gap)**. Smaller valid gap = fuller; **gap about 7.0 in or greater = 0%/empty**, after measuring actual mounting geometry. [CONFIRMED — USER]
5. Rain is a **forecast/sensor rule**, not a replacement for reservoir, pump, or sensor safety. Other interlocks may stop irrigation regardless of rain conditions.
6. Values are **live/latest** in Firebase-connected app, not necessarily high-frequency video. Threshold transitions generate **one alert/event**, rather than one push per sensor polling cycle.
7. Environmental alerts for **high temperature, low/high humidity and high/low brightness** are independent sensor conditions. DHT22 measures temperature/humidity; LDR measures brightness only.
8. LCD and buzzer work locally even when Wi-Fi is lost; background phone push requires FCM and a trusted sender.
9. **No pump or valve should energize just because the ESP32 reboots or a command is retried.** [SAFETY REQUIREMENT]
10. The earlier value **70%** is superseded by the clarified **60% threshold**. Only one configurable authoritative value should be used by app/backend/firmware.

## 1.4 Meaning of “live” and “notification”

| Feature | How it works | User sees |
|---|---|---|
| Live preview | RTDB listener on latest device telemetry/status | Values update when fresh data arrives; timestamps and stale badge are shown |
| Event history | Persisted event records | Time-ordered sensor warnings, rain, irrigation, low water, faults |
| In-app alert | ViewModel/UI observes new event and current state | Banner/screen notice while app is open |
| Push notification | Trusted backend receives a new event, sends FCM to registered Android tokens | System notification when backgrounded (subject to permission, network, OS delivery) |
| Local audible alarm | ESP32 buzzer state machine, no cloud dependency | Buzzer sounds when qualifying local event occurs |

**Never** represent a disconnected last-known sample as a current live reading; show `lastUpdated` and `offline/stale`.

---

# 02. Hardware selection and complete bill of materials

| Component | Quantity | Specification / usage | Status |
|---|---:|---|---|
| ESP32 Dev Module (classic ESP32-WROOM-32 / WROOM-32E, non-PSRAM) | 1 | 30-pin acceptable *if all pins below are physically exposed*; 38-pin DevKitC also acceptable | [VERIFY] actual board/pin silk |
| Capacitive soil moisture sensors | 3 | Analog AO, 3.3-V-compatible output; one per crop | [VERIFY] exact models |
| DHT22/AM2302 | 1 | Air temperature and relative humidity | Diagram |
| **LDR light sensor + 10 kΩ resistor** | 1 each | **Already in V3**; 3.3-V voltage divider to GPIO36/VP (ADC1) for relative brightness and bright/dark alerts | [CONFIRMED — DIAGRAM / EXISTING GUIDE] |
| Rain sensor with DO comparator module | 1 | Binary wet/dry; supply/output level must be 3.3V-safe | Diagram |
| Distance sensor | 1 | Top-mounted reservoir level; if HC-SR04 at 5 V, use an ECHO level divider | [VERIFY] exact model |
| **20×4 LCD2004 with I2C backpack** (PCF8574-type or compatible) | 1 | **Four-wire module header:** VCC, GND, SDA, SCL; full 20×4 character display; replaces parallel GPIO wiring | **[CONFIRMED — USER UPDATE]** |
| Four-channel 5-V relay board | 1 | 3 valves + shared pump; verify driver and logic levels | Diagram |
| 5-V normally-closed solenoid valves | 3 | Prior specification: ~0.4 A, 2 W each | [VERIFY] actual label |
| 3–5-V DC water pump | 1 | Prior spec ~100–200 mA nominal, higher startup possible | [VERIFY] actual label/inrush |
| Active buzzer (or passive with suitable drive) | 1 | Drive through a proper transistor if current requires | Diagram |
| USB power bank / regulated 5-V source | 1 | Stable **5 V, preferably 3 A or more** after measured-load verification | Design preference |
| Inline fuse + holder, DC-rated master switch | 1 each | Fuse sized to **wiring and actual measured circuit** | [SAFETY REQUIREMENT] |
| DC distribution terminals, wires, connectors, enclosure | As needed | Secure connections; avoid Dupont wires in the load power path | [SAFETY REQUIREMENT] |
| Suppression diodes/TVS for DC inductive loads | As needed | Protect relay contacts and reduce transient spikes | [SAFETY REQUIREMENT] |
| ECHO voltage-divider resistors / level shifter | As needed | Keep every ESP32 input ≤3.3 V | [SAFETY REQUIREMENT] |
| **Bidirectional I2C level shifter** (3.3 V ↔ 5 V) | 1, if LCD backpack uses 5-V I2C pull-ups | Keep GPIO21/GPIO22 bus HIGH level at **3.3 V**, not 5 V; verify actual backpack wiring | [SAFETY REQUIREMENT] |
| Water reservoir and tubing | 1 + set | Reservoir height **7 inches**, three branch outlets | Diagram |
| ½-inch plywood enclosure | As needed | Keep electricity dry and separate from reservoir | Previous project plan |

**Important:** The ESP32 can handle the *number of tasks*; electrical overload or shorts—not simply “many sensors”—are a greater risk. Do not buy an ESP32-S3 purely for this workload. A replacement WROOM module should have **GPIO16/17 available**; avoid versions where flash/PSRAM uses them.

### 2.1 Pre-power measurements

- Record bank output voltage under load (not just while idle).
- Record pump steady and startup current, valve current, relay board supply current, ESP32 supply current during Wi-Fi bursts.
- Calculate maximum intended simultaneous current: **one valve + pump + controller + relay board + sensors/LCD**, plus startup margin; do not assume all three valves operate together.
- Verify master switch and fuse can carry this current; verify fuse protects the thinnest relevant power wire. **The exact fuse rating cannot be determined from the diagram alone.**
- Verify there is no positive-to-ground short, reversed polarization, or 5-V wire landing on a GPIO.

---

# 03. Final ESP32 GPIO map — 20×4 I2C LCD (updated V3.1)

## 3.1 Authoritative pin assignments (replace the old parallel wiring)

| Function | ESP32 GPIO | Signal type | Electrical/wiring notes |
|---|---:|---|---|
| **LCD2004 I2C SDA** | **GPIO21** | I2C bidirectional | Via appropriate 3.3 V ↔ 5 V level shifter if backpack is powered/pulled up at 5 V |
| **LCD2004 I2C SCL** | **GPIO22** | I2C clock | Same voltage requirement as SDA |
| **Rain sensor DO** | **GPIO32** | DIGITAL INPUT | **User-confirmed**; output/pull-up must never apply 5 V directly to pin |
| **Buzzer control** | **GPIO18** | DIGITAL OUTPUT | **User-confirmed**; use transistor driver if buzzer load requires it |
| **LDR light sensor divider** | **GPIO36 / VP** | ADC1 ANALOG INPUT | **Already included in previous guide**; light/brightness only, not temperature or humidity |
| Tomato soil AO | GPIO34 | ADC1 ANALOG INPUT | 3.3-V compatible analog voltage |
| Chili soil AO | GPIO35 | ADC1 ANALOG INPUT | 3.3-V compatible analog voltage |
| Eggplant soil AO | GPIO33 | ADC1 ANALOG INPUT | 3.3-V compatible analog voltage |
| DHT22 DATA | GPIO4 | DIGITAL INPUT | Temperature + humidity; pull-up to 3.3 V if needed |
| Ultrasonic TRIG | GPIO16 | DIGITAL OUTPUT | Verify sensor accepts 3.3-V TRIG |
| Ultrasonic ECHO | GPIO17 | DIGITAL INPUT | If ECHO is 5 V, level shift/divide before GPIO17 |
| Relay IN1 — tomato valve | GPIO25 | DIGITAL OUTPUT | Relay logic level and safe OFF boot state verified |
| Relay IN2 — chili valve | GPIO26 | DIGITAL OUTPUT | Same precautions |
| Relay IN3 — eggplant valve | GPIO27 | DIGITAL OUTPUT | Same precautions |
| Relay IN4 — common pump | GPIO23 | DIGITAL OUTPUT | Same precautions |

**No duplicate GPIO assignments:** GPIO21 and GPIO22 belong to the I2C LCD; **GPIO32 belongs only to rain DO**, not LCD D7. **GPIO18 belongs only to buzzer**. GPIO13, GPIO14, GPIO19, and GPIO39 are **currently unused/reserved** in this guide (do not wire old LCD data lines or rain to them). Check that the actual 30-pin/38-pin board exposes every assigned pin; do not infer physical header position from GPIO number alone.

**ADC with Wi-Fi:** Three soil analog inputs (34/35/33) plus LDR analog input (36) are all ADC1, avoiding classic ESP32 ADC2 contention when Wi-Fi is active. GPIO34–39 are input-only; GPIO36/VP is suitable for the LDR analog input. GPIO32 is a general-purpose input capable of receiving the rain sensor digital output.

## 3.2 Exact I2C LCD2004 wiring and 5-V/3.3-V protection

**Buy/use:** a **20×4 LCD with installed I2C backpack**, normally marked `GND`, `VCC`, `SDA`, `SCL`. A bare 16-pin parallel LCD without a backpack is **not** the intended replacement; either buy the I2C variant or correctly attach a compatible backpack. No independent ESP32 LCD RS/E/D4/D5/D6/D7 wires are used.

| I2C backpack terminal | Connection |
|---|---|
| `VCC` | LCD/backpack rated supply, commonly regulated **+5 V** (verify label/model) |
| `GND` | Common **GND** |
| `SDA` | **GPIO21**, through bidirectional I2C level shifter **if the LCD bus uses 5-V pull-ups** |
| `SCL` | **GPIO22**, through the same level shifter when needed |

**Recommended for a typical 5-V backpack with onboard SDA/SCL pull-ups to 5 V:**

```text
ESP32 GPIO21 SDA -------- LV1  [3.3-V / 5-V I2C level shifter]  HV1 ----- LCD SDA
ESP32 GPIO22 SCL -------- LV2  [bidirectional, open-drain]      HV2 ----- LCD SCL
ESP32 3V3 ------------- LV power / 3.3-V reference
Regulated +5 V -------- HV power / 5-V reference AND LCD VCC
All grounds ----------- GND on ESP32, level shifter and LCD
```

- This is **two ESP32 signal pins**, not two total wires. With a 5-V backpack, use a proper rated level shifter to avoid driving ESP32 SDA/SCL at 5 V. The backpack itself has four terminals; the optional level shifter adds wiring.
- I2C requires pull-ups to the **correct voltage on each side**. Many I2C backpacks already have pull-ups on their own 5-V side. Check them; do not install arbitrary duplicate strong pull-ups.
- **Never directly connect 5-V I2C pull-ups to GPIO21/GPIO22.** A measured 3.3-V bus level is appropriate on the ESP32 side.
- A verified **3.3-V-rated backpack/LCD assembly** can sometimes operate without level shifting, but only if the actual module works at 3.3 V for power, contrast, and all signals; don't assume a generic 5-V LCD is a 3.3-V model.
- The I2C backpack may include a **built-in small contrast trimmer**; that is not a separate external potentiometer. Adjust it if backlight works but characters are invisible. Confirm the LCD/backpack has the correct mechanical solder/header connection.
- Common I2C addresses include `0x27` and `0x3F`, but **do not hardcode one without scanning/testing the actual backpack**.
- Do not attach the old LCD parallel control/data wires to GPIO13/14/19/21/22/32. GPIO21/22 now carry **only SDA/SCL**; GPIO32 is available for rain DO.

### Library and minimal standalone LCD test

For Arduino IDE, install **hd44780** (Bill Perry / duinoWitchery). Its `hd44780_I2Cexp` class can detect common PCF8574 I2C backpacks and their address/pin mapping. Confirm compatibility with your selected module; otherwise use its module-specific working library. Code below is a **standalone LCD test**, not the complete Smart Plant Care firmware:

```cpp
#include <Arduino.h>
#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

constexpr int PIN_LCD_SDA = 21;
constexpr int PIN_LCD_SCL = 22;
hd44780_I2Cexp lcd;   // auto-detect compatible I2C backpack

void setup() {
  Serial.begin(115200);
  Wire.begin(PIN_LCD_SDA, PIN_LCD_SCL);
  int status = lcd.begin(20, 4);
  if (status != 0) {
    Serial.printf("I2C LCD init failed: %d\n", status);
    return;  // Leave all pump/valve loads disconnected for this test.
  }
  lcd.backlight();
  lcd.setCursor(0, 0); lcd.print("SMART PLANT CARE");
  lcd.setCursor(0, 1); lcd.print("I2C SDA: GPIO21");
  lcd.setCursor(0, 2); lcd.print("I2C SCL: GPIO22");
  lcd.setCursor(0, 3); lcd.print("RAIN: GPIO32");
}
void loop() {} // Standalone display-only bench test.
```

**LCD debug sequence:** first verify LCD power/GND and 3.3-V SDA/SCL levels; then run the library's `I2CexpDiag` example or an I2C scanner and note the detected address; finally adjust backpack contrast and re-check SDA/SCL continuity. The LCD page **content**, rotation, event overlays, and alerts in Section 10 are unchanged by this wiring migration.

## 3.3 LDR voltage divider

```text
3.3 V --- LDR ---+--- GPIO36
                 |
               10 kΩ
                 |
                GND
```

With this exact arrangement, **more light usually creates a higher ADC reading**. If you swap resistor/LDR positions the direction reverses; calibrate and store `brightRaw`/`darkRaw`. An uncalibrated LDR **cannot honestly provide lux**. Display `brightness %` (relative) and `Bright/Normal/Dark` based on its calibration.

## 3.4 DHT22

```text
DHT22 VCC  -> 3.3 V
DHT22 DATA -> GPIO4
DHT22 GND  -> GND
DATA -> pull-up to 3.3 V if not already included on the sensor board
```

Sample no faster than roughly once every 2 seconds; **2.5–3 seconds** is a good starting interval. Show `--`/invalid after failed reads, *not* 0°C/0% humidity. Mount away from motor heat, rain spray, and direct sunlight. GPIO4 can participate in boot strapping; confirm that the physical DHT22 board and pull-up do not disturb ESP32 boot.

## 3.5 Three soil sensors

```text
Tomato:   sensor VCC -> 3.3 V, GND -> GND, AO -> GPIO34
Chili:    sensor VCC -> 3.3 V, GND -> GND, AO -> GPIO35
Eggplant: sensor VCC -> 3.3 V, GND -> GND, AO -> GPIO33
```

**Never** assume `raw=4095` means dry for every module or treat a disconnected channel as a valid 0%. Calibrate separately for all three soil samples and units; validate values, sudden jumps, cable disconnects and ADC saturation.

## 3.6 Rain digital sensor

```text
Sensor 3.3 V/VCC (only if module supports 3.3 V) -> 3.3 V
Sensor GND                                  -> GND
Sensor DO (3.3 V max)                       -> GPIO32
```

Many LM393-style rain modules provide a comparator digital signal whose active polarity depends on module and adjustment. **Bench test and save whether `LOW` or `HIGH` means wet.** If the module is powered at 5 V and DO pulls up to 5 V, level shift before **GPIO32**. A comparator with open-collector DO requires a pull-up to **3.3 V** (the internal GPIO32 pull-up may be evaluated if suitable; external pull-up can be more reliable for a long cable). Confirm comparator polarity physically; rain-sensor logic remains the strict Open-Meteo + wet sensor **AND** rule.

## 3.7 Ultrasonic distance sensor

```text
VCC -> the sensor's rated supply (often 5 V for HC-SR04)
GND -> common GND
TRIG -> GPIO16
ECHO -> voltage divider / logic level shifter -> GPIO17
```

For a 5-V ECHO, a **typical two-resistor divider** can use 10 kΩ from sensor ECHO to GPIO17, and 15 kΩ from GPIO17 to GND, giving approximately 3.0 V at a 5-V input. Confirm resistor values and measure at the GPIO with a meter before connecting. Sensor minimum range, splash protection, condensation, narrow reservoir walls, and mounting angle can cause false readings. No timeout should be converted to an empty/full value.

## 3.8 Relay inputs and active level

```text
GPIO25 -> IN1 : tomato valve
GPIO26 -> IN2 : chili valve
GPIO27 -> IN3 : eggplant valve
GPIO23 -> IN4 : shared pump
```

A relay board typically needs **5 V to drive its coils** and shares a reference GND with ESP32 control signals where the module design requires it; some optocoupled boards support true separate supply isolation and should follow their own manual. **ESP32's 3.3-V GPIO must not supply the relay coil current**. Test whether IN = LOW activates the relay; many modules are active-low. Confirm it accepts 3.3-V input safely. Use NO (normally-open) contacts so loads are off when relay coils are off; test the actual startup, reset, Wi-Fi connect, firmware crash, and power-loss behavior with loads disconnected first.

---

# 04. Power-bank, fuse, switch, relay contacts, and tubing

## 4.1 Recommended physical power diagram

```text
               5-V POWER BANK / VERIFIED REGULATED 5-V SUPPLY
                                |
                             +5 V rail
                                |
                   appropriately rated fuse
                                |
                       DC-rated master switch
                                |
                        +5-V terminal block
                  _____________|_______________________
                 |           |          |             |
                COM1        COM2       COM3          COM4
                 |           |          |             |
                NO1         NO2        NO3           NO4
                 |           |          |             |
          Tomato valve+ Chili valve+ Eggplant valve+ Pump+
                 |           |          |             |
              load -      load -     load -          load -
                  \___________|__________|_____________/
                                  |
                            5-V return / GND

   ESP32: separate suitable 5-V input (USB-C or verified 5-V input)
   Relay board: separately powered 5-V module supply as rated
   ESP32 GND and relay-input reference GND: properly joined as required
```

**Diagram meaning:** It is valid to distribute the *same positive supply* to COM1–COM4 **only if** contacts, relay PCB traces, wire sizes, connector ratings, distribution terminals, power source, and fuse all safely support the real measured currents. Each NO output sends switched positive to **its own** pump/valve. **All load negatives go to supply negative**, not relay NO. Do not wire all valve positives together on one NO or simultaneously open three valves unintentionally.

### Important practical warnings

- A USB power bank already outputs regulated **5 V**, so a **buck converter is normally not required** for rated 5-V loads. Verify actual output and current; some banks shut down when draw is too low.
- Some 5-V solenoids/relay modules may draw enough current to brown out a weak bank. A 3-A (or higher, if measured need requires) regulated output is a good prototype target.
- **Do not power motors/valves through the ESP32 5V/VIN pin or its 3.3-V regulator.**
- Use a proper USB breakout/terminal adapter or designed DC cable. Cutting a random USB cable and twisting it into a permanent power assembly is a poor choice for a water-handling prototype.
- Never connect exposed relay contacts to 220-V AC in this 5-V prototype.
- Use **load-rated** wire/connectors for the pump and valves. Thin breadboard Dupont wires are suitable for *signals only*, not for pump/solenoid distribution.
- Add DC inductive suppression on solenoid valves and pump according to manufacturer requirements (e.g. correctly rated flyback diode/TVS near the load; polarity matters). This helps prevent contact arcing and transient resets.
- Keep water tubing, reservoir and drain paths physically isolated from electronics; use drip loops, covered terminals, splash barriers, and a dry electronics compartment.
- If debugging from a PC USB connection while the power bank also powers the board, check for **backfeeding/dual-supply conflicts**; avoid powering a board from both sources without proper isolation.

## 4.2 Safe plumbing

```text
       Reservoir -> water pump -> common pressure/tube manifold
                                        |
                  +---------------------+---------------------+
                  |                     |                     |
             Tomato valve          Chili valve          Eggplant valve
                  |                     |                     |
              Tomato pot             Chili pot            Eggplant pot
```

Confirm water flow direction, pump priming, leak-free tubing, valve pressure requirements, and what happens if the pump runs with a closed valve. Do not assume a low-cost 5-V solenoid valve works at zero pressure; **bench test with the exact pump and hose layout**. Install a check valve only if reverse flow is actually observed and it suits the pump/valve specifications.

## 4.3 Mandatory boot/fault behavior

- **On power-up:** all NO load contacts physically OFF; configure GPIO inactive *before* normal control begins; test active-low boards at reset (not just after `setup()`). If needed use a module or hardware enable method that guarantees inactivity while booting.
- **At ESP32 crash/reset:** pump and valves must not remain powered by stuck control; use physical fail-safe, watchdog and maximum-runtime software limits. A basic ESP32 watchdog does not replace a hardware safety design.
- **On water-level invalid or critical low:** pump OFF immediately, valve OFF, display fault, create one alert.
- **On emergency/manual STOP:** pump OFF then valve OFF, update state and log event; stop commands receive highest precedence.
- **During normal irrigation shutdown:** pump OFF first, then close the selected valve after the verified brief run-down interval, avoiding closed-head operation.

---

# 05. Sensor measurements, calibration, and validity

## 5.1 Soil moisture conversion

For each crop, record the **dry raw ADC value** and **wet raw ADC value**. The relative percentage formula is:

```text
soilPercent = clamp(100 * (raw - dryRaw) / (wetRaw - dryRaw), 0, 100)
```

This equation works regardless of which direction raw values move, provided `dryRaw != wetRaw`. Example for **illustration only**: `dryRaw=3300`, `wetRaw=1300`, `raw=2300` -> 50%. Actual calibration values are not known yet; do not copy example numbers without measuring.

**Calibration steps:**

1. Label sensors TOMATO, CHILI, EGGPLANT; record unique module identity/GPIO.
2. Read 30–60 samples under a known dry condition for each sensor, use stable median/average; record `dryRaw`.
3. Read stable wet calibration condition for each, record `wetRaw`; avoid using a completely submerged unsealed electronics section.
4. Sample representative soil at realistic moisture to select **start** and **stop** thresholds separately for each crop. Use your capstone plant-care criteria and actual tests, not an arbitrary universal crop standard.
5. Require `startPercent < stopPercent` (hysteresis). Example ***test-only***: start below 35%, stop at 55%, but do not label it as a horticultural rule.
6. Reject invalid wiring/disconnect/stuck readings; **unknown is not dry**. A failed zone sensor blocks only its associated automatic watering, unless other global faults exist.

## 5.2 DHT22

Measurements: `temperatureC`, `humidityRH` (%). Typical project alerts: temperature high, optionally temperature low, humidity too low, optionally too high. Use a stable measuring interval around **2.5–3 s**, reject `NaN`, check sane ranges, use a multi-sample hold to prevent false spikes. Avoid putting the DHT22 near wet valves or inside sealed hot electronics housing.

## 5.3 LDR brightness

Calibration examples: cover LDR to measure `darkRaw`; expose it to representative bright light to measure `brightRaw`. Convert relative brightness:

```text
brightnessPercent = clamp(100 * (raw - darkRaw) /
                              (brightRaw - darkRaw), 0, 100)
```

For divider orientation in Section 3.3, brightRaw will typically be higher; verify. LDR percent is a **relative brightness index, NOT calibrated lux**. Too bright/too dark alerts are derived from that index and configured threshold(s).

## 5.4 Rain sensor and debounce

Record whether the comparator DO reads `LOW` or `HIGH` when wet. The sensor has at least four states: `DRY`, `WET_UNCONFIRMED`, `WET_CONFIRMED`, and `FAULT/UNKNOWN`. Set comparator sensitivity in a dry state and test with controlled drops/splash versus sustained surface wetting.

- [DESIGN DEFAULT] **Wet confirmed** only after **30 s continuously wet** (time resets if it goes dry).
- [DESIGN DEFAULT] **Dry recovered** only after **120 s continuously dry**.
- A wet sensor can be contaminated, splashed, or held wet; it **does not prove natural rain**. That is why the user requested the strict API + sensor AND logic.
- A broken/open DO wire and a dry comparator output may look identical without hardware diagnostics. Mount and test carefully; flag obviously stuck readings/timeout and inspect periodically.

## 5.5 Reservoir sensor: 7-inch specification

**[CONFIRMED — USER]** The reservoir height is **7 inches**. The sensor is aimed downward from the top. **The measured value is air gap, not water depth.**

| Air gap from sensor to water | Interpretation |
|---|---|
| Small, within usable range | Water near the top -> reservoir fuller |
| Increasing | Water surface falling -> level decreasing |
| ~7.0 in or more | Empty, **0%** (after physical calibration) |
| No echo / out-of-range | **UNKNOWN**, not automatically 100% or 0%; block pumping if safety-critical |

**Installation calibration:**

1. Physically measure from the **transducer face** to the actual empty-bottom water level: `D_EMPTY_IN`. The nominal design value is **7.0 in**, but a sensor mounted above the rim can have `D_EMPTY_IN > 7 in`; measure the installed geometry.
2. Fill only up to the **safe maximum water line**, measure the gap: `D_FULL_IN`. It will usually be **greater than zero** because of sensor height/minimum-range limits.
3. Confirm `D_EMPTY_IN > D_FULL_IN` and both within the rated detectable range.
4. Apply median filter (e.g. 5–7 qualified pings) and discard zero/no echo, impossible jumps, and readings outside calibrated geometry.
5. For a rectangular tank of nearly constant cross-section, the height percentage approximates volume percentage. For irregular shape, make a measured volume lookup table instead.

**Reservoir percentage:**

```text
waterPercent = clamp(
   100 * (D_EMPTY_IN - measuredAirGapIn) / (D_EMPTY_IN - D_FULL_IN),
   0, 100
)
```

### Example conversion table (CALIBRATION EXAMPLE — NOT A MEASURED VALUE)

Assume `D_EMPTY_IN=7.0` and `D_FULL_IN=1.0` solely to demonstrate the formula:

| Valid distance (in) | App reservoir level |
|---:|---:|
| 1.0 or less | 100% |
| 2.5 | 75% |
| 4.0 | 50% |
| 5.5 | 25% |
| 6.4 | 10% |
| 7.0 or more | 0% EMPTY |

**Firmware variable naming:** `airGapInches` = raw measurement; `waterLevelPercent` = computed amount of water. Do not call air gap `waterHeight`.

**Warnings and safety** [DESIGN DEFAULT, subject to testing]:

| Calibrated level | Device action | App notification | Buzzer |
|---|---|---|---|
| >20% through 100% | Normal water monitoring | Live value only | Silent |
| **>10% through 20%** after 10 s valid readings | `RESERVOIR_LOW` | Once on crossing 20%; event history | 2 short beeps once |
| **>0% through 10%** after validation | `RESERVOIR_CRITICAL` | Immediate event; pump shutdown / block | 3 short beeps once |
| **0%** (gap ≥ calibrated empty) | `RESERVOIR_EMPTY` | Immediate event; pump OFF/disabled | 3 short beeps once |
| Invalid/no echo | `RESERVOIR_SENSOR_FAULT` | Fault event; display `--%`; pump blocked | 3 short beeps once |

Stop threshold **10%** is a recommended starting point, not proof the water intake is still submerged at 10%. If the pump begins sucking air sooner, **raise the cutoff**. Recover from low/critical only after stable refilling **above a higher configured recovery value** (e.g. >25%) to prevent flapping. At critically low, zero, or invalid measurements, fail-safe pump OFF regardless of user commands.

**Why the air gap matters:** *Near the ultrasonic sensor means fuller*, exactly as requested. It is **not** “low water because the distance is small.”

---
# 06. Environmental warnings: temperature, humidity, and light

## 6.1 Which sensor creates which warning?

| Condition | Actual measurement source | Device behavior | Android behavior |
|---|---|---|---|
| High temperature | **DHT22 temperature** | Mark `TEMP_HIGH`; local buzzer once on confirmed transition | Live red/orange status, one high-temperature push event |
| Low temperature (if enabled) | DHT22 temperature | Mark `TEMP_LOW` | One low-temperature alert |
| Low humidity | **DHT22 relative humidity** | Mark `HUMIDITY_LOW`; one beep | Live warning + one low-humidity alert |
| High humidity (if enabled) | DHT22 relative humidity | Mark `HUMIDITY_HIGH` | Live warning + one high-humidity alert |
| Light too bright | **LDR relative brightness** | Mark `LIGHT_HIGH`; one beep | Live brightness index + one light alert |
| Light too dark | LDR relative brightness | Mark `LIGHT_LOW`; one beep | Live brightness index + one light alert |
| Moisture too low | **Individual crop soil sensor** | Queue that crop for automatic watering, subject to safety | Live crop warning and optional event; not a high-temperature alert |

These are **monitoring alerts**, not automatic pump shutdown conditions, unless a separately justified horticultural or electrical safety rule is configured. Temperature and humidity do not come from the LDR. Sensor disconnected/invalid is shown as a fault, not interpreted as an actual extreme condition.

## 6.2 Parameter defaults versus crop science

The diagrams request high/low environment alerts but **do not specify numeric acceptable temperature, humidity, or light limits**. Configure thresholds after testing and capstone-approved crop criteria. These numbers illustrate an initial lab run only; they are **NOT validated plant-care recommendations**:

```yaml
lab_test_example_only:
  temperature_high_c: 35.0
  temperature_rearm_below_c: 33.0
  humidity_low_rh: 40.0
  humidity_rearm_above_rh: 45.0
  brightness_high_percent: 85
  brightness_high_rearm_below_percent: 78
  brightness_low_percent: 20
  brightness_low_rearm_above_percent: 28
  confirm_duration_seconds: 30
  repeat_alert_after_hours_if_still_critical: 0  # 0 = no repeated push
```

**Recommended general alarm behavior**:

1. Take a fresh, valid sensor value.
2. Require condition to remain true for **30 s** (or several verified samples) to reduce false positives.
3. Open an alert only when status transitions `NORMAL -> ALERT`.
4. Immediately update dashboard status; write **one** event; play the buzzer pattern **once**; send **one** push via backend.
5. Keep the alert active and visible, but **do not ping the buzzer or push every 15 s**.
6. Clear only after the recovery threshold/hysteresis is satisfied for a stable period.
7. A *new* later transition to alert is eligible for a new notification; optional cooldowns protect against frequent flapping.
8. If invalid readings persist, create a distinct `SENSOR_FAULT` event rather than `TEMP_LOW`, `HUMIDITY_LOW`, etc.

### Example real notifications

- **Temperature High — Smart Plant Care:** `Air temperature is 36.1°C at Tomato/Chili/Eggplant unit. Check ventilation and heat exposure.`
- **Humidity Low:** `Relative humidity has dropped to 37%. Check the growing environment.`
- **Light Too Bright:** `Relative light level reached 91%. Check direct sunlight.`
- **Light Too Dark:** `Relative light level dropped to 14%. Check the plant's light exposure.`

The DHT22 and LDR monitor the **shared prototype environment**, not separately measured conditions per plant. Avoid suggesting a sensor measures an individual pot unless installed there.

---

# 07. Weather API, indoor/outdoor mode, and the exact rain condition

## 7.1 Define the probability correctly

**[CONFIRMED — USER]** The Open-Meteo probability **does not automatically suspend irrigation**. It is a forecast/advisory. Local rain sensor wetness alone **also does not suspend** irrigation, because splashing or a wrong forecast may cause false conclusions.

**FINAL, AUTHORITATIVE RAIN TRIGGER:**

```text
shouldTriggerRainSuspension =
   (mode == OUTDOOR)
   AND weather.isFresh == true
   AND weather.isValid == true
   AND weather.precipitationProbabilityPercent >= 60
   AND rainSensor.wetConfirmed == true
```

Use the weather **probability for a clearly defined time window**, not a random daily and hourly mix.

**[DESIGN DEFAULT] Forecast window:** maximum **hourly `precipitation_probability` in the next 3 hours**, starting from the current time in the installation location's time zone; fetch new weather about every **30 minutes**, mark stale after **60 minutes** unless backend data contract requires a different TTL. This is a choice for consistent behavior, not a number shown in the diagram. Show the chosen window in the app.

**Open-Meteo example query template** (the backend fills in real installation coordinates):

```text
GET https://api.open-meteo.com/v1/forecast
  ?latitude={LATITUDE}
  &longitude={LONGITUDE}
  &hourly=precipitation_probability
  &forecast_days=2
  &timezone=Asia%2FManila
```

Pick upcoming hourly samples using returned timestamps, **not** the first three array items blindly. Open-Meteo documents `precipitation_probability` as a probability for the preceding hour with a threshold of more than 0.1 mm; it is **forecast probability, not direct local rain detection**. Persist `windowStart`, `windowEnd`, `measuredAt/fetchedAt`, `validUntil`, location coordinates and chosen probability alongside value.

## 7.2 Complete truth table (outdoor mode)

| Fresh API probability | Rain sensor | Action | Buzzer | Phone |
|---|---|---|---|---|
| 80% | Dry | **Do not suspend**; continue eligible irrigation | Silent | **Forecast warning** once; live `DRY` |
| 80% | Wet for 4 s | Not yet confirmed; continue normal safety checks | Silent | Live `WET_UNCONFIRMED`; don't push confirmed-rain |
| 80% | Wet continuously for 30 s | **Latch rain suspension**; pump OFF, valve OFF | 2 short beeps once | **Rain Suspension** push once |
| 30% | Wet for 30 s | **Do not create new rain suspension** | Silent | Live `WET_CONFIRMED`; no rain-suspension push |
| 30% | Dry | Normal | Silent | Normal live status |
| 60% exactly | Wet confirmed | **Suspend** (threshold is `>=`, not `>`) | 2 beeps once | Rain-suspension push |
| Missing/stale | Any | **Weather unavailable policy**, not an invented 0% nor confirmed rain | Separate fault pattern if configured | `Weather data unavailable` warning |

**Key trade-off:** With this requested strict AND condition, *real rain can occur during a forecast below 60%, and this rule will not start a rain suspension*. This is explicitly intentional in the project specification; do not silently replace it with OR logic.

### Once rain has been suspended: recovery must be stable

```text
rainLatch = true immediately on the combined trigger

rainLatch can clear only if:
  valid fresh weather probability < 60%
  AND sensor has been DRY continuously for recovery window (default 120 s)

if rainLatch still true:
  block automatic irrigation even if one input later changes
```

Why a latch? If the rain sensor dries for a few seconds but the forecast is still high, the pump must not repeatedly restart. A latched suspension also remains active while weather information is temporarily unavailable; only a **fresh** forecast and **confirmed** dry recovery clear it.

## 7.3 Indoor/outdoor mode selected in the Android app

| Mode | Weather/rain behavior | Other sensors | Watering |
|---|---|---|---|
| **OUTDOOR** | Apply combined weather + rain rule; create forecast advisory | Temperature, humidity, light, soil, reservoir still work | Automatic only when no rain latch/global block |
| **INDOOR** | Outdoor rain gate disabled; optional weather display for context but no outdoor forecast advisory | All monitoring continues; soil/reservoir remain critical | Automatic controlled by soil and safety rules |

Mode changes must be validated, written with version/time, and acknowledged by the ESP32. Changing mode **must not bypass dry-run, reservoir, runtime, electrical fault, or emergency-stop rules**. If switching from OUTDOOR while a rain latch is active, first stop irrigation, then explicitly transition to INDOOR and log the change. Never let a UI toggle energize a pump immediately.

## 7.4 Who should call the API?

**Best lightweight design:** A trusted backend/scheduled service periodically fetches Open-Meteo and writes a *small processed weather object* to RTDB. The ESP32 reads that object and runs the small Boolean AND decision. The app displays the same object; no independent inconsistent weather thresholds. If no backend is available for weather, ESP32 direct HTTPS polling **every 30–60 min** is a fallback, but TLS/JSON parsing uses more memory; validate heap and reconnect behavior.

The ESP32 does not know its location automatically from Wi-Fi. During setup the **phone asks location permission, offers map/manual location, and saves the INSTALLATION COORDINATES**. Those are coordinates of the installed prototype, not always the current phone location (the owner may walk away).

## 7.5 Stale API or Wi-Fi loss is a separate state

Never silently replace unavailable weather with `0%` and call it sunny.

**[DESIGN DEFAULT conservative failure policy]:**

- Outdoor mode + no fresh valid forecast: **block new unattended automatic irrigation starts** until forecast is fresh again. A currently running cycle should be stopped safely when the required weather data expires; test grace windows before unattended use.
- This creates a `WEATHER_UNAVAILABLE` *technical fault* / app banner, **not** `RAIN_CONFIRMED` or `RAIN_SUSPENDED`.
- Indoor mode ignores the weather gate and can continue soil-controlled automatic watering if physical safety is normal.
- If offline, use `lastKnownWeather` only for display with its age; never treat it as fresh indefinitely.
- A manual override of *weather fault* must require explicit confirmation and **never override critical reservoir, relay, runtime, or sensor faults**. [DESIGN DEFAULT: leave override disabled until acceptance tests pass.]

---

# 08. Complete irrigation logic: three plants, three valves, one pump

## 8.1 Mapping

| Crop | Soil GPIO | Valve GPIO / relay | Shared pump |
|---|---:|---|---|
| Tomato | GPIO34 | GPIO25 / IN1 | GPIO23 / IN4 |
| Chili | GPIO35 | GPIO26 / IN2 | GPIO23 / IN4 |
| Eggplant | GPIO33 | GPIO27 / IN3 | GPIO23 / IN4 |

## 8.2 Per-zone configuration and eligibility

For every zone, use independent `soilStartPct`, `soilStopPct` (`stop > start`), `minIntervalBetweenWatering`, `maxPumpOnMs`, `pulseMs`, `settleMs`, `enabled`, and calibration. **[VERIFY]** Numeric moisture thresholds and water volumes must be decided from the actual sensors, pots, plant requirements, and measured pump flow, not guessed from a drawing.

```text
zoneEligible =
  zone.enabled
  AND soilSensorValid(zone)
  AND soilPercent(zone) <= startThreshold(zone)
  AND zoneCooldownExpired
  AND noOtherZoneActive

safetyOK =
  masterControllerReady AND locallyClaimed AND NOT resetPending
  AND validReservoirReading
  AND reservoirPercent > criticalCutoff
  AND noEmergencyStop
  AND noPump/relay/electricalFault
  AND noGlobalMaxRuntimeFault
  AND (INDOOR OR (weatherFreshAndValid AND rainSensorValid AND NOT rainSuspensionLatched))

if zoneEligible AND safetyOK:
  queue zone (do NOT run all three)
```

**Soil fault handling:** When only chili soil sensor fails, **block chili automatic watering**; tomato/eggplant can work if all global safety gates pass. `0%` or a saturated ADC reading should only mean dry soil if validated by calibration and sensor health checks.

## 8.3 State machine and transition sequence

```text
BOOT_SAFE
   |
   v
SELF_TEST + LOADING_CONFIG ---> FAULT_LOCKOUT (if unsafe)
   |
   v
IDLE_MONITORING
   |  crop dry + all safety OK
   v
QUEUED ---> OPENING_SELECTED_VALVE ---> PUMP_WATERING ---> PUMP_STOPPING
   ^                |                        |                 |
   |                | safety stop            | safety stop     v
   |                +------------------------+-------------> CLOSING_VALVE
   |                                                      |
   +--- SOAK_SETTLE <-------------------------------------+
   |
   +-----> evaluate all zones again only after cooldown + fresh soil

SUSPENDED_RAIN, RESERVOIR_CRITICAL, WEATHER_FAULT and EMERGENCY_STOP
  are blocking states or gates that preempt the normal cycle.
```

### Exact physical start sequence

1. Verify stable power, relay driver state, calibrated reservoir level, valid zone sensor, command age, weather/rain gates, and max runtime.
2. Confirm all four relay outputs OFF.
3. Select only **one** crop: `activeZone = TOMATO/CHILI/EGGPLANT`.
4. Energize **only** that crop valve relay; leave other valves OFF.
5. Wait a short *nonblocking* valve-settling interval [DESIGN DEFAULT: 300–500 ms; confirm valve opening speed].
6. Re-check safety before energizing pump.
7. Energize pump relay, record `pumpStartedAt`, set state `WATERING`.
8. Continuously check critical gates and a **hard timeout**; do not wait on Firebase calls or long delays.

### During watering

- Pump may run as a **short, measured pulse**, then stop and allow soil to soak. Soil sensor readings can lag; do not insist a soil value instantly rises while pump runs.
- Set verified maximum pump-on duration per pulse and per entire zone. [DESIGN DEFAULT: begin with **very short supervised pulses**, e.g. 2–5 seconds, not a final dosage.] 
- The pump's advertised free-flow rate is not actual pot delivery. Measure output into a graduated container at the actual tubing height/valve restriction to calibrate mL/s.
- Immediately stop if critical reservoir/fault/emergency occurs, or if a **new confirmed combined rain suspension** occurs in outdoor mode.
- Stop if configured target moisture/total safe dose/time is reached; log why.

### Stop sequence

1. Set **pump relay OFF**.
2. After a verified short settling interval (e.g. 100–300 ms), set **active valve relay OFF**.
3. Update pump status and write an `IRRIGATION_COMPLETED` or `IRRIGATION_ABORTED` event *once*, with crop, duration, reason, and command ID (if manual).
4. Update `activeZone=null`, `pumpOn=false`, all valve states false.
5. Wait zone cooldown/soak period and acquire **fresh valid moisture reading** before deciding if more watering is required.

### Priority / fairness

Start with **round-robin** among dry eligible zones, instead of always selecting tomato first. If a zone remains very dry, apply bounded retry/cooldown; never pump indefinitely. All zones share the same reservoir safety and weather gate.

## 8.4 Safety precedence (highest priority first)

| Priority | Event | Action |
|---:|---|---|
| 1 | Physical master switch OFF / emergency stop | No load power; set safe state when device is powered |
| 2 | ESP32 boot/reset, relay/pump electrical fault, critical unexpected condition | Outputs OFF, fault indication |
| 3 | Empty/critical/invalid reservoir | Pump OFF, valve OFF, block manual and automatic water |
| 4 | Maximum runtime exceeded / stuck valve state / broken control state | Pump OFF, valve OFF, latch fault |
| 5 | Outdoor combined rain trigger or existing rain latch | Pump OFF, valve OFF, block auto |
| 6 | Outdoor weather data stale/unavailable | Separate conservative fail policy; do not call it rain |
| 7 | Zone moisture sensor faulty, crop disabled or cooldown active | Block affected crop only |
| 8 | Dry soil or authenticated manual request | Start only if all higher-level safety conditions pass |

## 8.5 Manual watering through the app

The app offers `Water Tomato`, `Water Chili`, `Water Eggplant`, `Stop Watering`. Requests include target crop, maximum *requested* duration, unique `commandId`, `createdAt`, `expiresAt`, and requesting account UID. The ESP32 validates and acknowledges each command; **it never uses an incoming request to bypass physical safety**.

- An authenticated owner/operator **STOP**, or a local safety stop, is always allowed and processed with highest priority.
- Start requests expire; a disconnected device must **not start watering later because it replays an old command**.
- Reject simultaneous start requests while another zone is running (`BUSY`), or queue only under an explicitly designed policy.
- Reject empty/critical/unknown reservoir, abnormal relay state, max runtime, etc.
- [DESIGN DEFAULT] A manually requested watering during an active **rain suspension** is blocked unless an intentionally implemented, explicit owner override is chosen and recorded; the original diagram only mandates suspension of **automatic** irrigation. Decide this policy explicitly during review.
- Record `REQUESTED`, `ACCEPTED`/`REJECTED`, `RUNNING`, `DONE`/`ABORTED`; never report “watering successful” immediately after a button tap.

## 8.6 Cannot guarantee precise mL from time alone

The prior 5-V pump specifications suggest a nominal free-flow rate, not calibrated delivery. Exact 270–330 mL or any other dose must be based on a **graduated-container measurement** with your tubing, real reservoir level, and selected valve. A timer-only estimate can be displayed as `estimatedMl` with its limitations. True measured volume requires a **flow sensor**, which is *not in the current diagram*, so do not misrepresent pump duration as verified volume.

---

# 09. Buzzer: exact triggers, patterns, and priorities

## 9.1 Definitive project alert matrix

**[DESIGN DEFAULT]** Timings and trigger list below implement the requested buzzer; they are not physically shown as numeric durations in the diagram. Use a nonblocking buzzer scheduler. All patterns play **once per new event transition** unless a critical fault policy explicitly requests a periodic reminder.

| Trigger | Buzzer sound | LCD/action | Phone push? |
|---|---|---|---|
| Controller powered on and ready | Optional **one 100-ms chirp** | `SYSTEM READY` | No |
| New device successfully paired | **One 150-ms beep** | `DEVICE PAIRED` | Optional pairing confirmation |
| High temperature (confirmed) | **One 150-ms beep** | `TEMP HIGH` | Yes, once |
| Low temperature (enabled/confirmed) | One 150-ms beep | `TEMP LOW` | Yes |
| Low humidity (confirmed) | **One 150-ms beep** | `HUMIDITY LOW` | Yes, once |
| High humidity (enabled/confirmed) | One 150-ms beep | `HUMIDITY HIGH` | Yes |
| Light too bright (confirmed) | **One 150-ms beep** | `LIGHT TOO BRIGHT` | Yes |
| Light too dark (confirmed) | **One 150-ms beep** | `LIGHT TOO DARK` | Yes |
| Rain API forecast >=60%, rain sensor dry | **Silent** | Show `RAIN RISK` page/status | Forecast advisory push **once** |
| Rain sensor wet but forecast <60% | **Silent** | Show `SENSOR: WET` | Live state only; **no rain-suspension push** |
| Both combined rain conditions confirmed | **Two 150-ms beeps**, 200-ms gap | `RAIN - AUTO PAUSED` | Yes; single confirmed-rain event |
| Reservoir <=20%, valid reading | **Two 150-ms beeps** | `WATER LEVEL LOW` | Yes; single event |
| Reservoir <=10%, empty, or distance invalid | **Three 150-ms beeps**, gaps | `CRITICAL WATER`/`SENSOR ERROR` | Yes; high priority |
| Selected crop irrigation started | Optional quiet short chirp; **default SILENT** | `WATERING: TOMATO` etc. | Event history; push optional by preference |
| Crop watering completed | Silent | `WATERING COMPLETE` | In-app history; push optional |
| Emergency stop / electrical critical fault | Three 150-ms beeps; optionally repeat on carefully limited schedule | `EMERGENCY STOP` | Yes, urgent |
| Wi-Fi/Firebase temporarily disconnected | Silent (default) | `OFFLINE`/`SYNC ERROR` | Local history; server-side offline alert may be delayed |
| Sensor reading temporarily changes within normal band | **Silent** | Refresh value only | No |

**Priority:** `EMERGENCY/CRITICAL WATER` > `CONFIRMED RAIN STOP` > `LOW WATER` > environmental > `PAIR SUCCESS`. A critical alert may replace a low-priority sound; add a short queue and suppress repeats. Avoid a continuous tone that drains power or becomes a nuisance. If a critical fault persists, **keep the visual state**, and provide a user-mutable audible mute/acknowledge that does **not** clear safety lockout.

## 9.2 Buzzer output driver

GPIO18 is a control signal. For a low-current **active buzzer** a matching GPIO-rated driver might suffice, but use a transistor/MOSFET driver with appropriate resistors and a separate suitable supply for a buzzer whose draw exceeds GPIO ratings. Passive buzzers need a square-wave/PWM; active buzzers generally need a DC ON/OFF output. Check operating voltage and current before wiring. Keep buzzer grounds/reference correct.

## 9.3 Nonblocking sound algorithm

```text
on event transition:
  enqueue pattern(name, count, duration_ms, gap_ms, priority)

serviceBuzzer(nowMs):
  if inactive: choose highest-priority pending pattern
  if ON interval expired: set buzzer OFF; schedule GAP
  if GAP expired: if more pulses, set buzzer ON; otherwise complete

No delay(150), delay(500), or while-beeping loop.
```

---

# 10. I2C LCD 20×4: exactly what appears, when and why

The **I2C LCD2004** remains **20 columns × 4 lines**, with the same screens as the original V3. Only its electrical connection and Arduino driver change: I2C **SDA GPIO21, SCL GPIO22**, using an appropriate 3.3-V interface. Use `lcd.setCursor(0,row)` and a 20-character padded string per row so stale trailing characters disappear without repeatedly clearing the entire screen. All pages below fit within 20 characters per line. No old `LiquidCrystal(rs,e,d4,d5,d6,d7)` initialization is valid for this model.

## 10.1 Normal automatic page rotation

**[DESIGN DEFAULT]** Rotate every **3 seconds**, but show a critical event as a temporary priority overlay. Use these exact *example* formats; they are presentation strings, not actual live values.

### Page A — overview and connectivity

```text
SMART PLANT CARE
MODE: OUTDOOR
STATUS: ONLINE
IRRIGATION: IDLE
```

### Page B — environment

```text
TEMP: 30.2 C NORMAL
HUM:  61 % NORMAL
LIGHT: 67 % NORMAL
ENV: ALL NORMAL
```

### Page C — per-crop soil values

```text
SOIL MOISTURE
TOMATO:    42 %
CHILI:     61 %
EGGPLANT:  39 %
```

### Page D — reservoir

```text
WATER RESERVOIR
LEVEL:  76 %
AIR GAP: 2.44 IN
STATUS: NORMAL
```

### Page E — outdoor weather and rain

```text
FORECAST: 80 %
WINDOW: NEXT 3 H
RAIN SENSOR: DRY
AUTO WATER: ALLOWED
```

### Page F — watering

```text
IRRIGATION ACTIVE
CROP: TOMATO
PUMP: ON  VALVE: 1
TIME: 00:04 / 00:10
```

### Page G — pairing/provisioning (only in setup)

```text
DEVICE SETUP MODE
PAIR WITH PHONE
WIFI: NOT CONFIGURED
PUMP: DISABLED
```

### Page H — network outage

```text
NETWORK DISCONNECTED
SENSORS: ACTIVE
LAST SYNC: 08:15
PUMP: SAFE/IDLE
```

**Important:** When offline, don't show `STATUS: ONLINE`. The last synced time must come from an actual timestamp; if never connected, use `NEVER`.

## 10.2 Priority overlays (event-driven)

| Trigger | Priority LCD screen (example) | Duration |
|---|---|---|
| Reservoir critical / empty | `WATER CRITICAL` + actual percent + `PUMP OFF` | Hold until acknowledged/condition clears; alternate with diagnostic |
| Sensor fault preventing pump | `SENSOR ERROR` + component + `PUMP DISABLED` | Hold until resolved |
| Emergency stop | `EMERGENCY STOP` + `ALL OUTPUTS OFF` | Until cleared |
| Combined rain suspension | `RAIN CONFIRMED` + `AUTO PAUSED` + `FORECAST: xx%` | 5–10 s overlay; show current pause status thereafter |
| Water low | `WATER LEVEL LOW` + `LEVEL: nn%` | 5–10 s; warning persists in rotating reservoir page |
| High temperature | `TEMP HIGH` + measured °C | 5 s; then regular environment page |
| Low humidity | `HUMIDITY LOW` + measured % | 5 s; then regular environment page |
| High/low brightness | `LIGHT TOO BRIGHT` or `LIGHT TOO DARK` + index | 5 s |
| Pairing successful | `DEVICE PAIRED` + masked ID | 5 s |

Display invalid data explicitly as `--`, `ERR`, or `SENSOR OFFLINE`; **do not print an invented `0%` for failed sensors**. LCD should not freeze while waiting for Wi-Fi; keep servicing display, sensor safety, and buzzer from the main loop.

---
# 11. Firebase project and data contracts

## 11.1 Services required

1. **Firebase Authentication** — Android user accounts; use your existing app login setup.
2. **Firebase Realtime Database** — live device state, most recent valid samples, weather snapshot, configuration, commands, events and device permissions.
3. **Firebase Cloud Messaging (FCM)** — Android background push delivery.
4. **Trusted backend or Cloud Functions** — securely claim devices, manage user sharing, issue device authentication, obtain weather, and send FCM. **A bare RTDB client cannot safely send an FCM server-authenticated push by itself.**
5. **Optional analytics/crash logs** — helpful but not required to run the core irrigation project.

**Setup in order:** create/select correct Firebase project -> register Android app with its real package name (`com.example.smartplantcare` in the prior development setup, verify actual Gradle `applicationId`) -> add `google-services.json` into Android app module -> enable Auth methods -> create Realtime Database and correct regional URL -> configure Security Rules from **day one** -> enable FCM -> deploy backend only after rules tested -> connect ESP32 with independent device auth.

**Never** place Firebase Admin private keys, backend service credentials, or an unrestricted RTDB secret in firmware, Android source code, public GitHub, or a QR label. Firebase client config is different from Admin private credentials; still protect the database with real Security Rules.

## 11.2 Canonical RTDB path structure

```text
users/
  {uid}/
    displayName
    notificationPrefs
    fcmTokens/{tokenKey}: {token, platform, updatedAt}

devices/
  {deviceId}/
    meta/
      label, model, hardwareRevision, firmwareVersion,
      ownerUid, ownershipState, ownershipEpoch, installationLocation, createdAt
    acl/
      {uid}: "owner" | "operator" | "viewer"
    status/
      isOnline, lastHeartbeatAt, bootId, wifiConnected,
      firmwareState, faultCodes, activeZone, pumpOn, valvesOn,
      rainSuspensionLatched, lastFaultAt
    telemetry/
      updatedAt, sampledAt, bootId,
      environment/{temperatureC, humidityRh, brightnessPct, valid},
      soil/{tomato, chili, eggplant},
      rain/{sensorWet, wetConfirmed, valid},
      reservoir/{airGapInches, waterLevelPct, valid, state},
      outputs/{pumpOn, tomatoValveOn, chiliValveOn, eggplantValveOn}
    weather/
      valid, probabilityPct, windowStart, windowEnd,
      fetchedAt, validUntil, provider, latitude, longitude
    config/
      mode, rainThresholdPct, wetConfirmSeconds, dryRecoverySeconds,
      reservoirLowPct, reservoirCriticalPct,
      reservoirFullGapIn, reservoirEmptyGapIn,
      cropSettings/{tomato, chili, eggplant},
      environmentLimits, alertPreferences,
      configVersion, updatedAt
    commandRequests/
      {commandId}: {type, crop, durationMs, requestedByUid,
                    createdAt, expiresAt, ownershipEpoch, schemaVersion}
    commandResults/
      {commandId}: {state, reason, startedAt, finishedAt, bootId}
    events/
      {eventId}: {type, severity, title, message, createdAt,
                  bootId, ownershipEpoch, source, crop, values, dedupeKey}
    history/
      {rollupId}: {summary, timeBucket}

pairingClaims/                # server-only: no client public access
  {deviceId}/...
notificationOutbox/            # server-only deduplication jobs
  {deviceId}/{eventId}/...
```

**Small, predictable records beat downloading a whole database.** The ESP32 should read its own `/config`, `/weather`, and `/commandRequests` subset; write only its own `/status`, `/telemetry`, `/commandResults` and `/events`. The Android app subscribes to a **single selected device** at a time, not the entire RTDB root.

## 11.3 Example full live telemetry snapshot

*Example payload with **fictitious sample values**, not a measurement of your actual prototype:*

```json
{
  "updatedAt": 1794996000000,
  "sampledAt": 1794995999000,
  "bootId": "boot-20261117-01",
  "environment": {
    "temperatureC": 30.2,
    "humidityRh": 61.0,
    "brightnessPct": 67.0,
    "dhtValid": true,
    "lightValid": true
  },
  "soil": {
    "tomato": {"percent": 42.0, "raw": 2410, "valid": true},
    "chili": {"percent": 61.0, "raw": 2110, "valid": true},
    "eggplant": {"percent": 39.0, "raw": 2550, "valid": true}
  },
  "rain": {
    "sensorWet": false,
    "wetConfirmed": false,
    "valid": true
  },
  "reservoir": {
    "airGapInches": 2.44,
    "waterLevelPct": 76.0,
    "valid": true,
    "state": "NORMAL"
  },
  "outputs": {
    "pumpOn": false,
    "tomatoValveOn": false,
    "chiliValveOn": false,
    "eggplantValveOn": false
  }
}
```

The `updatedAt` timestamps in examples are placeholders. In production use real synchronized time or Firebase server timestamps, a monotonic `bootId`, and a receiver-side stale check. **Sensor validity and freshness are independent:** a last valid value can still be stale after device disconnection.

## 11.4 Example weather snapshot

```json
{
  "valid": true,
  "probabilityPct": 80,
  "windowDescription": "Maximum hourly probability in next 3 hours",
  "windowStart": "2026-11-17T15:00:00+08:00",
  "windowEnd": "2026-11-17T18:00:00+08:00",
  "fetchedAt": 1794898800000,
  "validUntil": 1794902400000,
  "provider": "Open-Meteo",
  "latitude": 0.0,
  "longitude": 0.0
}
```

`latitude/longitude=0` in this *example* are placeholders only: use the **real device installation coordinates chosen during pairing**. Never accidentally query weather at 0,0. Before deployment use one timestamp convention (prefer UTC epoch milliseconds for database fields), and generate actual times rather than copying illustrative values.

## 11.5 Example event record and alert types

```json
{
  "type": "RESERVOIR_CRITICAL",
  "severity": "critical",
  "title": "Water reservoir critically low",
  "message": "Level 9%. Irrigation stopped for safety.",
  "createdAt": 1794898800000,
  "bootId": "boot-20261117-01",
  "ownershipEpoch": 1,
  "source": "esp32",
  "crop": null,
  "values": {"waterLevelPct": 9},
  "dedupeKey": "unit123:reservoir:critical:activation-45"
}
```

Event types used consistently everywhere:

```text
DEVICE_READY; DEVICE_OFFLINE; DEVICE_ONLINE; PAIRING_COMPLETE
TEMP_HIGH; TEMP_LOW; HUMIDITY_LOW; HUMIDITY_HIGH
LIGHT_HIGH; LIGHT_LOW; SOIL_LOW_TOMATO; SOIL_LOW_CHILI; SOIL_LOW_EGGPLANT
FORECAST_RAIN_RISK; RAIN_SENSOR_WET; RAIN_SUSPENSION_STARTED
RAIN_SUSPENSION_CLEARED; WEATHER_UNAVAILABLE
RESERVOIR_LOW; RESERVOIR_CRITICAL; RESERVOIR_EMPTY
RESERVOIR_SENSOR_FAULT; SOIL_SENSOR_FAULT; DHT_SENSOR_FAULT; LIGHT_SENSOR_FAULT; RAIN_SENSOR_FAULT
IRRIGATION_STARTED; IRRIGATION_COMPLETED; IRRIGATION_ABORTED
MANUAL_COMMAND_REJECTED; EMERGENCY_STOP; POWER_OR_RELAY_FAULT
```

**Don't label wet-only status as “RAIN_CONFIRMED” if the defined AND trigger is false.** Use `RAIN_SENSOR_WET` for the physical sensor observation and `RAIN_SUSPENSION_STARTED` only for the combined condition.

## 11.6 Data write ownership

| Data path | Authorized writer | Authorized reader |
|---|---|---|
| `devices/id/status`, `telemetry`, `events`, `commandResults` | **Authenticated device identity** | Its members + device |
| `devices/id/weather` | **Trusted backend** | Its members + device |
| `devices/id/config` | Owner (or trusted backend); enforce validated schema | Its members + device |
| `devices/id/commandRequests/id` | Owner/operator via app, immutable after creation | Members + device |
| `devices/id/acl`, `meta.ownerUid` | **Trusted backend only**, after proven ownership/claim | Authorized members + device |
| `users/uid/fcmTokens` | That authenticated user/app instance | That user and trusted backend |
| `pairingClaims`, `notificationOutbox` | **Trusted backend only** | Trusted backend only |

`viewer` can monitor but cannot change irrigation/config or create commands. `operator` can request permitted watering/stop but cannot reassign ownership or change ACL. `owner` controls configuration/sharing. Device must validate every incoming config value regardless of RTDB rules.

## 11.7 Security rules: workable baseline to adapt and test

The following rule layout is a **reference baseline**, not a substitute for Security Rules Emulator testing or type/size schema validation. It assumes the trusted backend uses Admin SDK (which bypasses client RTDB rules), the device signs in with a Firebase Auth ID token containing custom claim `deviceId`, and membership roles live at `/devices/{id}/acl/{uid}`. Device claim issuance is described in Section 12.

```json
{
  "rules": {
    ".read": false,
    ".write": false,
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid && !root.child('deletingAccounts').child($uid).exists()"
      }
    },
    "devices": {
      "$deviceId": {
        ".read": "auth != null && !root.child('deletingAccounts').child(auth.uid).exists() && root.child('devices').child($deviceId).child('meta').child('ownershipState').val() === 'OWNED' && (((auth.token.deviceId === $deviceId && auth.token.ownershipEpoch === root.child('devices').child($deviceId).child('meta').child('ownershipEpoch').val()) || root.child('devices').child($deviceId).child('acl').child(auth.uid).exists()))",
        "meta": {
          ".write": false
        },
        "acl": {
          ".write": false
        },
        "weather": {
          ".write": false
        },
        "status": {
          ".write": "auth != null && !root.child('deletingAccounts').child(auth.uid).exists() && root.child('devices').child($deviceId).child('meta').child('ownershipState').val() === 'OWNED' && ((auth.token.deviceId === $deviceId && auth.token.ownershipEpoch === root.child('devices').child($deviceId).child('meta').child('ownershipEpoch').val()))"
        },
        "telemetry": {
          ".write": "auth != null && !root.child('deletingAccounts').child(auth.uid).exists() && root.child('devices').child($deviceId).child('meta').child('ownershipState').val() === 'OWNED' && ((auth.token.deviceId === $deviceId && auth.token.ownershipEpoch === root.child('devices').child($deviceId).child('meta').child('ownershipEpoch').val()))"
        },
        "config": {
          ".write": "auth != null && !root.child('deletingAccounts').child(auth.uid).exists() && root.child('devices').child($deviceId).child('meta').child('ownershipState').val() === 'OWNED' && (root.child('devices').child($deviceId).child('acl').child(auth.uid).val() === 'owner')"
        },
        "commandRequests": {
          "$commandId": {
            ".write": "auth != null && !root.child('deletingAccounts').child(auth.uid).exists() && root.child('devices').child($deviceId).child('meta').child('ownershipState').val() === 'OWNED' && (!data.exists() && (root.child('devices').child($deviceId).child('acl').child(auth.uid).val() === 'owner' || root.child('devices').child($deviceId).child('acl').child(auth.uid).val() === 'operator') && newData.child('requestedByUid').val() === auth.uid && newData.child('type').isString() && newData.child('createdAt').isNumber() && newData.child('expiresAt').isNumber() && newData.child('expiresAt').val() > now && newData.child('expiresAt').val() <= now + 60000)",
            ".validate": "newData.child('ownershipEpoch').val() === root.child('devices').child($deviceId).child('meta').child('ownershipEpoch').val()"
          }
        },
        "commandResults": {
          "$commandId": {
            ".write": "auth != null && !root.child('deletingAccounts').child(auth.uid).exists() && root.child('devices').child($deviceId).child('meta').child('ownershipState').val() === 'OWNED' && ((auth.token.deviceId === $deviceId && auth.token.ownershipEpoch === root.child('devices').child($deviceId).child('meta').child('ownershipEpoch').val()))"
          }
        },
        "events": {
          "$eventId": {
            ".write": "auth != null && !root.child('deletingAccounts').child(auth.uid).exists() && root.child('devices').child($deviceId).child('meta').child('ownershipState').val() === 'OWNED' && ((auth.token.deviceId === $deviceId && auth.token.ownershipEpoch === root.child('devices').child($deviceId).child('meta').child('ownershipEpoch').val()) && !data.exists())",
            ".validate": "newData.child('ownershipEpoch').val() === root.child('devices').child($deviceId).child('meta').child('ownershipEpoch').val()"
          }
        },
        "history": {
          ".write": false
        }
      }
    },
    "pairingClaims": {
      ".read": false,
      ".write": false
    },
    "notificationOutbox": {
      ".read": false,
      ".write": false
    }
  }
}
```

**Hardening before production:**

- Add strict `.validate` rules for each field: keys, value types/ranges, valid crop IDs, maximum text size, valid array sizes, min/max duration, configVersion, timestamp bounds and event shape.
- Prevent an owner from writing nonsensical `config` (e.g. critical percentage >100, negative pump timeout); validate in both backend/firmware.
- For critical commands consider **backend-issued commands** after server authorization instead of allowing direct member app writes; reject replay and untrusted config even when rules permit writes.
- The `users/$uid` write example allows a user to edit *their own profile/tokens*; move sensitive profile fields to server-managed paths if any are introduced.
- Test that `viewer` cannot write config or commands; other-device identities cannot read/write; an unclaimed user cannot read private telemetry; user cannot edit ACL/owner/weather.
- For the `config` owner write, the backend must still enforce maximum acceptable values and immutable secure fields; rules above are not a complete production schema.
- Rule **inheritance** matters: do not add a broad parent `.write: true` that accidentally overrides lower-level restrictions.

## 11.8 Keep telemetry cheap

Separate `latest` data from history. Realtime subscription is **not** necessarily one Firebase write per sensor reading. Sample fast enough for control, aggregate/send about **every 10–15 seconds**, immediately send meaningful transitions/events, and persist summary history only as needed. Never use unlimited event growth: implement time-based retention/archive in a trusted maintenance job.

---

# 12. Secure device pairing, Wi-Fi provisioning, ownership and sharing

## 12.1 Clarify three different actions

- **Discover:** The phone sees the ESP32 in BLE scan/temporary SoftAP and identifies its *device ID*. Discovery does **not** grant account ownership.
- **Provision Wi-Fi:** The phone securely supplies the home router SSID/password to the nearby ESP32. **The ESP32 stores Wi-Fi credentials locally**, not in Firebase.
- **Claim ownership:** The signed-in account proves physical possession / authorization to a trusted backend. The backend creates the device owner membership; scanning a QR by itself must not silently hijack an already-owned unit.

## 12.2 Proposed first-time flow

1. Assemble hardware, verify relay outputs OFF, flash **provisioning-capable firmware**, assign device ID (`spc-unit-0001`) and a **unique, cryptographically random** factory claim secret.
2. ESP32 starts `SETUP_MODE` only when unprovisioned or via a deliberate owner/factory-reset action; **pump and valves OFF throughout setup**.
3. Android user signs in using Firebase Authentication.
4. User taps `Add Smart Plant Care Device`.
5. Phone scans **nearby BLE advertisement** or ESP32's explicitly secured SoftAP and displays discovered `SmartPlantCare-XXXX`. The phone may need Bluetooth/nearby-device/location permissions depending on Android version and method.
6. User selects the physically present device and scans a QR/sticker or enters a **unique device claim code**. A hardcoded public `123456` is **not secure**.
7. Phone securely provisions **2.4-GHz Wi-Fi** to the ESP32 using a proven secure provisioning library/protocol with proof of possession (PoP); not plain open Wi-Fi credentials transmitted to arbitrary nearby devices.
8. ESP32 joins Wi-Fi. It authenticates as the **device**, not as the owner's personal Firebase account. It publishes a signed/authorized online handshake with device ID and firmware version.
9. App calls trusted backend `claimDevice(deviceId, claimCode, nonce)` with the user's **Firebase Auth identity**; backend validates unclaimed state, physical claim proof, anti-replay, rate limit and timing.
10. Backend conditionally claims only an UNCLAIMED device with current physical proof, sets ownershipState to OWNED and binds the session to the current ownershipEpoch in the same claim transaction. It sets owner and ACL: `devices/deviceId/meta/ownerUid = signedInUid` and `acl/signedInUid = owner`. Returns `CLAIMED`.
11. Phone saves **installation location** for weather by map/current-location-with-consent/manual coordinates, then chooses `INDOOR` or `OUTDOOR` and device nickname.
12. ESP32 receives config/version, runs sensor self-test, reports `READY`; app navigates to live dashboard; local buzzer emits **one pairing-success beep** and LCD displays `DEVICE PAIRED`.

**Avoid automatic pairing with any nearby ESP32**; that allows claiming or confusing another person's unit. A physical claim secret/proof and server-side owner verification are required.

## 12.3 Two provisioning transport choices

**Option A — BLE** (recommended for a modern nearby-phone experience): use Espressif provisioning/protocomm, security/PoP and a matching Android provisioning client. **Option B — WPA2-protected SoftAP** with a unique password and a secure provisioning protocol; the Android app temporarily joins that access point. These methods are alternatives, not simultaneous mandatory hardware requirements.

The exact Espressif API and gradle library version depend on the installed Arduino ESP32 core / Android libraries; pin these versions and confirm the sample provisioning app works **before** integrating into the full UI. Espressif documents secure provisioning handshakes, and its Arduino `WiFiProv` sample covers BLE and SoftAP paths. Use security-enabled options rather than plaintext demo configurations.

**Important physical consideration:** There is **no dedicated pairing button in the original diagram**. For setup/recovery, implement an **owner-authenticated app command when online** and a safe, deliberate factory-reset/recovery process when offline (e.g. temporary service jumper/serial service procedure). If you want a permanent pushbutton, treat it as an *explicit hardware design addition* with a revised GPIO map; do not silently assume it exists.

## 12.4 Secure identity of ESP32 in Firebase

Recommended design: give the ESP32 a **unique device credential**, stored in NVS with appropriate protection, used to authenticate to a trusted token service over TLS. The backend verifies that device credential, then **mints a short-lived Firebase custom sign-in token** with `uid=device:<deviceId>` and claims `deviceId=<id>` and `ownershipEpoch=<current generation>`. Device signs in to Firebase Auth using supported REST flows and uses its Firebase **ID token** for RTDB; it refreshes the session appropriately. The signed custom token is **not** a permanent Admin secret.

- Admin SDK keys must remain **only in trusted backend**.
- Device auth secret must be **different** from the printed claim code used by the human owner.
- Rotate/revoke device credentials after compromise, reset or decommissioning; enforce TLS certificate verification and server-side rate limiting.
- Do not trust `deviceId` sent in unauthenticated JSON as identity.
- Firmware must not write the RTDB root using public rules.

For an academic prototype with constrained backend resources, a Firebase Auth-backed device identity or a tightly scoped backend relay may be simpler to ship—but it must still be individually authenticated and restricted to one device. Do not replace the above with `.read:true`/`.write:true`.

## 12.5 Sharing one ESP32 with friends/teammates

The diagram allows a user to share the same ESP32 with friends. Implement sharing through backend ACL—not by distributing the device's own secret.

1. Owner taps `Share Device` -> enters another registered account email or selects existing account.
2. Trusted backend creates a limited invite and verifies owner authorization.
3. Invitee signs in and accepts invitation.
4. Backend grants `viewer` by default or `operator` if deliberately selected and confirmed by owner.
5. Authorized member receives the same live telemetry and permitted FCM events.
6. Owner can **revoke** access and remove pending invitations. Revocation removes future RTDB access, server FCM routing, and command permissions; client should unsubscribe/clear cached UI.
7. Only owner can transfer ownership or factory reset device claim association; require confirmation/proof.

Never pair every friend directly over BLE as the sole access control mechanism. BLE provisioning sets up the *physical device*, while Firebase ACL provides *cloud account sharing*.

## 12.6 Pairing/reconnect failure scenarios

| Failure | Expected user experience / safety |
|---|---|
| Bluetooth permission denied | Show explain-and-retry; allow SoftAP fallback if implemented; outputs remain OFF |
| Phone not near device | `No nearby device found`; no phantom device added |
| Wrong claim secret | `Cannot verify this device`; rate-limit attempts; do not reveal owner details |
| Wrong 2.4-GHz Wi-Fi password | Setup stays safe; retry credentials; don't leak password in logs |
| Firebase temporarily down | Device can connect Wi-Fi but claim pending; no ownership guess |
| Already claimed by another account | Refuse second claim; offer owner invite/release flow |
| Phone crashes midway | Idempotent claim with nonce; redo setup without duplicate ownership |
| Device power cycles during watering | Physical outputs OFF at boot; do not resume old watering automatically |
| Shared user revoked | RTDB denied and push subscription access removed promptly |

## 12.7 Delete my account and release my ESP32

**Settings → Account → Delete my account.** Show the signed-in email, the names of all devices this account owns, and the consequence for each shared member. Explain that deletion is permanent and that an offline board can finish its physical reset only when it reconnects. Offer Cancel and explicit confirmation. Reauthenticate with the original sign-in provider before submitting.

| Step | Implementation and completion condition |
|---|---|
| Accept once | The backend takes the UID from a verified ID token, checks recent `auth_time` from a freshly refreshed ID token, and creates a durable deletion job keyed by UID. Lock the account against new claims, shares and commands. Retry using the same job after a crash. Profile deletion alone cannot release a board. |
| Release owned devices | For each device, conditionally update the current owner and epoch together. When the owner matches, advance the epoch, clear owner and all ACL entries, and mark RESET_PENDING. Invalidate invitations and unused claim proofs, queued commands, pending deliveries and membership indexes. Scope cleanup to the old epoch so retries cannot erase a later claim. All membership mutations, invite acceptance and sends check the current state. |
| Handle shared devices | Remove the deleting UID from devices owned by someone else and cancel its invitations. A viewer or operator deleting their own account releases only their membership. Other people's accounts, their phones' tokens and their other devices stay active. |
| Finish account deletion | Clear the user's profile, tokens and account-owned cloud data, including personal plant records where stored. Clear the released device's private telemetry, history, events, commands, location, config and sharing records before making it claimable. Delete the Firebase Auth user through the trusted backend and record completion. Retry partial cleanup independently of the signed-out phone. Route direct Auth deletions through the same cleanup worker as a fallback. On Android detach listeners, clear private local records/notifications and sign out. |
| Reset the board | Use a device-authenticated lifecycle endpoint independent of member ACL. While online, subscribe to lifecycle changes or poll on each heartbeat. Treat loss of operating authorization as a stop-and-reconcile signal. Fence old weather jobs, delayed device writes and command callbacks by epoch, including backend Admin writes. At reconnect check this endpoint **before** accepting commands, telemetry uploads or queue replay. On RESET_PENDING stop pump first, close valves, persist setup mode and the new epoch, discard prior commands/events, user settings and Wi-Fi provisioning. Persist the reset marker before the first destructive step so a reboot resumes cleanup with outputs OFF. Preserve the board identity and hardware calibration. |
| Acknowledge and re-pair | Keep the live network session until the authenticated reset acknowledgment is sent, then disconnect. If the link drops after Wi-Fi credentials are erased, allow secure nearby Wi-Fi provisioning for reset acknowledgment only. Backend accepts repeated acknowledgments for the same epoch and opens UNCLAIMED only after board reset and old-data cleanup. A new user completes §12.2 with a fresh, short-lived proof obtained locally from the board. Bind the backend challenge to the device identity, epoch and provisioning session. A previously copied sticker alone cannot reclaim it. Serialize competing claims so only one wins. |

**Offline display.** After cloud revocation show “Account deleted · device reset pending” only when Auth deletion has completed. A powered-off board cannot acknowledge. A running, disconnected board may continue its existing local control policy until it receives the release. Keep the device unavailable for another owner's claim during that interval. If the router has changed, use the physical recovery procedure in §12.3 to reconnect. Local recovery alone never releases an active cloud owner.

**Deployment order.** Backfill existing claimed devices with OWNED and an initial epoch from verified owner records. Provision matching device tokens and update firmware/app clients before enforcing epoch checks. Quarantine conflicting records for reconciliation. Device credentials authorize lifecycle checks during setup. Issue an operating token only for the current epoch of an owned device.


---

# 13. Android Kotlin + Jetpack Compose implementation

## 13.1 What is already in the project vs. what this IoT guide adds

**Existing mobile capstone modules:** Firebase Authentication, AI disease detection (TensorFlow Lite), plant identification and treatment-related app features. Preserve those modules. This IoT implementation adds secure device onboarding, device list/sharing, live monitoring and control screens, weather/irrigation alerts, reservoir visualization and notification/history integration; do not reimplement or move the AI model to the ESP32.

**Recommended app layers:**

```text
ui/                 Jetpack Compose Screens / Components
  auth/
  devices/          MyDevicesScreen, AddDeviceScreen, PairingScreen
  dashboard/        LiveDashboard, CropCards, ReservoirGauge, StatusBanner
  irrigation/       ManualWateringScreen, ZoneSettings
  alerts/           AlertsScreen, AlertDetails
  sharing/          ShareDeviceScreen, MembersScreen
  settings/         DeviceSettings, NotificationSettings

data/
  firebase/         FirebaseAuthRepository, RTDBDeviceRepository, FCMTokenRepository
  remote/           PairingBackendAPI, InstallationWeatherConfiguration
  model/            DeviceTelemetry, DeviceStatus, WeatherSnapshot, IrrigationCommand

domain/
  DevicePermissionPolicy, SensorDisplayFormatter, StaleDataPolicy

service/
  SmartPlantCareFirebaseMessagingService
```

## 13.2 Android setup checklist

- Confirm module `applicationId`, Firebase project and `google-services.json` match the current app.
- Add Google Services plugin and current compatible Firebase **BoM** dependencies for Auth, RTDB, Messaging (use official current dependency declarations; do not hardcode stale versions from an old tutorial).
- Use a ViewModel with Kotlin `StateFlow`/immutable UI state to expose RTDB data to Compose.
- Add `android.permission.INTERNET` and runtime `android.permission.POST_NOTIFICATIONS` on Android 13+; create Android notification channels.
- For BLE provisioning on Android 12+, implement requested Bluetooth Scan/Connect permissions as applicable; for older versions/location or SoftAP, follow current Android API permission requirements.
- Use lifecycle-aware collection of `StateFlow`; detach RTDB listeners on device switch/logout; do not create duplicate listeners every Compose recomposition.
- Guard all user actions by role (`viewer` read only) and current device state; additionally enforce roles in RTDB rules/backend.

## 13.3 Required screens and exact content

| Screen | Key UI elements | Behavior |
|---|---|---|
| My Devices | Device cards, online/offline, role, last update | Choose one authorized device; no global public discovery list |
| Add/Pair Device | BLE/SoftAP discovery, proof code, progress, errors | Never drive pump in setup |
| Installation Location | Map/current phone location/manual coordinates, indoor/outdoor | Saves installation coordinates, not phone tracking |
| Live Dashboard | Temp °C, RH %, brightness index %, 3 soil % cards, reservoir %, forecast %, rain state, active pump/valve, mode and freshness | Continuous RTDB-based changes without manual refresh |
| Irrigation Control | Per-crop start/stop, duration limit, state, rejection reason | Commands with acknowledgments; no direct GPIO control |
| Water Reservoir | Large 0–100% gauge, status, air gap, critical threshold | `--% UNKNOWN` on invalid reading; show 7-inch calibration metadata |
| Weather | Forecast chance/time window, current sensor wet/dry, rain latch | Separate forecast risk from combined rain suspension |
| Events/Alerts | List with type, severity, device, time, crop, readings | Deduplicated by event ID, seen/unseen |
| Device Settings | Crop thresholds, environment limits, indoor/outdoor, location, sync policy | Validated; config version/revision acknowledgment |
| Account | Delete my account, affected devices, confirmation and reauthentication | Show deletion progress and pending offline resets per §12.7 |
| Sharing | Members/invitations/roles/revoke | Owner-only writes through trusted backend |
| Existing AI module | Plant ID/disease classifier, treatments | No change to core AI from this IoT guide |

## 13.4 Live update timings and freshness

- A sensor reading may be sampled every **2.5–5 seconds** without all of those samples being sent to Firebase.
- RTDB telemetry snapshot about **every 10–15 seconds**, plus immediate safety/relay state changes (within available connection latency).
- Each card should show current value, validity badge, **age since last update** (e.g. `Updated 8 s ago`) and an `Offline` state when heartbeat is old.
- [DESIGN DEFAULT] Mark device stale/offline if no heartbeat for **45 seconds**; backend may compute presence from heartbeat timestamp rather than trusting a stale boolean `isOnline:true`.
- When stale, gray last known values and disallow unsafe start requests; do not report a frozen temperature as live.

### Recommended reservoir gauge behavior

- Valid 76% -> filled gauge at 76%, label `76% · Normal`.
- Valid 18% -> amber `18% · Low Water`, notification on transition.
- Valid 8% -> red `8% · Critical — Pump Disabled`.
- Valid 0% -> `0% · Empty — Refill Reservoir`.
- Invalid distance -> **`--% · Sensor Error — Pump Disabled`**, never `100%`.

## 13.5 Minimal Kotlin Firebase listener pattern

This example shows the **listener lifecycle**, not the complete final Android app:

```kotlin
class DeviceRealtimeRepository(private val db: FirebaseDatabase) {
    private var ref: DatabaseReference? = null
    private var listener: ValueEventListener? = null

    fun subscribe(deviceId: String, onSnapshot: (DataSnapshot) -> Unit,
                  onError: (DatabaseError) -> Unit) {
        unsubscribe()
        val r = db.getReference("devices").child(deviceId).child("telemetry")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onSnapshot(snapshot)
            override fun onCancelled(error: DatabaseError) = onError(error)
        }
        ref = r
        listener = l
        r.addValueEventListener(l)
    }

    fun unsubscribe() {
        val r = ref
        val l = listener
        if (r != null && l != null) r.removeEventListener(l)
        ref = null
        listener = null
    }
}
```

In production parse typed domain DTOs with safe missing/null handling, read `status` and `weather` in parallel with appropriate lifecycle, and perform error mapping. Treat database snapshots as **untrusted** until data types/ranges are validated. Device permissions are enforced by RTDB rules, not merely hidden buttons.

## 13.6 Manual command UX

- When `Water Tomato` tapped, show `Sending request…`.
- Wait for server/ESP32 `ACCEPTED` or `REJECTED`; display actual response.
- Only display `Watering tomato` when output status confirms pump and tomato valve active.
- The Stop button remains visible and takes priority.
- Time out/reconnect UI appropriately; a timed-out request must **not** retry with a new ID automatically and trigger duplicate water.
- If user is viewer, display `View only` and remove controls; security rules must still block writes.

---

# 14. Notifications: exact trigger, recipient and delivery flow

## 14.1 End-to-end event lifecycle

```text
ESP32 detects a state TRANSITION (not a routine sensor sample)
  -> update local alert state / LCD / buzzer immediately
  -> append one event using a unique event ID to RTDB when online
  -> trusted backend observes NEW event
  -> backend validates device/event, checks severity + recipient prefs/roles
  -> backend sends FCM to each current authorized account's device tokens
  -> Android service verifies current access and posts locally if permission/network allow
  -> user taps -> app opens device+event detail (dedupe by eventId)
```

If ESP32 is offline, local alarm and safety still operate. Queue a bounded set of durable critical events for synchronization upon reconnection; ensure event IDs stay stable so restored events **do not become duplicate alerts**. **FCM delivery is best-effort** and can be delayed; don't claim guaranteed immediate delivery on offline phones.

## 14.2 Trigger table — what sends a push?

| Event | Required trigger | App live | Push default | ESP32 buzzer |
|---|---|---|---|---|
| TEMP_HIGH | DHT22 temp above configured high limit, stable 30 s | Yes | Once on transition | One beep |
| TEMP_LOW (optional) | Temp below configured low limit, stable | Yes | Once | One beep |
| HUMIDITY_LOW | RH below configured low limit, stable | Yes | Once | One beep |
| HUMIDITY_HIGH (optional) | RH above configured high limit, stable | Yes | Once | One beep |
| LIGHT_HIGH | Calibrated LDR brightness exceeds limit, stable | Yes | Once | One beep |
| LIGHT_LOW | Calibrated LDR brightness below limit, stable | Yes | Once | One beep |
| SOIL_LOW_{CROP} | Valid crop percent below setpoint, stable | Yes | Optional, once | Silent by default |
| FORECAST_RAIN_RISK | Outdoor, fresh next-3-h forecast enters the high-risk band (>=60%), sensor dry, suspension inactive | Yes | **Once**, advisory only | **Silent** |
| RAIN_SENSOR_WET | Sensor wet but rain AND not satisfied | Yes | **No rain-suspension push** | **Silent** |
| RAIN_SUSPENSION_STARTED | Outdoor & fresh forecast >=60% & wet confirmed | Yes | **Once**, urgent | **Two beeps** |
| RAIN_SUSPENSION_CLEARED | Fresh forecast <60% & continuously dry 120 s | Yes | Optional once | Silent |
| RESERVOIR_LOW | Valid reservoir >10% through 20%, stable 10 s | Yes | **Once** | Two beeps |
| RESERVOIR_CRITICAL | Valid reservoir above empty through 10%, blocks pump | Yes | **Once**, urgent | Three beeps |
| RESERVOIR_EMPTY | Valid air gap at/above calibrated 7-in empty line | Yes | **Once**, urgent | Three beeps |
| RESERVOIR_SENSOR_FAULT | No valid distance for fault timeout | Yes (`--%`) | **Once**, urgent | Three beeps |
| IRRIGATION_STARTED / IRRIGATION_COMPLETED | Actual output/status transition | Yes | Optional user preference | Silent |
| IRRIGATION_ABORTED | Irrigation stops early due to safety | Yes | **Once** | Depends on cause |
| WEATHER_UNAVAILABLE | Outdoor required forecast stale beyond TTL | Yes | Once, technical warning | Silent by default |
| DEVICE_OFFLINE | Backend observes missed heartbeats | Yes | Optional (backend, not instantaneous from dead ESP32) | No sound possible while off |
| PAIRING_COMPLETE | Backend verifies claim and device handshake | Yes | Optional, owner | One beep once on the device acknowledgment |
| DEVICE_READY | ESP32 completes self-test | Yes | History only | Optional boot chirp |
| DEVICE_ONLINE | Backend sees a fresh heartbeat after a recorded outage | Yes | Optional recovery | Silent |
| SOIL_SENSOR_FAULT | Qualified invalid reading on one named crop | Yes, crop blocked for auto | Optional technical alert once per affected crop | Silent by default |
| DHT_SENSOR_FAULT / LIGHT_SENSOR_FAULT | Qualified invalid DHT22 or LDR reading | Yes, affected fields unknown | Optional technical alert once per sensor | Silent by default |
| RAIN_SENSOR_FAULT | Diagnosable invalid rain input outdoors | Yes, block unattended outdoor starts | Optional technical alert once | Silent by default |
| MANUAL_COMMAND_REJECTED | ESP32 rejects an authorized request with a reason | Command result for requester | Optional to requester only | Silent |
| EMERGENCY_STOP / POWER_OR_RELAY_FAULT | Emergency stop latched or detectable critical fault | Yes, include reason | Once, urgent | Three beeps if powered |

Each row is a test case. For each enabled transition verify the event ID, recipient, display, sound and rearm behavior. Repeat with an ordinary sample, a retry, muted preferences and a revoked member. For backend-generated OFFLINE/ONLINE and PAIRING_COMPLETE events, use a trusted server writer and one producer per transition. A normal manual STOP is separate from a latched emergency. Without physical feedback, output fields describe relay commands. Report a mechanical or electrical failure only if detected.

Resolve overlapping thresholds once per sample. Invalid distance blocks the pump immediately, while the fault notification uses the qualified timeout. For valid distances select EMPTY, CRITICAL, LOW or NORMAL in that order. If a safety event aborts an active run, keep both history records with a shared incident ID and send the cause as a single push including the affected crop. Other aborts keep their normal push. If a forecast update and confirmed wet input arrive together, issue the suspension alert. A prior dry advisory and a later wet suspension are distinct transitions.

**Alert wording examples**:

- `Rain Risk — 80%` / `Rain chance is 80% in the next 3 hours. Irrigation is NOT yet suspended; sensor is dry.`
- `Automatic Irrigation Suspended` / `Forecast 80% and rain sensor confirmed wet. Pump stopped and valves closed.`
- `Low Reservoir — 18%` / `Refill the 7-inch water reservoir soon.`
- `Critical Water Level — 8%` / `Pump is disabled to prevent dry running.`
- `Distance Sensor Error` / `Water percentage unavailable. Pump disabled until reading is valid.`
- `High Temperature — 36.1°C` / `Measured air temperature exceeded the configured limit.`
- `Low Humidity — 37%` / `Relative humidity is below the configured minimum.`
- `High Brightness — 91%` / `LDR brightness index is above the configured range.`

## 14.3 Dedupe, recovery and notification preference

- Generate one `eventId` for each **NORMAL -> ALERT** activation, include `bootId` + type + activation sequence. Backend should dedupe retries using that ID and the client should ignore an already-displayed ID.
- Send a **recovery event** only after stable recovery hysteresis, not repeatedly.
- Configure `pushEnabled` by category/role/user. **Physical pump safety does not depend on whether notifications are enabled.**
- Don't push 15-second telemetry samples. A live dashboard updates quietly.
- Handle token rotation, permission denial, signout and revoked member access. Remove invalid FCM tokens from server storage.
- Backend can suppress duplicate sends but end-to-end *exactly-once push delivery cannot be guaranteed*; mobile deduplication is still worthwhile.

## 14.4 Trusted Cloud Function / backend pseudo-implementation

```ts
// PSEUDOCODE: adapt to your backend / Cloud Functions version.
// MUST run in a trusted server environment, not inside the Android app or ESP32.
async function onNewDeviceEvent(deviceId: string, eventId: string) {
  const event = await database.read(`devices/${deviceId}/events/${eventId}`);
  if (!event || !allowedEventType(event.type)) return;

  if (!await matchesOwnedEpoch(deviceId, event.ownershipEpoch)) return;
  // Persistent unique deliveries per epoch, event, member and installation.
  await enqueueEligibleDeliveries(deviceId, eventId, event);
}

// Called by a retryable worker. All helpers shown here are server-side.
async function deliver(job) {
  if (!await acquireExpiringLease(job)) return;
  if (!await stillEligible(job)) return cancel(job);
  // Recheck epoch, current member, preference, incident, and token ownership.
  // Send only opaque references. Android fetches authorized event details.
  const result = await sendDataMessage(job.token, {
    deviceId: job.deviceId, eventId: job.eventId,
    ownershipEpoch: String(job.ownershipEpoch)
  }, deliveryOptionsForSeverityAndExpiry(job));
  await recordPerTokenOutcome(job, result); // retry transient errors only
}
```

Use Admin SDK server auth, validate event data and rate limits, never expose message-sending authority in an app or firmware. A job reserved before a crash needs retry/lease recovery, not a permanently stuck "already sent" flag. The backend also needs a heartbeat evaluator for offline notices (a powered-off ESP32 cannot notify the server itself).

## 14.5 Android notification requirement

Use data-only FCM for this handler. In foreground or background, FirebaseMessagingService checks the signed-in UID and fetches the event with current server authorization before posting one local notification keyed by device, epoch and event. Use bounded background work if a fetch exceeds callback time. Drop denied or expired alerts. Recheck access on tap. If offline, defer the check until online within the alert lifetime. Replayed alerts carry their occurrence time and show as history if already resolved or stale. Reserve high-priority Android delivery for time-sensitive user-visible alerts. Renew installation tokens on rotation and detach the previous UID when a phone changes accounts.

On Android 13+ request runtime `POST_NOTIFICATIONS`. Create distinct notification channels such as `Critical irrigation`, `Environment`, and `Device status`; deep-link to event detail; show appropriate status when notification permission is disabled. FCM registration token is **per app installation**, must be refreshed and bound to the signed-in user, and should be deleted/updated on logout.

---
# 15. ESP32 firmware: architecture, scheduling, and reference code

## 15.1 Target toolchain and partition planning

- Development machine from prior setup: Windows, Arduino IDE / Android Studio.
- Select board compatible with your **actual ESP32-WROOM Dev Module**, correct USB driver (`CP210x` / `CH340` depends on board), correct COM port, and stable compatible Arduino ESP32 board-core version.
- The prior Arduino IDE is 1.8.19; using it is possible with compatible board/core libraries, but pin an actually tested core/library combination. If using newer core facilities, follow its official IDE/core install documentation.
- Wi-Fi provisioning libraries, Firebase/TLS stacks and JSON parsers consume substantial flash/RAM. Check sketch size and **partition scheme**. Do not select OTA partitioning unless the firmware size and available flash truly support it.
- Use USB data-capable cable (not charge-only); verify the unconnected ESP32 *before* wiring relays, pump and sensors. A previously damaged/dead board must not be used in the live pump system.

## 15.2 Recommended firmware source tree

```text
firmware/
  SmartPlantCare.ino         # setup(), nonblocking loop() or task bootstrap
  config/
    pins.h                  # final GPIO map
    defaults.h              # verified configurable defaults
    secrets_example.h       # placeholder schema only, no credentials
  core/
    Scheduler.h/.cpp        # millis cadence
    StateMachine.h/.cpp     # irrigation states
    SafetyManager.h/.cpp    # hard stop and fault precedence
    EventManager.h/.cpp     # state transition events + dedupe
    RelayController.h/.cpp  # active LOW/HIGH handling + safe start
    BuzzerManager.h/.cpp    # nonblocking beeps
    LcdManager.h/.cpp       # pages/priority overlays
  sensors/
    SoilSensors.h/.cpp
    DhtSensor.h/.cpp
    LightSensor.h/.cpp
    RainSensor.h/.cpp
    ReservoirSensor.h/.cpp
    Calibration.h/.cpp
  net/
    WiFiManager.h/.cpp
    FirebaseDeviceAuth.h/.cpp
    FirebaseTransport.h/.cpp
    DeviceProvisioning.h/.cpp
    WeatherSnapshot.h/.cpp
  model/
    DeviceState.h
    DeviceConfig.h
    DeviceTelemetry.h
    DeviceEvent.h
    DeviceCommand.h
  tests/
    logic_truth_tables.md
    calibration_records.csv
```

These are proposed module names, not files that already exist in your project. Each function should have **one responsibility** so a network bug cannot stop the emergency control path.

## 15.3 Task cadence, chosen to keep ESP32 responsive

| Task | Recommended starting cadence | Processing budget/notes |
|---|---:|---|
| Emergency-stop check / actuator safety | Every 20–50 ms | Highest priority, no network calls |
| Rain DO sampling | 100–200 ms | Keep wet/dry duration counters; no push here |
| Irrigation state machine | 50–100 ms | No long blocking waits |
| DHT22 read | 2.5–3 s | DHT22 not designed for very fast sampling |
| 3 soil ADCs | 3 s | Calibrated median/range checks |
| LDR ADC | 5 s | Relative brightness index |
| Ultrasonic air gap | 2–3 s | Time-bounded echo; median filter |
| **I2C LCD2004 update** | ~750 ms | Use two GPIO signal pins (21/22); rotate page every ~3 s; avoid `lcd.clear()` each frame and avoid blocking repeated I2C bus recovery |
| Buzzer update | 10–25 ms | State machine; short pulses only |
| RTDB telemetry upload | Every 10–15 s | Skip unchanged/minor data if appropriate |
| Immediate event/status publish | On new event | Enqueue; never block pump shutdown |
| Backend weather fetch | 30 min | ESP32 receives compact snapshot via RTDB |
| Wi-Fi/Firebase retry | Exponential backoff | No reboot loops; no blocking endless retry |
| Memory/uptime diagnostics | 60 s | Logging; add watchdog/heap monitoring |

**`delay(5000)` inside an irrigation loop is unsafe** because it delays detecting an emergency. `millis()` schedules using unsigned subtraction and short per-task service methods are the first choice. If Firebase/TLS calls can block for seconds, move network work to **one isolated network task/queue**, keep local control responsive, and do not create one FreeRTOS task per sensor. If your selected Firebase library is asynchronous and bounded, the simpler loop may suffice.

## 15.4 Pin header skeleton

```cpp
// config/pins.h -- WROOM-32 / WROOM-32E ONLY; confirm exposed pins.
#pragma once
constexpr int PIN_LCD_SDA = 21; // I2C LCD2004 SDA, 3.3-V safe bus
constexpr int PIN_LCD_SCL = 22; // I2C LCD2004 SCL, 3.3-V safe bus
// Old parallel LCD RS/E/D4/D5/D6/D7 definitions REMOVED.

constexpr int PIN_TOMATO_SOIL   = 34;
constexpr int PIN_CHILI_SOIL    = 35;
constexpr int PIN_EGGPLANT_SOIL = 33;
constexpr int PIN_LDR          = 36;
constexpr int PIN_RAIN_DO      = 32; // USER-CONFIRMED; freed by I2C LCD
constexpr int PIN_DHT          = 4;
constexpr int PIN_US_TRIG      = 16;
constexpr int PIN_US_ECHO      = 17; // only 3.3-V-safe signal
constexpr int PIN_BUZZER       = 18;

constexpr int PIN_RELAY_TOMATO = 25;
constexpr int PIN_RELAY_CHILI  = 26;
constexpr int PIN_RELAY_EGGPLANT = 27;
constexpr int PIN_RELAY_PUMP   = 23;
```

## 15.5 Simple safe relay test sketch (standalone, bench stage)

The following is a **manual relay logic safety test**, not the final Firebase firmware. **All pump/valve load wires must be DISCONNECTED** for this step. This sketch assumes the board was measured to be **active-LOW**; change the levels only after physical verification.

```cpp
#include <Arduino.h>

const int relayPins[4] = {25, 26, 27, 23};
const int RELAY_OFF = HIGH;  // ONLY for a verified active-LOW board
const int RELAY_ON  = LOW;

void allRelaysOff() {
  for (int pin : relayPins) digitalWrite(pin, RELAY_OFF);
}

void setup() {
  Serial.begin(115200);
  // Preload inactive latch state before changing each pin to OUTPUT.
  for (int pin : relayPins) {
    digitalWrite(pin, RELAY_OFF);
    pinMode(pin, OUTPUT);
  }
  allRelaysOff();
  Serial.println("SAFE: ALL RELAY INPUTS INACTIVE");
}

void loop() {
  // No relay energizing in this test. Inspect each channel's physical NO/COM
  // continuity and confirm NO contact is OPEN at power-on, reset and shutdown.
  allRelaysOff();
  delay(1000); // acceptable ONLY in this isolated no-actuator bench test
}
```

**This sketch alone does not guarantee glitch-free boot!** Hardware pull-ups/driver choice and relay power sequencing must keep NO contacts open **before firmware runs**. Also test while USB is unplugged and power source reconnects. Only after passing, test one 5-V load in a controlled separate setup with proper fuse and flyback suppression.

## 15.6 Reservoir formula — Arduino helper

```cpp
#include <math.h>

float reservoirPct(float gapIn, float fullGapIn, float emptyGapIn) {
  if (!isfinite(gapIn) || !isfinite(fullGapIn) ||
      !isfinite(emptyGapIn) || emptyGapIn <= fullGapIn) {
    return NAN; // Signal invalid: DO NOT run pump
  }
  const float pct = 100.0f * (emptyGapIn - gapIn) /
                    (emptyGapIn - fullGapIn);
  return constrain(pct, 0.0f, 100.0f);
}

// Use only after verified measurement and ultrasonic validity checks:
// float level = reservoirPct(gap, measuredFullGap, measuredEmptyGap);
// if (isnan(level)) { emergencyPumpStop("RESERVOIR_SENSOR_FAULT"); }
```

Do not convert `pulseIn` timeout (`0`) to a valid gap of 0 inches. Use a short time-bounded echo measurement plus validity filtering (e.g. never let a **0-echo timeout** become `100% water`). Ultrasonic modules have minimum-range/dead-zone limitations.

## 15.7 Rain AND logic — independent of cloud notifications

```cpp
struct WeatherCache {
  bool valid = false;
  bool fresh = false;
  int probabilityPct = -1;
};

bool outdoorMode = true;
bool wetConfirmed = false;
bool dryRecovered = false;
bool rainLatched = false;
WeatherCache weather;

void updateRainGate() {
  const bool validHighForecast =
      weather.valid && weather.fresh && weather.probabilityPct >= 60;

  if (outdoorMode && validHighForecast && wetConfirmed && !rainLatched) {
    rainLatched = true;
    // Synchronous local hardware stop FIRST; enqueue event/beeps afterward.
    stopPumpAndValvesSafely();
    queueEvent("RAIN_SUSPENSION_STARTED");
    buzzerQueueOnce("CONFIRMED_RAIN");
  }

  if (rainLatched && dryRecovered && weather.valid && weather.fresh &&
      weather.probabilityPct < 60) {
    rainLatched = false;
    queueEvent("RAIN_SUSPENSION_CLEARED");
  }
}
```

`stopPumpAndValvesSafely`, `queueEvent`, and `buzzerQueueOnce` are **application-defined interfaces** to write. This illustrates the *exact Boolean logic*, not a complete compiling program. `wetConfirmed` must be computed from continuous wet duration, not from one digital sample. When the owner explicitly selects indoor mode, coordinate and log the separate mode transition; don't randomly clear the outdoor latch inside this code.

## 15.8 Nonblocking skeleton for the main control loop

```cpp
uint32_t lastRain=0, lastControl=0, lastSoil=0, lastDht=0;
uint32_t lastLdr=0, lastReservoir=0, lastLCD=0, lastTelemetry=0;

void loop() {
  const uint32_t now = millis();

  // FIRST: never let optional communications delay physical safety.
  serviceEmergencyStopAndRelaySafety(now);

  if (uint32_t(now - lastRain) >= 200) {
    lastRain = now; sampleAndDebounceRain(now);
  }
  if (uint32_t(now - lastControl) >= 50) {
    lastControl = now;
    updateRainGate();
    advanceIrrigationStateMachine(now);
  }
  if (uint32_t(now - lastSoil) >= 3000) {
    lastSoil = now; sampleAllThreeSoilSensors();
  }
  if (uint32_t(now - lastDht) >= 2500) {
    lastDht = now; sampleDHT22();
  }
  if (uint32_t(now - lastLdr) >= 5000) {
    lastLdr = now; sampleLDR();
  }
  if (uint32_t(now - lastReservoir) >= 3000) {
    lastReservoir = now; sampleDistanceAndValidate();
  }
  if (uint32_t(now - lastLCD) >= 750) {
    lastLCD = now; updateLCDNonBlocking();
  }

  serviceBuzzerNonBlocking(now);
  serviceLocalAlertTransitions(now);

  if (uint32_t(now - lastTelemetry) >= 15000) {
    lastTelemetry = now;
    queueCompactTelemetrySnapshot(); // network transmission elsewhere
  }
  serviceNonBlockingNetworkQueue(); // MUST be nonblocking/bounded
  delay(1); // permit other core/RTOS background work
}
```

`uint32_t(now-last)` handles normal `millis()` wrap-around as long as used consistently and intervals are bounded. Every undefined function is a **named module to implement**, not a hidden claim that the above is a ready-to-upload complete firmware.

## 15.9 Sensor state model and faults

Use typed struct-like state:

```text
Measurement = {
  rawValue, engineeringValue, valid,
  measuredAtUptimeMs, source, calibrationVersion,
  faultCode
}
```

Error examples: ADC pin stuck high/low for abnormal duration, DHT NaN, ultrasonic no echo, rain comparator floating, weather stale, relay status unexpected. The output `valid=false` means **stop relying on the reading**, not `value=0`. For reservoir fault, pump blocked; for a single crop soil fault, only that crop blocked. Record explicit `faultCode` and recovery time.

## 15.10 RAM/CPU optimization and safe networking

- Run mobile AI **only on phone**, not ESP32.
- Avoid huge `String` concatenation/JSON buffers and unnecessary dynamic allocation in the recurring loop; preallocate small buffers/structures where practical.
- For Firebase writes, send one compact latest snapshot rather than separate writes per ADC sample.
- Download only needed config/weather/commands; do not subscribe to `/devices` root.
- Give network operations timeouts and **retry backoff**, but never delay a local pump safety stop while waiting for TLS.
- `ESP.getFreeHeap()` and `ESP.getMinFreeHeap()` (when supported) help spot degradation. Log at ~60-s intervals during testing.
- Watch for brownout resets when pump or valve switches; a reboot at pump-start usually points to **power/inrush/noise** rather than CPU overload.
- If networking library is blocking, run it via a bounded background task and queue messages from the safety loop. Don't put relays under Firebase callback ownership.
- Add heap, uptime, reset reason, Wi-Fi RSSI, last successful sync, loop latency/max cycle time, queue depth, and fault counters to diagnostics.
- **No continuous busy `while()` loop** waiting for network, weather, ultrasonic, or relay operations.
- Confirm buzzer/LCD responsiveness while Wi-Fi and Firebase are intentionally disconnected.

**Full-feature build check.** Use one latest-telemetry slot, a bounded queue for events and a separate priority path for stops. Replace superseded telemetry while offline. Reserve queue space for critical transitions, count overflows and expose a missed-history indicator if the bounded queue fills. Write NVS on configuration, durable critical transitions or reset checkpoints. Keep routine samples in RAM. Run FCM recipient selection and account deletion on the backend. Prefer backend weather retrieval, with the existing §7.4 fallback subject to the same memory and timing tests.

Measure the same firmware with every feature enabled during watering, TLS reconnect, event backlog drain and provisioning. Record binary size versus the selected partition, minimum free heap, queue high-water mark and worst interval between safety checks. Pass when the binary fits, memory stays stable across repeated reconnects, the §15.3 safety interval is met and all existing section 18 behaviors still pass. Tune buffers, scheduling or payload size if a check fails.

## 15.11 Firmware event deduplication and command replay

- Every new event gets `deviceId:bootId:sequence` or other stable unique ID.
- Maintain a local bounded unsent-event queue (persistent for critical events if feasible) and drop/coalesce noncritical noise.
- `processedCommandIds` cache or backend status prevents duplicate watering from duplicate snapshots/retry.
- A command without valid `expiresAt` is rejected, not executed blindly.
- After reboot, do **not** restart a partly completed pump run from a previous command. Mark it `ABORTED_ON_REBOOT` after establishing connectivity.
- On Wi-Fi reconnection publish true current relay status **before** reporting queued events; the app's dashboard should immediately reflect reality.

---

# 16. How to build the entire system: phase-by-phase workflow

These phases must pass **in sequence**. No phase requiring water or relay current should begin while basic power/sensor validation fails.

## Phase 0 — freeze hardware and capture evidence

- [ ] Photograph and record ESP32 front/back and 30-pin/38-pin silkscreen; verify **GPIO21/SDA, GPIO22/SCL, GPIO32 rain, GPIO36 LDR and GPIO18 buzzer** are exposed and correctly identified.
- [ ] Verify which soil sensors provide AO and whether AO at 3.3 V fits ESP32 ADC.
- [ ] Verify rain sensor power voltage and DO wet polarity.
- [ ] Verify distance sensor supply and ECHO high level.
- [ ] Verify relay 5-V coil supply, input trigger logic, contact rating and NO/COM labeling.
- [ ] Verify pump/valve label current/rated voltage (all nominally 5 V).
- [ ] Measure reservoir empty and safe-full sensor-face air gaps.
- [ ] Photograph and label every wire; tag each crop tubing branch.
- [ ] Record exact tool and library versions in Section 20 worksheet.

**Pass condition:** final pin map and power-path drawing are signed off; no 5-V input into any ESP32 GPIO; master-switch/fuse/load harness not improvised.

## Phase 1 — bare ESP32 health and USB

1. Disconnect **everything** from board.
2. Connect to PC using a verified USB **data** cable.
3. Confirm port enumeration and a simple **Blink/Serial 115200** sketch.
4. If no power LED / no Serial, stop and diagnose board regulator, USB cable, USB-UART, boot mode before connecting other hardware.
5. If COM detected but voltage irregular, measure board 5 V and 3.3 V rails with a proper multimeter without shorting adjacent pins.

**Pass:** repeatable upload/reset and stable serial output over multiple power cycles.

## Phase 2 — all sensors, individually

1. Wire **one sensor at a time** and print raw + converted values + validity.
2. DHT22: check stable temperature/RH, disconnect test, NaN handling.
3. LDR: cover/expose and verify direction/relative percentage.
4. Tomato, chili, eggplant: record dry/wet raw values and verify mappings.
5. Rain: measure wet/dry levels, 30-s wet filter, 120-s dry recovery, disconnected comparator behavior.
6. Ultrasonic: test at measured full/mid/empty points, check ECHO voltage, min range, no-echo invalid case.

**Pass:** sensor cards can safely show `valid=false`; no phantom 100% reservoir on no echo.

## Phase 3 — I2C LCD2004 and buzzer

1. **Do not attach the old parallel LCD wires.** Install the new LCD2004 backpack: SDA to GPIO21, SCL to GPIO22, GND to common GND and VCC to its rated supply. If the backpack uses 5-V SDA/SCL pull-ups, install a **bidirectional 3.3 V ↔ 5 V I2C level shifter** first.
2. With the LCD and shifter powered, **measure the ESP32 side SDA/SCL HIGH levels**: they must be 3.3-V safe. Do not proceed if pulled to 5 V.
3. Run `I2CexpDiag` or I2C scanner; record actual address, then run the independent `hd44780_I2Cexp` LCD test in Section 3.2. Adjust backpack contrast if necessary; verify all 20 columns × 4 rows.
4. Cycle all normal 20×4 pages; verify clearing/padding and no screen flicker. Verify LCD text is unchanged by moving from parallel wiring to I2C.
5. Confirm a simultaneous rain DO signal on **GPIO32** does not interfere with LCD I2C traffic. Confirm the **LDR on GPIO36** still changes brightness readings as light is covered/exposed.
6. Connect buzzer driver on **GPIO18** and play 1/2/3-beep patterns in controlled test mode.
7. Test priority: critical reservoir overlay interrupts normal pages and buzzer event sequence, without blocking sensor reads.

**Pass:** I2C LCD, rain GPIO32, LDR GPIO36 and buzzer GPIO18 work together with Wi-Fi connected **and** disconnected, without incorrect GPIO reuse or unsafe voltage.

## Phase 4 — relay board, with loads disconnected

1. Confirm NC/COM/NO with printed markings or continuity meter; COM is **not the same as relay IN/VCC/GND**.
2. Measure active-low/active-high without pumps/valves attached.
3. Boot, reset, USB unplug/replug; all NO paths must be open by default.
4. Turn on each relay one at a time for 1 s **using a temporary supervised test sketch**, then OFF.
5. Verify command channel matches physical relay: IN1 tomato, IN2 chili, IN3 eggplant, IN4 pump.

**Pass:** no unexpected relay activation. **If a channel energizes briefly during boot, do not connect a pump until hardware safe-state design is corrected.**

## Phase 5 — power and hydraulics (supervised only)

1. Prepare regulated 5-V supply, measured fuse/wiring, DC master switch and terminal distribution.
2. Add valve/pump transient suppression with correct polarity.
3. Test *one valve only*, then *pump only* using safely isolated 5-V power and measured current.
4. Build reservoir -> pump -> manifold -> valves -> separate pots routing.
5. Measure real output mL/s for each branch, at representative reservoir level and actual tubing lengths.
6. Test normal sequence: valve ON -> short wait -> pump ON -> pump OFF -> valve OFF.
7. Test master switch OFF during pumping; verify water flow stops.
8. Check leaks, hot wires, undervoltage and ESP32 resets.

**Pass:** no electrical overheating, relay chatter, dry running, backfeed, plumbing leaks or unsafe pressure.

## Phase 6 — local control with zero internet dependencies

1. Add calibration config and validity filters.
2. Implement reservoir low/critical gates.
3. Implement one-zone irrigation state machine and hard runtime limit.
4. Add rain wet/dry filtering and a simulated valid forecast input for offline truth-table testing.
5. Add indoor/outdoor mode and weather-fault gate.
6. Add alarm state transitions, LCD priority overlay and event queue.
7. Test physically disconnecting each sensor during watering.

**Pass:** all physical safety and rain/irrigation tests pass with Wi-Fi disabled.

## Phase 7 — Firebase Authentication, RTDB and rules

1. Enable Firebase services described in Section 11.
2. Create RTDB schema and role-based rules; use Firebase Emulator Suite where practical.
3. Test user UID read/write permission; viewer cannot command, other user cannot read device.
4. Build device identity/auth handshake, *no Admin credential on the ESP32*.
5. Publish 15-s snapshots, immediate local fault events, and reconnect updates.
6. Confirm server timestamps and stale/online behavior.

**Pass:** ESP32 has no public database-wide read/write; no API key mistakenly treated as Admin secret.

## Phase 8 — secure pairing, location and sharing

1. Test ESP32 discovery using selected BLE or SoftAP transport.
2. Provision Wi-Fi securely (2.4-GHz SSID); verify reboot persistence.
3. Claim the unit using physical proof and backend; show `owner` role.
4. Set installation coordinates and indoor/outdoor.
5. Invite a second test user as `viewer`: watch dashboard, reject control.
6. Upgrade to `operator`: test allowed start/stop via validated commands.
7. Revoke role: listener/commands/push access denied.
8. Test wrong code, already-claimed device, offline onboarding and factory-reset recovery.

**Pass:** an unrelated signed-in user cannot claim/control/read the device.

## Phase 9 — Android live dashboard, controls, alerts

1. Add RTDB repository, ViewModels, state conversion and Compose cards.
2. Populate temp/RH/LDR, three soil cards, reservoir gauge, rain wet state, forecast probability/time window, pump/valve status and updatedAt.
3. Implement command acknowledgment and duplicate-safe STOP.
4. Add environmental status transition displays and history.
5. Register device-scoped FCM tokens for current signed-in account.
6. Send controlled test push, background app, close app, lock phone, change network.
7. Confirm permission-denied UI, stale status and no push spam.

**Pass:** Android shows real device reading changes and notifications from events, not simulated UI values.

## Phase 10 — weather backend + full integration

1. Save installation coordinates and outdoor mode.
2. Fetch Open-Meteo on schedule, extract **max of next 3 hourly probabilities**, write compact fresh snapshot.
3. Confirm forecast-only warning, wet-only status, both-confirmed automatic suspension and latched recovery.
4. Simulate stale API -> fault not rain; indoor mode ignores weather rain gate.
5. Test water level going from full to low to critical to empty; verify gauge percentages.
6. Test camera/AI app flow still works and does not corrupt IoT listener/session.
7. Record final demo video and collected calibration readings.

**Pass:** every expected result in Section 18 is observed and documented, not merely programmed.

---
# 17. Offline, reset, and fault-management contract

## 17.1 Device safety during network loss

| What fails | Local controller action | Phone behavior |
|---|---|---|
| Phone app closed | Continue local measurement/safety/auto logic | Push still possible through FCM when phone network/permission allow |
| Phone temporarily offline | Local device unchanged | Show last value with timestamp, sync on reconnect; push may delay |
| Wi-Fi lost at ESP32 | **Pump/valve safety logic continues locally**; no endless Wi-Fi reconnection loop | Values become stale; backend may notify offline after heartbeat timeout |
| RTDB unavailable but Wi-Fi still connected | Same as above; queue bounded events; don't execute expired remote commands later | Show `Sync error`; don't falsely report live data |
| Open-Meteo API unavailable/stale | Weather validity false; **don't interpret as 0% rain**. Outdoor default block *new automatic* starts | `Weather unavailable` technical status, not `Rain confirmed` |
| Rain DO invalid/disconnected outdoors | **[DESIGN DEFAULT] block new unattended outdoor starts as a technical sensor fault**, not a combined rain event; manual only after deliberate review | `Rain sensor fault`; no false rain-confirmed push |
| Reservoir measurement invalid | **Pump OFF** and critical sensor fault | Reservoir `--% UNKNOWN`; urgent alert |
| One soil sensor invalid | Block only that crop's automatic cycle | `Tomato/Chili/Eggplant soil sensor fault`; other zones may continue |
| DHT22 invalid | Stop deriving temp/RH warnings from last stale data; physical pump may continue if other gates safe | Display `--` and DHT fault |
| LDR invalid | No light warning based on guessed percent | `Brightness unavailable` |
| MCU reboot during watering | **Physical loads OFF at reset/boot**; no automatic command replay | Last operation recorded aborted when device reconnects |
| Power bank dies/master switch OFF | Solenoid and pump physically powerless through NO contacts | App eventually detects offline; no immediate ESP32-origin push possible |
| Wrong Firebase permission rules | Fail closed; log/indicate `ACCESS_DENIED`; no open public fallback | Clear error and instructions to admin |

## 17.2 Recovery behavior

- If a critical reservoir fault occurred, keep it **latched** until sensor readings are stably valid and water rises above the configured recovery threshold.
- A rain latch clears only with fresh forecast below 60% **AND** continuous confirmed dry period; changing only one input is insufficient.
- A manual STOP does not clear unrelated critical faults.
- After reboot, load persistent configuration but **do not restore prior `pumpOn=true` or active valve state**. Always begin in a safe state and re-evaluate from new samples.
- After reconnect, publish `bootId`, `lastHeartbeatAt`, firmware version and current outputs before flushing old events. Do not replay expired start commands.
- If time has not synchronized and forecast TTL/command expirations cannot be evaluated, treat them as **unverified/stale** until valid time is available.

## 17.3 Power-resilience special checks

An ESP32 can appear “overloaded” when it is actually resetting due to a 5-V power drop. Symptoms: boot banner repeating, Wi-Fi disconnect on pump start, backlight flicker, spontaneous relay changes, or brownout messages. Investigate bank current limit, cable voltage drop, motor transient suppression, ground layout and startup/inrush before changing firmware cores or buying an expensive MCU. Measure under *actual load*, not only on an unloaded breadboard.

---

# 18. Full verification / acceptance-test matrix

**Instructions:** Test with controlled simulated inputs first, and actual water/relays only after supervised electrical checks. For every case record `PASS/FAIL`, date/time, tester, actual sensor values, outputs, LCD screen, buzzer, RTDB event ID, Android screenshot, and observed push behavior. Document final settings used.

## 18.1 Rain + weather truth-table tests

| Test ID | Setup / simulated input | Expected outputs and alerts |
|---|---|---|
| R01 | OUTDOOR; API 59%; rain DRY | No rain suspension; normal irrigation eligibility |
| R02 | OUTDOOR; API **60%**; rain DRY | Forecast advisory once, **no buzzer**, no new rain suspension |
| R03 | OUTDOOR; API 80%; rain WET for only 10 s | Wet unconfirmed, no suspension yet |
| R04 | OUTDOOR; API 80%; rain continuously WET >=30 s | **Suspend AUTO**, pump OFF, valve OFF, `RAIN_SUSPENSION_STARTED`, **2 beeps**, one push |
| R05 | OUTDOOR; API 30%; WET confirmed | No **new** rain suspension, live `WET_CONFIRMED`, silent |
| R06 | OUTDOOR; API 100%; DRY | Forecast advisory only, no suspension |
| R07 | OUTDOOR; API stale; WET | `WEATHER_UNAVAILABLE`, not rain; apply separate fail-safe policy |
| R08 | INDOOR; API 100%; WET | No outdoor rain suspension; monitor sensors normally |
| R09 | Rain latch ACTIVE; API falls to 40% but still wet | **Remain suspended** |
| R10 | Rain latch ACTIVE; API 40%; sensor DRY 119 s | Remain suspended |
| R11 | Rain latch ACTIVE; API 40%; sensor DRY >=120 s | Clear latch; one recovery event; reconsider eligible watering only |
| R12 | Rain latch ACTIVE; API 90%; sensor DRY >=120 s | **Remain suspended** until forecast below threshold |
| R13 | Rain sensor briefly splashed while API 80% | No suspended event before stable wet confirmation |
| R14 | API value invalid/null instead of probability | No false 0% and no false confirmed rain; weather fault |
| R15 | Rain sensor wire disconnected / floating | Technical sensor fault; fail-safe default blocks unattended outdoor starts |
| R16 | Device disconnects during R04 watering | Outputs locally stop safely; event sync upon reconnect |

## 18.2 Reservoir (nominal 7-inch) tests

Use *actual installed* `D_FULL_IN`, `D_EMPTY_IN` for the final acceptance run; the percent figures here use an **example** where `D_FULL_IN=1`, `D_EMPTY_IN=7`.

| Test ID | Air gap / condition | Expected reservoir percent and safety |
|---|---|---|
| W01 | Gap 1.0 in | `100% FULL`, if sensor is within valid range |
| W02 | Gap 2.5 in | `75%` |
| W03 | Gap 4.0 in | `50%` |
| W04 | Gap 5.5 in | `25%` |
| W05 | Gap 5.8 in | `20%`; low-water warning on stable crossing |
| W06 | Gap 6.4 in | `10%`; critical cutoff, pump OFF |
| W07 | Gap 7.0 in | `0% EMPTY`, pump OFF; urgent event |
| W08 | Gap 7.4 in (beyond empty mark) | Clamp `0%`, alert only once per state activation |
| W09 | No echo / timeout 0 | `--% UNKNOWN`, pump OFF, sensor fault; **not 100%** |
| W10 | Bouncing invalid/valid readings | Debounced status, no rapid pump cycling or push spam |
| W11 | Refill from 8% to 30% stable | Critical lockout clears only under configured higher recovery condition |
| W12 | Physically mount sensor higher than rim | Re-measure `D_EMPTY_IN`; 7-inch tank height alone may not equal air gap |

**Test W09 is mandatory:** do not allow a `pulseIn()` timeout converted to 0 inches to produce a fake “100%” full water gauge.

## 18.3 Environmental sensors and notifications

| ID | Stimulus | Expected |
|---|---|---|
| E01 | DHT temp crosses high threshold for 3 s only | Live preview shows value, no confirmed alarm yet |
| E02 | DHT temp remains above configured high for 30 s | `TEMP_HIGH`, 1 beep, single push, alert appears in LCD/event history |
| E03 | DHT temp remains high for 10 min | Dashboard stays high, **no push every telemetry upload** |
| E04 | Temperature drops below rearm threshold stably, then rises again | Previous alert clears; a new high transition can notify again |
| E05 | DHT RH below low threshold, stable | `HUMIDITY_LOW`, 1 beep, single push |
| E06 | LDR above configured high brightness stably | `LIGHT_HIGH`, 1 beep, single push; value labeled relative index |
| E07 | LDR below configured low brightness stably | `LIGHT_LOW`, 1 beep, single push |
| E08 | DHT returns NaN/disconnected | `DHT_SENSOR_FAULT`; not 0 °C/0% or false high/low |
| E09 | LDR disconnects/stuck extreme | Fault/invalid after qualification; no invented brightness |
| E10 | App closed, Android notification permission granted | Push can arrive and opens correct device/event |
| E11 | Android notification permission denied | No misleading claim push displayed; in-app history still available |

## 18.4 Irrigation, relay, safety, and app commands

| ID | Scenario | Expected |
|---|---|---|
| I01 | Tomato soil dry, other zones okay, reservoir good | Only tomato valve opens, then pump; other valves off |
| I02 | Chili dry while tomato watering | Chili waits in queue; **no simultaneous valve** |
| I03 | Eggplant soil invalid | Eggplant auto blocked; other valid zones unaffected unless global gate |
| I04 | Tomato reaches stop moisture threshold / safe dose | Pump OFF, then tomato valve closes; completion recorded |
| I05 | Rain AND becomes true mid-watering | Pump OFF and active valve closes; suspended alert |
| I06 | Reservoir crosses critical while pumping | Pump immediately OFF; valve closes; critical event |
| I07 | Ultrasonic loses echo while pump running | Fail-safe stop; `--%` and sensor-fault alert |
| I08 | Runtime limit exceeded | Pump OFF, lockout/fault logged |
| I09 | App creates manual command twice with same ID | At most one action; one command result |
| I10 | Start command expires while ESP32 offline | Rejected on reconnect; no unexpected delayed watering |
| I11 | STOP arrives while watering | Actuators stop safely, highest priority |
| I12 | Viewer account requests water | Denied by server rules/backend, not just hidden button |
| I13 | ESP32 reset while pump on | Outputs physically go OFF, do not resume old command |
| I14 | Power bank removed while pump on | NO contacts de-energize; pump/valves physically off |
| I15 | Wi-Fi lost but all sensors valid, indoor mode | Local safety continues; no dashboard false-live status |
| I16 | Wi-Fi lost, outdoor weather becomes stale | Separate weather fault policy, not fake rain |
| I17 | One valve/pump switched repeatedly | No brownout/reset, no wire overheating, no relay contact failure |
| I18 | Owner changes soil threshold beyond permitted range | Reject invalid config; no unsafe automatic watering |
| I19 | Pairing mode active | All pump/valve load outputs OFF |
| I20 | Master switch OFF | All devices no-power; app eventually offline |

## 18.5 Pairing, users, and FCM security

| ID | Scenario | Expected |
|---|---|---|
| P01 | User discovers nearby unclaimed unit | Device visible but **not yet owned** |
| P02 | User enters correct claim proof and Wi-Fi | One owner assigned after trusted backend verification |
| P03 | Another signed-in user tries same claim without invitation | Denied; no device hijack |
| P04 | Owner invites friend as viewer | Friend sees live dashboard only |
| P05 | Owner changes friend's role to operator | Friend may use validated irrigation commands |
| P06 | Owner revokes friend | Database/push/command access removed |
| P07 | Wi-Fi password intentionally wrong | Provisioning retry; pump remains off |
| P08 | Duplicate FCM trigger retry | No repeated user-visible alert for same event ID when dedupe works |
| P09 | App switches devices | Old RTDB listeners removed; no mixed dashboard values |
| P10 | App closed + event created + Android permission enabled | Phone receives FCM when delivery is possible; deep-link points correctly |
| P11 | Unauthenticated user writes RTDB telemetry/commands | Rules deny |
| P12 | Device A tries to write Device B data | Rules deny |
| P13 | Owner deletes account while an operator is sharing and watering | All member access revoked. Board stops safely on release, clears old state and acknowledges. Shared account can still use its other devices. |
| P14 | Owner deletes while board offline, then board reboots during reset | Pending until reset acknowledged. No replay of old commands or alerts. Fresh physical claim succeeds once. |
| P15 | Viewer deletes own account or attempts owner release | Remove only that membership. Owner and other members retain access. |
| P16 | Retry deletion, concurrent claim, old invite, old token or stale outbox delivery | One cleanup, one new owner, no old data or push reaches the next owner. |
| P17 | Push to two phones, one transient failure, one token rotates or changes user | Retry eligible installation only. Former user’s event cannot appear on the switched account. |

## 18.6 LCD, buzzer, freshness, and physical safety

| ID | Scenario | Expected |
|---|---|---|
| L01 | LCD cycles normal pages | Four lines, max 20 visible characters each, no old trailing characters |
| L02 | Reservoir critical | Critical overlay supersedes normal page; 3 beeps once |
| L03 | Confirmed combined rain | Rain overlay + 2 beeps once |
| L04 | Forecast-only >60% and dry | Forecast page/advisory, **no buzzer** |
| L05 | Temperature high, light low, humidity low simultaneously | Alerts queued/prioritized, no long blocking beep storm |
| L06 | Firebase unreachable while ESP32 on | Local LCD/buzzer and emergency safety still function |
| L07 | ESP32 unplugged | App eventually shows offline; no false live preview |
| L08 | Load wiring accidentally shorted (simulated safely with protected test setup) | Supply protection/physical isolation prevents sustained overheating; never intentionally short an unprotected power bank |

---

# 19. Troubleshooting guide by symptom

| Symptom | Most likely checks | Action |
|---|---|---|
| ESP32 COM port exists but no LED/Serial | Faulty USB cable/regulator, damaged board, wrong COM/baud | Disconnect loads, check rails carefully with multimeter, try verified data cable and clean board |
| Upload times out / connecting dots forever | Wrong board/port, bootloader mode, driver, data cable | Correct settings, try BOOT button method for this board, verify USB-UART driver |
| ESP32 reboots when solenoid opens | 5-V supply droop, coil transients, ground/relay spikes | Verify supply current/headroom, add suppression, wiring and decoupling |
| Relay clicks on reset unexpectedly | Active-low logic, floating input, unsafe module startup | Hardware safe-state driver/pull-up, NO wiring, bench test before loads |
| I2C LCD backlight works but no text | Backpack contrast, wrong I2C address, incorrect wiring, missing library | Confirm module has I2C backpack; use `I2CexpDiag`, check SDA21/SCL22, adjust backpack contrast trimmer |
| I2C LCD not found / characters garbled | SDA/SCL reversed, level shifter direction/grounds/pull-ups wrong, incompatible backpack | Check 3.3-V ESP32-side bus voltage, share GND, scan I2C, use compatible `hd44780_I2Cexp` (or backpack-specific library) |
| Rain sensor on GPIO32 reads incorrectly | DO active polarity wrong, 5-V signal, open-collector output floating | Verify DO wet/dry with meter/Serial; supply/pull-up at max 3.3 V, consider external 3.3-V pull-up |
| LDR on GPIO36 not reporting brightness | Divider reversed/unpowered or calibration absent | Verify 3.3-V LDR/10-kΩ divider, ADC1 GPIO36, dark/bright calibration; display relative brightness %, not lux |
| Soil reading stuck ~4095 | Unplugged/wiring, dry raw, voltage error, calibration failure | Check AO voltage, physical connection, sensor power, dry/wet calibration |
| ADC sensor works until Wi-Fi | ADC2 conflict | Keep chosen soil/LDR inputs on ADC1; do not reassign to ADC2 |
| DHT22 always NaN | Wrong pin/pull-up, read too frequently, power/cable | Confirm GPIO4, 3.3 V, required pull-up, sampling ≥2 s |
| Reservoir always 100% | Invalid timeout `0` used as 0 in gap formula | Treat no-echo as invalid, `--%`, pump OFF |
| Reservoir percent reversed | Distance confused with water depth | Air gap smaller = fuller; apply exact calibrated formula |
| Reservoir percent wrong near full | Sensor minimum range, surface reflections, wrong mounting | Lower safe-full line or choose suitable sensor; measure `D_FULL_IN` |
| Pump won't push through valve | 5-V valve pressure/flow unsuitable, tubing kinks, wrong valve orientation | Bench test pump/valve combination at actual head pressure |
| Pump starts all three branches | Three valves energized or manifold leaks | Recheck relay mapping and normally-closed valve installation |
| Forecast 80% suspends immediately | Firmware mistakenly used `OR`, forgot rain confirmation | Replace with **strict AND**, use 30-s wet debounce |
| Rain wet at 30% triggers suspension | Wrong `OR`/comparison or stale latch misunderstood | New suspension requires >=60%; separate prior latched state |
| Rain never stops even when sensor dry | Rain latch recovery requires **both** dry 120 s and fresh forecast <60 | Check status timeline and calibrated wet polarity |
| Firebase 401/permission denied | Wrong auth ID token, path/rules, expired token, wrong project URL | Check authenticated role, target device ID, token refresh, rules emulator |
| Firebase data old despite connection | RTDB listener on wrong path, no successful writes/heartbeat | Verify deviceId, timestamps, listener lifecycle, rules |
| Push arrives twice | Event recreated on telemetry, duplicate triggers, multiple tokens | Create event only on transitions; use stable event IDs and dedupe |
| Push not seen on Android 13–15 | Permission denied, wrong token, background handling, no backend sender | Request permission, verify channel/token and trusted FCM send logs |
| Device pairing sees nearby unit but no owner | Provisioning discovery != claim; backend not reached | Verify separate owner-claim step and signed-in token |
| ESP32 memory drops over time | Dynamic allocations, repeated HTTP/String parsing, queue growth | Observe min free heap, use compact queues/buffers, cap history |

**Document every discovered bug** with reproduction steps, serial logs without secrets, component/wiring photo, voltage/current measurements, and exact commit/version. Don't solve repeated brownouts by only adding a longer `delay()`.

---

# 20. Final configuration worksheet (fill before unattended use)

Copy the following worksheet to your capstone implementation notes and replace all `TBD` using **actual readings**:

```yaml
project:
  project_title: "Smart Plant Care"
  firmware_version: TBD
  mobile_version: TBD
  date_last_tested: TBD
  esp32_board: "Classic ESP32-WROOM-32 / WROOM-32E non-PSRAM (verify)"
  arduino_esp32_core_version: TBD
  firebase_project_id: TBD
  firebase_rtdb_url: TBD
  device_id: TBD

wiring:
  lcd_type: "20x4 I2C LCD2004 with PCF8574-compatible backpack"
  lcd_sda: 21
  lcd_scl: 22
  lcd_i2c_address: TBD # scan actual backpack; do not assume 0x27/0x3F
  lcd_i2c_esp32_side_voltage: "3.3V"
  lcd_i2c_level_shifter_verified: false # required for 5V bus pull-ups
  tomato_soil_adc1: 34
  chili_soil_adc1: 35
  eggplant_soil_adc1: 33
  ldr_adc1: 36
  rain_digital: 32
  dht22_data: 4
  ultrasonic_trig: 16
  ultrasonic_echo: 17
  buzzer: 18
  relay_tomato: 25
  relay_chili: 26
  relay_eggplant: 27
  relay_pump: 23
  relay_active_low: TBD # determine physically
  rain_wet_digital_level: TBD # LOW or HIGH verified
  echo_level_shifter_tested: false

power:
  supply: "5V regulated power bank or equivalent"
  available_output_current_amps: TBD
  pump_steady_current_amps: TBD
  pump_startup_current_amps: TBD
  each_solenoid_current_amps: TBD
  relay_board_current_amps: TBD
  total_measured_peak_current_amps: TBD
  wire_sizes_and_terminal_ratings: TBD
  fuse_rating_amps: TBD # protect wire and actual circuit
  fuse_type_and_holder: TBD
  switch_rating: TBD
  all_loads_default_off_on_boot: false

reservoir:
  physical_height_in: 7.0
  calibrated_full_gap_in: TBD   # sensor face to safe-full surface
  calibrated_empty_gap_in: TBD  # nominal ~7, verify actual mount
  low_warning_percent: 20       # design starting value
  pump_cutoff_percent: 10        # design starting value; raise if intake sucks air
  recover_above_percent: 25     # design starting value
  sensor_fault_timeout_seconds: TBD
  ultrasonic_model: TBD
  echo_timeout_microseconds: TBD

soil:
  tomato: {dry_adc: TBD, wet_adc: TBD, start_pct: TBD, stop_pct: TBD}
  chili: {dry_adc: TBD, wet_adc: TBD, start_pct: TBD, stop_pct: TBD}
  eggplant: {dry_adc: TBD, wet_adc: TBD, start_pct: TBD, stop_pct: TBD}
  pulse_duration_seconds: TBD
  max_pump_on_seconds: TBD
  soak_settle_seconds: TBD
  min_time_between_watering_minutes: TBD
  measured_flow_ml_per_second_by_zone: TBD

weather:
  mode: TBD  # INDOOR or OUTDOOR (user-chosen)
  installation_latitude: TBD
  installation_longitude: TBD
  provider: Open-Meteo
  precipitation_threshold_percent: 60 # CONFIRMED USER LOGIC
  forecast_window_hours: 3           # recommended; confirm
  rain_wet_confirm_seconds: 30       # recommended; calibrate
  rain_dry_recover_seconds: 120      # recommended; calibrate
  fetch_interval_minutes: 30        # recommended
  max_weather_age_minutes: 60       # recommended
  unavailable_weather_blocks_outdoor_automatic: true # conservative default
  invalid_rain_sensor_blocks_outdoor_automatic: true # conservative default

environment:
  temperature_high_c: TBD
  temperature_low_c: TBD
  humidity_low_rh: TBD
  humidity_high_rh: TBD
  light_dark_raw: TBD
  light_bright_raw: TBD
  light_low_pct: TBD
  light_high_pct: TBD
  alert_confirm_seconds: 30         # recommended
  alert_hysteresis: TBD

notifications:
  fcm_backend_deployed_and_secure: false
  android_13plus_permission_tested: false
  dedupe_by_event_id: true
  temperature_high_push: true
  humidity_low_push: true
  light_high_push: true
  light_low_push: true
  forecast_only_advisory_push: true
  confirmed_rain_push: true
  reservoir_low_and_critical_push: true
  critical_sensor_fault_push: true

security:
  anonymous_public_rtdb_access: false
  unique_device_credentials: false
  trusted_claim_backend: false
  physical_proof_required_for_claim: true
  owner_operator_viewer_rules_tested: false
  device_push_routing_removed_on_unshare: false

acceptance:
  verified_no_5v_at_gpio_inputs: false
  verified_relay_safe_on_boot_and_reset: false
  full_sensor_calibration_done: false
  measured_reservoir_7in_behavior: false
  rain_truth_table_R01_to_R16_passed: false
  water_tests_W01_to_W12_passed: false
  environmental_tests_E01_to_E11_passed: false
  irrigation_tests_I01_to_I20_passed: false
  pairing_tests_P01_to_P17_passed: false
  lcd_buzzer_tests_L01_to_L08_passed: false
  unattended_irrigation_enabled: false
```

### Calibration evidence table

| Item | Dry/empty actual | Wet/full actual | Sample count/date | Verified by |
|---|---|---|---|---|
| Tomato soil | TBD | TBD | TBD | TBD |
| Chili soil | TBD | TBD | TBD | TBD |
| Eggplant soil | TBD | TBD | TBD | TBD |
| LDR dark/bright | TBD | TBD | TBD | TBD |
| Ultrasonic air gap (in) | Empty TBD | Full TBD | TBD | TBD |
| Rain comparator wet polarity | Dry TBD | Wet TBD | TBD | TBD |
| Pump actual flow (tomato) | N/A | mL/s TBD | TBD | TBD |
| Pump actual flow (chili) | N/A | mL/s TBD | TBD | TBD |
| Pump actual flow (eggplant) | N/A | mL/s TBD | TBD | TBD |

---

# 21. Finished-project acceptance criteria

You can call the **IoT integration complete** only when all of the following are demonstrated on the actual prototype:

- [ ] ESP32 starts consistently and **all pump/valve channels are electrically OFF at power-up/reset**.
- [ ] **20×4 I2C LCD2004** uses **SDA GPIO21/SCL GPIO22**; **rain DO on GPIO32**, **buzzer GPIO18**, **LDR analog GPIO36**; no duplicate pins; LCD bus and sensor signals at ESP32 are 3.3-V safe.
- [ ] LCD shows working 20×4 normal pages and fault overlays; buzzer gives exactly defined patterns **once** on alarm activation.
- [ ] All seven physical sensing responsibilities are validated: 3 soils, DHT22, LDR, rain DO, ultrasonic air gap (7 physical sensing channels, with DHT22 reporting 2 values).
- [ ] Reservoir shows percent and air-gap measurement correctly, **0% at verified empty**; **unknown is never mistaken for full**.
- [ ] Reservoir critical/empty/fault instantly stops pump; reload/recovery is stable with hysteresis.
- [ ] Each crop's soil sensor activates only its corresponding valve and the **shared pump**, not other valves; one zone at a time.
- [ ] Crop moisture thresholds are measured and independently configurable; irrigation has bounded pulse/total on time and cooldown.
- [ ] Outdoor rain suspension requires **BOTH fresh API >=60% AND continuously confirmed local wet sensor**; no false OR logic.
- [ ] Forecast-only warning and wet-only live status are distinguishable; rain latch requires confirmed dry **and** forecast <60 to recover.
- [ ] Indoor mode does not apply the outdoor rain gate; global electrical/water safety is still enforced.
- [ ] DHT22 and LDR warnings create **live UI updates + one push** per transition, not a push per reading, and display sensor failures distinctly.
- [ ] Device can connect to the configured Firebase project with proper authentication and restrictive database rules.
- [ ] Owner can securely provision/claim an ESP32; friends can share the unit according to roles and are blocked after revocation.
- [ ] Android live dashboard displays three plant zones, environment, reservoir percentage, forecast/rain, pump, valve, device connection and data age.
- [ ] FCM push delivery is tested with app foreground/background/closed and notification permission granted; Android event history stays available even when push is disabled.
- [ ] Network outages, app closure, ESP32 resets, stale commands and expired API forecasts cannot cause uncontrolled watering.
- [ ] Hardware wiring, fuse/current ratings and physically measured maximum loads have passed electrical tests; no water reaches exposed terminals.
- [ ] Logs/screenshots/test matrix and exact firmware/app/backend versions are archived for capstone evidence.

---

# 22. Sources, verification notes and unresolved facts

## 22.1 Official technical documentation used for recommendations

Use these primary documentation URLs when a firmware/Android/library API changes:

1. **Espressif ESP32 hardware/pins/ADC considerations** — <https://docs.espressif.com/projects/esp-hardware-design-guidelines/en/latest/esp32/schematic-checklist.html>
2. **Espressif GPIO restrictions** — <https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/peripherals/gpio.html>
3. **Espressif secure provisioning transport and security** — <https://docs.espressif.com/projects/esp-idf/en/stable/esp32/api-reference/provisioning/protocomm.html>
4. **Espressif Arduino WiFiProv example** — <https://github.com/espressif/arduino-esp32/tree/master/libraries/WiFiProv/examples/WiFiProv>
5. **Open-Meteo hourly precipitation probability definitions** — <https://open-meteo.com/en/docs>
6. **Firebase Realtime Database Android listeners** — <https://firebase.google.com/docs/database/android/read-and-write>
7. **Firebase Realtime Database Security Rules** — <https://firebase.google.com/docs/database/security/>
8. **Firebase RTDB security conditions and custom claims** — <https://firebase.google.com/docs/database/security/rules-conditions>
9. **Firebase create custom tokens** — <https://firebase.google.com/docs/auth/admin/create-custom-tokens>
10. **Firebase FCM server environment** — <https://firebase.google.com/docs/cloud-messaging/server-environment>
11. **Firebase FCM Android setup and runtime notification permission** — <https://firebase.google.com/docs/cloud-messaging/android/get-started>
12. **Firebase Cloud Functions getting started and deployment billing** — <https://firebase.google.com/docs/functions/get-started>
13. **Firebase pricing plans** — <https://firebase.google.com/docs/projects/billing/firebase-pricing-plans>

**Cloud billing note:** Deploying Cloud Functions for Firebase normally requires **Blaze pay-as-you-go** plan, even though free usage allowances may exist; evaluate costs and budget alerts before selecting this implementation. A **separate trusted backend** is an alternative for pairing/FCM/forecast scheduling, but it still needs secure hosting and credentials. Do not claim Firebase can automatically send FCM from RTDB alone without server-side execution.

**Additional V3.1 hardware/library references:**
- Espressif's Arduino-ESP32 I2C documentation (default generic ESP32 pins SDA21/SCL22): <https://docs.espressif.com/projects/arduino-esp32/en/latest/api/i2c.html>
- Espressif GPIO/ADC documentation (GPIO32; GPIO36 input-only / ADC1): <https://docs.espressif.com/projects/esp-idf/en/v4.4.7/esp32/api-reference/peripherals/gpio.html>
- `hd44780_I2Cexp` official code/reference for compatible I2C backpacks and autodetection: <https://github.com/duinoWitchery/hd44780>

Account and notification implementation references: [Firebase account management](https://firebase.google.com/docs/auth/android/manage-users), [Android FCM reception](https://firebase.google.com/docs/cloud-messaging/android/receive-messages), and [RTDB rules inheritance](https://firebase.google.com/docs/database/security/core-syntax).

## 22.2 Differences between what was shown and what was designed here

| Item | Source / status | Consequence |
|---|---|---|
| Three crop soils, 3 solenoids, 1 pump, 4 relay inputs | Original diagram | Exact crop-to-relay map preserved |
| **20×4 I2C LCD2004** with backpack | **New user decision**, supersedes original parallel-LCD diagram | LCD SDA GPIO21 / SCL GPIO22; retains all 20×4 status/page content |
| Rain DO on GPIO32 | **User reconfirmed** after choosing I2C LCD | Kept on GPIO32; old conflict disappears because LCD D7 is no longer wired |
| **LDR light sensor** on GPIO36 | Original diagram and previous V3 | **Already included** and kept: analog ADC1 divider, relative brightness and light high/low warnings |
| 60% weather chance + physical rain sensor | Diagram and user clarification | **Strict AND**, not OR; older 70% replaced |
| Indoor/outdoor selectable on app | Diagram | Outdoor rain gate disabled indoors |
| User pairing and sharing | Diagram | Secure BLE/SoftAP discovery, owner claim, backend ACL designed |
| “Live preview” and all sensor notifications | Diagram and recent request | RTDB listeners + event/FCM logic designed |
| Buzzer exact patterns/durations | Not specified numerically by diagram | Clearly designated **DESIGN DEFAULT** |
| LCD exact text/pages and timing | Not specified numerically by diagram | Clearly designated **DESIGN DEFAULT** |
| Reservoir 7-inch height, far distance = empty | Explicit user clarification | Full-percent formula and calibrated empty line; invalid means unknown |
| 20% warning / 10% cutoff | Earlier design recommendation, not a user-measured cutoff | **Adjustable starting point; physical verification mandatory** |
| Temperature/humidity/light alert numeric thresholds | Not stated by user | Leave `TBD` until crop specification/calibration |
| Per-zone irrigation dosage and timers | Not stated by user | Calibrate pump flow and moisture response |
| Specific ultrasonic model/relay board revision | Not identifiable with certainty | Verify signal voltage, minimum range and relay logic |
| Weather forecast selected time window | Not specified by diagram | `max of next 3 hourly probabilities` recommended, record choice |
| Backend/Cloud Functions services | Necessary for secure ownership and automatic background FCM | Provision and deploy or implement compatible trusted server |
| Sensor physical-fault detection | Not fully specified by diagram | Safety-oriented design rule added |

## 22.3 Unresolved hardware facts: do not invent answers

This guide intentionally does **not** invent:

- Real ESP32 board revision, ultrasonic sensor revision, relay trigger polarity, or exact wiring harness voltage distribution measurements.
- Actual pump starting current, real flow rate through solenoids, safe fuse value or real battery/power bank runtime.
- Measured `D_FULL_IN` and installed `D_EMPTY_IN` (only **7-inch nominal tank height** is known).
- Crop-specific ideal irrigation thresholds or exact high-temperature/humidity/light limits.
- Firebase service credentials, active device IDs, user UID, FCM tokens, secure provisioning PoP secret, or device locations.
- Guaranteed FCM timing/delivery or uninterrupted cloud service availability.

**These are implementation inputs to measure/record; leaving them `TBD` is more correct and safer than fabricated “complete” values.**

---

# 23. Final implementation decisions and warning flags

### Must preserve

```text
OUTDOOR && FRESH_FORECAST >= 60% && RAIN_SENSOR_WET_CONFIRMED
    -> STOP AUTO IRRIGATION, latch pause, 2-beep alert, one event/push

FORECAST >= 60% && SENSOR_DRY
    -> FORECAST ADVISORY ONLY; pump not blocked by forecast alone

FORECAST < 60% && SENSOR_WET (no previous latch)
    -> WET SENSOR STATUS ONLY; no new rain-suspension event

AIR_GAP >= CALIBRATED_EMPTY_GAP (~7 IN)
    -> WATER = 0%, pump blocked, urgent reservoir alert

AIR_GAP INVALID
    -> WATER = UNKNOWN, pump blocked, sensor-fault alert

PER-CROP DRY SOIL && GLOBAL_SAFETY_OK
    -> ONE MATCHING VALVE + ONE PUMP ONLY, time-bounded

TEMP_HIGH || HUMIDITY_LOW || LIGHT_OUT_OF_RANGE
    -> LIVE STATUS + SINGLE STATE-TRANSITION ALERT + 1 BEEP
```

### Most serious implementation mistakes to avoid

1. Powering a 5-V valve/pump directly from ESP32 GPIO or running load current through thin jumper wires.
2. Wiring the old parallel LCD D7 onto GPIO32 **after** moving to I2C; GPIO32 is reserved for rain DO. LCD uses SDA21/SCL22 only.
3. Supplying 5-V ECHO/DO signals into 3.3-V ESP32 inputs.
4. Letting ESP32 boot accidentally energize active-LOW relay channels.
5. Calling an ultrasonic timeout "100% full".
6. Using 60% forecast **OR** wet sensor instead of the confirmed **AND** rule.
7. Treating 59% forecast + actual rain as a confirmed automated rain event contrary to the requested logic.
8. Spraying water onto electronics without physical separation or rated splash protection.
9. Publishing every sensor read as a push notification.
10. Putting Firebase Admin credentials / FCM server credentials inside Android/ESP32 or opening RTDB rules publicly.
11. Starting delayed/duplicate irrigation commands after reconnect.
12. Calling LDR raw values “lux” or interpreting LDR as temperature/humidity.
13. Allowing app mode changes/manual requests to bypass pump dry-run protection.
14. Assuming Cloud Functions deployment is always free/no billing requirement.
15. Claiming the prototype is complete before passing **physical** load, safety, and acceptance tests.

**Final professional recommendation:** Build, verify, and sign off the **electrical safety + local state machine first**; then add Firebase telemetry and the live dashboard; then pairing/sharing and notifications; finally weather automation and complete stress testing. An ESP32-WROOM-32-class module is sufficient if the work stays lightweight and the physical power supply/firmware architecture are correct.

---

*End of Smart Plant Care Professional Complete Implementation Guide V3.1 — I2C LCD2004 revision.*
