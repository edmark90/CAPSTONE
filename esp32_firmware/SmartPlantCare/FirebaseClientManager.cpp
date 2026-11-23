/**
 * @file FirebaseClientManager.cpp
 * @brief Implementation of Wi-Fi, NTP, Firebase RTDB REST sync, and direct Open-Meteo
 *        fallback for Firebase Free Spark Plan compatibility.
 */

#include "FirebaseClientManager.h"
#include <esp_wifi.h>
#include <math.h>
#include <sys/time.h>
#include <time.h>

namespace {
constexpr uint32_t ID_TOKEN_REFRESH_INTERVAL_MS   = 3000000UL;  // Refresh at 50 min
constexpr uint32_t AUTH_RETRY_BACKOFF_MS          = 15000UL;
constexpr uint32_t DIRECT_WEATHER_FETCH_PERIOD_MS = 900000UL;   // Every 15 min on Spark Plan
constexpr uint32_t DIRECT_WEATHER_RETRY_MS        = 30000UL;    // Retry failed weather fetch after 30s
}

void FirebaseClientManager::begin(ProvisioningPairingManager* provisioning) {
  provisioning_ = provisioning;
  rtdbClient_.setInsecure();
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
    resetRtdbConnection();
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
  resetRtdbConnection();
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

    // Bootstrap wall-clock UTC time from Firebase's resolved server timestamp if SNTP hasn't synced yet
    if (time(nullptr) <= 1700000000L && resp.length() > 16) {
      JsonDocument respDoc;
      if (deserializeJson(respDoc, resp) == DeserializationError::Ok) {
        uint64_t serverTs = respDoc["status"]["lastSeenAt"] | 0ULL;
        if (serverTs < 1700000000000ULL) {
          serverTs = respDoc["telemetry"]["updatedAt"] | 0ULL;
        }
        if (serverTs >= 1700000000000ULL) {
          syncClockFromServerTimestamp(serverTs);
        }
      }
    }

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

void FirebaseClientManager::pollConfigOnly(
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    BuzzerManager& buzzer) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return;
  }
  if (provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }
  syncConfigNode(ioConfig, relays, buzzer);
}

void FirebaseClientManager::pollMetaOnly(
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return;
  }
  syncMetaNode(nowMs, ioConfig, relays, irrigation, buzzer, events, lcd);

  if (provisioning_->isResetAckPendingToCloud()) {
    if (sendResetAckToBackend(nowMs)) {
      resetRtdbConnection();
      provisioning_->finalizeLocalUnclaimAndEnterSetup(ioConfig, true);
    }
  }
}

void FirebaseClientManager::pollWeatherOnly(uint32_t nowMs, RainWeatherManager& rainWeather) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return;
  }
  if (provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }
  syncWeatherNode(nowMs, rainWeather);
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
  (void)ioDiag;
  pollMetaOnly(nowMs, ioConfig, relays, irrigation, buzzer, events, lcd);
  if (!provisioning_ || provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }
  pollWeatherOnly(nowMs, rainWeather);
  pollConfigOnly(nowMs, ioConfig, relays, buzzer);
  pollPendingCommandsOnly(nowMs, ioConfig, irrigation, buzzer);
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
        lastDirectWeatherAtMs_   = 0;
        weatherRefreshRequested_ = true;
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

void FirebaseClientManager::resetRtdbConnection() {
  if (rtdbHttpActive_) {
    rtdbHttp_.setReuse(false);
    rtdbHttp_.end();
    rtdbHttpActive_ = false;
  }
  rtdbClient_.stop();
}

