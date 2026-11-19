# SMART PLANT CARE V3.1 — I2C LCD WIRING, BUZZER, WATER & NOTIFICATION QUICK REFERENCE

**Companion to:** `Smart_Plant_Care_Professional_Complete_Implementation_Guide_v3_1_I2C.md`  
**Revision:** 18 November 2026  
**Status labels:** `CONFIRMED` = user's final rule; `DEFAULT` = test/calibration starting point; `VERIFY` = physical measurement required.

**V3.1 authoritative update:** 20×4 **I2C LCD2004** (not parallel); **SDA GPIO21**, **SCL GPIO22**, **Rain DO GPIO32**, **Buzzer GPIO18**. The **LDR light sensor is already included on GPIO36/VP**, with light bright/dark warning logic. All other V3 irrigation, reservoir, weather, notifications, pairing, sharing, and safety rules remain in force.

---

## 1. Final ESP32 GPIO assignments — LCD2004 I2C, rain GPIO32

| Function | Pin | Function | Pin |
|---|---:|---|---:|
| **20×4 I2C LCD SDA** | **GPIO21** | **20×4 I2C LCD SCL** | **GPIO22** |
| Tomato soil AO | 34 | Tomato valve IN1 | 25 |
| Chili soil AO | 35 | Chili valve IN2 | 26 |
| Eggplant soil AO | 33 | Eggplant valve IN3 | 27 |
| **LDR light sensor (ADC1)** | **36 / VP** | Common pump IN4 | 23 |
| DHT22 DATA | 4 | **Buzzer output** | **18** |
| Ultrasonic TRIG / ECHO | 16 / 17 | **Rain sensor DO** | **32** |

**There is NO pin conflict now.** The prior **parallel LCD RS/E/D4/D5/D6/D7** wiring is completely removed. In particular, **GPIO32 is exclusively rain DO**; it is **not LCD D7**. Old LCD GPIO13/14/19 and old rain GPIO39 are now unassigned. Soil and **LDR GPIO36** remain on ADC1 for Wi-Fi compatibility. The LDR is a 3.3-V divider with a 10-kΩ resistor: **3.3 V → LDR → junction(GPIO36) → 10 kΩ → GND**. Brighter light typically increases ADC raw for this arrangement; **calibrate** and show relative brightness %, **not lux**.

### New I2C LCD wiring (only 2 ESP32 signal pins)

| LCD2004 I2C backpack | Connect to |
|---|---|
| VCC | Rated supply (usually regulated 5 V) |
| GND | Common GND |
| SDA | GPIO21 via 3.3-V/5-V bidirectional I2C level shifter if backpack uses 5-V pull-ups |
| SCL | GPIO22 via same level shifter if needed |

**Important:** Typical 5-V PCF8574 backpacks pull SDA/SCL up to 5 V. **Do not put 5 V directly on ESP32 GPIO21/22**. Use level shifter with LV=3.3 V, HV=5 V and common GND; confirm each side's pull-ups. A verified fully 3.3-V-compatible backpack may not need a shifter. An I2C backpack often has its own contrast trimmer, and its address must be scanned (`0x27`/`0x3F` are only common examples).

**Recommended Arduino library:** `hd44780` with `hd44780_I2Cexp`, using `Wire.begin(21,22);` and `lcd.begin(20,4);` after installing the library. Use `I2CexpDiag` if the display is not found. Do **not** use `LiquidCrystal(13,14,19,21,22,32)` or old parallel-pin code. All 20×4 sample text pages below remain unchanged.

**Rain sensor:** 3.3-V-safe DO to **GPIO32**, common GND; test whether wet means LOW or HIGH. If the comparator is 5-V-powered, verify/level-shift DO. **Buzzer:** drive from GPIO18 through a transistor if needed. **Ultrasonic ECHO:** if 5 V, still needs a level divider/shifter to GPIO17.

---

## 2. Power and relay contact wiring

