/**
 * @file FirebaseClientManager.cpp
 * @brief Implementation of Wi-Fi, NTP, Firebase RTDB REST sync, and direct Open-Meteo
 *        fallback for Firebase Free Spark Plan compatibility.
 */

#include "FirebaseClientManager.h"
#include <esp_wifi.h>
#include <math.h>
#include <time.h>

namespace {
constexpr uint32_t ID_TOKEN_REFRESH_INTERVAL_MS   = 3000000UL;  // Refresh at 50 min
constexpr uint32_t AUTH_RETRY_BACKOFF_MS          = 15000UL;
constexpr uint32_t DIRECT_WEATHER_FETCH_PERIOD_MS = 900000UL;   // Every 15 min on Spark Plan
constexpr uint32_t DIRECT_WEATHER_RETRY_MS        = 30000UL;    // Retry failed weather fetch after 30s
}

void FirebaseClientManager::begin(ProvisioningPairingManager* provisioning) {
  provisioning_ = provisioning;
  if (!provisioning_ || !provisioning_->isSetupApActive()) {
    WiFi.mode(WIFI_STA);
  }
  WiFi.setAutoReconnect(false);
}

uint64_t FirebaseClientManager::currentEpochMs() const {
  time_t nowSec = time(nullptr);
  if (nowSec > 1700000000L) {
    return static_cast<uint64_t>(nowSec) * 1000ULL + (millis() % 1000ULL);
  }
  return static_cast<uint64_t>(millis());
}

String FirebaseClientManager::buildRtdbUrl(const String& path) const {
  String url = String(Secrets::FIREBASE_RTDB_BASE_URL) + path;
  if (idTokenValid_ && idToken_.length() > 0) {
    url += (path.indexOf('?') >= 0 ? "&auth=" : "?auth=") + idToken_;
  }
  return url;
}

