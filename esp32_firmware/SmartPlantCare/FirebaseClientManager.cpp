/**
 * @file FirebaseClientManager.cpp
 * @brief Implementation of Wi-Fi, NTP, Firebase RTDB REST sync, and direct Open-Meteo
 *        fallback for Firebase Free Spark Plan compatibility.
 */

#include "FirebaseClientManager.h"
#include <time.h>

namespace {
constexpr uint32_t ID_TOKEN_REFRESH_INTERVAL_MS   = 3000000UL;  // Refresh at 50 min
constexpr uint32_t AUTH_RETRY_BACKOFF_MS          = 15000UL;
constexpr uint32_t DIRECT_WEATHER_FETCH_PERIOD_MS = 900000UL;   // Every 15 min on Spark Plan
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

void FirebaseClientManager::serviceNetworkAndTime(uint32_t nowMs, DiagnosticsMetrics& ioDiag) {
  if (!provisioning_ || !provisioning_->hasWifiCredentials()) {
    return;
  }

  if (WiFi.status() == WL_CONNECTED) {
    wifiAttemptActive_ = false;
    wifiBackoffMs_     = Config::WIFI_RECONNECT_MIN_MS;
    ioDiag.wifiRssiDbm = static_cast<int8_t>(WiFi.RSSI());

    if (!ntpConfigured_) {
      configTime(0, 0, "pool.ntp.org", "time.google.com");
      ntpConfigured_ = true;
      Serial.printf("[NET] Wi-Fi connected (IP=%s, RSSI=%d dBm). NTP sync started.\n",
                    WiFi.localIP().toString().c_str(),
                    static_cast<int>(ioDiag.wifiRssiDbm));
    }
    return;
  }

  ioDiag.wifiRssiDbm = -127;
  if (nowMs - lastWifiAttemptAtMs_ >= wifiBackoffMs_) {
    lastWifiAttemptAtMs_ = nowMs;
    wifiAttemptActive_   = true;
    ioDiag.wifiReconnectCount++;

    Serial.printf("[NET] Connecting to Wi-Fi SSID '%s' (backoff=%lu ms)...\n",
                  provisioning_->wifiSsid(),
                  static_cast<unsigned long>(wifiBackoffMs_));
    WiFi.disconnect(false, false);
    WiFi.begin(provisioning_->wifiSsid(), provisioning_->wifiPassword());

    wifiBackoffMs_ = min(wifiBackoffMs_ * 2UL, Config::WIFI_RECONNECT_MAX_MS);
  }
}

bool FirebaseClientManager::ensureValidAuth(uint32_t nowMs) {
  if (!isWifiConnected() || !provisioning_) {
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

  uint64_t epochMs = currentEpochMs();
  JsonDocument root;

  JsonObject st = root["status"].to<JsonObject>();
  st["online"]                  = true;
  st["lastSeenAt"]              = epochMs;
  st["bootId"]                  = provisioning_->bootId();
  st["firmwareVersion"]         = Config::FIRMWARE_VERSION;
  st["ipAddress"]               = WiFi.localIP().toString();
  st["wifiRssi"]                = static_cast<int>(ioDiag.wifiRssiDbm);
  st["mode"]                    = (cfg.mode == DeviceMode::OUTDOOR) ? "OUTDOOR" : "INDOOR";
  st["rainSuspensionActive"]    = rain.rainSuspensionLatched;
  st["weatherAdvisoryActive"]   = rain.forecastAdvisoryActive;
  st["weatherUnavailableFault"] = rain.weatherUnavailableFault;
  st["emergencyStop"]           = irrigation.isEmergencyStopped();
  st["faultLockout"]            = irrigation.isFaultLocked();
  st["activeValve"]             = cropZoneSlug(relays.activeValveZone());
  st["pumpState"]               = relays.isPumpOn() ? "ON" : "OFF";
  st["irrigationState"]         = irrigationStateString(irrigation.state());
  st["physicalLoadsEnabled"]    = relays.isPhysicalLoadsEnabled();
  st["lcdAddress"]              = lcdAddr;
  st["freeHeap"]                = ioDiag.freeHeapBytes;
  st["ownershipEpoch"]          = provisioning_->ownershipEpoch();

  JsonObject tel = root["telemetry"].to<JsonObject>();
  tel["updatedAt"]     = epochMs;
  tel["dhtValid"]      = env.dhtValid;
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

  tel["rainRawWet"]       = rain.rawWet;
  tel["rainWetConfirmed"] = rain.wetConfirmed;
  tel["rainValid"]        = rain.sensorValid;

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
    doc["commandId"]      = report.commandId;
    doc["state"]          = report.state;
    doc["reason"]         = report.reason;
    doc["updatedAt"]      = currentEpochMs();
    doc["ownershipEpoch"] = provisioning_->ownershipEpoch();

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
    evDoc["eventId"]        = ev.eventId;
    evDoc["type"]           = ev.type;
    evDoc["severity"]       = severityToString(ev.severity);
    evDoc["title"]          = ev.title;
    evDoc["message"]        = ev.message;
    evDoc["crop"]           = strlen(ev.crop) > 0 ? ev.crop : nullptr;
    evDoc["dedupeKey"]      = ev.dedupeKey;
    evDoc["createdAt"]      = currentEpochMs();
    evDoc["ownershipEpoch"] = ev.ownershipEpoch;
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
      break;
    }
  }
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
      provisioning_->finalizeLocalUnclaimAndEnterSetup(ioConfig, false);
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
  if (deserializeJson(doc, resp) != DeserializationError::Ok || doc.isNull()) {
    return;
  }