```text
Regulated 5 V bank -> rated fuse -> DC master switch -> rated +5 V distribution
                                                           | | | |
                                                         COM1 COM2 COM3 COM4
                                                           | | | |
                                      NO1->tomato valve +  | | | |
                                      NO2->chili valve + -----+ | |
                                      NO3->eggplant valve + ----+ |
                                      NO4->pump + -----------------+

All four load negatives -> supply GND/return.
ESP32 uses its own suitable 5-V input; GPIO only controls relay IN1–IN4.
```

Use NO contacts, verify relay active-LOW/HIGH startup behavior, all outputs OFF at power-up/reset, add DC load suppression, measure pump current/voltage under load. **Do not use thin Dupont wires for motor/solenoid load current.**

---

## 3. Strict weather AND logic (CONFIRMED)

```text
OUTDOOR
AND WEATHER_VALID_AND_FRESH
AND OPEN_METEO_PROBABILITY >= 60%
AND RAIN_SENSOR_CONTINUOUSLY_WET_CONFIRMED
    => LATCH RAIN SUSPENSION, PUMP OFF, VALVE OFF,
       ONE RAIN EVENT/PUSH, TWO BEEPS
```

| Forecast | Sensor | Suspension | Buzzer | App |
|---|---|---|---|---|
| ≥60% | Dry | NO | Silent | Forecast risk advisory **once** |
| ≥60% | Brief wet | NO (pending confirmation) | Silent | Live wet/unconfirmed |
| ≥60% | Continuously wet 30 s **(DEFAULT)** | **YES** | **2 beeps** once | Urgent rain suspension push |
| Below 60% | Confirmed wet | **NO new rain suspension** | Silent | Live wet status, not confirmed-rain push |
| Below 60% | Dry | NO | Silent | Normal |
| API stale or invalid | Any | Separate conservative fault policy | Silent by default | Weather unavailable; block new unattended outdoor starts (DEFAULT) |
| Indoor mode | Any | Outdoor rain rule disabled | Silent for forecast | Other sensors/irrigation still active |

**Recovery:** After latch, clear only when **forecast is fresh and <60% AND rain sensor stays dry 120 s (DEFAULT)**. Without both conditions it remains latched. A one-time splash may keep the plate wet longer than 30 s; actual placement/testing matters.

**Forecast window (DEFAULT):** maximum hourly probability in the next 3 hours, refreshed ~30 min; mark stale after 60 min. Show the window and timestamp on the phone. API probability ≠ proof of current rain.

---

## 4. Reservoir: the user's 7-inch tank (CONFIRMED)

**Ultrasonic measures AIR GAP:** small distance = more water; **7-inch nominal distance or greater = EMPTY**. Measure actual empty gap from the transducer face because placement above the tank changes it. Sensor no-echo = **UNKNOWN**, not empty/full by assumption.

```text
water % = clamp(100 × (EMPTY_GAP_IN - MEASURED_GAP_IN)
                         / (EMPTY_GAP_IN - FULL_GAP_IN), 0, 100)
```

**Example calibration only:** FULL_GAP=1.0 in, EMPTY_GAP=7.0 in.

| Distance | Water |
|---:|---:|
| 1.0 in | 100% |
| 2.5 in | 75% |
| 4.0 in | 50% |
| 5.5 in | 25% |
| 5.8 in | 20% LOW warning (DEFAULT) |
| 6.4 in | 10% CRITICAL cutoff (DEFAULT) |
| 7.0+ in | 0% EMPTY, pump OFF |
| No valid distance | `--% UNKNOWN`, pump OFF |

Pump cutoff may need to be **higher than 10%** if it begins sucking air before that level. Refill/recovery after stable reading above e.g. 25% (DEFAULT). Percent assumes reasonably constant cross-section; irregular reservoir requires calibrated volume table.

---

## 5. One crop at a time

