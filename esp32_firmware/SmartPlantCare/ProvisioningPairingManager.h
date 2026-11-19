#pragma once
/**
 * @file ProvisioningPairingManager.h
 * @brief NVS persistence, Owner Wireless Wi-Fi Provisioning (WPA2 SoftAP + HTTP App/Web Portal),
 *        physical claim verification, ownershipEpoch enforcement, and RESET_PENDING lifecycle.
 *
 * HOW THE OWNER SETS WI-FI (ZERO HARDCODED WI-FI IN CODE):
 * 1. When the ESP32 is UNCLAIMED or has no Wi-Fi in NVS, it automatically broadcasts a
 *    WPA2 Setup Hotspot:
 *      SSID:     "SPC-SETUP-spc-unit-0001"
 *      Password: Secrets::DEFAULT_PROV_POP ("spc-pop-0001")
 *      IP:       192.168.4.1
 * 2. The Owner sets the Wi-Fi either:
 *    - From the Android App via POST http://192.168.4.1/api/provision
 *      (JSON: { "ssid", "password", "claimCode", "ownerUid" }), OR
 *    - From their phone browser at http://192.168.4.1 (built-in mobile setup page).
 * 3. The ESP32 verifies the physical claimCode, saves the Owner's Wi-Fi to NVS, shuts down
 *    the Setup AP, connects to the Owner's Wi-Fi, beeps once (150ms), and transitions to OWNED.
 * 4. When the Owner unclaims the device or deletes their account (RESET_PENDING), the ESP32
 *    wipes the Owner's Wi-Fi from NVS and re-opens the Setup Hotspot for the next owner.
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

  // Nonblocking service loop for the Owner Setup AP & HTTP Provisioning Endpoints
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
  bool           setupApActive_          = false;
  bool           routesRegistered_       = false;
  bool           pendingApShutdown_      = false;
  uint32_t       apShutdownAtMs_         = 0;

  char           deviceId_[28]           = {0};
  char           bootId_[28]             = {0};
  char           claimCode_[36]          = {0};
  char           deviceAuthSecret_[48]   = {0};
  char           wifiSsid_[36]           = {0};
  char           wifiPassword_[68]       = {0};
  char           setupApSsid_[32]        = {0};

  OwnershipState ownershipState_          = OwnershipState::UNCLAIMED;
  uint32_t       ownershipEpoch_          = 0;
  bool           resetAckPendingToCloud_  = false;
  uint32_t       pendingResetTargetEpoch_ = 0;
};