void FirebaseClientManager::serviceNetworkAndTime(uint32_t nowMs,
                                                  DiagnosticsMetrics& ioDiag,
                                                  BuzzerManager& buzzer) {
  if (!provisioning_ || !provisioning_->hasWifiCredentials()) {
    return;
  }

  // CRITICAL: Do not run STA reconnect/channel-hopping scans while the SPC-SETUP-* SoftAP
  // or live Wi-Fi password verification test is active.
  if (provisioning_->isSetupApActive() || provisioning_->isWifiTestInProgress()) {
    return;
  }

  if (WiFi.status() == WL_CONNECTED && WiFi.localIP()[0] != 0) {
    wifiAttemptActive_     = false;
    wifiConnectAttemptNum_ = 0;
    wifiDhcpWaitStartMs_   = 0;
    wifiBackoffMs_         = 12000UL;
    ioDiag.wifiRssiDbm     = static_cast<int8_t>(WiFi.RSSI());

    if (wifiConnectedAtMs_ == 0) {
      wifiConnectedAtMs_ = (nowMs == 0) ? 1UL : nowMs;
      WiFi.setTxPower(WIFI_POWER_19_5dBm);
      WiFi.setAutoReconnect(true);
      Serial.printf("[NET] Wi-Fi connected (IP=%s, ch=%ld, RSSI=%d dBm). Settling network stack...\n",
                    WiFi.localIP().toString().c_str(),
                    static_cast<long>(WiFi.channel()),
                    static_cast<int>(ioDiag.wifiRssiDbm));
      // Sound confirmation beep on GPIO18 when ESP32 connects to Wi-Fi (avoid double-beep right after setup)
      const uint32_t pairedAt = provisioning_->lastPairedAtMs();
      const bool justBeepedForPairing = (pairedAt > 0) && (uint32_t(nowMs - pairedAt) < 12000UL);
      if (!justBeepedForPairing) {
        buzzer.clearEventLatch(BuzzerEventId::WIFI_CONNECTED_SUCCESS);
        buzzer.triggerEventTransition(BuzzerEventId::WIFI_CONNECTED_SUCCESS);
      }
    }

    // Delay SNTP start by 2s after DHCP and use numeric NTP IPs (Google Time / NIST)
    // to prevent LwIP udp_new_ip_type TCPIP core-lock assert on Arduino-ESP32 3.x.
    if (!ntpConfigured_ && (nowMs - wifiConnectedAtMs_ >= 2000UL)) {
      configTime(0, 0, "216.239.35.0", "129.6.15.28");
      ntpConfigured_ = true;
      Serial.println(F("[NET] NTP sync started."));
    }
    return;
  }

  // If STA already associated with the router and is only waiting for DHCP IP, give DHCP up to 12s
  // instead of interrupting the handshake mid-lease!
  if (WiFi.status() == WL_CONNECTED && WiFi.localIP()[0] == 0) {
    if (wifiDhcpWaitStartMs_ == 0) {
      wifiDhcpWaitStartMs_ = (nowMs == 0) ? 1UL : nowMs;
      Serial.println(F("[NET] Wi-Fi associated — waiting for router DHCP IP..."));
    }
    if (uint32_t(nowMs - wifiDhcpWaitStartMs_) < 12000UL) {
      return;
    }
    Serial.println(F("[NET] DHCP wait timed out (12s); retrying Wi-Fi connection..."));
    wifiDhcpWaitStartMs_ = 0;
  } else {
    wifiDhcpWaitStartMs_ = 0;
  }

  wifiConnectedAtMs_ = 0;
  ioDiag.wifiRssiDbm = -127;
  // Connect IMMEDIATELY on boot (lastWifiAttemptAtMs_ == 0), then retry every 12s..20s
  // using alternating standard & compatibility profiles so post-reboot reconnect ALWAYS succeeds!
  if (lastWifiAttemptAtMs_ == 0 || (nowMs - lastWifiAttemptAtMs_ >= wifiBackoffMs_)) {
    lastWifiAttemptAtMs_ = (nowMs == 0) ? 1UL : nowMs;
    wifiAttemptActive_   = true;
    ioDiag.wifiReconnectCount++;

    const uint8_t profileIdx = wifiConnectAttemptNum_ % 3;
    wifiConnectAttemptNum_++;

    Serial.printf("[NET] Connecting to Wi-Fi SSID '%s' (attempt #%u, profile=%u, nextRetry=%lu ms)...\n",
                  provisioning_->wifiSsid(),
                  static_cast<unsigned int>(wifiConnectAttemptNum_),
                  static_cast<unsigned int>(profileIdx),
                  static_cast<unsigned long>(wifiBackoffMs_));

    const char* ssidStr = provisioning_->wifiSsid();
    const char* passStr = provisioning_->wifiPassword();
    const bool  hasPass = (passStr && strlen(passStr) > 0);

    WiFi.persistent(false);
    WiFi.setAutoReconnect(false);
    if (WiFi.getMode() != WIFI_STA) {
      WiFi.mode(WIFI_STA);
      delay(100);
    }
    esp_wifi_disconnect();
    delay(120);

    wifi_country_t country;
    memset(&country, 0, sizeof(country));
    country.cc[0]  = 'P';
    country.cc[1]  = 'H';
    country.schan  = 1;
    country.nchan  = 13;
    country.policy = WIFI_COUNTRY_POLICY_AUTO;
    esp_wifi_set_country(&country);

    WiFi.setSleep(false);
    const wifi_auth_mode_t minAuth = hasPass ? WIFI_AUTH_WEP : WIFI_AUTH_OPEN;
    WiFi.setScanMethod(WIFI_ALL_CHANNEL_SCAN);
    WiFi.setSortMethod(WIFI_CONNECT_AP_BY_SIGNAL);
    WiFi.setMinSecurity(minAuth);

    if (profileIdx == 0) {
      // Profile 0 (Immediate on boot): Direct native 2-arg WiFi.begin(ssid, pass)
      // — identical to the post-provisioning station connect that works on First Connect!
      WiFi.setTxPower(WIFI_POWER_19_5dBm);
      if (hasPass) {
        WiFi.begin(ssidStr, passStr);
      } else {
        WiFi.begin(ssidStr);
      }
    } else if (profileIdx == 1) {
      // Profile 1 (12s fallback): Explicit compatibility wifi_config_t (PMF=off, HT20, 15dBm)
      esp_wifi_set_bandwidth(WIFI_IF_STA, WIFI_BW_HT20);
      WiFi.setTxPower(WIFI_POWER_15dBm);
      const char* passArg = hasPass ? passStr : static_cast<const char*>(nullptr);
      WiFi.begin(ssidStr, passArg, static_cast<int32_t>(0), static_cast<const uint8_t*>(nullptr), false);

      wifi_config_t conf;
      memset(&conf, 0, sizeof(conf));
      if (esp_wifi_get_config(WIFI_IF_STA, &conf) == ESP_OK) {
        conf.sta.scan_method        = WIFI_ALL_CHANNEL_SCAN;
        conf.sta.sort_method        = WIFI_CONNECT_AP_BY_SIGNAL;
        conf.sta.channel            = 0;
        conf.sta.bssid_set          = 0;
        conf.sta.threshold.rssi     = -127;
        conf.sta.threshold.authmode = minAuth;
        conf.sta.pmf_cfg.capable    = false;
        conf.sta.pmf_cfg.required   = false;
        esp_err_t setErr = esp_wifi_set_config(WIFI_IF_STA, &conf);
        if (setErr == ESP_ERR_WIFI_STATE) {
          esp_wifi_disconnect();
          delay(200);
          esp_wifi_set_config(WIFI_IF_STA, &conf);
        }
      }
      wl_status_t ret = WiFi.begin();
      if (ret == WL_CONNECT_FAILED) {
        esp_wifi_connect();
      }
    } else {
      // Profile 2 (24s fallback): Active 2.4 GHz scan for target SSID -> direct BSSID + channel lock!
      WiFi.setTxPower(WIFI_POWER_17dBm);
      int32_t targetCh = 0;
      uint8_t targetBssid[6] = {0};
      bool    foundTarget = false;
      int     n = WiFi.scanNetworks(false, false);
      int     bestRssi = -1000;
      for (int i = 0; i < n; ++i) {
        if (WiFi.SSID(i) == ssidStr && WiFi.RSSI(i) > bestRssi) {
          bestRssi = WiFi.RSSI(i);
          targetCh = WiFi.channel(i);
          const uint8_t* b = WiFi.BSSID(i);
          if (b) {
            memcpy(targetBssid, b, 6);
            foundTarget = true;
          }
        }
      }
      WiFi.scanDelete();
      if (foundTarget && targetCh >= 1 && targetCh <= 13) {
        Serial.printf("[NET] Profile 2 locking onto '%s' ch=%ld rssi=%d dBm\n",
                      ssidStr, static_cast<long>(targetCh), bestRssi);
        WiFi.begin(ssidStr, hasPass ? passStr : nullptr, targetCh, targetBssid, true);
      } else if (hasPass) {
        WiFi.begin(ssidStr, passStr);
      } else {
        WiFi.begin(ssidStr);
      }
    }

    // Keep retries steady (12s -> 15s) so ESP32 recovers fast after router/power cycle
    wifiBackoffMs_ = (wifiBackoffMs_ < 15000UL) ? 12000UL : 15000UL;
  }
}

