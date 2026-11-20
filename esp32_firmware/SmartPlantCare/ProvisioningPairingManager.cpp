/**
 * @file ProvisioningPairingManager.cpp
 * @brief Implementation of NVS provisioning, Owner Wireless Setup Hotspot (WIFI_AP_STA +
 *        2.4 GHz /api/wifi-scan + /api/info + /api/provision), custom box displayName,
 *        35s wrong-password recovery watchdog, and RESET_PENDING lifecycle.
 */

#include "ProvisioningPairingManager.h"
#include <ArduinoJson.h>
#include <esp_system.h>
#include <stdio.h>
#include <string.h>

namespace {
constexpr const char* NVS_NAMESPACE        = "spc_v31";
constexpr const char* DEFAULT_DISPLAY_NAME = "SmartPlantCare Box";

struct WifiScanEntry {
  char ssid[33];
  int  rssi;
  bool secure;
};
}  // namespace

void ProvisioningPairingManager::begin(RuntimeConfig& ioConfig) {
  prefs_.begin(NVS_NAMESPACE, false);

  String devId  = prefs_.getString("deviceId", Secrets::DEFAULT_DEVICE_ID);
  String disp   = prefs_.getString("dispName", DEFAULT_DISPLAY_NAME);
  String claim  = prefs_.getString("claimCode", Secrets::DEFAULT_FACTORY_CLAIM_CODE);
  String secret = prefs_.getString("devSecret", Secrets::DEFAULT_DEVICE_AUTH_SECRET);
  String ssid   = prefs_.getString("wifiSsid", Secrets::WIFI_FALLBACK_SSID);
  String pass   = prefs_.getString("wifiPass", Secrets::WIFI_FALLBACK_PASSWORD);

  snprintf(deviceId_, sizeof(deviceId_), "%s", devId.c_str());
  snprintf(displayName_, sizeof(displayName_), "%s",
           disp.length() > 0 ? disp.c_str() : DEFAULT_DISPLAY_NAME);
  snprintf(claimCode_, sizeof(claimCode_), "%s", claim.c_str());
  snprintf(deviceAuthSecret_, sizeof(deviceAuthSecret_), "%s", secret.c_str());
  snprintf(wifiSsid_, sizeof(wifiSsid_), "%s", ssid.c_str());
  snprintf(wifiPassword_, sizeof(wifiPassword_), "%s", pass.c_str());
  snprintf(setupApSsid_, sizeof(setupApSsid_), "SPC-SETUP-%s", deviceId_);

  uint32_t randSuffix = esp_random() & 0xFFFFu;
  snprintf(bootId_, sizeof(bootId_), "b%04X-%lu",
           static_cast<unsigned int>(randSuffix),
           static_cast<unsigned long>(millis()));

  ownershipState_ = static_cast<OwnershipState>(
      prefs_.getUChar("ownState", static_cast<uint8_t>(OwnershipState::UNCLAIMED)));
  ownershipEpoch_          = prefs_.getULong("ownEpoch", 0);
  resetAckPendingToCloud_  = prefs_.getBool("rstPending", false);
  pendingResetTargetEpoch_ = prefs_.getULong("rstEpoch", ownershipEpoch_);

  loadHardwareCalibration(ioConfig);

  if (resetAckPendingToCloud_) {
    ownershipState_ = OwnershipState::RESET_PENDING;
    Serial.println(F("[PROV] Resumed incomplete RESET_PENDING checkpoint from NVS."));
  }

  prefs_.end();

  Serial.printf("[PROV] Loaded deviceId=%s name='%s' state=%s epoch=%lu wifiConfigured=%s\n",
                deviceId_,
                displayName_,
                ownershipStateToString(ownershipState_),
                static_cast<unsigned long>(ownershipEpoch_),
                hasWifiCredentials() ? "YES" : "NO");

  if (!hasWifiCredentials() || ownershipState_ == OwnershipState::UNCLAIMED) {
    startOwnerSetupHotspot();
  }
}

