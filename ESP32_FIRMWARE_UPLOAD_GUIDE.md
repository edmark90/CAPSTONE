# ESP32 FIRMWARE UPLOAD GUIDE — Smart Plant Care (V3.1 I2C Edition)

This guide explains how to configure, compile, and upload the production **Smart Plant Care V3.1** C++ firmware located in [`esp32_firmware/SmartPlantCare/`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/esp32_firmware/SmartPlantCare/SmartPlantCare.ino) using either **Arduino IDE 2.x** or **PlatformIO**.

---

## 1. Critical Pre-Upload Hardware Safety Checklist

> [!CAUTION]
> **DO NOT** connect the 5V water pump or the three 5V solenoid valves during your first firmware upload.
> 1. In [`Config.h`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/esp32_firmware/SmartPlantCare/Config.h), `ENABLE_PHYSICAL_LOAD_OUTPUTS` is intentionally set to **`false`** by default so the ESP32 **never** energizes physical relay coils during initial upload and sensor calibration.
> 2. Verify with a multimeter in continuity mode that all four relay channels (`IN1=GPIO25`, `IN2=GPIO26`, `IN3=GPIO27`, `IN4=GPIO23`) are wired to **COM** and **NO** (Normally Open), **not NC**.
> 3. Verify that your 20×4 I2C LCD backpack (`SDA=GPIO21`, `SCL=GPIO22`) is connected via a **3.3V ↔ 5V bidirectional I2C logic level shifter** if powered from 5V.
> 4. Verify that the HC-SR04 Ultrasonic `ECHO` pin (`GPIO17`) passes through a **5V → 3.3V voltage divider** (`1 kΩ` series + `2 kΩ` to `GND`) if powered from 5V.

---

## 2. Firmware Directory Structure

```text
esp32_firmware/
├── platformio.ini                         # PlatformIO build & library configuration
├── SmartPlantCare/                        # Main Production Firmware (Arduino IDE & PlatformIO)
│   ├── SmartPlantCare.ino                 # Main entry point & nonblocking millis() scheduler
│   ├── Pins.h                             # Authoritative V3.1 GPIO map + static_assert checks
│   ├── Config.h                           # Cadences, thresholds, hysteresis & bench safety gate
│   ├── SecretsTemplate.h                  # Device ID, endpoints & NVS secret placeholders
│   ├── Models.h                           # Strongly typed state & telemetry structs
│   ├── RelayController.h / .cpp           # Safe-boot latch preload & single-valve interlock
│   ├── BuzzerManager.h / .cpp             # Nonblocking GPIO18 buzzer scheduler & priority matrix
│   ├── EventNotificationManager.h / .cpp  # Bounded 16-slot ring buffer & transition deduplication
│   ├── EnvironmentalSensors.h / .cpp      # DHT22 (GPIO4) + LDR (GPIO36 relative brightness %)
│   ├── SoilMoistureManager.h / .cpp       # 3-zone ADC1 soil moisture (GPIO34, GPIO35, GPIO33)
│   ├── RainWeatherManager.h / .cpp        # Rain DO (GPIO32) + strict Open-Meteo AND-condition
│   ├── ReservoirManager.h / .cpp          # 7-inch ultrasonic air-gap monitor (GPIO16/GPIO17)
│   ├── IrrigationController.h / .cpp      # Complete 11-state irrigation state machine
│   ├── LcdManager.h / .cpp                # 20x4 I2C LCD2004 (GPIO21/22) flicker-free controller
│   ├── ProvisioningPairingManager.h / .cpp# NVS storage, pairing beep & RESET_PENDING lifecycle
│   ├── FirebaseClientManager.h / .cpp     # Wi-Fi, NTP, custom-token auth & RTDB REST sync
│   └── DiagnosticsManager.h / .cpp        # Heap/timing metrics & interactive Serial CLI (115200)
└── bench_tests/
    ├── 01_i2c_lcd_scanner_and_test/       # Isolated 20x4 I2C address scanner & display test
    ├── 02_safe_relay_off_verifier/        # Isolated relay COM/NO & active-LOW polarity verifier
    └── 03_sensors_dry_bench_test/         # Isolated dry sensor ADC/ultrasonic/DHT22 reader
```