bool FirebaseClientManager::ensureValidAuth(uint32_t nowMs) {
  if (!isWifiConnected() || !provisioning_) {
    return false;
  }
  // Wait 3.5s after Wi-Fi link-up so DHCP + SNTP settle cleanly before TLS/DNS calls
  if (wifiConnectedAtMs_ == 0 || (nowMs - wifiConnectedAtMs_ < 3500UL)) {
    return false;
  }
  if (strncmp(Secrets::FIREBASE_RTDB_BASE_URL, "https://YOUR_", 13) == 0) {
    return false;
  }

  if (idTokenValid_ && (nowMs - idTokenObtainedAtMs_ < ID_TOKEN_REFRESH_INTERVAL_MS)) {
    return true;
  }
  if (sparkDirectMode_ && (nowMs - idTokenObtainedAtMs_ < ID_TOKEN_REFRESH_INTERVAL_MS)) {
    return true;
  }

  if (nowMs - lastAuthAttemptAtMs_ < AUTH_RETRY_BACKOFF_MS && lastAuthAttemptAtMs_ != 0) {
    return idTokenValid_ || sparkDirectMode_;
  }
  lastAuthAttemptAtMs_ = nowMs;

  // If Cloud Functions URL is configured (Blaze Plan), try Custom Token auth first
  bool functionsConfigured =
      (strlen(Secrets::BACKEND_FUNCTIONS_BASE_URL) > 0) &&
      (strstr(Secrets::BACKEND_FUNCTIONS_BASE_URL, "YOUR_REGION") == nullptr) &&
      (strstr(Secrets::BACKEND_FUNCTIONS_BASE_URL, "YOUR_PROJECT_ID") == nullptr);

  if (functionsConfigured && requestCustomTokenAndSignIn(nowMs)) {
    sparkDirectMode_ = false;
    return true;
  }

  // Fallback for Free Spark Plan (no Cloud Functions needed)
  return requestAnonymousOrSparkAuth(nowMs);
}

bool FirebaseClientManager::requestCustomTokenAndSignIn(uint32_t nowMs) {
  String mintUrl = String(Secrets::BACKEND_FUNCTIONS_BASE_URL) + "/mintDeviceCustomToken";
  JsonDocument reqDoc;
  reqDoc["deviceId"]         = provisioning_->deviceId();
  reqDoc["deviceAuthSecret"] = provisioning_->deviceAuthSecret();
  reqDoc["bootId"]           = provisioning_->bootId();
  reqDoc["firmwareVersion"]  = Config::FIRMWARE_VERSION;

  String reqBody;
  serializeJson(reqDoc, reqBody);

  String mintResp;
  if (!httpRequestJson("POST", mintUrl, reqBody, mintResp)) {
    return false;
  }

  JsonDocument mintDoc;
  if (deserializeJson(mintDoc, mintResp) != DeserializationError::Ok) {
    return false;
  }

  const char* customToken = mintDoc["customToken"] | "";
  if (strlen(customToken) == 0) {
    return false;
  }

  String signInUrl =
      String("https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=") +
      Secrets::FIREBASE_WEB_API_KEY;

  JsonDocument signInReq;
  signInReq["token"]             = customToken;
  signInReq["returnSecureToken"] = true;
  String signInBody;
  serializeJson(signInReq, signInBody);

  String signInResp;
  if (!httpRequestJson("POST", signInUrl, signInBody, signInResp)) {
    return false;
  }

  JsonDocument signInDoc;
  if (deserializeJson(signInDoc, signInResp) != DeserializationError::Ok) {
    return false;
  }

  const char* tokenStr = signInDoc["idToken"] | "";
  if (strlen(tokenStr) == 0) {
    return false;
  }

  idToken_             = tokenStr;
  idTokenObtainedAtMs_ = nowMs;
  idTokenValid_        = true;
  Serial.println(F("[AUTH] Firebase Custom Token ID acquired (Blaze Mode)."));
  return true;
}

bool FirebaseClientManager::requestAnonymousOrSparkAuth(uint32_t nowMs) {
  // Try Firebase Anonymous Sign-Up REST call if enabled in Firebase Auth
  if (strncmp(Secrets::FIREBASE_WEB_API_KEY, "YOUR_", 5) != 0) {
    String signUpUrl =
        String("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=") +
        Secrets::FIREBASE_WEB_API_KEY;
    String resp;
    if (httpRequestJson("POST", signUpUrl, "{\"returnSecureToken\":true}", resp)) {
      JsonDocument doc;
      if (deserializeJson(doc, resp) == DeserializationError::Ok) {
        const char* tok = doc["idToken"] | "";
        if (strlen(tok) > 0) {
          idToken_             = tok;
          idTokenObtainedAtMs_ = nowMs;
          idTokenValid_        = true;
          sparkDirectMode_     = true;
          Serial.println(F("[AUTH] Firebase Spark Auth token acquired."));
          return true;
        }
      }
    }
  }

  // Direct Spark RTDB mode (works with Spark database.rules.json without Cloud Functions)
  idToken_             = "";
  idTokenValid_        = false;
  sparkDirectMode_     = true;
  idTokenObtainedAtMs_ = nowMs;
  Serial.println(F("[AUTH] Running in Firebase Free Spark Direct RTDB Mode."));
  return true;
}