bool FirebaseClientManager::httpRequestJson(const char* method,
                                            const String& url,
                                            const String& payload,
                                            String& outResponse) {
  if (WiFi.status() != WL_CONNECTED) {
    resetRtdbConnection();
    return false;
  }

  const bool isHttps = url.startsWith("https://");
  const bool isRtdbHttps =
      isHttps && url.startsWith(Secrets::FIREBASE_RTDB_BASE_URL);

  // Fast Path: Persistent HTTPS Keep-Alive socket dedicated to Firebase RTDB
  // Eliminates 1.5-3.0s SSL/TLS handshakes on every poll/upload (~0.2-0.4s per request!)
  if (isRtdbHttps) {
    for (int attempt = 0; attempt < 2; ++attempt) {
      if (!rtdbHttpActive_) {
        rtdbClient_.setInsecure();
        rtdbClient_.setTimeout(Config::HTTP_TIMEOUT_MS / 1000 + 1);
      }
      rtdbHttp_.setReuse(true);
      rtdbHttp_.setTimeout(Config::HTTP_TIMEOUT_MS);

      if (!rtdbHttp_.begin(rtdbClient_, url)) {
        resetRtdbConnection();
        continue;
      }
      rtdbHttpActive_ = true;
      rtdbHttp_.addHeader("Content-Type", "application/json");
      rtdbHttp_.addHeader("Connection", "keep-alive");

      int statusCode = -1;
      if (strcmp(method, "GET") == 0) {
        statusCode = rtdbHttp_.GET();
      } else if (strcmp(method, "POST") == 0) {
        statusCode = rtdbHttp_.POST(payload);
      } else if (strcmp(method, "PUT") == 0) {
        statusCode = rtdbHttp_.PUT(payload);
      } else if (strcmp(method, "PATCH") == 0) {
        statusCode = rtdbHttp_.PATCH(payload);
      } else if (strcmp(method, "DELETE") == 0) {
        statusCode = rtdbHttp_.sendRequest("DELETE");
      }

      if (statusCode >= 200 && statusCode < 300) {
        outResponse = rtdbHttp_.getString();
        // With setReuse(true), end() drains body & clears headers while keeping TLS open!
        rtdbHttp_.end();
        return true;
      }

      // Transport/idle-close error (<=0): tear down stale socket and silently retry once
      if (statusCode <= 0) {
        resetRtdbConnection();
        if (attempt == 0) {
          continue;
        }
        Serial.printf("[HTTP] %s transport error (code=%d) for %s\n",
                      method, statusCode, url.c_str());
        return false;
      }

      if (statusCode == 401 || statusCode == 403) {
        idTokenValid_        = false;
        idToken_             = "";
        lastAuthAttemptAtMs_ = 0;
      }
      String errSnippet = rtdbHttp_.getString();
      if (errSnippet.length() > 100) {
        errSnippet = errSnippet.substring(0, 100);
      }
      errSnippet.replace("\n", " ");
      errSnippet.replace("\r", " ");
      Serial.printf("[HTTP] %s failed (code=%d) for %s %s\n",
                    method, statusCode, url.c_str(), errSnippet.c_str());
      rtdbHttp_.end();
      return false;
    }
    return false;
  }

  // Non-RTDB hosts (Open-Meteo, Custom Token Cloud Functions): isolated temporary client
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
  } else if (strcmp(method, "DELETE") == 0) {
    statusCode = http.sendRequest("DELETE");
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

// ============================================================================
// 7-DAY HOURLY HISTORICAL MONITORING, IRRIGATION RECORDS & PHT RETENTION
// ============================================================================

void FirebaseClientManager::syncClockFromServerTimestamp(uint64_t serverEpochMs) {
  if (serverEpochMs < 1700000000000ULL) return;
  struct timeval tv;
  tv.tv_sec  = static_cast<time_t>(serverEpochMs / 1000ULL);
  tv.tv_usec = static_cast<suseconds_t>((serverEpochMs % 1000ULL) * 1000ULL);
  settimeofday(&tv, nullptr);
  Serial.printf("[TIME] Bootstrapped UTC clock from Firebase server timestamp (%llu ms).\n",
                static_cast<unsigned long long>(serverEpochMs));
}

bool FirebaseClientManager::formatPhDateAndHour(uint64_t epochMs,
                                                char* outDateKey,
                                                size_t dateLen,
                                                uint8_t& outHour) const {
  if (epochMs < 1700000000000ULL || !outDateKey || dateLen < 11) {
    return false;
  }
  time_t phSec = static_cast<time_t>(epochMs / 1000ULL) + Config::PHILIPPINES_UTC_OFFSET_SEC;
  struct tm tmPh;
  memset(&tmPh, 0, sizeof(tmPh));
  if (gmtime_r(&phSec, &tmPh) == nullptr) {
    return false;
  }
  snprintf(outDateKey, dateLen, "%04d-%02d-%02d",
           tmPh.tm_year + 1900, tmPh.tm_mon + 1, tmPh.tm_mday);
  outHour = static_cast<uint8_t>(tmPh.tm_hour);
  return true;
}

bool FirebaseClientManager::formatPhCutoffDate(uint64_t epochMs,
                                               uint8_t keepDays,
                                               char* outCutoffKey,
                                               size_t cutoffLen) const {
  if (epochMs < 1700000000000ULL || !outCutoffKey || cutoffLen < 11) {
    return false;
  }
  const uint8_t daysBack = (keepDays > 1) ? static_cast<uint8_t>(keepDays - 1) : 0;
  time_t cutoffPhSec = static_cast<time_t>(epochMs / 1000ULL) +
                       Config::PHILIPPINES_UTC_OFFSET_SEC -
                       static_cast<time_t>(daysBack) * 86400L;
  struct tm tmCut;
  memset(&tmCut, 0, sizeof(tmCut));
  if (gmtime_r(&cutoffPhSec, &tmCut) == nullptr) {
    return false;
  }
  snprintf(outCutoffKey, cutoffLen, "%04d-%02d-%02d",
           tmCut.tm_year + 1900, tmCut.tm_mon + 1, tmCut.tm_mday);
  return true;
}

void FirebaseClientManager::addSampleToAccumulator(HourlySensorAccumulator& acc,
                                                   uint64_t nowEpochMs,
                                                   const EnvironmentReading& env,
                                                   const SoilZoneStatus soil[3],
                                                   const ReservoirReading& reservoir,
                                                   const RainGateStatus& rain,
                                                   const WeatherSnapshot& weather) {
  if (acc.firstSeenAtEpochMs == 0 || nowEpochMs < acc.firstSeenAtEpochMs) {
    acc.firstSeenAtEpochMs = nowEpochMs;
  }
  if (nowEpochMs > acc.lastSeenAtEpochMs) {
    acc.lastSeenAtEpochMs = nowEpochMs;
  }
  if (acc.sampleCount < 65000) {
    ++acc.sampleCount;
  }
  if (rain.rawWet || rain.wetConfirmed) {
    if (acc.rainWetSamples < 65000) {
      ++acc.rainWetSamples;
    }
  }

  // Strictly accumulate ONLY valid readings; never insert 0 for invalid/faulted sensors!
  if (soil[0].valid && isfinite(soil[0].moisturePct)) {
    acc.tomatoSum += soil[0].moisturePct;
    ++acc.tomatoCnt;
  }
  if (soil[1].valid && isfinite(soil[1].moisturePct)) {
    acc.chiliSum += soil[1].moisturePct;
    ++acc.chiliCnt;
  }
  if (soil[2].valid && isfinite(soil[2].moisturePct)) {
    acc.eggplantSum += soil[2].moisturePct;
    ++acc.eggplantCnt;
  }
  if (env.dhtValid && isfinite(env.temperatureC)) {
    acc.tempSum += env.temperatureC;
    ++acc.tempCnt;
  }
  if (env.dhtValid && isfinite(env.humidityRh)) {
    acc.humSum += env.humidityRh;
    ++acc.humCnt;
  }
  if (env.lightValid && isfinite(env.brightnessPct)) {
    acc.lightSum += env.brightnessPct;
    ++acc.lightCnt;
  }
  if (reservoir.valid && isfinite(reservoir.waterLevelPct) && isfinite(reservoir.airGapInches)) {
    acc.reservoirPctSum += reservoir.waterLevelPct;
    acc.reservoirGapSum += reservoir.airGapInches;
    ++acc.reservoirCnt;
  }
  if (weather.valid && weather.probabilityPct >= 0 && weather.probabilityPct <= 100) {
    acc.rainProbSum += static_cast<double>(weather.probabilityPct);
    ++acc.rainProbCnt;
  }
}

void FirebaseClientManager::inspectExistingCloudHourOnReconnect(const char* dateKey,
                                                                uint8_t hour,
                                                                uint64_t nowEpochMs) {
  cloudHourInspectedForBoot_ = true;
  if (!provisioning_) return;

  char hourKey[4];
  snprintf(hourKey, sizeof(hourKey), "%02u", static_cast<unsigned int>(hour));
  String url = buildRtdbUrl("/deviceHistory/" + String(provisioning_->deviceId()) +
                            "/days/" + String(dateKey) + "/hours/" + String(hourKey) + ".json");
  String resp;
  if (!httpRequestJson("GET", url, "", resp) || resp.length() < 4 || resp == "null") {
    return;
  }

  JsonDocument doc;
  if (deserializeJson(doc, resp) != DeserializationError::Ok || !doc.is<JsonObject>()) {
    return;
  }

  const uint64_t existingFirst = doc["firstSeenAt"] | 0ULL;
  const uint64_t existingLast  = doc["lastSeenAt"] | 0ULL;
  const uint8_t  existingGaps  = static_cast<uint8_t>(doc["gapCount"] | 0);

  uint8_t maxSegIdxPlusOne = 0;
  JsonObject segs = doc["segments"];
  if (!segs.isNull()) {
    for (JsonPair kv : segs) {
      const char* k = kv.key().c_str();
      if (k && k[0] == 's' && k[1] >= '0' && k[1] <= '9') {
        uint8_t idx = static_cast<uint8_t>(atoi(k + 1));
        if (idx + 1 > maxSegIdxPlusOne) {
          maxSegIdxPlusOne = static_cast<uint8_t>(idx + 1);
        }
      }
    }
  } else if (existingLast > 1700000000000ULL) {
    maxSegIdxPlusOne = 1;
  }

  if (existingFirst > 1700000000000ULL && existingFirst < nowEpochMs) {
    hourAcc_.firstSeenAtEpochMs = existingFirst;
  }

  if (existingLast > 1700000000000ULL && nowEpochMs > existingLast) {
    const uint64_t gapMs = nowEpochMs - existingLast;
    if (gapMs >= Config::HISTORY_INTRA_HOUR_GAP_MS) {
      // Confirmed offline gap (>=5 min) within the same hour: preserve old segments & start a new one!
      activeHourGapCount_ = static_cast<uint8_t>(existingGaps + 1);
      activeSegIdx_       = (maxSegIdxPlusOne < Config::HISTORY_MAX_SEGMENTS_PER_HOUR)
                                ? maxSegIdxPlusOne
                                : static_cast<uint8_t>(Config::HISTORY_MAX_SEGMENTS_PER_HOUR - 1);
      Serial.printf("[HIST] Detected %lu s intra-hour offline gap on %s %02u:00 -> starting segment s%u.\n",
                    static_cast<unsigned long>(gapMs / 1000ULL),
                    dateKey,
                    static_cast<unsigned int>(hour),
                    static_cast<unsigned int>(activeSegIdx_));
    } else {
      activeHourGapCount_ = existingGaps;
      activeSegIdx_       = (maxSegIdxPlusOne < Config::HISTORY_MAX_SEGMENTS_PER_HOUR)
                                ? maxSegIdxPlusOne
                                : static_cast<uint8_t>(Config::HISTORY_MAX_SEGMENTS_PER_HOUR - 1);
    }
  }
}

void FirebaseClientManager::recordLiveSampleForHistory(uint32_t nowMs,
                                                       const EnvironmentReading& env,
                                                       const SoilZoneStatus soil[3],
                                                       const ReservoirReading& reservoir,
                                                       const RainGateStatus& rain,
                                                       const WeatherSnapshot& weather) {
  (void)nowMs;
  if (!provisioning_ || provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }
  const uint64_t nowEpochMs = currentEpochMs();
  if (nowEpochMs < 1700000000000ULL) {
    return;  // Wait for valid UTC wall-clock time before bucketing into PHT date/hour
  }

  char dateKey[12] = {0};
  uint8_t hour     = 0;
  if (!formatPhDateAndHour(nowEpochMs, dateKey, sizeof(dateKey), hour)) {
    return;
  }

  const bool slotChanged =
      !activeHistValid_ ||
      (strcmp(activeHistDateKey_, dateKey) != 0) ||
      (activeHistHour_ != hour);

  if (slotChanged) {
    // Finalize and flush previous hour before rolling over
    if (activeHistValid_ && activeHistDirty_ && segAcc_.sampleCount > 0) {
      flushHourlyAccumulatorToRtdb(activeHistDateKey_,
                                   activeHistHour_,
                                   activeSegIdx_,
                                   activeHourGapCount_,
                                   segAcc_,
                                   hourAcc_);
    }

    snprintf(activeHistDateKey_, sizeof(activeHistDateKey_), "%s", dateKey);
    activeHistHour_              = hour;
    activeSegIdx_                = 0;
    activeHourGapCount_          = 0;
    hourAcc_.reset();
    segAcc_.reset();
    activeHistValid_             = true;
    activeSegNeedsImmediateFlush_ = true;

    if (!cloudHourInspectedForBoot_) {
      inspectExistingCloudHourOnReconnect(dateKey, hour, nowEpochMs);
    }
  } else if (segAcc_.lastSeenAtEpochMs > 1700000000000ULL &&
             nowEpochMs > segAcc_.lastSeenAtEpochMs &&
             (nowEpochMs - segAcc_.lastSeenAtEpochMs) >= Config::HISTORY_INTRA_HOUR_GAP_MS) {
    // Reconnected within the same hour after a >=5 min Wi-Fi/internet outage!
    if (activeHistDirty_ && segAcc_.sampleCount > 0) {
      flushHourlyAccumulatorToRtdb(activeHistDateKey_,
                                   activeHistHour_,
                                   activeSegIdx_,
                                   activeHourGapCount_,
                                   segAcc_,
                                   hourAcc_);
    }
    if (activeSegIdx_ + 1 < Config::HISTORY_MAX_SEGMENTS_PER_HOUR) {
      ++activeSegIdx_;
    }
    ++activeHourGapCount_;
    segAcc_.reset();
    activeSegNeedsImmediateFlush_ = true;
  }

  addSampleToAccumulator(hourAcc_, nowEpochMs, env, soil, reservoir, rain, weather);
  addSampleToAccumulator(segAcc_, nowEpochMs, env, soil, reservoir, rain, weather);
  activeHistDirty_ = true;
}

bool FirebaseClientManager::flushHourlyAccumulatorToRtdb(const char* dateKey,
                                                         uint8_t hour,
                                                         uint8_t segIdx,
                                                         uint8_t gapCount,
                                                         const HourlySensorAccumulator& segAcc,
                                                         const HourlySensorAccumulator& hourAcc) {
  if (!provisioning_ || !dateKey || dateKey[0] == '\0' || segAcc.sampleCount == 0) {
    return false;
  }

  char hourKey[4];
  snprintf(hourKey, sizeof(hourKey), "%02u", static_cast<unsigned int>(hour));
  char segKey[8];
  snprintf(segKey, sizeof(segKey), "s%u", static_cast<unsigned int>(segIdx));

  // 1. Write segment node /deviceHistory/{id}/days/{date}/hours/{HH}/segments/s{idx}
  JsonDocument segDoc;
  segDoc["startAt"] = segAcc.firstSeenAtEpochMs;
  segDoc["endAt"]   = segAcc.lastSeenAtEpochMs;
  segDoc["samples"] = segAcc.sampleCount;

  if (segAcc.tomatoCnt > 0) segDoc["tomatoPct"] = serialized(String(segAcc.tomatoSum / segAcc.tomatoCnt, 1));
  else segDoc["tomatoPct"] = nullptr;

  if (segAcc.chiliCnt > 0) segDoc["chiliPct"] = serialized(String(segAcc.chiliSum / segAcc.chiliCnt, 1));
  else segDoc["chiliPct"] = nullptr;

  if (segAcc.eggplantCnt > 0) segDoc["eggplantPct"] = serialized(String(segAcc.eggplantSum / segAcc.eggplantCnt, 1));
  else segDoc["eggplantPct"] = nullptr;

  if (segAcc.tempCnt > 0) segDoc["temperatureC"] = serialized(String(segAcc.tempSum / segAcc.tempCnt, 1));
  else segDoc["temperatureC"] = nullptr;

  if (segAcc.humCnt > 0) segDoc["humidityPct"] = serialized(String(segAcc.humSum / segAcc.humCnt, 1));
  else segDoc["humidityPct"] = nullptr;

  if (segAcc.lightCnt > 0) segDoc["lightPct"] = serialized(String(segAcc.lightSum / segAcc.lightCnt, 1));
  else segDoc["lightPct"] = nullptr;

  if (segAcc.reservoirCnt > 0) {
    segDoc["reservoirPct"]   = serialized(String(segAcc.reservoirPctSum / segAcc.reservoirCnt, 1));
    segDoc["reservoirGapIn"] = serialized(String(segAcc.reservoirGapSum / segAcc.reservoirCnt, 2));
  } else {
    segDoc["reservoirPct"]   = nullptr;
    segDoc["reservoirGapIn"] = nullptr;
  }

  if (segAcc.rainProbCnt > 0) {
    segDoc["rainProbPct"] = static_cast<int>(lround(segAcc.rainProbSum / segAcc.rainProbCnt));
  } else {
    segDoc["rainProbPct"] = nullptr;
  }

  String segBody;
  serializeJson(segDoc, segBody);
  String baseDayPath = "/deviceHistory/" + String(provisioning_->deviceId()) + "/days/" + String(dateKey);
  String segUrl = buildRtdbUrl(baseDayPath + "/hours/" + String(hourKey) + "/segments/" + String(segKey) + ".json");
  String segResp;
  if (!httpRequestJson("PUT", segUrl, segBody, segResp)) {
    return false;
  }

  // 2. PATCH hour summary node (preserves existing sibling segments/s0..sN!)
  JsonDocument hourDoc;
  hourDoc["hour"]           = hour;
  hourDoc["firstSeenAt"]    = hourAcc.firstSeenAtEpochMs;
  hourDoc["lastSeenAt"]     = hourAcc.lastSeenAtEpochMs;
  hourDoc["sampleCount"]    = hourAcc.sampleCount;
  hourDoc["gapCount"]       = gapCount;
  hourDoc["rainWetSamples"] = hourAcc.rainWetSamples;

  if (hourAcc.tomatoCnt > 0) hourDoc["tomatoPct"] = serialized(String(hourAcc.tomatoSum / hourAcc.tomatoCnt, 1));
  else hourDoc["tomatoPct"] = nullptr;

  if (hourAcc.chiliCnt > 0) hourDoc["chiliPct"] = serialized(String(hourAcc.chiliSum / hourAcc.chiliCnt, 1));
  else hourDoc["chiliPct"] = nullptr;

  if (hourAcc.eggplantCnt > 0) hourDoc["eggplantPct"] = serialized(String(hourAcc.eggplantSum / hourAcc.eggplantCnt, 1));
  else hourDoc["eggplantPct"] = nullptr;

  if (hourAcc.tempCnt > 0) hourDoc["temperatureC"] = serialized(String(hourAcc.tempSum / hourAcc.tempCnt, 1));
  else hourDoc["temperatureC"] = nullptr;

  if (hourAcc.humCnt > 0) hourDoc["humidityPct"] = serialized(String(hourAcc.humSum / hourAcc.humCnt, 1));
  else hourDoc["humidityPct"] = nullptr;

  if (hourAcc.lightCnt > 0) hourDoc["lightPct"] = serialized(String(hourAcc.lightSum / hourAcc.lightCnt, 1));
  else hourDoc["lightPct"] = nullptr;

  if (hourAcc.reservoirCnt > 0) {
    hourDoc["reservoirPct"]   = serialized(String(hourAcc.reservoirPctSum / hourAcc.reservoirCnt, 1));
    hourDoc["reservoirGapIn"] = serialized(String(hourAcc.reservoirGapSum / hourAcc.reservoirCnt, 2));
  } else {
    hourDoc["reservoirPct"]   = nullptr;
    hourDoc["reservoirGapIn"] = nullptr;
  }

  if (hourAcc.rainProbCnt > 0) {
    hourDoc["rainProbPct"] = static_cast<int>(lround(hourAcc.rainProbSum / hourAcc.rainProbCnt));
  } else {
    hourDoc["rainProbPct"] = nullptr;
  }

  String hourBody;
  serializeJson(hourDoc, hourBody);
  String hourUrl = buildRtdbUrl(baseDayPath + "/hours/" + String(hourKey) + ".json");
  String hourResp;
  if (!httpRequestJson("PATCH", hourUrl, hourBody, hourResp)) {
    return false;
  }

  // 3. Patch lightweight day metadata
  JsonDocument dayMeta;
  dayMeta["dateKey"]           = dateKey;
  dayMeta["updatedAt"][".sv"]  = "timestamp";
  String dayMetaBody;
  serializeJson(dayMeta, dayMetaBody);
  String dayUrl = buildRtdbUrl(baseDayPath + ".json");
  String dayResp;
  httpRequestJson("PATCH", dayUrl, dayMetaBody, dayResp);
  return true;
}

void FirebaseClientManager::flushPendingIrrigationHistory(uint32_t nowMs,
                                                          IrrigationController& irrigation,
                                                          DiagnosticsMetrics& ioDiag) {
  const uint64_t nowEpochMs = currentEpochMs();
  if (nowEpochMs < 1700000000000ULL) {
    return;  // Keep queued in RAM until real wall-clock epoch is synced
  }

  char cutoffKey[12] = {0};
  formatPhCutoffDate(nowEpochMs, Config::HISTORY_RETENTION_DAYS, cutoffKey, sizeof(cutoffKey));

  for (int i = 0; i < 2; ++i) {
    IrrigationHistoryRecord rec;
    if (!irrigation.peekPendingIrrigationHistory(rec)) {
      break;
    }

    // Reconstruct exact real timestamp from monotonic uptime delta if cycle ran offline pre-NTP
    uint64_t resolvedEndEpoch = rec.endedAtEpochMs;
    if (resolvedEndEpoch < 1700000000000ULL) {
      const uint32_t ageMs = uint32_t(nowMs - rec.endedAtUptimeMs);
      resolvedEndEpoch = (nowEpochMs > ageMs) ? (nowEpochMs - ageMs) : nowEpochMs;
    }
    uint64_t resolvedStartEpoch = rec.startedAtEpochMs;
    if (resolvedStartEpoch < 1700000000000ULL) {
      resolvedStartEpoch = (resolvedEndEpoch > rec.durationMs)
                               ? (resolvedEndEpoch - rec.durationMs)
                               : resolvedEndEpoch;
    }

    char dateKey[12] = {0};
    uint8_t hour     = 0;
    if (!formatPhDateAndHour(resolvedStartEpoch, dateKey, sizeof(dateKey), hour)) {
      irrigation.popOldestIrrigationHistory();
      continue;
    }

    // Never upload expired records older than 7-day retention window
    if (cutoffKey[0] != '\0' && strcmp(dateKey, cutoffKey) < 0) {
      irrigation.popOldestIrrigationHistory();
      continue;
    }

    char stableId[56];
    snprintf(stableId, sizeof(stableId), "irr_%llu_%s",
             static_cast<unsigned long long>(resolvedStartEpoch),
             rec.crop);

    JsonDocument doc;
    doc["irrigationId"]    = stableId;
    doc["crop"]            = rec.crop;
    doc["trigger"]         = rec.isManual ? "MANUAL" : "AUTO";
    doc["status"]          = rec.completed ? "COMPLETED" : "ABORTED";
    doc["reason"]          = rec.reason;
    doc["startedAt"]       = resolvedStartEpoch;
    doc["endedAt"]         = resolvedEndEpoch;
    doc["hour"]            = hour;
    doc["durationMs"]      = rec.durationMs;
    doc["recordedOffline"] = rec.recordedOffline;
    doc["ownershipEpoch"]  = provisioning_->ownershipEpoch();
    if (isfinite(rec.startMoisturePct)) {
      doc["startMoisturePct"] = serialized(String(rec.startMoisturePct, 1));
    } else {
      doc["startMoisturePct"] = nullptr;
    }
    if (isfinite(rec.endMoisturePct)) {
      doc["endMoisturePct"] = serialized(String(rec.endMoisturePct, 1));
    } else {
      doc["endMoisturePct"] = nullptr;
    }

    String body;
    serializeJson(doc, body);
    String url = buildRtdbUrl("/deviceHistory/" + String(provisioning_->deviceId()) +
                              "/days/" + String(dateKey) +
                              "/irrigations/" + String(stableId) + ".json");
    String resp;
    if (httpRequestJson("PUT", url, body, resp)) {
      irrigation.popOldestIrrigationHistory();
      ioDiag.firebaseSyncSuccessCnt++;

      // Ensure day metadata exists even if irrigation ran before the first hourly sensor flush
      JsonDocument dayMeta;
      dayMeta["dateKey"]          = dateKey;
      dayMeta["updatedAt"][".sv"] = "timestamp";
      String metaBody;
      serializeJson(dayMeta, metaBody);
      String metaUrl = buildRtdbUrl("/deviceHistory/" + String(provisioning_->deviceId()) +
                                    "/days/" + String(dateKey) + ".json");
      String metaResp;
      httpRequestJson("PATCH", metaUrl, metaBody, metaResp);
    } else {
      ioDiag.firebaseSyncFailCnt++;
      break;  // Keep in RAM queue to retry on next tick!
    }
  }
}

void FirebaseClientManager::pruneExpiredHistoryDaysIfDue(uint32_t nowMs, uint64_t nowEpochMs) {
  if (!provisioning_ || nowEpochMs < 1700000000000ULL) return;

  char todayKey[12]  = {0};
  char cutoffKey[12] = {0};
  uint8_t dummyHour  = 0;
  if (!formatPhDateAndHour(nowEpochMs, todayKey, sizeof(todayKey), dummyHour) ||
      !formatPhCutoffDate(nowEpochMs, Config::HISTORY_RETENTION_DAYS, cutoffKey, sizeof(cutoffKey))) {
    return;
  }

  // Already pruned today in Philippine Local Time
  if (strcmp(lastPrunedDateKey_, todayKey) == 0) {
    return;
  }
  if (lastPruneAttemptAtMs_ != 0 && uint32_t(nowMs - lastPruneAttemptAtMs_) < 60000UL) {
    return;
  }
  lastPruneAttemptAtMs_ = (nowMs == 0) ? 1UL : nowMs;

  String shallowUrl = buildRtdbUrl("/deviceHistory/" + String(provisioning_->deviceId()) +
                                   "/days.json?shallow=true");
  String resp;
  if (!httpRequestJson("GET", shallowUrl, "", resp)) {
    return;
  }

  if (resp.length() > 2 && resp != "null") {
    JsonDocument doc;
    if (deserializeJson(doc, resp) == DeserializationError::Ok && doc.is<JsonObject>()) {
      for (JsonPair kv : doc.as<JsonObject>()) {
        const char* dayKey = kv.key().c_str();
        if (dayKey && strlen(dayKey) == 10 && strcmp(dayKey, cutoffKey) < 0) {
          String delUrl = buildRtdbUrl("/deviceHistory/" + String(provisioning_->deviceId()) +
                                       "/days/" + String(dayKey) + ".json");
          String delResp;
          if (httpRequestJson("DELETE", delUrl, "", delResp)) {
            Serial.printf("[HIST] Pruned expired history day %s (cutoff %s).\n", dayKey, cutoffKey);
          }
        }
      }
    }
  }

  snprintf(lastPrunedDateKey_, sizeof(lastPrunedDateKey_), "%s", todayKey);
  JsonDocument rootMeta;
  rootMeta["ownershipEpoch"] = provisioning_->ownershipEpoch();
  rootMeta["lastPrunedDate"] = todayKey;
  String rootBody;
  serializeJson(rootMeta, rootBody);
  String rootUrl = buildRtdbUrl("/deviceHistory/" + String(provisioning_->deviceId()) + ".json");
  String rootResp;
  httpRequestJson("PATCH", rootUrl, rootBody, rootResp);
}

void FirebaseClientManager::serviceHistorySyncAndRetention(uint32_t nowMs,
                                                           IrrigationController& irrigation,
                                                           DiagnosticsMetrics& ioDiag) {
  if (!ensureValidAuth(nowMs) || !provisioning_) {
    return;
  }
  if (provisioning_->ownershipState() != OwnershipState::OWNED) {
    return;
  }

  // 1. Upload any completed/aborted irrigation cycles immediately
  flushPendingIrrigationHistory(nowMs, irrigation, ioDiag);

  // 2. Flush hourly telemetry on first sample of a new hour/segment, or every 5 minutes
  const bool periodicDue =
      (lastHistoryFlushAtMs_ == 0) ||
      (uint32_t(nowMs - lastHistoryFlushAtMs_) >= Config::INTERVAL_HISTORY_FLUSH_MS);

  if (activeHistValid_ && activeHistDirty_ && segAcc_.sampleCount > 0 &&
      (activeSegNeedsImmediateFlush_ || periodicDue)) {
    if (flushHourlyAccumulatorToRtdb(activeHistDateKey_,
                                     activeHistHour_,
                                     activeSegIdx_,
                                     activeHourGapCount_,
                                     segAcc_,
                                     hourAcc_)) {
      activeHistDirty_             = false;
      activeSegNeedsImmediateFlush_ = false;
      lastHistoryFlushAtMs_        = (nowMs == 0) ? 1UL : nowMs;
    }
  }

  // 3. Enforce strict 7-day Philippine calendar retention once per day
  pruneExpiredHistoryDaysIfDue(nowMs, currentEpochMs());
}