  const char* stateStr = doc["ownershipState"] | "UNCLAIMED";
  uint32_t    epoch    = doc["ownershipEpoch"] | 0;
  bool        resetReq = doc["resetRequired"] | false;

  if (!doc["location"]["lat"].isNull() && !doc["location"]["lon"].isNull()) {
    deviceLat_ = doc["location"]["lat"].as<float>();
    deviceLon_ = doc["location"]["lon"].as<float>();
  }

  provisioning_->handleCloudMetaSnapshot(
      stateStr, epoch, resetReq, ioConfig, relays, irrigation, buzzer, events, lcd);
  lastSuccessfulSyncMs_ = nowMs;
}

void FirebaseClientManager::syncWeatherNode(uint32_t nowMs, RainWeatherManager& rainWeather) {
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

      uint64_t nowEpoch = currentEpochMs();
      if (validUntil > 0 && nowEpoch > 1700000000000ULL && nowEpoch > validUntil) {
        valid = false;
        needDirectFetch = true;
      }
      if (!valid || probPct < 0) {
        needDirectFetch = true;
      } else {
        rainWeather.updateWeatherSnapshot(valid, probPct, fetchedAt, validUntil, nowMs, winLabel);
        return;
      }
    } else {
      needDirectFetch = true;
    }
  } else {
    needDirectFetch = true;
  }

  // On Free Spark Plan (no scheduled Cloud Function), ESP32 fetches Open-Meteo directly every 15m
  if (needDirectFetch &&
      (lastDirectWeatherAtMs_ == 0 || (nowMs - lastDirectWeatherAtMs_ >= DIRECT_WEATHER_FETCH_PERIOD_MS))) {
    lastDirectWeatherAtMs_ = nowMs;
    fetchOpenMeteoDirectAndCache(nowMs, rainWeather);
  }
}

bool FirebaseClientManager::fetchOpenMeteoDirectAndCache(uint32_t nowMs,
                                                         RainWeatherManager& rainWeather) {
  String omUrl = String("https://api.open-meteo.com/v1/forecast?latitude=") +
                 String(deviceLat_, 4) + "&longitude=" + String(deviceLon_, 4) +
                 "&hourly=precipitation_probability&forecast_hours=3&timezone=auto";

  String omResp;
  if (!httpRequestJson("GET", omUrl, "", omResp)) {
    Serial.println(F("[WEATHER] Direct Open-Meteo fetch failed."));
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

  rainWeather.updateWeatherSnapshot(true, maxProb, nowEpoch, validUntil, nowMs, "NEXT 3 H");
  Serial.printf("[WEATHER] Direct Open-Meteo 3h max rainProbabilityPct=%d%%\n", maxProb);

  // Cache snapshot back to RTDB /devices/{deviceId}/weather so Android app sees it too
  JsonDocument cacheDoc;
  cacheDoc["provider"]           = "open-meteo-esp32";
  cacheDoc["valid"]              = true;
  cacheDoc["rainProbabilityPct"] = maxProb;
  cacheDoc["windowHours"]        = 3;
  cacheDoc["windowLabel"]        = "NEXT 3 H";
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

  if (!doc["physicalLoadsEnabled"].isNull()) {
    ioConfig.physicalLoadsEnabled = doc["physicalLoadsEnabled"].as<bool>();
    relays.setPhysicalLoadsEnabled(ioConfig.physicalLoadsEnabled);
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

  WiFiClientSecure client;
  client.setInsecure();
  client.setTimeout(Config::HTTP_TIMEOUT_MS / 1000 + 1);

  HTTPClient http;
  http.setTimeout(Config::HTTP_TIMEOUT_MS);
  if (!http.begin(client, url)) {
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

  if (statusCode == 401 || statusCode == 403) {
    idTokenValid_ = false;
  }
  http.end();
  return false;
}
