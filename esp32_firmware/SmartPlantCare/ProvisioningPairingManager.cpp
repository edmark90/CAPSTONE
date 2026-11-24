/**
 * @file ProvisioningPairingManager.cpp
 * @brief Implementation of NVS provisioning, Owner Wireless Setup Hotspot (WIFI_AP_STA +
 *        2.4 GHz /api/wifi-scan + /api/info + /api/provision + /api/provision-status),
 *        LIVE Wi-Fi password verification BEFORE saving/entering OWNED, custom displayName,
 *        buzzer confirmation on successful connect, and RESET_PENDING lifecycle.
 */

#include "ProvisioningPairingManager.h"
#include <ArduinoJson.h>
#include <esp_system.h>
#include <stdio.h>
#include <string.h>

namespace {
constexpr const char* NVS_NAMESPACE             = "spc_v31";
constexpr const char* DEFAULT_DISPLAY_NAME      = "SmartPlantCare Box";
constexpr uint32_t    HTTP_ACK_SETTLE_MS        = 300UL;    // Flush HTTP 202 before touching Wi-Fi radio
constexpr uint32_t    PROFILE_STALL_TIMEOUT_MS  = 5500UL;   // Advance profile if no link/event within 5.5s
constexpr uint32_t    RETRY_SETTLE_DELAY_MS     = 400UL;    // Pause between controlled STA retries
constexpr uint32_t    AP_STA_PHASE_MAX_MS       = 16000UL;  // Max time in dual AP+STA before pure-STA fallback
constexpr uint32_t    PURE_STA_PHASE_MAX_MS     = 10000UL;  // Max time in pure-STA fallback
constexpr uint32_t    WIFI_L2_CONFIRM_HOLD_MS   = 2500UL;   // 2.5s stable STA_CONNECTED proves WPA2 key valid
constexpr uint32_t    AP_CLOSE_GRACE_MS         = 8000UL;   // Keep AP open up to 8s so Android reads CONNECTED
constexpr uint32_t    AP_CLOSE_AFTER_ACK_MS     = 1500UL;   // Close AP 1.5s after Android confirms CONNECTED

struct WifiScanEntry {
  char    ssid[33];
  int     rssi;
  int32_t channel;
  uint8_t authMode;
  uint8_t bssid[6];
  bool    hasBssid;
  bool    secure;
};

ProvisioningPairingManager* gActiveProvInstance  = nullptr;
bool                        gWifiEventRegistered = false;

void handleWifiStaEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  if (!gActiveProvInstance) {
    return;
  }
  switch (event) {
    case ARDUINO_EVENT_WIFI_STA_CONNECTED:
      gActiveProvInstance->onStaConnected();
      break;
    case ARDUINO_EVENT_WIFI_STA_GOT_IP:
      gActiveProvInstance->onStaGotIp();
      break;
    case ARDUINO_EVENT_WIFI_STA_DISCONNECTED:
      gActiveProvInstance->onStaDisconnectReason(info.wifi_sta_disconnected.reason);
      break;
    default:
      break;
  }
}

const char* wifiReasonToString(uint8_t reason) {
  switch (reason) {
    case 0:   return "NONE";
    case 1:   return "UNSPECIFIED";
    case 2:   return "AUTH_EXPIRE";
    case 3:   return "AUTH_LEAVE";
    case 4:   return "ASSOC_EXPIRE";
    case 6:   return "NOT_AUTHED";
    case 7:   return "NOT_ASSOCED";
    case 8:   return "ASSOC_LEAVE";
    case 14:  return "MIC_FAILURE";
    case 15:  return "4WAY_HANDSHAKE_TIMEOUT";
    case 16:  return "GROUP_KEY_UPDATE_TIMEOUT";
    case 23:  return "802_1X_AUTH_FAILED";
    case 200: return "BEACON_TIMEOUT";
    case 201: return "NO_AP_FOUND";
    case 202: return "AUTH_FAIL";
    case 203: return "ASSOC_FAIL";
    case 204: return "HANDSHAKE_TIMEOUT";
    case 205: return "CONNECTION_FAIL";
    case 210: return "NO_AP_COMPAT_SECURITY";
    case 211: return "NO_AP_AUTHMODE_THRESHOLD";
    case 212: return "NO_AP_RSSI_THRESHOLD";
    default:  return "WIFI_ERR";
  }
}

const char* authModeToString(uint8_t authMode) {
  switch (authMode) {
    case WIFI_AUTH_OPEN:         return "OPEN";
    case WIFI_AUTH_WEP:          return "WEP";
    case WIFI_AUTH_WPA_PSK:      return "WPA";
    case WIFI_AUTH_WPA2_PSK:     return "WPA2";
    case WIFI_AUTH_WPA_WPA2_PSK: return "WPA/WPA2";
    case WIFI_AUTH_WPA2_ENTERPRISE: return "WPA2-ENT";
#ifdef WIFI_AUTH_WPA3_PSK
    case WIFI_AUTH_WPA3_PSK:     return "WPA3";
#endif
#ifdef WIFI_AUTH_WPA2_WPA3_PSK
    case WIFI_AUTH_WPA2_WPA3_PSK: return "WPA2/WPA3";
#endif
    default:                     return "SEC";
  }
}

bool isDefiniteWrongPasswordReason(uint8_t reason) {
  // Definite WPA/WPA2/WPA3 key/auth rejection codes in ESP-IDF:
  // 14 = MIC_FAILURE, 15 = 4WAY_HANDSHAKE_TIMEOUT, 16 = GROUP_KEY_UPDATE_TIMEOUT,
  // 23 = 802_1X_AUTH_FAILED, 202 = AUTH_FAIL, 204 = HANDSHAKE_TIMEOUT
  return (reason == 14 || reason == 15 || reason == 16 ||
          reason == 23 || reason == 202 || reason == 204);
}

bool isPossibleAuthRejectReason(uint8_t reason) {
  // Codes that frequently indicate wrong password on ISP routers, but can occur once transiently
  return (reason == 2 || reason == 6 || reason == 7);
}

bool isApNotFoundOrSecurityMismatchReason(uint8_t reason) {
  // 201 = NO_AP_FOUND, 210 = NO_AP_COMPAT_SECURITY, 211 = NO_AP_AUTHMODE_THRESHOLD, 212 = RSSI_THRESHOLD
  return (reason == 201 || reason == 210 || reason == 211 || reason == 212);
}