bool FirebaseClientManager::publishStatusAndTelemetry(
    uint32_t nowMs,
    const RuntimeConfig& cfg,
    const EnvironmentReading& env,
    const SoilZoneStatus soil[3],
    const ReservoirReading& reservoir,
    const RainGateStatus& rain,
    const RelayController& relays,
    const IrrigationController& irrigation,
    uint8_t lcdAddr,
    DiagnosticsMetrics& ioDiag) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return false;
  }
  if (provisioning_->ownershipState() != OwnershipState::OWNED) {
    return false;
  }

  JsonDocument root;

  // Use Firebase server timestamp {".sv":"timestamp"} so lastSeenAt & updatedAt are ALWAYS
  // exact UTC Unix epoch ms immediately after reboot — even before NTP finishes syncing!
  JsonObject st = root["status"].to<JsonObject>();
  st["online"]                  = true;
  st["lastSeenAt"][".sv"]       = "timestamp";
  st["bootId"]                  = provisioning_->bootId();
  st["firmwareVersion"]         = Config::FIRMWARE_VERSION;
  st["ipAddress"]               = WiFi.localIP().toString();
  st["wifiRssi"]                = static_cast<int>(ioDiag.wifiRssiDbm);
  st["mode"]                    = (cfg.mode == DeviceMode::OUTDOOR) ? "OUTDOOR" : "INDOOR";
  st["autoIrrigationEnabled"]   = cfg.autoIrrigationEnabled;
  st["rainSuspensionActive"]    = rain.rainSuspensionLatched;
  st["weatherAdvisoryActive"]   = rain.forecastAdvisoryActive;
  st["weatherUnavailableFault"] = rain.weatherUnavailableFault;
  st["emergencyStop"]           = irrigation.isEmergencyStopped();
  st["faultLockout"]            = irrigation.isFaultLocked();
  const CropZoneId reportedZone = (relays.activeValveZone() != CropZoneId::NONE)
                                      ? relays.activeValveZone()
                                      : irrigation.activeZone();
  st["activeValve"]             = cropZoneSlug(reportedZone);
  st["pumpState"]               = relays.isPumpOn() ? "ON" : "OFF";
  st["irrigationState"]         = irrigationStateString(irrigation.state());
  st["activePumpElapsedSec"]    = irrigation.activePumpElapsedSec(nowMs);
  st["activeTargetDurationSec"] = irrigation.activeTargetDurationSec();
  st["lastAbortReason"]         = irrigation.lastAbortReason();
  st["physicalLoadsEnabled"]    = relays.isPhysicalLoadsEnabled();
  st["lcdAddress"]              = lcdAddr;
  st["freeHeap"]                = ioDiag.freeHeapBytes;
  st["ownershipEpoch"]          = provisioning_->ownershipEpoch();

  JsonObject tel = root["telemetry"].to<JsonObject>();
  tel["updatedAt"][".sv"] = "timestamp";
  tel["dhtValid"]         = env.dhtValid;
  if (env.dhtValid) {
    tel["temperatureC"] = serialized(String(env.temperatureC, 1));
    tel["humidityPct"]  = serialized(String(env.humidityRh, 1));
  } else {
    tel["temperatureC"] = nullptr;
    tel["humidityPct"]  = nullptr;
  }
  tel["tempBand"]      = envBandShort(env.tempBand);
  tel["humidityBand"]  = envBandShort(env.humidityBand);

  tel["lightRaw"]      = env.ldrRaw;
  tel["lightValid"]    = env.lightValid;
  if (env.lightValid) {
    tel["lightBrightnessPct"] = serialized(String(env.brightnessPct, 1));
  } else {
    tel["lightBrightnessPct"] = nullptr;
  }
  tel["lightBand"]     = envBandShort(env.lightBand);

  tel["rainRawWet"]        = rain.rawWet;
  tel["rainWetConfirmed"]  = rain.wetConfirmed;
  tel["rainAccidentalWet"] = rain.accidentalWet;
  tel["rainValid"]         = rain.sensorValid;

  tel["reservoirValid"] = reservoir.valid;
  tel["reservoirState"] = reservoirStateString(reservoir.state);
  if (reservoir.valid) {
    tel["reservoirAirGapInches"] = serialized(String(reservoir.airGapInches, 2));
    tel["reservoirPct"]          = serialized(String(reservoir.waterLevelPct, 1));
  } else {
    tel["reservoirAirGapInches"] = nullptr;
    tel["reservoirPct"]          = nullptr;
  }

  JsonObject soilObj = tel["soil"].to<JsonObject>();
  const CropZoneId zones[3] = {CropZoneId::TOMATO, CropZoneId::CHILI, CropZoneId::EGGPLANT};
  for (int i = 0; i < 3; ++i) {
    JsonObject z = soilObj[cropZoneSlug(zones[i])].to<JsonObject>();
    z["raw"]        = soil[i].rawAdc;
    z["valid"]      = soil[i].valid;
    z["needsWater"] = soil[i].needsWater;
    if (soil[i].valid) {
      z["pct"] = serialized(String(soil[i].moisturePct, 1));
    } else {
      z["pct"] = nullptr;
    }
  }

  String body;
  serializeJson(root, body);

  String url = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) + ".json");
  String resp;
  if (httpRequestJson("PATCH", url, body, resp)) {
    lastSuccessfulSyncMs_ = nowMs;
    ioDiag.firebaseSyncSuccessCnt++;
    if (!firstTelemetryPublished_) {
      firstTelemetryPublished_ = true;
      Serial.printf("[SYNC] Live status & telemetry synced to Firebase (bootId=%s, epoch=%lu)!\n",
                    provisioning_->bootId(),
                    static_cast<unsigned long>(provisioning_->ownershipEpoch()));
    }
    return true;
  }

  ioDiag.firebaseSyncFailCnt++;
  return false;
}

void FirebaseClientManager::flushPendingEventsAndReports(
    uint32_t nowMs,
    EventNotificationManager& events,
    IrrigationController& irrigation,
    DiagnosticsMetrics& ioDiag) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return;
  }
  if (provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }

  CommandExecutionReport report;
  if (irrigation.popPendingCommandReport(report) && report.hasReport) {
    JsonDocument doc;
    doc["commandId"]        = report.commandId;
    doc["state"]            = report.state;
    doc["reason"]           = report.reason;
    doc["updatedAt"][".sv"] = "timestamp";
    doc["ownershipEpoch"]   = provisioning_->ownershipEpoch();

    String body;
    serializeJson(doc, body);
    String url = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) +
                              "/commandResults/" + String(report.commandId) + ".json");
    String resp;
    httpRequestJson("PUT", url, body, resp);
  }

  for (int i = 0; i < 2; ++i) {
    DeviceEventRecord ev;
    if (!events.peekNextPending(ev)) {
      break;
    }

    JsonDocument evDoc;
    evDoc["eventId"]          = ev.eventId;
    evDoc["type"]             = ev.type;
    evDoc["severity"]         = severityToString(ev.severity);
    evDoc["title"]            = ev.title;
    evDoc["message"]          = ev.message;
    evDoc["crop"]             = strlen(ev.crop) > 0 ? ev.crop : nullptr;
    evDoc["dedupeKey"]        = ev.dedupeKey;
    evDoc["createdAt"][".sv"] = "timestamp";
    // Always use current live ownershipEpoch so events queued during early boot match /meta!
    evDoc["ownershipEpoch"]   = provisioning_->ownershipEpoch();
    if (!isnan(ev.primaryValue)) {
      evDoc["value"] = serialized(String(ev.primaryValue, 1));
    }

    String evBody;
    serializeJson(evDoc, evBody);

    String evUrl = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) +
                                "/events/" + String(ev.eventId) + ".json");
    String evResp;
    if (httpRequestJson("PUT", evUrl, evBody, evResp)) {
      events.popOldestPending();
      lastSuccessfulSyncMs_ = nowMs;
      ioDiag.firebaseSyncSuccessCnt++;
    } else {
      ioDiag.firebaseSyncFailCnt++;
      // Drop unaccepted/duplicate event so it never blocks the queue or loops 401 forever
      events.popOldestPending();
      break;
    }
  }
}