void ProvisioningPairingManager::startOwnerSetupHotspot() {
  if (setupApActive_) {
    return;
  }

  // IMPORTANT: Always use WIFI_AP_STA so GET /api/wifi-scan can call WiFi.scanNetworks()
  // while keeping the SPC-SETUP-* SoftAP alive for the Android app.
  WiFi.mode(WIFI_AP_STA);
  bool ok = WiFi.softAP(setupApSsid_, Secrets::DEFAULT_PROV_POP);
  if (ok) {
    setupApActive_ = true;
    if (routesRegistered_) {
      setupServer_.begin();
    }
    Serial.println(F("======================================================================"));
    Serial.printf("[PROV] OWNER WI-FI SETUP HOTSPOT ACTIVE (WIFI_AP_STA)!\n");
    Serial.printf("       Hotspot SSID : %s\n", setupApSsid_);
    Serial.printf("       Hotspot PIN  : %s\n", Secrets::DEFAULT_PROV_POP);
    Serial.printf("       Setup URL    : http://%s  (/api/info, /api/wifi-scan, /api/provision)\n",
                  WiFi.softAPIP().toString().c_str());
    Serial.println(F("======================================================================"));
  }
}

void ProvisioningPairingManager::stopOwnerSetupHotspot() {
  if (!setupApActive_) {
    return;
  }
  setupServer_.stop();
  WiFi.softAPdisconnect(true);
  WiFi.mode(WIFI_STA);
  setupApActive_     = false;
  pendingApShutdown_ = false;
  Serial.println(F("[PROV] Owner Setup Hotspot closed. Switching to normal station mode."));
}