| Sensor | Zone | Relay | Behavior |
|---|---|---|---|
| GPIO34 | Tomato | IN1 / GPIO25 | Tomato dry AND global safety -> open tomato valve -> pump |
| GPIO35 | Chili | IN2 / GPIO26 | Chili dry AND global safety -> open chili valve -> pump |
| GPIO33 | Eggplant | IN3 / GPIO27 | Eggplant dry AND global safety -> open eggplant valve -> pump |
| Shared | All three | IN4 / GPIO23 | Only ONE crop valve open with the common pump |

Safety: valid critical reservoir reading above cutoff; no emergency stop; maximum duration not exceeded; valid soil sensor; no rain latch in outdoor; valid weather/rain sensors in outdoor when unattended; no stuck relay/pump fault. **Valve ON first -> wait -> pump ON; pump OFF first -> valve OFF**. Time-bounded short pulses; calibrate output with container to choose real water volumes.

---

## 6. When the buzzer sounds

| Trigger | Pattern (DEFAULT) | Push |
|---|---|---|
| Pairing complete | 1 × 150 ms | Optional |
| Temperature high / low (low if enabled) | 1 × 150 ms | Once on confirmed threshold crossing |
| Humidity low/high | 1 × 150 ms | Once |
| Light too bright/dark | 1 × 150 ms | Once |
| Forecast ≥60%, rain sensor dry | **SILENT** | Forecast advisory only |
| Rain sensor wet, forecast <60% | **SILENT** | Live-only status |
| Both rain AND conditions true | **2 × 150 ms** | Urgent rain suspension push |
| Reservoir ≤20% | **2 × 150 ms** | Low water push |
| Reservoir ≤10%, empty, no echo | **3 × 150 ms** | Critical/fault push |
| Emergency stop/critical electrical fault | 3 × 150 ms | Critical push |
| Ordinary sensor sample or watering progress | **SILENT** | Live-only/event history |

All beep patterns must be **nonblocking** and **not repeat on every sensor sample**. Critical events take priority, alert type and duration configurable after testing. Physical safety never depends on audible mute.

---

## 7. I2C LCD2004 20×4 pages (same display content)

Normal rotation (DEFAULT ~3 s/page):

```text
SMART PLANT CARE
MODE: OUTDOOR
STATUS: ONLINE
IRRIGATION: IDLE
```

```text
TEMP: 30.2 C NORMAL
HUM:  61 % NORMAL
LIGHT: 67 % NORMAL
ENV: ALL NORMAL
```

```text
SOIL MOISTURE
TOMATO:    42 %
CHILI:     61 %
EGGPLANT:  39 %
```

```text
WATER RESERVOIR
LEVEL:  76 %
AIR GAP: 2.44 IN
STATUS: NORMAL
```

```text
FORECAST: 80 %
WINDOW: NEXT 3 H
RAIN SENSOR: DRY
AUTO WATER: ALLOWED
```

**On the I2C LCD:** normal pages still rotate about every 3 seconds; only the wiring and driver changed to `SDA=21`, `SCL=22`. The rain input is **GPIO32** and the LDR analog brightness input is **GPIO36**.

**Critical priority overlays:** `EMERGENCY STOP` > `WATER CRITICAL`/`SENSOR ERROR` > `RAIN - AUTO PAUSED` > `WATER LEVEL LOW` > environment warning. Invalid values display `--`/`ERR`; don't show frozen values as live. Verify LCD contrast, 5-V/3.3-V logic compatibility and 20-character line widths.

---

## 8. Mobile live preview + notification logic

| Metric | Live in app | Push when |
|---|---|---|
| DHT22 temperature | °C, freshness, normal/high/low | Confirmed threshold transition |
| DHT22 humidity | RH %, freshness | Confirmed low/high transition |
| LDR brightness | Relative brightness %, not lux | Confirmed bright/dark transition |
| 3 soil sensors | Crop-specific %, status | Optional dry/fault transition |
| Rain sensor | DRY/WET_UNCONFIRMED/WET_CONFIRMED | **Rain suspension push only if weather AND is also true** |
| Open-Meteo | Probability, forecast window, time, location | Fresh forecast enters ≥60% outdoors while dry and unlatched (advisory) |
| Reservoir | 0–100% (or `--%`) + actual air gap | Low, critical, empty, sensor-fault transitions |
| Pump and 3 valves | Commanded relay state, crop and remaining max time | Abort on safety; started/done optional |
| Device online/offline | Last heartbeat, data age | Backend offline detector, not a dead ESP32 |