void configurePhilippinesWifiCountry() {
  wifi_country_t country;
  memset(&country, 0, sizeof(country));
  country.cc[0]  = 'P';
  country.cc[1]  = 'H';
  country.cc[2]  = '\0';
  country.schan  = 1;
  country.nchan  = 13;  // Channels 1..13 (Philippines NTC 2.4 GHz band)
  country.policy = WIFI_COUNTRY_POLICY_AUTO;
  esp_wifi_set_country(&country);
}
}  // namespace

void ProvisioningPairingManager::begin(RuntimeConfig& ioConfig) {
  gActiveProvInstance = this;
  WiFi.persistent(false);

  // CRITICAL (Arduino-ESP32 3.x fix): Register ONE global WiFi.onEvent(cb) listener!
  // Calling WiFi.onEvent(sameFn, evt1/evt2/evt3) 3 times triggers NetworkEvents::onEvent()
  // duplicate callback rejection in Arduino-ESP32 3.x, silently dropping GOT_IP & DISCONNECTED!
  if (!gWifiEventRegistered) {
    WiFi.onEvent(handleWifiStaEvent);
    gWifiEventRegistered = true;
  }

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

  if (resetAckPendingToCloud_ && !hasWifiCredentials()) {
    ownershipState_ = OwnershipState::RESET_PENDING;
    Serial.println(F("[PROV] Resumed incomplete RESET_PENDING checkpoint from NVS."));
  } else if (hasWifiCredentials() && ownershipState_ != OwnershipState::OWNED) {
    // Self-heal: if Wi-Fi credentials exist in NVS from a completed pairing, ensure the board
    // boots in OWNED station mode so power-cycling NEVER drops an active owner offline!
    ownershipState_         = OwnershipState::OWNED;
    if (ownershipEpoch_ == 0) {
      ownershipEpoch_ = 1;
    }
    resetAckPendingToCloud_ = false;
    prefs_.putUChar("ownState", static_cast<uint8_t>(OwnershipState::OWNED));
    prefs_.putULong("ownEpoch", ownershipEpoch_);
    prefs_.putBool("rstPending", false);
    Serial.println(F("[PROV] Self-healed NVS ownershipState -> OWNED (saved Wi-Fi credentials present)."));
  }

  prefs_.end();

  Serial.printf("[PROV] Loaded deviceId=%s name='%s' state=%s epoch=%lu wifiConfigured=%s\n",
                deviceId_,
                displayName_,
                ownershipStateToString(ownershipState_),
                static_cast<unsigned long>(ownershipEpoch_),
                hasWifiCredentials() ? "YES" : "NO");

  if (!hasWifiCredentials()) {
    startOwnerSetupHotspot();
  }
}

// Kept ultra-lightweight & Serial-free because Arduino-ESP32 executes WiFi events
// on a dedicated FreeRTOS background task (`arduino_events`).
void ProvisioningPairingManager::onStaConnected() {
  if (wifiTestState_ != WifiTestState::TESTING || ignoreDisconnectEvents_) {
    return;
  }
  staLinkConnected_ = true;
  if (staConnectedAtMs_ == 0) {
    const uint32_t now = millis();
    staConnectedAtMs_  = (now == 0) ? 1UL : now;
  }
}

void ProvisioningPairingManager::onStaGotIp() {
  if (wifiTestState_ != WifiTestState::TESTING || ignoreDisconnectEvents_) {
    return;
  }
  staLinkConnected_ = true;
  staGotIp_         = true;
  if (staConnectedAtMs_ == 0) {
    const uint32_t now = millis();
    staConnectedAtMs_  = (now == 0) ? 1UL : now;
  }
}

void ProvisioningPairingManager::onStaDisconnectReason(uint8_t reasonCode) {
  if (wifiTestState_ != WifiTestState::TESTING || ignoreDisconnectEvents_) {
    return;
  }
  // Reason 8 (ASSOC_LEAVE) is triggered by our own esp_wifi_disconnect(); ignore it.
  if (reasonCode == 8) {
    return;
  }
  staLinkConnected_        = false;
  staGotIp_                = false;
  staConnectedAtMs_        = 0;
  lastStaDisconnectReason_ = reasonCode;
  staDisconnectCount_      = static_cast<uint8_t>(staDisconnectCount_ + 1);
  if (isDefiniteWrongPasswordReason(reasonCode)) {
    authFailCount_ = static_cast<uint8_t>(authFailCount_ + 1);
  } else if (isPossibleAuthRejectReason(reasonCode)) {
    authExpireCount_ = static_cast<uint8_t>(authExpireCount_ + 1);
  } else if (isApNotFoundOrSecurityMismatchReason(reasonCode)) {
    noApCount_ = static_cast<uint8_t>(noApCount_ + 1);
  }
}

const ProvisioningPairingManager::CachedScanInfo*
ProvisioningPairingManager::findCachedScanForSsid(const char* ssid) const {
  if (!ssid || strlen(ssid) == 0) {
    return nullptr;
  }
  for (int i = 0; i < cachedScanCount_; ++i) {
    if (strcmp(cachedScan_[i].ssid, ssid) == 0) {
      return &cachedScan_[i];
    }
  }
  return nullptr;
}

