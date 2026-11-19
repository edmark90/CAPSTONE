#pragma once
/**
 * @file SecretsTemplate.h
 * @brief Configuration for ESP32 device identity and Firebase endpoints (Spark & Blaze compatible).
 *
 * SECURITY RULES:
 * 1. NEVER place Firebase Admin SDK service-account private keys or FCM server keys here.
 * 2. Works out-of-the-box on Firebase Free Spark Plan (Direct RTDB + Direct Open-Meteo fallback)
 *    AND automatically upgrades to Custom Token auth if Cloud Functions (Blaze Plan) are deployed.
 */

namespace Secrets {

// Unique hardware unit identifier (matches /devices/spc-unit-0001 in sample_rtdb_seed.json)
constexpr const char* DEFAULT_DEVICE_ID          = "spc-unit-0001";

// Physical proof-of-possession code for first-time pairing (printed on device sticker/QR)
constexpr const char* DEFAULT_FACTORY_CLAIM_CODE = "SPC-CLAIM-0001";

// Per-device authentication secret stored in NVS
constexpr const char* DEFAULT_DEVICE_AUTH_SECRET = "SPC-DEVSECRET-0001";

// BLE / SoftAP Provisioning Proof-of-Possession (PoP) PIN
constexpr const char* DEFAULT_PROV_POP           = "spc-pop-0001";

// Firebase Project Web API Key (from your app/google-services.json for smartplantcare-7c3e5)
constexpr const char* FIREBASE_WEB_API_KEY       = "AIzaSyA-IatQdL6hnxcVYH94Ksx0DaF--a5Y2wQ";

// Firebase Realtime Database base URL (from your updated app/google-services.json)
constexpr const char* FIREBASE_RTDB_BASE_URL     = "https://smartplantcare-7c3e5-default-rtdb.asia-southeast1.firebasedatabase.app";

// Leave empty ("") for Free Spark Plan (ESP32 connects directly to RTDB & Open-Meteo without Cloud Functions)
constexpr const char* BACKEND_FUNCTIONS_BASE_URL = "";

// Default Open-Meteo coordinates (Manila, PH) used when ESP32 fetches 3-hour forecast directly on Spark Plan
constexpr float       DEFAULT_WEATHER_LATITUDE   = 14.5995f;
constexpr float       DEFAULT_WEATHER_LONGITUDE  = 120.9842f;

// Put your Wi-Fi SSID & Password here (or type `wifi <SSID> <Password>` in Arduino Serial Monitor)
constexpr const char* WIFI_FALLBACK_SSID         = "";
constexpr const char* WIFI_FALLBACK_PASSWORD     = "";

}  // namespace Secrets