void ProvisioningPairingManager::registerHttpRoutes(
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  if (routesRegistered_) {
    return;
  }
  routesRegistered_ = true;

  // 1. Mobile Browser Provisioning Page (GET /)
  setupServer_.on("/", HTTP_GET, [this]() {
    String html = F(
        "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<title>Smart Plant Care Setup</title>"
        "<style>body{font-family:sans-serif;background:#f6f9f7;padding:20px;color:#111827}"
        ".card{max-width:380px;margin:auto;background:#fff;padding:20px;border-radius:16px;"
        "border:1px solid #e3ece6}input,button{width:100%;padding:10px;margin:8px 0;"
        "border-radius:10px;border:1px solid #ccc;box-sizing:border-box}button{background:#1b4332;"
        "color:#fff;border:none;font-weight:bold;cursor:pointer}</style></head><body><div class='card'>"
        "<h2>Smart Plant Care</h2><p>Device ID: <b>");
    html += deviceId_;
    html += F(
        "</b></p><form method='POST' action='/provisionForm'>"
        "<label>Plant Box Name</label><input name='displayName' value='SmartPlantCare Box'>"
        "<label>2.4 GHz Wi-Fi Name (SSID)</label><input name='ssid' required placeholder='Enter Wi-Fi SSID'>"
        "<label>Wi-Fi Password</label><input name='password' type='password' placeholder='Enter Wi-Fi Password'>"
        "<label>Device Claim Code</label><input name='claimCode' required placeholder='e.g. SPC-CLAIM-0001'>"
        "<button type='submit'>Save Wi-Fi & Pair Device</button></form></div></body></html>");
    setupServer_.send(200, "text/html", html);
  });

  // 2. Device Discovery Info for Android App (GET /api/info)
  setupServer_.on("/api/info", HTTP_GET, [this]() {
    JsonDocument doc;
    doc["deviceId"]         = deviceId_;
    doc["displayName"]      = displayName_;
    doc["setupApSsid"]      = setupApSsid_;
    doc["model"]            = Config::MODEL_NAME;
    doc["firmwareVersion"]  = Config::FIRMWARE_VERSION;
    doc["hardwareRevision"] = Config::HARDWARE_REVISION;
    doc["ownershipState"]   = ownershipStateToString(ownershipState_);
    doc["ownershipEpoch"]   = ownershipEpoch_;
    String out;
    serializeJson(doc, out);
    setupServer_.send(200, "application/json", out);
  });

  // 3. 2.4 GHz Nearby Wi-Fi Scanner for Android App Picker (GET /api/wifi-scan)
  setupServer_.on("/api/wifi-scan", HTTP_GET, [this]() {
    Serial.println(F("[PROV] Running 2.4 GHz Wi-Fi scan for Android setup picker..."));
    int found = WiFi.scanNetworks(false, false);

    constexpr int MAX_UNIQUE = 16;
    WifiScanEntry entries[MAX_UNIQUE];
    int uniqueCount = 0;

    if (found > 0) {
      for (int i = 0; i < found; ++i) {
        String ssid = WiFi.SSID(i);
        ssid.trim();
        if (ssid.length() == 0 || ssid.startsWith("SPC-SETUP-")) {
          continue;
        }
        int  rssi   = WiFi.RSSI(i);
        bool secure = (WiFi.encryptionType(i) != WIFI_AUTH_OPEN);

        int existingIdx = -1;
        for (int j = 0; j < uniqueCount; ++j) {
          if (strcmp(entries[j].ssid, ssid.c_str()) == 0) {
            existingIdx = j;
            break;
          }
        }
        if (existingIdx >= 0) {
          if (rssi > entries[existingIdx].rssi) {
            entries[existingIdx].rssi   = rssi;
            entries[existingIdx].secure = secure;
          }
        } else if (uniqueCount < MAX_UNIQUE) {
          snprintf(entries[uniqueCount].ssid, sizeof(entries[uniqueCount].ssid), "%s", ssid.c_str());
          entries[uniqueCount].rssi   = rssi;
          entries[uniqueCount].secure = secure;
          uniqueCount++;
        }
      }
    }
    WiFi.scanDelete();

    // Sort descending by RSSI (strongest Wi-Fi first)
    for (int i = 0; i < uniqueCount - 1; ++i) {
      for (int j = i + 1; j < uniqueCount; ++j) {
        if (entries[j].rssi > entries[i].rssi) {
          WifiScanEntry tmp = entries[i];
          entries[i]        = entries[j];
          entries[j]        = tmp;
        }
      }
    }

    JsonDocument doc;
    JsonArray arr = doc["networks"].to<JsonArray>();
    for (int i = 0; i < uniqueCount; ++i) {
      JsonObject net = arr.add<JsonObject>();
      net["ssid"]    = entries[i].ssid;
      net["rssi"]    = entries[i].rssi;
      net["secure"]  = entries[i].secure;
    }

    String out;
    serializeJson(doc, out);
    setupServer_.send(200, "application/json", out);
    Serial.printf("[PROV] Returned %d unique 2.4 GHz Wi-Fi networks.\n", uniqueCount);
  });

  // 4. Android App JSON Provisioning Endpoint (POST /api/provision)
  setupServer_.on("/api/provision", HTTP_POST, [this, &ioConfig, &relays, &irrigation, &buzzer, &events, &lcd]() {
    JsonDocument doc;
    if (deserializeJson(doc, setupServer_.arg("plain")) != DeserializationError::Ok) {
      setupServer_.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
      return;
    }

    const char* ssid      = doc["ssid"] | "";
    const char* pass      = doc["password"] | "";
    const char* claim     = doc["claimCode"] | "";
    const char* boxName   = doc["displayName"] | "";
    uint32_t    nextEpoch = doc["ownershipEpoch"] | (ownershipEpoch_ + 1);

    if (strlen(ssid) == 0) {
      setupServer_.send(400, "application/json", "{\"error\":\"Missing Wi-Fi SSID\"}");
      return;
    }
    if (strcmp(claim, claimCode_) != 0) {
      setupServer_.send(403, "application/json", "{\"error\":\"Invalid physical claim code\"}");
      return;
    }

    if (strlen(boxName) > 0) {
      saveDisplayName(boxName);
    }
    saveWifiCredentials(ssid, pass);
    handleCloudMetaSnapshot("OWNED", nextEpoch, false, ioConfig, relays, irrigation, buzzer, events, lcd);

    JsonDocument resp;
    resp["success"]        = true;
    resp["deviceId"]       = deviceId_;
    resp["displayName"]    = displayName_;
    resp["ownershipState"] = "OWNED";
    resp["ownershipEpoch"] = ownershipEpoch_;
    String out;
    serializeJson(resp, out);
    setupServer_.send(200, "application/json", out);

    // Keep AP alive 2.5s so Android cleanly receives the 200 OK JSON before AP closes
    pendingApShutdown_    = true;
    apShutdownAtMs_       = millis() + 2500UL;
    wifiVerifyPending_    = true;
    wifiVerifyDeadlineMs_ = millis() + 2500UL + Config::WIFI_PROVISION_VERIFY_TIMEOUT_MS;
  });

  // 5. Browser Form Submission Handler (POST /provisionForm)
  setupServer_.on("/provisionForm", HTTP_POST, [this, &ioConfig, &relays, &irrigation, &buzzer, &events, &lcd]() {
    String ssid    = setupServer_.arg("ssid");
    String pass    = setupServer_.arg("password");
    String claim   = setupServer_.arg("claimCode");
    String boxName = setupServer_.arg("displayName");
    ssid.trim();
    claim.trim();
    boxName.trim();

    if (ssid.length() == 0 || claim != String(claimCode_)) {
      setupServer_.send(403, "text/html",
                        "<html><body><h3>Invalid Claim Code or Empty SSID.</h3>"
                        "<a href='/'>Go Back</a></body></html>");
      return;
    }

    if (boxName.length() > 0) {
      saveDisplayName(boxName.c_str());
    }
    saveWifiCredentials(ssid.c_str(), pass.c_str());
    handleCloudMetaSnapshot(
        "OWNED", ownershipEpoch_ + 1, false, ioConfig, relays, irrigation, buzzer, events, lcd);

    setupServer_.send(200, "text/html",
                      "<html><body style='font-family:sans-serif;text-align:center;padding:30px'>"
                      "<h2>Wi-Fi Saved & Device Paired!</h2>"
                      "<p>ESP32 is now connecting to your Wi-Fi and syncing with Firebase.</p></body></html>");

    pendingApShutdown_    = true;
    apShutdownAtMs_       = millis() + 2500UL;
    wifiVerifyPending_    = true;
    wifiVerifyDeadlineMs_ = millis() + 2500UL + Config::WIFI_PROVISION_VERIFY_TIMEOUT_MS;
  });

  setupServer_.begin();
}