void ProvisioningPairingManager::startOwnerSetupHotspot(int32_t channel) {
  if (channel < 1 || channel > 13) {
    channel = 1;
  }
  if (setupApActive_) {
    return;
  }

  // Always use WIFI_AP_STA so both GET /api/wifi-scan and live Wi-Fi password testing
  // work WITHOUT dropping the Android phone's connection to SPC-SETUP-*!
  WiFi.mode(WIFI_AP_STA);
  configurePhilippinesWifiCountry();
  WiFi.setSleep(false);
  bool ok = WiFi.softAP(setupApSsid_, Secrets::DEFAULT_PROV_POP, channel);
  if (ok) {
    setupApActive_ = true;
    if (routesRegistered_) {
      setupServer_.begin();
    }
    Serial.println(F("======================================================================"));
    Serial.printf("[PROV] OWNER WI-FI SETUP HOTSPOT ACTIVE (WIFI_AP_STA, ch=%ld)!\n",
                  static_cast<long>(channel));
    Serial.printf("       Hotspot SSID : %s\n", setupApSsid_);
    Serial.printf("       Hotspot PIN  : %s\n", Secrets::DEFAULT_PROV_POP);
    Serial.printf("       Setup URL    : http://%s\n", WiFi.softAPIP().toString().c_str());
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
  Serial.println(F("[PROV] Owner Setup Hotspot closed. Running in normal station mode."));
}

void ProvisioningPairingManager::queueLiveWifiCredentialTest(const char* ssid,
                                                             const char* pass,
                                                             const char* boxName,
                                                             uint32_t nextEpoch) {
  wifiTestState_           = WifiTestState::IDLE;
  wifiTestPhase_           = WifiTestPhase::NONE;
  pendingApShutdown_       = false;
  clientReadConnectedAck_  = false;
  connectProfileIdx_       = 0;
  pureStaRetryUsed_        = false;
  ignoreDisconnectEvents_  = false;

  snprintf(pendingSsid_, sizeof(pendingSsid_), "%s", ssid ? ssid : "");
  snprintf(pendingPass_, sizeof(pendingPass_), "%s", pass ? pass : "");
  snprintf(pendingBoxName_, sizeof(pendingBoxName_), "%s",
           (boxName && strlen(boxName) > 0) ? boxName : displayName_);
  pendingNextEpoch_ = nextEpoch;
  wifiTestError_[0] = '\0';

  staLinkConnected_        = false;
  staGotIp_                = false;
  staConnectedAtMs_        = 0;
  lastStaDisconnectReason_ = 0;
  staDisconnectCount_      = 0;
  authFailCount_           = 0;
  authExpireCount_         = 0;
  noApCount_               = 0;
  loggedDisconnectCount_   = 0;
  loggedStaConnected_      = false;
  loggedStaGotIp_          = false;

  const uint32_t nowMs     = millis();
  wifiTestStartedAtMs_     = nowMs;
  wifiTestActionAtMs_      = nowMs + HTTP_ACK_SETTLE_MS;
  lastAttemptLaunchedAtMs_ = nowMs;
  wifiTestPhase_           = WifiTestPhase::WAIT_HTTP_ACK;
  wifiTestState_           = WifiTestState::TESTING;

  const CachedScanInfo* cached = findCachedScanForSsid(pendingSsid_);
  if (cached) {
    Serial.printf("[PROV] Queued Wi-Fi test: SSID='%s' passLen=%u (scanned ch=%ld, rssi=%d, auth=%s)\n",
                  pendingSsid_,
                  static_cast<unsigned int>(strlen(pendingPass_)),
                  static_cast<long>(cached->channel),
                  cached->rssi,
                  authModeToString(cached->authMode));
  } else {
    Serial.printf("[PROV] Queued Wi-Fi test: SSID='%s' passLen=%u (not in scan cache; will scan ch 1-13)\n",
                  pendingSsid_,
                  static_cast<unsigned int>(strlen(pendingPass_)));
  }
}

void ProvisioningPairingManager::startSerialWifiTest(const char* ssid, const char* password) {
  if (!ssid || strlen(ssid) == 0) {
    Serial.println(F("[CLI] Usage: testwifi <ssid> <password>"));
    return;
  }
  queueLiveWifiCredentialTest(ssid, password ? password : "", displayName_, ownershipEpoch_ + 1);
  wifiTestActionAtMs_ = millis();  // Start immediately from Serial CLI
}

void ProvisioningPairingManager::printWifiScanToSerial() {
  Serial.println(F("[CLI] Scanning nearby 2.4 GHz Wi-Fi networks (ch 1-13)..."));
  if (WiFi.getMode() == WIFI_OFF) {
    WiFi.mode(WIFI_STA);
  }
  configurePhilippinesWifiCountry();
  int found = WiFi.scanNetworks(false, false);
  if (found <= 0) {
    Serial.printf("[CLI] Scan returned %d networks.\n", found);
    WiFi.scanDelete();
    return;
  }
  Serial.printf("[CLI] Found %d 2.4 GHz networks:\n", found);
  for (int i = 0; i < found; ++i) {
    const uint8_t* b = WiFi.BSSID(i);
    Serial.printf("  #%02d SSID='%s' ch=%ld rssi=%d dBm auth=%s bssid=%02X:%02X:%02X:%02X:%02X:%02X\n",
                  i + 1,
                  WiFi.SSID(i).c_str(),
                  static_cast<long>(WiFi.channel(i)),
                  WiFi.RSSI(i),
                  authModeToString(static_cast<uint8_t>(WiFi.encryptionType(i))),
                  b ? b[0] : 0, b ? b[1] : 0, b ? b[2] : 0,
                  b ? b[3] : 0, b ? b[4] : 0, b ? b[5] : 0);
  }
  WiFi.scanDelete();
}

void ProvisioningPairingManager::launchWifiConnectAttempt(uint8_t profileIdx, bool pureStaMode) {
  const size_t          passLen = strlen(pendingPass_);
  const CachedScanInfo* cached  = findCachedScanForSsid(pendingSsid_);

  // Suppress disconnect events while cleanly resetting STA so esp_wifi_set_config()
  // NEVER fails with ESP_ERR_WIFI_STATE ("sta is connecting, cannot set config").
  ignoreDisconnectEvents_ = true;
  staLinkConnected_       = false;
  staGotIp_               = false;
  staConnectedAtMs_       = 0;
  loggedStaConnected_     = false;
  loggedStaGotIp_         = false;

  WiFi.persistent(false);
  WiFi.setAutoReconnect(false);
  WiFi.scanDelete();

  if (pureStaMode) {
    if (setupApActive_) {
      setupServer_.stop();
      WiFi.softAPdisconnect(false);
      setupApActive_ = false;
    }
    if (WiFi.getMode() != WIFI_STA) {
      WiFi.mode(WIFI_STA);
      delay(150);
    }
  } else {
    if (WiFi.getMode() != WIFI_AP_STA) {
      WiFi.mode(WIFI_AP_STA);
      delay(100);
    }
  }

  esp_wifi_disconnect();
  delay(150);

  configurePhilippinesWifiCountry();
  WiFi.setSleep(false);  // Disable modem sleep during handshake for maximum stability

  // Progressive compatibility profiles:
  // - Profile 0: Full 1-13 channel scan, PMF capable/optional, full TX power
  // - Profile 1: PMF disabled, HT20 20MHz bandwidth, 15dBm TX (fixes USB brownout + quirky ISP routers)
  // - Profile 2: Direct cached BSSID+channel lock (if scanned) with 17dBm TX
  bool pmfCapable     = true;
  bool useCachedBssid = false;
  if (profileIdx == 0) {
    WiFi.setTxPower(WIFI_POWER_19_5dBm);
    esp_wifi_set_bandwidth(WIFI_IF_STA, WIFI_BW_HT20);
    pmfCapable     = true;
    useCachedBssid = false;
  } else if (profileIdx == 1) {
    WiFi.setTxPower(WIFI_POWER_15dBm);
    esp_wifi_set_bandwidth(WIFI_IF_STA, WIFI_BW_HT20);
    pmfCapable     = false;
    useCachedBssid = false;
  } else {
    WiFi.setTxPower(WIFI_POWER_17dBm);
    esp_wifi_set_bandwidth(WIFI_IF_STA, WIFI_BW_HT20);
    pmfCapable     = true;
    useCachedBssid = (cached != nullptr && cached->hasBssid && cached->channel >= 1 && cached->channel <= 13);
  }

  // Use WIFI_AUTH_WEP minimum threshold when password is provided so WPA, WPA2, WPA/WPA2,
  // WPA3, and WPA2/WPA3 routers are ALL accepted without false AUTHMODE_THRESHOLD (211) drops.
  const wifi_auth_mode_t minAuth = (passLen > 0) ? WIFI_AUTH_WEP : WIFI_AUTH_OPEN;
  WiFi.setScanMethod(WIFI_ALL_CHANNEL_SCAN);
  WiFi.setSortMethod(WIFI_CONNECT_AP_BY_SIGNAL);
  WiFi.setMinSecurity(minAuth);

  // Initialize Arduino STA netif & credentials with tryConnect=false so we can apply
  // exact wifi_config_t parameters cleanly before invoking esp_wifi_connect().
  const char* ssidPtr = pendingSsid_;
  const char* passPtr = (passLen > 0) ? pendingPass_ : static_cast<const char*>(nullptr);
  WiFi.begin(ssidPtr, passPtr, static_cast<int32_t>(0), static_cast<const uint8_t*>(nullptr), false);

  wifi_config_t conf;
  memset(&conf, 0, sizeof(conf));
  esp_err_t getErr = esp_wifi_get_config(WIFI_IF_STA, &conf);
  if (getErr == ESP_OK) {
    conf.sta.scan_method        = WIFI_ALL_CHANNEL_SCAN;
    conf.sta.sort_method        = WIFI_CONNECT_AP_BY_SIGNAL;
    conf.sta.threshold.rssi     = -127;
    conf.sta.threshold.authmode = minAuth;
    conf.sta.pmf_cfg.capable    = pmfCapable;
    conf.sta.pmf_cfg.required   = false;
    if (useCachedBssid && cached) {
      conf.sta.channel   = static_cast<uint8_t>(cached->channel);
      conf.sta.bssid_set = 1;
      memcpy(conf.sta.bssid, cached->bssid, 6);
    } else {
      conf.sta.channel   = 0;
      conf.sta.bssid_set = 0;
      memset(conf.sta.bssid, 0, 6);
    }
    esp_err_t setErr = esp_wifi_set_config(WIFI_IF_STA, &conf);
    if (setErr == ESP_ERR_WIFI_STATE) {
      esp_wifi_disconnect();
      delay(250);
      setErr = esp_wifi_set_config(WIFI_IF_STA, &conf);
    }
    if (setErr != ESP_OK) {
      Serial.printf("[PROV] WARNING: esp_wifi_set_config failed: 0x%x (%s)\n",
                    static_cast<unsigned int>(setErr), esp_err_to_name(setErr));
    }
  }

  // Re-enable event capture right before triggering the radio connection!
  ignoreDisconnectEvents_  = false;
  lastAttemptLaunchedAtMs_ = millis();

  // Zero-arg WiFi.begin() preserves our exact wifi_config_t, sets up DHCP, and calls esp_wifi_connect()
  wl_status_t beginRet = WiFi.begin();
  if (beginRet == WL_CONNECT_FAILED) {
    esp_err_t directErr = esp_wifi_connect();
    Serial.printf("[PROV] Direct esp_wifi_connect() fallback returned 0x%x (%s)\n",
                  static_cast<unsigned int>(directErr), esp_err_to_name(directErr));
  }

  Serial.printf("[PROV] Connect attempt [%s profile=%u]: SSID='%s' pmf=%u bssidLock=%u ch=%ld beginRet=%d\n",
                pureStaMode ? "PURE_STA" : "AP_STA",
                static_cast<unsigned int>(profileIdx),
                pendingSsid_,
                pmfCapable ? 1U : 0U,
                useCachedBssid ? 1U : 0U,
                static_cast<long>(useCachedBssid && cached ? cached->channel : 0),
                static_cast<int>(beginRet));
}

void ProvisioningPairingManager::finalizeWifiTestSuccess(
    uint32_t nowMs,
    bool gotValidIp,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  wifiTestState_ = WifiTestState::CONNECTED;
  wifiTestPhase_ = WifiTestPhase::NONE;
  lastPairedAtMs_ = (nowMs == 0) ? 1UL : nowMs;

  // Restore full TX power once link is verified
  WiFi.setTxPower(WIFI_POWER_19_5dBm);
  WiFi.setAutoReconnect(true);

  saveDisplayName(pendingBoxName_);
  saveWifiCredentials(pendingSsid_, pendingPass_);
  handleCloudMetaSnapshot(
      "OWNED", pendingNextEpoch_, false, ioConfig, relays, irrigation, buzzer, events, lcd);

  // Guarantee audible confirmation beep on GPIO18 upon successful Wi-Fi connection!
  buzzer.clearEventLatch(BuzzerEventId::PAIRING_SUCCESS);
  buzzer.triggerEventTransition(BuzzerEventId::PAIRING_SUCCESS);

  const int32_t activeCh = WiFi.channel();
  Serial.printf("[PROV] Wi-Fi PASSWORD VERIFIED (%s)! Connected to '%s' (IP=%s, ch=%ld, RSSI=%d dBm). Buzzer beeped!\n",
                gotValidIp ? "GOT_IP" : "STA_WPA_HANDSHAKE_OK",
                pendingSsid_,
                gotValidIp ? WiFi.localIP().toString().c_str() : "pending-dhcp",
                static_cast<long>(activeCh),
                WiFi.RSSI());

  lcd.triggerTemporaryOverlay(
      displayName_,
      "WIFI CONNECTED!",
      gotValidIp ? WiFi.localIP().toString().c_str() : "PASSWORD VERIFIED",
      "DEVICE PAIRED",
      5000);

  // If Tier 2 pure-STA succeeded while SoftAP was paused, briefly re-open SoftAP on the
  // router's EXACT channel so Android can read CONNECTED if it rejoined; then close cleanly.
  if (!setupApActive_) {
    startOwnerSetupHotspot((activeCh >= 1 && activeCh <= 13) ? activeCh : 1);
  }

  pendingApShutdown_ = true;
  apShutdownAtMs_    = nowMs + AP_CLOSE_GRACE_MS;
}

void ProvisioningPairingManager::finalizeWifiTestFailure(
    uint32_t nowMs,
    const char* reasonMessage,
    LcdManager& lcd) {
  (void)nowMs;
  snprintf(wifiTestError_, sizeof(wifiTestError_), "%s",
           (reasonMessage && strlen(reasonMessage) > 0)
               ? reasonMessage
               : "Invalid Wi-Fi password. Please check your password and try again.");

  // Cleanly stop STA without saving bad credentials and restore full TX power
  ignoreDisconnectEvents_ = true;
  WiFi.setAutoReconnect(false);
  esp_wifi_disconnect();
  delay(80);
  WiFi.setTxPower(WIFI_POWER_19_5dBm);
  ignoreDisconnectEvents_ = false;

  wifiTestState_     = WifiTestState::FAILED;
  wifiTestPhase_     = WifiTestPhase::NONE;
  pendingApShutdown_ = false;

  // Ensure Owner Setup Hotspot is active so user can immediately fix password & retry!
  if (!setupApActive_) {
    startOwnerSetupHotspot(1);
  }

  Serial.printf("[PROV] Wi-Fi test FAILED (status=%d, reason=%u [%s], staLink=%u, authFails=%u, authExp=%u, noAp=%u): %s\n",
                static_cast<int>(WiFi.status()),
                static_cast<unsigned int>(lastStaDisconnectReason_),
                wifiReasonToString(lastStaDisconnectReason_),
                staLinkConnected_ ? 1U : 0U,
                static_cast<unsigned int>(authFailCount_),
                static_cast<unsigned int>(authExpireCount_),
                static_cast<unsigned int>(noApCount_),
                wifiTestError_);

  const bool likelyPassErr = (authFailCount_ > 0 || authExpireCount_ > 0 ||
                              isDefiniteWrongPasswordReason(lastStaDisconnectReason_));
  lcd.triggerTemporaryOverlay(
      likelyPassErr ? "INVALID PASSWORD!" : "WIFI FAILED!",
      "CHECK PASSWORD",
      "RETRY IN APP",
      setupApSsid_,
      4000);
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
        "<button type='submit'>Verify Wi-Fi & Pair Device</button></form></div></body></html>");
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
    Serial.println(F("[PROV] Running 2.4 GHz Wi-Fi scan (ch 1-13) for Android setup picker..."));
    configurePhilippinesWifiCountry();
    int found = WiFi.scanNetworks(false, false);

    constexpr int MAX_UNIQUE = 16;
    WifiScanEntry entries[MAX_UNIQUE];
    memset(entries, 0, sizeof(entries));
    int uniqueCount = 0;

    if (found > 0) {
      for (int i = 0; i < found; ++i) {
        String ssid = WiFi.SSID(i);
        ssid.trim();
        if (ssid.length() == 0 || ssid.startsWith("SPC-SETUP-")) {
          continue;
        }
        int            rssi     = WiFi.RSSI(i);
        int32_t        channel  = WiFi.channel(i);
        uint8_t        authMode = static_cast<uint8_t>(WiFi.encryptionType(i));
        bool           secure   = (authMode != WIFI_AUTH_OPEN);
        const uint8_t* bssidPtr = WiFi.BSSID(i);

        int existingIdx = -1;
        for (int j = 0; j < uniqueCount; ++j) {
          if (strcmp(entries[j].ssid, ssid.c_str()) == 0) {
            existingIdx = j;
            break;
          }
        }
        if (existingIdx >= 0) {
          if (rssi > entries[existingIdx].rssi) {
            entries[existingIdx].rssi     = rssi;
            entries[existingIdx].channel  = channel;
            entries[existingIdx].authMode = authMode;
            entries[existingIdx].secure   = secure;
            if (bssidPtr != nullptr) {
              memcpy(entries[existingIdx].bssid, bssidPtr, 6);
              entries[existingIdx].hasBssid = true;
            }
          }
        } else if (uniqueCount < MAX_UNIQUE) {
          snprintf(entries[uniqueCount].ssid, sizeof(entries[uniqueCount].ssid), "%s", ssid.c_str());
          entries[uniqueCount].rssi     = rssi;
          entries[uniqueCount].channel  = channel;
          entries[uniqueCount].authMode = authMode;
          entries[uniqueCount].secure   = secure;
          if (bssidPtr != nullptr) {
            memcpy(entries[uniqueCount].bssid, bssidPtr, 6);
            entries[uniqueCount].hasBssid = true;
          }
          uniqueCount++;
        }
      }
    }
    WiFi.scanDelete();

    for (int i = 0; i < uniqueCount - 1; ++i) {
      for (int j = i + 1; j < uniqueCount; ++j) {
        if (entries[j].rssi > entries[i].rssi) {
          WifiScanEntry tmp = entries[i];
          entries[i]        = entries[j];
          entries[j]        = tmp;
        }
      }
    }

    // Cache SSID -> channel/BSSID/authMode/RSSI for diagnostics and Profile 2 fallback
    cachedScanCount_ = (uniqueCount < MAX_CACHED_CHANNELS) ? uniqueCount : MAX_CACHED_CHANNELS;
    for (int i = 0; i < cachedScanCount_; ++i) {
      snprintf(cachedScan_[i].ssid, sizeof(cachedScan_[i].ssid), "%s", entries[i].ssid);
      cachedScan_[i].channel  = entries[i].channel;
      cachedScan_[i].rssi     = entries[i].rssi;
      cachedScan_[i].authMode = entries[i].authMode;
      cachedScan_[i].hasBssid = entries[i].hasBssid;
      memcpy(cachedScan_[i].bssid, entries[i].bssid, 6);
    }

    JsonDocument doc;
    JsonArray arr = doc["networks"].to<JsonArray>();
    for (int i = 0; i < uniqueCount; ++i) {
      JsonObject net = arr.add<JsonObject>();
      net["ssid"]    = entries[i].ssid;
      net["rssi"]    = entries[i].rssi;
      net["secure"]  = entries[i].secure;
      net["channel"] = entries[i].channel;
    }

    String out;
    serializeJson(doc, out);
    setupServer_.send(200, "application/json", out);
    Serial.printf("[PROV] Returned %d unique 2.4 GHz Wi-Fi networks.\n", uniqueCount);
  });

  // 4. Android App JSON Provisioning Start (POST /api/provision)
  // IMPORTANT: Queues live Wi-Fi verification to start 300ms AFTER HTTP 202 flushes!
  setupServer_.on("/api/provision", HTTP_POST, [this]() {
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

    queueLiveWifiCredentialTest(ssid, pass, boxName, nextEpoch);

    JsonDocument resp;
    resp["status"]   = "TESTING";
    resp["success"]  = false;
    resp["deviceId"] = deviceId_;
    resp["message"]  = "Testing Wi-Fi password on ESP32...";
    String out;
    serializeJson(resp, out);
    setupServer_.send(202, "application/json", out);
  });

  // 5. Live Wi-Fi Password Verification Polling Endpoint (GET /api/provision-status)
  setupServer_.on("/api/provision-status", HTTP_GET, [this]() {
    JsonDocument resp;
    resp["deviceId"]     = deviceId_;
    resp["wifiStatus"]   = static_cast<int>(WiFi.status());
    resp["reasonCode"]   = static_cast<unsigned int>(lastStaDisconnectReason_);
    resp["reasonName"]   = wifiReasonToString(lastStaDisconnectReason_);
    resp["staConnected"] = static_cast<bool>(staLinkConnected_);
    resp["gotIp"]        = static_cast<bool>(staGotIp_ || (WiFi.status() == WL_CONNECTED && WiFi.localIP()[0] != 0));
    if (wifiTestState_ == WifiTestState::CONNECTED) {
      resp["status"]         = "CONNECTED";
      resp["success"]        = true;
      resp["displayName"]    = displayName_;
      resp["ipAddress"]      = WiFi.localIP().toString();
      resp["ownershipState"] = "OWNED";
      resp["ownershipEpoch"] = ownershipEpoch_;
      if (!clientReadConnectedAck_) {
        clientReadConnectedAck_ = true;
        uint32_t fastCloseAt = millis() + AP_CLOSE_AFTER_ACK_MS;
        if (pendingApShutdown_ && static_cast<int32_t>(apShutdownAtMs_ - fastCloseAt) > 0) {
          apShutdownAtMs_ = fastCloseAt;
        }
      }
    } else if (wifiTestState_ == WifiTestState::FAILED) {
      resp["status"]  = "FAILED";
      resp["success"] = false;
      resp["error"]   = strlen(wifiTestError_) > 0
                            ? wifiTestError_
                            : "Invalid Wi-Fi password. Please check your password and try again.";
    } else if (wifiTestState_ == WifiTestState::TESTING) {
      resp["status"]    = "TESTING";
      resp["success"]   = false;
      resp["profile"]   = static_cast<unsigned int>(connectProfileIdx_);
      resp["elapsedMs"] = static_cast<uint32_t>(millis() - wifiTestStartedAtMs_);
    } else {
      resp["status"]  = "IDLE";
      resp["success"] = false;
    }
    String out;
    serializeJson(resp, out);
    setupServer_.send(200, "application/json", out);
  });

  // 6. Browser Form Submission Handler (POST /provisionForm)
  setupServer_.on("/provisionForm", HTTP_POST, [this]() {
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

    queueLiveWifiCredentialTest(ssid.c_str(), pass.c_str(), boxName.c_str(), ownershipEpoch_ + 1);
    setupServer_.send(200, "text/html",
                      "<html><body style='font-family:sans-serif;text-align:center;padding:30px'>"
                      "<h2>Verifying Wi-Fi Password...</h2>"
                      "<p>Listen for 1 confirmation beep on the ESP32 once connected. "
                      "If the password is wrong, this setup hotspot stays open so you can retry.</p>"
                      "</body></html>");
  });

  setupServer_.begin();
  (void)ioConfig;
  (void)relays;
  (void)irrigation;
  (void)buzzer;
  (void)events;
  (void)lcd;
}