void FirebaseClientManager::pollPendingCommandsOnly(
    uint32_t nowMs,
    const RuntimeConfig& cfg,
    IrrigationController& irrigation,
    BuzzerManager& buzzer) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return;
  }
  if (provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }
  syncPendingCommands(nowMs, cfg, irrigation, buzzer);
}

void FirebaseClientManager::pollCloudControlAndWeather(
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    RainWeatherManager& rainWeather,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd,
    DiagnosticsMetrics& ioDiag) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return;
  }

  syncMetaNode(nowMs, ioConfig, relays, irrigation, buzzer, events, lcd);

  if (provisioning_->isResetAckPendingToCloud()) {
    if (sendResetAckToBackend(nowMs)) {
      provisioning_->finalizeLocalUnclaimAndEnterSetup(ioConfig, true);
    }
    return;
  }

  if (provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }

  syncWeatherNode(nowMs, rainWeather);
  syncConfigNode(ioConfig, relays, buzzer);
  syncPendingCommands(nowMs, ioConfig, irrigation, buzzer);
}

void FirebaseClientManager::syncMetaNode(
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  String url = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) + "/meta.json");
  String resp;
  if (!httpRequestJson("GET", url, "", resp)) {
    return;
  }

  JsonDocument doc;
  const bool metaMissing = (deserializeJson(doc, resp) != DeserializationError::Ok || doc.isNull());
  if (metaMissing) {
    if (provisioning_->ownershipState() == OwnershipState::OWNED) {
      JsonDocument patchDoc;
      patchDoc["deviceId"]       = provisioning_->deviceId();
      patchDoc["ownershipState"] = "OWNED";
      patchDoc["ownershipEpoch"] = provisioning_->ownershipEpoch();
      patchDoc["displayName"]    = provisioning_->displayName();
      patchDoc["resetRequired"]  = false;
      patchDoc["claimedAt"]      = currentEpochMs();
      String patchBody;
      serializeJson(patchDoc, patchBody);
      String patchResp;
      if (httpRequestJson("PATCH", url, patchBody, patchResp)) {
        lastSuccessfulSyncMs_ = nowMs;
      }
    }
    return;
  }

  const char* stateStr = doc["ownershipState"] | "UNCLAIMED";
  uint32_t    epoch    = doc["ownershipEpoch"] | 0;
  bool        resetReq = doc["resetRequired"] | false;
  const char* cloudDispName = doc["displayName"] | "";

  if (!doc["location"]["lat"].isNull() && !doc["location"]["lon"].isNull()) {
    const float newLat = doc["location"]["lat"].as<float>();
    const float newLon = doc["location"]["lon"].as<float>();
    const char* cloudLocName = doc["location"]["name"] | "";
    const bool validCoords = (fabsf(newLat) > 0.0001f || fabsf(newLon) > 0.0001f);
    if (validCoords) {
      const bool coordsChanged = !ownerLocationLoaded_ ||
                                 fabsf(newLat - deviceLat_) > 0.0005f ||
                                 fabsf(newLon - deviceLon_) > 0.0005f;
      deviceLat_           = newLat;
      deviceLon_           = newLon;
      ownerLocationLoaded_ = true;
      if (strlen(cloudLocName) > 0) {
        snprintf(deviceLocationName_, sizeof(deviceLocationName_), "%s", cloudLocName);
      }
      if (coordsChanged) {
        // Force immediate weather refresh for the Owner's updated mobile GPS location
        lastDirectWeatherAtMs_ = 0;
        Serial.printf("[WEATHER] Adopted Owner GPS location: '%s' (lat=%.4f, lon=%.4f)\n",
                      deviceLocationName_[0] ? deviceLocationName_ : "Box GPS",
                      deviceLat_, deviceLon_);
      }
    }
  }

  // If Owner renamed the plant box from Android Settings while OWNED, update NVS displayName
  if (strlen(cloudDispName) > 0 && strcmp(cloudDispName, provisioning_->displayName()) != 0 &&
      strcmp(stateStr, "OWNED") == 0) {
    provisioning_->saveDisplayName(cloudDispName);
  }

  // IMPORTANT (Firebase RTDB Rules Compatibility):
  // Once /meta/ownershipState in Firebase is "OWNED", database.rules.json ONLY permits authenticated
  // Android user writes (`auth != null`) to /meta AND requires /events to match /meta/ownershipEpoch!
  // Therefore, when cloud /meta is "OWNED" (and !resetReq), ALWAYS adopt cloud `epoch` locally
  // without attempting an unauthenticated PATCH on /meta.json (which would return 401 Permission denied).
  if (!resetReq && strcmp(stateStr, "OWNED") == 0) {
    const uint32_t adoptEpoch = (epoch > 0) ? epoch : provisioning_->ownershipEpoch();
    provisioning_->handleCloudMetaSnapshot(
        "OWNED", adoptEpoch, false, ioConfig, relays, irrigation, buzzer, events, lcd);
    lastSuccessfulSyncMs_ = nowMs;
    return;
  }

  // If the Owner just paired the ESP32 via the local Setup Hotspot (within 60s grace window)
  // while cloud /meta is still UNCLAIMED or RESET_PENDING (where unauthenticated /meta write IS allowed),
  // push the new OWNED state, epoch, and custom displayName up to Firebase /meta!
  const uint32_t pairedAt = provisioning_->lastPairedAtMs();
  const bool justPairedLocally =
      (pairedAt > 0) && (uint32_t(nowMs - pairedAt) < 60000UL);

  if (provisioning_->ownershipState() == OwnershipState::OWNED &&
      justPairedLocally &&
      (strcmp(stateStr, "UNCLAIMED") == 0 || strcmp(stateStr, "RESET_PENDING") == 0)) {
    const uint32_t targetEpoch =
        (epoch >= provisioning_->ownershipEpoch()) ? (epoch + 1UL) : provisioning_->ownershipEpoch();
    if (targetEpoch != provisioning_->ownershipEpoch()) {
      provisioning_->handleCloudMetaSnapshot(
          "OWNED", targetEpoch, false, ioConfig, relays, irrigation, buzzer, events, lcd);
    }
    JsonDocument patchDoc;
    patchDoc["ownershipState"] = "OWNED";
    patchDoc["ownershipEpoch"] = targetEpoch;
    patchDoc["displayName"]    = provisioning_->displayName();
    patchDoc["resetRequired"]  = false;
    patchDoc["claimedAt"]      = currentEpochMs();
    String patchBody;
    serializeJson(patchDoc, patchBody);
    String patchResp;
    httpRequestJson("PATCH", url, patchBody, patchResp);
    lastSuccessfulSyncMs_ = nowMs;
    return;
  }

  provisioning_->handleCloudMetaSnapshot(
      stateStr, epoch, resetReq, ioConfig, relays, irrigation, buzzer, events, lcd);
  lastSuccessfulSyncMs_ = nowMs;
}

