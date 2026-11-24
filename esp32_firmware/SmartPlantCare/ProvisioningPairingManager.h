#pragma once
/**
 * @file ProvisioningPairingManager.h
 * @brief NVS persistence, Owner Wireless Wi-Fi Provisioning (WIFI_AP_STA + 2.4 GHz Scanner
 *        + live Wi-Fi password verification via /api/provision & /api/provision-status),
 *        custom box displayName, buzzer confirmation on connect, and RESET_PENDING lifecycle.
 */

#include <Arduino.h>
#include <Preferences.h>
#include <WebServer.h>
#include <WiFi.h>
#include <esp_err.h>
#include <esp_wifi.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EventNotificationManager.h"
#include "IrrigationController.h"
#include "LcdManager.h"
#include "Models.h"
#include "RelayController.h"
#include "SecretsTemplate.h"

enum class WifiTestState : uint8_t {
  IDLE      = 0,
  TESTING   = 1,
  CONNECTED = 2,
  FAILED    = 3
};

enum class WifiTestPhase : uint8_t {
  NONE          = 0,
  WAIT_HTTP_ACK = 1,  // Brief 300ms pause so HTTP 202 flushes cleanly over SoftAP before touching radio
  AP_STA_TEST   = 2,  // Dual WIFI_AP_STA progressive verification (Profiles 0, 1, 2)
  PURE_STA_TEST = 3   // Last-resort interference-free WIFI_STA fallback if dual-mode couldn't associate
};

class ProvisioningPairingManager {
 public:
  void begin(RuntimeConfig& ioConfig);

  // Nonblocking service loop for Setup AP, HTTP API (/api/info, /api/wifi-scan,
  // /api/provision, /api/provision-status), and live Wi-Fi password verification.
  void serviceProvisioningPortal(uint32_t nowMs,
                                 RuntimeConfig& ioConfig,
                                 RelayController& relays,
                                 IrrigationController& irrigation,
                                 BuzzerManager& buzzer,
                                 EventNotificationManager& events,
                                 LcdManager& lcd);

  // Update Wi-Fi credentials in NVS (only called AFTER password is verified!)
  void saveWifiCredentials(const char* ssid, const char* password);
  bool hasWifiCredentials() const { return strlen(wifiSsid_) > 0; }

  // Update Owner custom box name in NVS (e.g. "Backyard Vegetable Bed")
  void saveDisplayName(const char* name);

  // Update device identity / secrets in NVS
  void saveFactoryIdentity(const char* deviceId,
                           const char* claimCode,
                           const char* deviceAuthSecret);

  // Persist hardware sensor calibration in NVS (preserved across unclaim/reset)
  void saveHardwareCalibration(const RuntimeConfig& cfg);
  void loadHardwareCalibration(RuntimeConfig& ioConfig);

  // Persist optional Spark Firebase Auth refresh token so reboots renew cleanly
  void loadSparkRefreshToken(char* outToken, size_t maxLen);
  void saveSparkRefreshToken(const char* token);

  // Evaluate cloud /devices/{deviceId}/meta state & epoch
  void handleCloudMetaSnapshot(const char* cloudOwnershipStateStr,
                               uint32_t cloudOwnershipEpoch,
                               bool resetRequired,
                               RuntimeConfig& ioConfig,
                               RelayController& relays,
                               IrrigationController& irrigation,
                               BuzzerManager& buzzer,
                               EventNotificationManager& events,
                               LcdManager& lcd);

  // Execute immediate safe shutdown and NVS checkpoint for RESET_PENDING
  void initiateResetPendingLifecycle(uint32_t targetEpoch,
                                     RelayController& relays,
                                     IrrigationController& irrigation,
                                     BuzzerManager& buzzer,
                                     EventNotificationManager& events);

  // Finalize local unclaim after backend reset acknowledgment (or local factory reset)
  void finalizeLocalUnclaimAndEnterSetup(RuntimeConfig& ioConfig, bool wipeWifiCredentials = true);

  // Called from WiFi event handlers during live Wi-Fi password verification
  void onStaConnected();
  void onStaGotIp();
  void onStaDisconnectReason(uint8_t reasonCode);

  // Serial bench CLI helpers for diagnosing 2.4 GHz router compatibility
  void startSerialWifiTest(const char* ssid, const char* password);
  void printWifiScanToSerial();

  const char*    deviceId() const { return deviceId_; }
  const char*    displayName() const { return displayName_; }
  const char*    bootId() const { return bootId_; }
  const char*    claimCode() const { return claimCode_; }
  const char*    deviceAuthSecret() const { return deviceAuthSecret_; }
  const char*    wifiSsid() const { return wifiSsid_; }
  const char*    wifiPassword() const { return wifiPassword_; }
  const char*    setupApSsid() const { return setupApSsid_; }
  bool           isSetupApActive() const { return setupApActive_; }
  bool           isWifiTestInProgress() const { return wifiTestState_ == WifiTestState::TESTING; }
  uint32_t       lastPairedAtMs() const { return lastPairedAtMs_; }
  OwnershipState ownershipState() const { return ownershipState_; }
  uint32_t       ownershipEpoch() const { return ownershipEpoch_; }