**Other alerts.** Ready is history only. Pairing confirmation, reconnection, rain recovery and qualified soil/DHT/LDR/rain-input faults have optional pushes. A rejected manual command returns its reason to the requester, with optional push. Emergency stop and detected critical electrical faults produce urgent alerts. Select one reservoir severity per reading. A safety-triggered abort keeps both history records and sends one cause-specific notification. See §14.2 of the full guide for every event and buzzer pattern.

**Data flow:** ESP32 -> authenticated Firebase RTDB telemetry/events -> Android live listener. For background push, backend detects a *new event* -> FCM -> Android. **Do not send a push every 15 seconds.** Android 13+ requires notification permission.

---

## 9. Pairing and sharing

1. Phone authenticated with Firebase Auth.
2. Discover physically nearby ESP32 using secure BLE provisioning or protected SoftAP; scan ≠ ownership.
3. Provision 2.4-GHz Wi-Fi securely; pump/valves OFF during setup.
4. Prove physical possession with unique claim code/QR + backend verification.
5. Backend registers owner; device authenticates with **its own restricted credentials**.
6. Choose installation location and indoor/outdoor mode in app.
7. App opens real live dashboard and device beeps once upon verified success.
8. Share to friend's **registered account** via backend invite/ACL (invite as `viewer` or `operator`). Owner can revoke.

**Delete my account.** In Settings → Account, the owner reviews the affected devices, signs in again and confirms. The backend releases every owned unit, revokes all sharing and old invitations, removes personal data and deletes the account. A shared member deleting their own account only leaves that unit. An offline unit shows reset pending. On reconnect it stops the pump, closes valves, clears the former setup and acknowledges reset. After cleanup it shows unclaimed and a new owner can pair with fresh physical proof. Old commands, history and queued alerts stay bound to the former ownership epoch.

**Security rule:** no public RTDB writes, no Admin private keys or FCM sending secrets in ESP32/app, no automatic claiming of unknown ESP32 devices.

---

## 10. Ready-for-demo checklist

- [ ] Upload/boot succeeds with all loads disconnected; old 4-bit parallel LCD wires removed.
- [ ] 5-V power, fuse, terminal wires and master switch sized by measured current.
- [ ] All NO relay outputs OFF on startup, reset and supply loss.
- [ ] DHT22, **LDR on GPIO36**, 3 soils, **rain DO on GPIO32**, distance sensor individually validated.
- [ ] **LCD2004 I2C** detected on SDA21/SCL22 through correct 3.3-V bus wiring/level shifter, with all 20×4 pages working; **buzzer GPIO18** scenarios pass nonblocking.
- [ ] 7-inch reservoir 0%/100% calibration recorded, no echo = unknown.
- [ ] Rain 59/60/80% combinations, wet duration, dry recovery, indoor mode and stale API tested.
- [ ] Each crop opens only matching solenoid with shared pump, runtime bounded.
- [ ] Android live shows actual readings and stale/offline flags, not dummy values.
- [ ] Background push arrives from new event, deduped, with user permission.
- [ ] Pairing & friends/viewer/revocation tested under secure Firebase rules.
- [ ] Delete an owner account with sharing enabled, repeat with the ESP32 offline, then pair a new owner. Check for old data, commands and notifications.
- [ ] Run the full firmware during reconnect and watering. Record binary size, minimum heap, maximum safety-check interval and event queue usage. Use bounded buffers, coalesce telemetry and preserve local stop priority (§15.10).
- [ ] Proof photos, multimeter readings, Serial logs and acceptance matrix saved.

**All complete details, updated I2C LCD code example, troubleshooting, data schema and test IDs are in the companion V3.1 master `.md`.**