void FirebaseClientManager::syncWeatherNode(uint32_t nowMs, RainWeatherManager& rainWeather) {
  // Weather coordinates come strictly from the Owner's mobile GPS saved in /meta/location
  // (never from ESP32 Wi-Fi IP geolocation).
  // 1. Check RTDB /weather cache (written by Owner's app, ESP32, or Cloud Function)
  String url = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) + "/weather.json");
  String resp;
  bool needDirectFetch = false;

  if (httpRequestJson("GET", url, "", resp)) {
    JsonDocument doc;
    if (deserializeJson(doc, resp) == DeserializationError::Ok && !doc.isNull()) {
      bool        valid      = doc["valid"] | false;
      int         probPct    = doc["rainProbabilityPct"] | -1;
      uint64_t    fetchedAt  = doc["fetchedAt"] | 0ULL;
      uint64_t    validUntil = doc["validUntil"] | 0ULL;
      const char* winLabel   = doc["windowLabel"] | "NEXT 3 H";
      const char* locName    = doc["locationName"] | "";
      const float wLat       = doc["lat"] | 0.0f;
      const float wLon       = doc["lon"] | 0.0f;

      if (!ownerLocationLoaded_ && (fabsf(wLat) > 0.0001f || fabsf(wLon) > 0.0001f)) {
        deviceLat_           = wLat;
        deviceLon_           = wLon;
        ownerLocationLoaded_ = true;
      }
      if (strlen(locName) > 0 && deviceLocationName_[0] == '\0') {
        snprintf(deviceLocationName_, sizeof(deviceLocationName_), "%s", locName);
      }

      uint64_t nowEpoch = currentEpochMs();
      if (validUntil == 0 ||
          (nowEpoch > 1700000000000ULL && validUntil > 1700000000000ULL && nowEpoch > validUntil)) {
        valid = false;
        needDirectFetch = true;
      }

      if (!valid || probPct < 0) {
        needDirectFetch = true;
      } else {
        // Only refresh uptime timestamp when RTDB actually contains a NEWER snapshot (avoid masking stale data!)
        if (fetchedAt != lastCloudWeatherFetched_ || !rainWeather.weather().valid) {
          lastCloudWeatherFetched_ = fetchedAt;
          rainWeather.updateWeatherSnapshot(
              valid, probPct, fetchedAt, validUntil, nowMs, winLabel,
              strlen(locName) > 0 ? locName : deviceLocationName_);
        }
        // Still schedule periodic 15-min direct Open-Meteo refresh using Owner's exact coordinates
        if (lastDirectWeatherAtMs_ == 0 ||
            uint32_t(nowMs - lastDirectWeatherAtMs_) >= DIRECT_WEATHER_FETCH_PERIOD_MS) {
          needDirectFetch = true;
        }
      }
    } else {
      needDirectFetch = true;
    }
  } else {
    needDirectFetch = true;
  }

  // 2. Fetch live 3-hour rain probability directly from Open-Meteo using Owner's saved GPS coordinates
  const bool retryReady = (lastDirectWeatherRetryMs_ == 0) ||
                          (uint32_t(nowMs - lastDirectWeatherRetryMs_) >= DIRECT_WEATHER_RETRY_MS);
  const bool periodicDue = (lastDirectWeatherAtMs_ == 0) ||
                           (uint32_t(nowMs - lastDirectWeatherAtMs_) >= DIRECT_WEATHER_FETCH_PERIOD_MS);
  if (needDirectFetch && retryReady && (periodicDue || !rainWeather.weather().valid)) {
    lastDirectWeatherRetryMs_ = nowMs;
    if (fetchOpenMeteoDirectAndCache(nowMs, rainWeather)) {
      lastDirectWeatherAtMs_ = nowMs;
    }
  }
}

