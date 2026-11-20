#pragma once
/**
 * @file ProvisioningPairingManager.h
 * @brief NVS persistence, Owner Wireless Wi-Fi Provisioning (WPA2 SoftAP + 2.4 GHz Wi-Fi Scanner
 *        + HTTP App/Web Portal), custom box displayName, physical claim verification,
 *        wrong-password recovery watchdog, and RESET_PENDING lifecycle.
 */

#include <Arduino.h>
#include <Preferences.h>
#include <WebServer.h>
#include <WiFi.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EventNotificationManager.h"
#include "IrrigationController.h"
#include "LcdManager.h"
#include "Models.h"
#include "RelayController.h"
#include "SecretsTemplate.h"

class ProvisioningPairingManager {
 public:
  void begin(RuntimeConfig& ioConfig);

  // Nonblocking service loop for Setup AP, HTTP API (/api/info, /api/wifi-scan, /api/provision),
  // and 35s post-provisioning Wi-Fi join recovery watchdog.
  void serviceProvisioningPortal(uint32_t nowMs,
                                 RuntimeConfig& ioConfig,
                                 RelayController& relays,
                                 IrrigationController& irrigation,
                                 BuzzerManager& buzzer,
                                 EventNotificationManager& events,
                                 LcdManager& lcd);

  // Update Wi-Fi credentials in NVS (from Owner Setup Portal / App / Serial)
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

  const char*    deviceId() const { return deviceId_; }
  const char*    displayName() const { return displayName_; }
  const char*    bootId() const { return bootId_; }
  const char*    claimCode() const { return claimCode_; }
  const char*    deviceAuthSecret() const { return deviceAuthSecret_; }
  const char*    wifiSsid() const { return wifiSsid_; }
  const char*    wifiPassword() const { return wifiPassword_; }
  const char*    setupApSsid() const { return setupApSsid_; }
  bool           isSetupApActive() const { return setupApActive_; }
  OwnershipState ownershipState() const { return ownershipState_; }
  uint32_t       ownershipEpoch() const { return ownershipEpoch_; }

  bool     isResetAckPendingToCloud() const { return resetAckPendingToCloud_; }
  uint32_t pendingResetTargetEpoch() const { return pendingResetTargetEpoch_; }

  static OwnershipState parseOwnershipState(const char* stateStr);
  static const char*    ownershipStateToString(OwnershipState st);

 private:
  void startOwnerSetupHotspot();
  void stopOwnerSetupHotspot();
  void registerHttpRoutes(RuntimeConfig& ioConfig,
                          RelayController& relays,
                          IrrigationController& irrigation,
                          BuzzerManager& buzzer,
                          EventNotificationManager& events,
                          LcdManager& lcd);

  Preferences    prefs_;
  WebServer      setupServer_{80};
  bool           setupApActive_           = false;
  bool           setupApDesired_          = false;
  uint32_t       lastApRetryMs_           = 0;
  bool           routesRegistered_        = false;
  bool           pendingApShutdown_       = false;
  uint32_t       apShutdownAtMs_          = 0;
  bool           wifiVerifyPending_       = false;
  uint32_t       wifiVerifyDeadlineMs_    = 0;

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