void ProvisioningPairingManager::serviceProvisioningPortal(
    uint32_t nowMs,
    RuntimeConfig& ioConfig,
    RelayController& relays,
    IrrigationController& irrigation,
    BuzzerManager& buzzer,
    EventNotificationManager& events,
    LcdManager& lcd) {
  // CRITICAL: Keep servicing even if SoftAP is temporarily paused during PURE_STA_TEST!
  if (!setupApActive_ && wifiTestState_ != WifiTestState::TESTING) {
    return;
  }

  if (setupApActive_) {
    if (!routesRegistered_) {
      registerHttpRoutes(ioConfig, relays, irrigation, buzzer, events, lcd);
    }
    setupServer_.handleClient();
  }

  // ─── Monitor Live Wi-Fi Password Verification State Machine ────────────────
  if (wifiTestState_ == WifiTestState::TESTING) {
    // 1. Flush any Wi-Fi event logs safely from the main loop (never inside FreeRTOS event task)
    if (staLinkConnected_ && !loggedStaConnected_) {
      loggedStaConnected_ = true;
      Serial.printf("[PROV] STA_CONNECTED! Router accepted password for '%s' (ch=%ld, RSSI=%d dBm).\n",
                    pendingSsid_, static_cast<long>(WiFi.channel()), WiFi.RSSI());
    }
    if (staGotIp_ && !loggedStaGotIp_) {
      loggedStaGotIp_ = true;
      Serial.printf("[PROV] STA_GOT_IP! IP=%s\n", WiFi.localIP().toString().c_str());
    }
    const uint8_t curDisconnCnt = staDisconnectCount_;
    bool newlyDisconnected = false;
    if (curDisconnCnt != loggedDisconnectCount_) {
      loggedDisconnectCount_ = curDisconnCnt;
      newlyDisconnected      = true;
      const uint8_t r        = lastStaDisconnectReason_;
      Serial.printf("[PROV] STA_DISCONNECTED #%u: reason=%u (%s) [phase=%u, profile=%u, authFail=%u, authExp=%u, noAp=%u]\n",
                    static_cast<unsigned int>(curDisconnCnt),
                    static_cast<unsigned int>(r),
                    wifiReasonToString(r),
                    static_cast<unsigned int>(wifiTestPhase_),
                    static_cast<unsigned int>(connectProfileIdx_),
                    static_cast<unsigned int>(authFailCount_),
                    static_cast<unsigned int>(authExpireCount_),
                    static_cast<unsigned int>(noApCount_));
    }

    // 2. Phase WAIT_HTTP_ACK: let HTTP 202 finish flushing over SoftAP before touching radio
    if (wifiTestPhase_ == WifiTestPhase::WAIT_HTTP_ACK) {
      if (static_cast<int32_t>(nowMs - wifiTestActionAtMs_) >= 0) {
        wifiTestPhase_     = WifiTestPhase::AP_STA_TEST;
        connectProfileIdx_ = 0;
        wifiTestActionAtMs_ = 0;
        launchWifiConnectAttempt(0, false);
      }
      return;
    }

    // 3. Hardware ground-truth association check (independent of event delivery!)
    if (!staLinkConnected_) {
      wifi_ap_record_t apInfo;
      memset(&apInfo, 0, sizeof(apInfo));
      if (esp_wifi_sta_get_ap_info(&apInfo) == ESP_OK &&
          strncmp(reinterpret_cast<const char*>(apInfo.ssid), pendingSsid_, 32) == 0) {
        staLinkConnected_ = true;
        staConnectedAtMs_ = (nowMs == 0) ? 1UL : nowMs;
      }
    }

    const bool gotValidIp =
        staGotIp_ || ((WiFi.status() == WL_CONNECTED) && (WiFi.localIP()[0] != 0));
    const uint32_t staConnAt = staConnectedAtMs_;
    const bool l2PasswordVerified =
        staLinkConnected_ &&
        (staConnAt != 0) &&
        (uint32_t(nowMs - staConnAt) >= WIFI_L2_CONFIRM_HOLD_MS) &&
        (authFailCount_ == 0);

    if (gotValidIp || l2PasswordVerified) {
      finalizeWifiTestSuccess(
          nowMs, gotValidIp, ioConfig, relays, irrigation, buzzer, events, lcd);
      return;
    }

    const uint32_t totalElapsed = uint32_t(nowMs - wifiTestStartedAtMs_);
    const uint8_t  reason       = lastStaDisconnectReason_;
    const uint8_t  authFails    = authFailCount_;
    const uint8_t  authExps     = authExpireCount_;
    const uint8_t  noApFails    = noApCount_;

    // 4. Phase AP_STA_TEST (Tier 1: Dual WIFI_AP_STA with 3 progressive profiles)
    if (wifiTestPhase_ == WifiTestPhase::AP_STA_TEST) {
      // Fast, accurate WRONG PASSWORD rejection when 2 clean profiles both get pure key/auth failure
      if (authFails >= 2 && noApFails == 0 && curDisconnCnt == authFails) {
        char msg[160];
        snprintf(msg, sizeof(msg),
                 "Invalid Wi-Fi password for '%s' (code %u: %s). Please check your password and try again.",
                 pendingSsid_, static_cast<unsigned int>(reason), wifiReasonToString(reason));
        finalizeWifiTestFailure(nowMs, msg, lcd);
        return;
      }

      // If a retry delay was scheduled after a disconnect, launch the next profile now
      if (wifiTestActionAtMs_ != 0 && static_cast<int32_t>(nowMs - wifiTestActionAtMs_) >= 0) {
        wifiTestActionAtMs_ = 0;
        launchWifiConnectAttempt(connectProfileIdx_, false);
        return;
      }

      const bool profileStalled =
          !staLinkConnected_ &&
          (wifiTestActionAtMs_ == 0) &&
          (uint32_t(nowMs - lastAttemptLaunchedAtMs_) >= PROFILE_STALL_TIMEOUT_MS);

      if (newlyDisconnected || profileStalled) {
        if (connectProfileIdx_ < 2 && totalElapsed < AP_STA_PHASE_MAX_MS - 1500UL) {
          connectProfileIdx_ = static_cast<uint8_t>(connectProfileIdx_ + 1);
          wifiTestActionAtMs_ = nowMs + RETRY_SETTLE_DELAY_MS;
          return;
        }

        // All 3 AP_STA profiles exhausted (or timed out) without connecting:
        // If we got pure auth rejections (including AUTH_EXPIRE) across all attempts, report invalid password immediately!
        if ((authFails >= 1 || authExps >= 2) && noApFails == 0) {
          char msg[160];
          snprintf(msg, sizeof(msg),
                   "Invalid Wi-Fi password for '%s' (code %u: %s). Please check uppercase/lowercase and try again.",
                   pendingSsid_, static_cast<unsigned int>(reason), wifiReasonToString(reason));
          finalizeWifiTestFailure(nowMs, msg, lcd);
          return;
        }

        // Otherwise escalate to Tier 2: interference-free PURE WIFI_STA verification!
        Serial.printf("[PROV] Dual AP+STA did not associate after %lu ms (reason=%u [%s]) — switching to PURE WIFI_STA verification...\n",
                      static_cast<unsigned long>(totalElapsed),
                      static_cast<unsigned int>(reason),
                      wifiReasonToString(reason));
        wifiTestPhase_           = WifiTestPhase::PURE_STA_TEST;
        wifiTestActionAtMs_      = nowMs;  // Track Pure-STA phase start time
        connectProfileIdx_       = 0;
        pureStaRetryUsed_        = false;
        lastStaDisconnectReason_ = 0;
        staDisconnectCount_      = 0;
        loggedDisconnectCount_   = 0;
        authFailCount_           = 0;
        authExpireCount_         = 0;
        noApCount_               = 0;
        launchWifiConnectAttempt(0, true);
        return;
      }

      if (totalElapsed >= AP_STA_PHASE_MAX_MS) {
        Serial.println(F("[PROV] AP_STA phase elapsed — switching to PURE WIFI_STA verification..."));
        wifiTestPhase_           = WifiTestPhase::PURE_STA_TEST;
        wifiTestActionAtMs_      = nowMs;
        connectProfileIdx_       = 0;
        pureStaRetryUsed_        = false;
        lastStaDisconnectReason_ = 0;
        staDisconnectCount_      = 0;
        loggedDisconnectCount_   = 0;
        authFailCount_           = 0;
        authExpireCount_         = 0;
        noApCount_               = 0;
        launchWifiConnectAttempt(0, true);
        return;
      }
      return;
    }

    // 5. Phase PURE_STA_TEST (Tier 2: Zero SoftAP interference for stubborn routers/channel locks)
    if (wifiTestPhase_ == WifiTestPhase::PURE_STA_TEST) {
      const uint32_t pureElapsed = uint32_t(nowMs - wifiTestActionAtMs_);

      // Retry once inside Pure-STA with Profile 1 (PMF=false, HT20, 15dBm) on first failure or 4.5s stall
      const bool pureStalled =
          !staLinkConnected_ &&
          !pureStaRetryUsed_ &&
          (uint32_t(nowMs - lastAttemptLaunchedAtMs_) >= 4500UL);

      if ((newlyDisconnected || pureStalled) && !pureStaRetryUsed_ && pureElapsed < PURE_STA_PHASE_MAX_MS - 1500UL) {
        pureStaRetryUsed_  = true;
        connectProfileIdx_ = 1;
        launchWifiConnectAttempt(1, true);
        return;
      }

      // Definite failure inside Pure-STA after retry (or immediate 2x auth fail)
      if ((authFails >= 2) ||
          (pureStaRetryUsed_ && newlyDisconnected) ||
          (pureElapsed >= PURE_STA_PHASE_MAX_MS)) {
        char msg[160];
        if (authFails > 0 || authExps > 0 || isDefiniteWrongPasswordReason(reason) || isPossibleAuthRejectReason(reason)) {
          snprintf(msg, sizeof(msg),
                   "Invalid Wi-Fi password for '%s' (code %u: %s). Please check your password and try again.",
                   pendingSsid_, static_cast<unsigned int>(reason), wifiReasonToString(reason));
        } else if (noApFails > 0 || isApNotFoundOrSecurityMismatchReason(reason)) {
          if (reason == 210 || reason == 211) {
            snprintf(msg, sizeof(msg),
                     "Router security for '%s' is incompatible (code %u: %s). Set router 2.4 GHz to WPA2-PSK.",
                     pendingSsid_, static_cast<unsigned int>(reason), wifiReasonToString(reason));
          } else {
            snprintf(msg, sizeof(msg),
                     "2.4 GHz Wi-Fi '%s' not found (code %u: %s). Check router 2.4 GHz band and signal.",
                     pendingSsid_, static_cast<unsigned int>(reason), wifiReasonToString(reason));
          }
        } else {
          snprintf(msg, sizeof(msg),
                   "Could not connect to '%s' (status=%d, code=%u: %s). Check Wi-Fi password and router 2.4 GHz WPA2.",
                   pendingSsid_,
                   static_cast<int>(WiFi.status()),
                   static_cast<unsigned int>(reason),
                   wifiReasonToString(reason));
        }
        finalizeWifiTestFailure(nowMs, msg, lcd);
        return;
      }
    }
  }

  // ─── Close Owner Setup Hotspot cleanly after grace window ──────────────────
  if (pendingApShutdown_ && static_cast<int32_t>(nowMs - apShutdownAtMs_) >= 0) {
    stopOwnerSetupHotspot();
    // Stay connected (or finish clean DHCP in pure WIFI_STA mode if dual AP+STA held DHCP)
    if ((WiFi.status() != WL_CONNECTED || WiFi.localIP()[0] == 0) && hasWifiCredentials()) {
      const size_t passLen = strlen(wifiPassword_);
      configurePhilippinesWifiCountry();
      WiFi.setSleep(false);
      WiFi.setScanMethod(WIFI_ALL_CHANNEL_SCAN);
      WiFi.setSortMethod(WIFI_CONNECT_AP_BY_SIGNAL);
      WiFi.setMinSecurity(passLen > 0 ? WIFI_AUTH_WEP : WIFI_AUTH_OPEN);
      if (passLen > 0) {
        WiFi.begin(wifiSsid_, wifiPassword_);
      } else {
        WiFi.begin(wifiSsid_);
      }
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

  Serial.printf("[PROV] Verified Owner Wi-Fi SSID '%s' saved to NVS.\n", wifiSsid_);
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

void ProvisioningPairingManager::loadSparkRefreshToken(char* outToken, size_t maxLen) {
  if (!outToken || maxLen == 0) return;
  outToken[0] = '\0';
  prefs_.begin(NVS_NAMESPACE, true);
  String tok = prefs_.getString("fbRefTok", "");
  prefs_.end();
  if (tok.length() > 0 && tok.length() < maxLen) {
    snprintf(outToken, maxLen, "%s", tok.c_str());
  }
}

void ProvisioningPairingManager::saveSparkRefreshToken(const char* token) {
  prefs_.begin(NVS_NAMESPACE, false);
  prefs_.putString("fbRefTok", token ? token : "");
  prefs_.end();
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

    if (setupApActive_ && !pendingApShutdown_ && wifiTestState_ != WifiTestState::TESTING) {
      stopOwnerSetupHotspot();
    }

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

  if (cloudState == OwnershipState::CLAIM_PENDING) {
    // Transient CLAIM_PENDING written by Android while claiming ACL: NEVER wipe NVS Wi-Fi!
    if (ownershipState_ != OwnershipState::OWNED) {
      ownershipState_ = cloudState;
      ownershipEpoch_ = cloudOwnershipEpoch;
    }
    return;
  }

  if (cloudState == OwnershipState::UNCLAIMED) {
    if (ownershipState_ == OwnershipState::OWNED) {
      // Only wipe local NVS if cloud explicitly bumped ownershipEpoch past our paired epoch;
      // otherwise ignore stale UNCLAIMED snapshots so rebooting never loses owner Wi-Fi!
      if (cloudOwnershipEpoch > ownershipEpoch_) {
        initiateResetPendingLifecycle(cloudOwnershipEpoch, relays, irrigation, buzzer, events);
        finalizeLocalUnclaimAndEnterSetup(ioConfig, true);
      }
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
  irrigation.clearProcessedCommandsForReset();
  irrigation.clearIrrigationHistoryQueue();
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
  wifiTestState_          = WifiTestState::IDLE;
  wifiTestPhase_          = WifiTestPhase::NONE;
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

  esp_wifi_disconnect();
  startOwnerSetupHotspot(1);
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