bool FirebaseClientManager::fetchOpenMeteoDirectAndCache(uint32_t nowMs,
                                                         RainWeatherManager& rainWeather) {
  String omUrl = String("https://api.open-meteo.com/v1/forecast?latitude=") +
                 String(deviceLat_, 4) + "&longitude=" + String(deviceLon_, 4) +
                 "&hourly=precipitation_probability&forecast_hours=3&timezone=auto";

  String omResp;
  if (!httpRequestJson("GET", omUrl, "", omResp)) {
    Serial.println(F("[WEATHER] Direct Open-Meteo fetch failed (will retry in 30s)."));
    return false;
  }

  JsonDocument omDoc;
  if (deserializeJson(omDoc, omResp) != DeserializationError::Ok) {
    return false;
  }

  JsonArray probs = omDoc["hourly"]["precipitation_probability"].as<JsonArray>();
  if (probs.isNull() || probs.size() == 0) {
    return false;
  }

  int maxProb = 0;
  size_t count = min(static_cast<size_t>(3), probs.size());
  for (size_t i = 0; i < count; ++i) {
    int p = probs[i] | 0;
    if (p > maxProb) maxProb = p;
  }
  maxProb = constrain(maxProb, 0, 100);

  uint64_t nowEpoch   = currentEpochMs();
  uint64_t validUntil = nowEpoch + Config::DEFAULT_WEATHER_MAX_AGE_MS;
  const char* locStr  = (deviceLocationName_[0] != '\0') ? deviceLocationName_ : "Manila, PH";

  lastCloudWeatherFetched_ = nowEpoch;
  rainWeather.updateWeatherSnapshot(true, maxProb, nowEpoch, validUntil, nowMs, "NEXT 3 H", locStr);
  Serial.printf("[WEATHER] Open-Meteo (%s, %.4f, %.4f): 3h max rainProbabilityPct=%d%%\n",
                locStr, deviceLat_, deviceLon_, maxProb);

  // Cache snapshot + Wi-Fi location back to RTDB /devices/{deviceId}/weather so Android Dashboard shows it
  JsonDocument cacheDoc;
  cacheDoc["provider"]           = "open-meteo-esp32";
  cacheDoc["valid"]              = true;
  cacheDoc["rainProbabilityPct"] = maxProb;
  cacheDoc["windowHours"]        = 3;
  cacheDoc["windowLabel"]        = "NEXT 3 H";
  cacheDoc["locationName"]       = locStr;
  cacheDoc["lat"]                = serialized(String(deviceLat_, 4));
  cacheDoc["lon"]                = serialized(String(deviceLon_, 4));
  cacheDoc["fetchedAt"]          = nowEpoch;
  cacheDoc["validUntil"]         = validUntil;

  String cacheBody;
  serializeJson(cacheDoc, cacheBody);
  String rtdbUrl = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) + "/weather.json");
  String rtdbResp;
  httpRequestJson("PUT", rtdbUrl, cacheBody, rtdbResp);
  return true;
}

void FirebaseClientManager::syncConfigNode(RuntimeConfig& ioConfig,
                                           RelayController& relays,
                                           BuzzerManager& buzzer) {
  String url = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) + "/config.json");
  String resp;
  if (!httpRequestJson("GET", url, "", resp)) {
    return;
  }

  JsonDocument doc;
  if (deserializeJson(doc, resp) != DeserializationError::Ok || doc.isNull()) {
    return;
  }

  const char* modeStr = doc["mode"] | "OUTDOOR";
  ioConfig.mode = (strcmp(modeStr, "INDOOR") == 0) ? DeviceMode::INDOOR : DeviceMode::OUTDOOR;
  ioConfig.autoIrrigationEnabled = doc["autoIrrigationEnabled"] | ioConfig.autoIrrigationEnabled;
  ioConfig.rainThresholdPct     = doc["rainThresholdPct"] | ioConfig.rainThresholdPct;
  ioConfig.wetConfirmMs         = doc["wetConfirmMs"] | ioConfig.wetConfirmMs;
  ioConfig.dryRecoverMs         = doc["dryRecoverMs"] | ioConfig.dryRecoverMs;
  ioConfig.reservoirFullGapIn   = doc["reservoirFullGapIn"] | ioConfig.reservoirFullGapIn;
  ioConfig.reservoirEmptyGapIn  = doc["reservoirEmptyGapIn"] | ioConfig.reservoirEmptyGapIn;
  ioConfig.reservoirLowPct      = doc["reservoirLowPct"] | ioConfig.reservoirLowPct;
  ioConfig.reservoirCriticalPct = doc["reservoirCriticalPct"] | ioConfig.reservoirCriticalPct;
  ioConfig.tempHighC            = doc["tempHighC"] | ioConfig.tempHighC;
  ioConfig.humLowRh             = doc["humLowRh"] | ioConfig.humLowRh;
  ioConfig.lightHighPct         = doc["lightHighPct"] | ioConfig.lightHighPct;
  ioConfig.lightLowPct          = doc["lightLowPct"] | ioConfig.lightLowPct;
  ioConfig.buzzerMuted          = doc["buzzerMuted"] | ioConfig.buzzerMuted;
  buzzer.setMuted(ioConfig.buzzerMuted);

  // Always keep physical 4-channel relay outputs ENABLED so an older cloud /config
  // with "physicalLoadsEnabled": false never silently turns off valves & pump.
  ioConfig.physicalLoadsEnabled = true;
  relays.setPhysicalLoadsEnabled(true);
  if (!doc["physicalLoadsEnabled"].isNull() && !doc["physicalLoadsEnabled"].as<bool>()) {
    String fixResp;
    httpRequestJson("PATCH", url, "{\"physicalLoadsEnabled\":true}", fixResp);
  }

  JsonObject crops = doc["crops"];
  if (!crops.isNull()) {
    const char* slugs[3] = {"tomato", "chili", "eggplant"};
    for (int i = 0; i < 3; ++i) {
      JsonObject c = crops[slugs[i]];
      if (!c.isNull()) {
        ioConfig.zones[i].enabled           = c["enabled"] | ioConfig.zones[i].enabled;
        ioConfig.zones[i].dryRaw            = c["dryRaw"] | ioConfig.zones[i].dryRaw;
        ioConfig.zones[i].wetRaw            = c["wetRaw"] | ioConfig.zones[i].wetRaw;
        ioConfig.zones[i].startThresholdPct = c["startPct"] | ioConfig.zones[i].startThresholdPct;
        ioConfig.zones[i].stopThresholdPct  = c["stopPct"] | ioConfig.zones[i].stopThresholdPct;
        ioConfig.zones[i].pulseDurationMs   = c["pulseMs"] | ioConfig.zones[i].pulseDurationMs;
        ioConfig.zones[i].maxPumpOnMs       = c["maxRuntimeMs"] | ioConfig.zones[i].maxPumpOnMs;
        ioConfig.zones[i].cooldownMs        = c["cooldownMs"] | ioConfig.zones[i].cooldownMs;
      }
    }
    provisioning_->saveHardwareCalibration(ioConfig);
  }
}