---

## 3. Configure `SecretsTemplate.h` Before Upload

Open [`esp32_firmware/SmartPlantCare/SecretsTemplate.h`](file:///c:/Users/Admin/AndroidStudioProjects/SmartPlantCare/esp32_firmware/SmartPlantCare/SecretsTemplate.h) and set:
1. `DEFAULT_DEVICE_ID`: e.g. `"spc-unit-0001"`
2. `DEFAULT_FACTORY_CLAIM_CODE`: e.g. `"SPC-CLAIM-0001"` (printed on your physical QR/label)
3. `DEFAULT_DEVICE_AUTH_SECRET`: e.g. `"SPC-DEVSECRET-0001"` (unique per board, different from `DEFAULT_FACTORY_CLAIM_CODE`)
4. `FIREBASE_WEB_API_KEY`: Your Web API Key from `app/google-services.json` (`api_key.current_key`)
5. `FIREBASE_RTDB_BASE_URL`: Your RTDB URL (without trailing slash)
6. `BACKEND_FUNCTIONS_BASE_URL`: Your deployed Cloud Functions URL (without trailing slash)

*(Tip: You can also leave `WIFI_FALLBACK_SSID` empty and set Wi-Fi credentials at runtime via the Serial Monitor command `wifi <ssid> <password>`, which saves them into ESP32 NVS.)*

---

## 4. Option A — Build & Upload with Arduino IDE 2.x

### Step 4.1 — Install ESP32 Board Support
1. In Arduino IDE, go to **File → Preferences → Additional Boards Manager URLs** and add:
   ```text
   https://espressif.github.io/arduino-esp32/package_esp32_index.json
   ```
2. Open **Tools → Board → Boards Manager**, search for **`esp32` by Espressif Systems**, and install version **2.0.17** or **3.x**.
3. Select **Tools → Board → esp32 → ESP32 Dev Module**.

### Step 4.2 — Install Required Libraries (Library Manager)
Install these exact libraries via **Tools → Manage Libraries**:
1. **`hd44780`** by Bill Perry (`^1.3.2`) — primary 20×4 I2C LCD driver (`hd44780_I2Cexp`).
2. **`LiquidCrystal I2C`** by Frank de Brabander / Marco Schwartz (`^1.1.4`) — optional fallback if `USE_LIQUIDCRYSTAL_I2C_FALLBACK` is defined.
3. **`DHT sensor library`** by Adafruit (`^1.4.6`).
4. **`Adafruit Unified Sensor`** by Adafruit (`^1.1.14`).
5. **`ArduinoJson`** by Benoit Blanchon (`^7.2.0`).

### Step 4.3 — Upload Settings
- **Board:** `ESP32 Dev Module`
- **Upload Speed:** `921600` (or `115200` if your USB-UART cable is noisy)
- **CPU Frequency:** `240MHz (WiFi/BT)`
- **Flash Frequency:** `80MHz`
- **Partition Scheme:** `Default 4MB with spiffs (1.2MB APP/1.5MB SPIFFS)` or `Minimal SPIFFS (1.9MB APP with OTA/190KB SPIFFS)`
- Open `esp32_firmware/SmartPlantCare/SmartPlantCare.ino` and click **Upload**.

---

## 5. Option B — Build & Upload with PlatformIO

From `C:\Users\Admin\AndroidStudioProjects\SmartPlantCare\esp32_firmware`:
```powershell
pio run
pio run --target upload
pio device monitor -b 115200
```

---

## 6. Post-Upload Serial Verification (`115200` baud)

Open the Serial Monitor at **`115200` baud** (`Newline` line ending). You will see:
1. Boot banner showing `Physical Load Outputs Default: DISABLED (BENCH SAFE)`.
2. I2C bus scan result on `SDA=GPIO21, SCL=GPIO22` (e.g., `[LCD] I2C device detected at 0x27`).
3. Type `help` or `status` in the Serial Monitor to inspect live sensor readings, test the strict rain AND-condition (`simweather 75`), or provision Wi-Fi (`wifi MyHomeSSID MyPassword`).