  bool     isResetAckPendingToCloud() const { return resetAckPendingToCloud_; }
  uint32_t pendingResetTargetEpoch() const { return pendingResetTargetEpoch_; }

  static OwnershipState parseOwnershipState(const char* stateStr);
  static const char*    ownershipStateToString(OwnershipState st);

 private:
  void startOwnerSetupHotspot(int32_t channel = 1);
  void stopOwnerSetupHotspot();
  void queueLiveWifiCredentialTest(const char* ssid,
                                   const char* pass,
                                   const char* boxName,
                                   uint32_t nextEpoch);
  void launchWifiConnectAttempt(uint8_t profileIdx, bool pureStaMode);
  void finalizeWifiTestSuccess(uint32_t nowMs,
                               bool gotValidIp,
                               RuntimeConfig& ioConfig,
                               RelayController& relays,
                               IrrigationController& irrigation,
                               BuzzerManager& buzzer,
                               EventNotificationManager& events,
                               LcdManager& lcd);
  void finalizeWifiTestFailure(uint32_t nowMs,
                               const char* reasonMessage,
                               LcdManager& lcd);
  void registerHttpRoutes(RuntimeConfig& ioConfig,
                          RelayController& relays,
                          IrrigationController& irrigation,
                          BuzzerManager& buzzer,
                          EventNotificationManager& events,
                          LcdManager& lcd);

  Preferences    prefs_;
  WebServer      setupServer_{80};
  bool           setupApActive_           = false;
  bool           routesRegistered_        = false;
  bool           pendingApShutdown_       = false;
  uint32_t       apShutdownAtMs_          = 0;

  // Live Wi-Fi Password Verification State
  WifiTestState    wifiTestState_           = WifiTestState::IDLE;
  WifiTestPhase    wifiTestPhase_           = WifiTestPhase::NONE;
  uint32_t         wifiTestStartedAtMs_     = 0;
  uint32_t         wifiTestActionAtMs_      = 0;
  uint32_t         lastAttemptLaunchedAtMs_ = 0;
  uint32_t         lastPairedAtMs_          = 0;
  uint8_t          connectProfileIdx_       = 0;
  bool             pureStaRetryUsed_        = false;
  volatile bool    ignoreDisconnectEvents_  = false;
  volatile bool    staLinkConnected_        = false;
  volatile bool    staGotIp_                = false;
  volatile uint32_t staConnectedAtMs_       = 0;
  volatile uint8_t lastStaDisconnectReason_ = 0;
  volatile uint8_t staDisconnectCount_      = 0;
  volatile uint8_t authFailCount_           = 0;
  volatile uint8_t authExpireCount_         = 0;
  volatile uint8_t noApCount_               = 0;
  volatile uint8_t loggedDisconnectCount_   = 0;
  volatile bool    loggedStaConnected_      = false;
  volatile bool    loggedStaGotIp_          = false;
  bool             clientReadConnectedAck_  = false;
  char             pendingSsid_[36]         = {0};
  char             pendingPass_[68]         = {0};
  char             pendingBoxName_[36]      = {0};
  uint32_t         pendingNextEpoch_        = 1;
  char             wifiTestError_[160]      = {0};

  // Cached 2.4 GHz scan table from /api/wifi-scan (channel, BSSID, authMode, RSSI)
  static constexpr int MAX_CACHED_CHANNELS  = 16;
  struct CachedScanInfo {
    char    ssid[33];
    int32_t channel;
    int     rssi;
    uint8_t authMode;
    uint8_t bssid[6];
    bool    hasBssid;
  };
  CachedScanInfo   cachedScan_[MAX_CACHED_CHANNELS] = {};
  int              cachedScanCount_         = 0;
  const CachedScanInfo* findCachedScanForSsid(const char* ssid) const;

  char           deviceId_[28]            = {0};
  char           displayName_[36]         = "SmartPlantCare Box";
  char           bootId_[28]              = {0};
  char           claimCode_[36]           = {0};
  char           deviceAuthSecret_[48]    = {0};
  char           wifiSsid_[36]            = {0};
  char           wifiPassword_[68]        = {0};
  char           setupApSsid_[40]         = {0};

  OwnershipState ownershipState_          = OwnershipState::UNCLAIMED;
  uint32_t       ownershipEpoch_          = 0;
  bool           resetAckPendingToCloud_  = false;
  uint32_t       pendingResetTargetEpoch_ = 0;
};