void FirebaseClientManager::syncPendingCommands(
    uint32_t nowMs,
    const RuntimeConfig& cfg,
    IrrigationController& irrigation,
    BuzzerManager& buzzer) {
  String url = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) + "/commandRequests.json");
  String resp;
  if (!httpRequestJson("GET", url, "", resp)) {
    return;
  }

  JsonDocument doc;
  if (deserializeJson(doc, resp) != DeserializationError::Ok || !doc.is<JsonObject>()) {
    return;
  }

  for (JsonPair kv : doc.as<JsonObject>()) {
    JsonObject cmdObj = kv.value().as<JsonObject>();
    const char* state = cmdObj["state"] | "PENDING";
    if (strcmp(state, "PENDING") != 0) {
      continue;
    }

    CommandRequest req;
    req.valid = true;
    snprintf(req.commandId, sizeof(req.commandId), "%s", kv.key().c_str());

    const char* typeStr = cmdObj["type"] | "";
    if (strcmp(typeStr, "WATER_CROP") == 0) {
      req.type = CommandType::WATER_CROP;
    } else if (strcmp(typeStr, "STOP_WATERING") == 0) {
      req.type = CommandType::STOP_WATERING;
    } else if (strcmp(typeStr, "CLEAR_EMERGENCY") == 0) {
      req.type = CommandType::CLEAR_EMERGENCY;
    } else if (strcmp(typeStr, "MUTE_BUZZER") == 0) {
      req.type = CommandType::MUTE_BUZZER;
      buzzer.setMuted(true);
    } else {
      req.type = CommandType::NONE;
    }

    const char* cropStr = cmdObj["crop"] | "none";
    if (strcmp(cropStr, "tomato") == 0) {
      req.crop = CropZoneId::TOMATO;
    } else if (strcmp(cropStr, "chili") == 0) {
      req.crop = CropZoneId::CHILI;
    } else if (strcmp(cropStr, "eggplant") == 0) {
      req.crop = CropZoneId::EGGPLANT;
    } else {
      req.crop = CropZoneId::NONE;
    }

    req.durationMs     = cmdObj["durationMs"] | Config::DEFAULT_WATER_PULSE_MS;
    req.ownershipEpoch = cmdObj["ownershipEpoch"] | 0;
    req.createdAtMs    = cmdObj["createdAt"] | 0ULL;
    req.expiresAtMs    = cmdObj["expiresAt"] | 0ULL;

    irrigation.handleCommand(req,
                             nowMs,
                             currentEpochMs(),
                             provisioning_->ownershipEpoch(),
                             cfg,
                             provisioning_->ownershipState());

    String ackUrl = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) +
                                 "/commandRequests/" + String(req.commandId) + "/state.json");
    String ackResp;
    httpRequestJson("PUT", ackUrl, "\"PROCESSED\"", ackResp);
  }
}

bool FirebaseClientManager::sendResetAckToBackend(uint32_t nowMs) {
  (void)nowMs;
  if (sparkDirectMode_) {
    // On Free Spark Plan, transition /meta directly in RTDB
    String url = buildRtdbUrl("/devices/" + String(provisioning_->deviceId()) + "/meta.json");
    JsonDocument doc;
    doc["ownershipState"]    = "UNCLAIMED";
    doc["ownerUid"]          = nullptr;
    doc["resetRequired"]     = false;
    doc["lastResetAckEpoch"] = provisioning_->pendingResetTargetEpoch();
    String body;
    serializeJson(doc, body);
    String resp;
    return httpRequestJson("PATCH", url, body, resp);
  }

  String url = String(Secrets::BACKEND_FUNCTIONS_BASE_URL) + "/acknowledgeDeviceReset";
  JsonDocument doc;
  doc["deviceId"]          = provisioning_->deviceId();
  doc["deviceAuthSecret"]  = provisioning_->deviceAuthSecret();
  doc["acknowledgedEpoch"] = provisioning_->pendingResetTargetEpoch();

  String body;
  serializeJson(doc, body);
  String resp;
  return httpRequestJson("POST", url, body, resp);
}

bool FirebaseClientManager::httpRequestJson(const char* method,
                                            const String& url,
                                            const String& payload,
                                            String& outResponse) {
  if (WiFi.status() != WL_CONNECTED) {
    return false;
  }

  const bool isHttps = url.startsWith("https://");
  WiFiClient       plainClient;
  WiFiClientSecure secureClient;
  HTTPClient       http;
  http.setTimeout(Config::HTTP_TIMEOUT_MS);

  bool began = false;
  if (isHttps) {
    secureClient.setInsecure();
    secureClient.setTimeout(Config::HTTP_TIMEOUT_MS / 1000 + 1);
    began = http.begin(secureClient, url);
  } else {
    plainClient.setTimeout(Config::HTTP_TIMEOUT_MS / 1000 + 1);
    began = http.begin(plainClient, url);
  }
  if (!began) {
    return false;
  }

  http.addHeader("Content-Type", "application/json");
  int statusCode = -1;
  if (strcmp(method, "GET") == 0) {
    statusCode = http.GET();
  } else if (strcmp(method, "POST") == 0) {
    statusCode = http.POST(payload);
  } else if (strcmp(method, "PUT") == 0) {
    statusCode = http.PUT(payload);
  } else if (strcmp(method, "PATCH") == 0) {
    statusCode = http.PATCH(payload);
  }

  if (statusCode >= 200 && statusCode < 300) {
    outResponse = http.getString();
    http.end();
    return true;
  }

  if (isHttps && (statusCode == 401 || statusCode == 403)) {
    idTokenValid_        = false;
    idToken_             = "";
    lastAuthAttemptAtMs_ = 0;
  }
  String errSnippet = (statusCode > 0) ? http.getString() : String();
  if (errSnippet.length() > 100) {
    errSnippet = errSnippet.substring(0, 100);
  }
  errSnippet.replace("\n", " ");
  errSnippet.replace("\r", " ");
  Serial.printf("[HTTP] %s failed (code=%d) for %s %s\n",
                method, statusCode, url.c_str(), errSnippet.c_str());
  http.end();
  return false;
}