void ProvisioningPairingManager::serviceProvisioningPortal(
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  if (setupApActive_) {
    if (!routesRegistered_) {
      registerHttpRoutes(ioConfig, relays, irrigation, buzzer, events, lcd);
    }

    setupServer_.handleClient();

    if (pendingApShutdown_ && static_cast<int32_t>(nowMs - apShutdownAtMs_) >= 0) {
      stopOwnerSetupHotspot();
      WiFi.begin(wifiSsid_, wifiPassword_);
    }
    return;
  }

  // Post-provisioning 35s Wi-Fi join watchdog:
  // If user typed a wrong Wi-Fi password, automatically reopen SPC-SETUP-* so the board is never stuck.
  if (wifiVerifyPending_) {
    if (WiFi.status() == WL_CONNECTED) {
      wifiVerifyPending_ = false;
      Serial.println(F("[PROV] Wi-Fi join verified! Setup complete."));
    } else if (static_cast<int32_t>(nowMs - wifiVerifyDeadlineMs_) >= 0) {
      wifiVerifyPending_ = false;
      Serial.println(F("[PROV] Wi-Fi join timed out (wrong password or 5G SSID?) — reopening Setup Hotspot!"));
      lcd.triggerTemporaryOverlay(
          "WIFI JOIN FAILED",
          "CHECK PASSWORD",
          "REOPENING SETUP",
          setupApSsid_,
          5000);
      startOwnerSetupHotspot();
    }
  }
}

void ProvisioningPairingManager::saveWifiCredentials(const char* ssid, const char* password) {
  snprintf(wifiSsid_, sizeof(wifiSsid_), "%s", ssid ? ssid : "");
  snprintf(wifiPassword_, sizeof(wifiPassword_), "%s", password ? password : "");

  prefs_.begin(NVS_NAMESPACE, false);
  prefs_.putString("wifiSsid", wifiSsid_);
  prefs_.putString("wifiPass", wifiPassword_);
  prefs_.end();

  Serial.printf("[PROV] Owner Wi-Fi SSID '%s' saved to NVS.\n", wifiSsid_);
}

void ProvisioningPairingManager::saveDisplayName(const char* name) {
  if (!name || strlen(name) == 0) {
    snprintf(displayName_, sizeof(displayName_), "%s", DEFAULT_DISPLAY_NAME);
  } else {
    snprintf(displayName_, sizeof(displayName_), "%s", name);
  }

  prefs_.begin(NVS_NAMESPACE, false);
  prefs_.putString("dispName", displayName_);
  prefs_.end();

  Serial.printf("[PROV] Plant Box Display Name updated to '%s'.\n", displayName_);
}

void ProvisioningPairingManager::saveFactoryIdentity(const char* deviceId,
                                                     const char* claimCode,
                                                     const char* deviceAuthSecret) {
  if (deviceId && strlen(deviceId) > 0) {
    snprintf(deviceId_, sizeof(deviceId_), "%s", deviceId);
  }
  if (claimCode && strlen(claimCode) > 0) {
    snprintf(claimCode_, sizeof(claimCode_), "%s", claimCode);
  }
  if (deviceAuthSecret && strlen(deviceAuthSecret) > 0) {
    snprintf(deviceAuthSecret_, sizeof(deviceAuthSecret_), "%s", deviceAuthSecret);
  }

  prefs_.begin(NVS_NAMESPACE, false);
  prefs_.putString("deviceId", deviceId_);
  prefs_.putString("claimCode", claimCode_);
  prefs_.putString("devSecret", deviceAuthSecret_);
  prefs_.end();

  Serial.printf("[PROV] Updated factory identity in NVS: deviceId=%s\n", deviceId_);
}

void ProvisioningPairingManager::saveHardwareCalibration(const RuntimeConfig& cfg) {
  prefs_.begin(NVS_NAMESPACE, false);
  prefs_.putInt("tDry", cfg.zones[0].dryRaw);
  prefs_.putInt("tWet", cfg.zones[0].wetRaw);
  prefs_.putInt("cDry", cfg.zones[1].dryRaw);
  prefs_.putInt("cWet", cfg.zones[1].wetRaw);
  prefs_.putInt("eDry", cfg.zones[2].dryRaw);
  prefs_.putInt("eWet", cfg.zones[2].wetRaw);
  prefs_.putFloat("resFull", cfg.reservoirFullGapIn);
  prefs_.putFloat("resEmp", cfg.reservoirEmptyGapIn);
  prefs_.putInt("ldrDark", cfg.ldrDarkRaw);
  prefs_.putInt("ldrBrt", cfg.ldrBrightRaw);
  prefs_.end();
}

void ProvisioningPairingManager::loadHardwareCalibration(RuntimeConfig& ioConfig) {
  ioConfig.zones[0].dryRaw = prefs_.getInt("tDry", Config::DEFAULT_SOIL_DRY_RAW);
  ioConfig.zones[0].wetRaw = prefs_.getInt("tWet", Config::DEFAULT_SOIL_WET_RAW);
  ioConfig.zones[1].dryRaw = prefs_.getInt("cDry", Config::DEFAULT_SOIL_DRY_RAW);
  ioConfig.zones[1].wetRaw = prefs_.getInt("cWet", Config::DEFAULT_SOIL_WET_RAW);
  ioConfig.zones[2].dryRaw = prefs_.getInt("eDry", Config::DEFAULT_SOIL_DRY_RAW);
  ioConfig.zones[2].wetRaw = prefs_.getInt("eWet", Config::DEFAULT_SOIL_WET_RAW);
  ioConfig.reservoirFullGapIn  = prefs_.getFloat("resFull", Config::DEFAULT_RESERVOIR_FULL_GAP_IN);
  ioConfig.reservoirEmptyGapIn = prefs_.getFloat("resEmp", Config::DEFAULT_RESERVOIR_EMPTY_GAP_IN);
  ioConfig.ldrDarkRaw          = prefs_.getInt("ldrDark", Config::DEFAULT_LDR_DARK_RAW);
  ioConfig.ldrBrightRaw        = prefs_.getInt("ldrBrt", Config::DEFAULT_LDR_BRIGHT_RAW);
}

void ProvisioningPairingManager::handleCloudMetaSnapshot(
    const char* cloudOwnershipStateStr,
    uint32_t cloudOwnershipEpoch,
    bool resetRequired,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  OwnershipState cloudState = parseOwnershipState(cloudOwnershipStateStr);

  if (resetRequired || cloudState == OwnershipState::RESET_PENDING) {
    if (!resetAckPendingToCloud_ || ownershipState_ != OwnershipState::RESET_PENDING) {
      initiateResetPendingLifecycle(cloudOwnershipEpoch, relays, irrigation, buzzer, events);
    }
    return;
  }

  if (cloudState == OwnershipState::OWNED) {
    bool newlyPaired = (ownershipState_ != OwnershipState::OWNED) ||
                       (cloudOwnershipEpoch > ownershipEpoch_);

    ownershipState_         = OwnershipState::OWNED;
    ownershipEpoch_         = cloudOwnershipEpoch;
    resetAckPendingToCloud_ = false;

    prefs_.begin(NVS_NAMESPACE, false);
    prefs_.putUChar("ownState", static_cast<uint8_t>(ownershipState_));
    prefs_.putULong("ownEpoch", ownershipEpoch_);
    prefs_.putBool("rstPending", false);
    prefs_.end();

    events.setIdentity(deviceId_, bootId_, ownershipEpoch_);

    if (newlyPaired) {
      Serial.printf("[PROV] Device PAIRED / OWNED ('%s', epoch=%lu)!\n",
                    displayName_,
                    static_cast<unsigned long>(ownershipEpoch_));
      buzzer.clearEventLatch(BuzzerEventId::PAIRING_SUCCESS);
      buzzer.triggerEventTransition(BuzzerEventId::PAIRING_SUCCESS);
      lcd.triggerTemporaryOverlay(
          displayName_,
          "DEVICE PAIRED",
          deviceId_,
          "CLOUD SYNC ACTIVE",
          5000);
    }
    return;
  }

  if (cloudState == OwnershipState::UNCLAIMED || cloudState == OwnershipState::CLAIM_PENDING) {
    if (ownershipState_ == OwnershipState::OWNED) {
      initiateResetPendingLifecycle(cloudOwnershipEpoch, relays, irrigation, buzzer, events);
      finalizeLocalUnclaimAndEnterSetup(ioConfig, true);
    } else {
      ownershipState_ = cloudState;
      ownershipEpoch_ = cloudOwnershipEpoch;
    }
  }
}

void ProvisioningPairingManager::initiateResetPendingLifecycle(
    uint32_t targetEpoch,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events) {
  Serial.printf("[PROV] Initiating RESET_PENDING lifecycle (targetEpoch=%lu)!\n",
                static_cast<unsigned long>(targetEpoch));

  relays.emergencyAllOff();
  irrigation.triggerEmergencyStop("RESET_PENDING");
  buzzer.stopImmediately();
  events.clearAllForReset();

  ownershipState_          = OwnershipState::RESET_PENDING;
  resetAckPendingToCloud_  = true;
  pendingResetTargetEpoch_ = targetEpoch;

  prefs_.begin(NVS_NAMESPACE, false);
  prefs_.putUChar("ownState", static_cast<uint8_t>(OwnershipState::RESET_PENDING));
  prefs_.putBool("rstPending", true);
  prefs_.putULong("rstEpoch", targetEpoch);
  prefs_.end();
}

void ProvisioningPairingManager::finalizeLocalUnclaimAndEnterSetup(
    RuntimeConfig& ioConfig,
    bool wipeWifiCredentials) {
  saveHardwareCalibration(ioConfig);

  bool loadsEnabled = ioConfig.physicalLoadsEnabled;
  ioConfig = RuntimeConfig();
  ioConfig.physicalLoadsEnabled = loadsEnabled;

  prefs_.begin(NVS_NAMESPACE, false);
  loadHardwareCalibration(ioConfig);

  ownershipState_         = OwnershipState::UNCLAIMED;
  ownershipEpoch_         = pendingResetTargetEpoch_;
  resetAckPendingToCloud_ = false;
  wifiVerifyPending_      = false;
  snprintf(displayName_, sizeof(displayName_), "%s", DEFAULT_DISPLAY_NAME);

  prefs_.putUChar("ownState", static_cast<uint8_t>(OwnershipState::UNCLAIMED));
  prefs_.putULong("ownEpoch", ownershipEpoch_);
  prefs_.putBool("rstPending", false);
  prefs_.putString("dispName", displayName_);

  if (wipeWifiCredentials && strlen(Secrets::WIFI_FALLBACK_SSID) == 0) {
    prefs_.putString("wifiSsid", "");
    prefs_.putString("wifiPass", "");
    wifiSsid_[0]     = '\0';
    wifiPassword_[0] = '\0';
  }
  prefs_.end();

  startOwnerSetupHotspot();
  Serial.println(F("[PROV] Local unclaim complete. Owner Setup Hotspot re-opened."));
}

OwnershipState ProvisioningPairingManager::parseOwnershipState(const char* stateStr) {
  if (!stateStr) return OwnershipState::UNCLAIMED;
  if (strcmp(stateStr, "OWNED") == 0) return OwnershipState::OWNED;
  if (strcmp(stateStr, "CLAIM_PENDING") == 0) return OwnershipState::CLAIM_PENDING;
  if (strcmp(stateStr, "RESET_PENDING") == 0) return OwnershipState::RESET_PENDING;
  return OwnershipState::UNCLAIMED;
}

const char* ProvisioningPairingManager::ownershipStateToString(OwnershipState st) {
  switch (st) {
    case OwnershipState::UNCLAIMED:     return "UNCLAIMED";
    case OwnershipState::CLAIM_PENDING: return "CLAIM_PENDING";
    case OwnershipState::OWNED:         return "OWNED";
    case OwnershipState::RESET_PENDING: return "RESET_PENDING";
    default:                            return "UNCLAIMED";
  }
}
