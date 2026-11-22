#pragma once
/**
 * @file FirebaseClientManager.h
 * @brief Nonblocking Wi-Fi, NTP, Firebase RTDB REST sync, and Open-Meteo 3-hour weather fetcher.
 *
 * SUPPORTS BOTH FIREBASE PLANS AUTOMATICALLY:
 * - Blaze Plan (Cloud Functions deployed): Uses /mintDeviceCustomToken + signInWithCustomToken
 *   and reads cached weather from /devices/{deviceId}/weather.
 * - Free Spark Plan (No Cloud Functions): Automatically falls back to Firebase REST Auth /
 *   Spark RTDB rules and fetches the 3-hour max precipitation_probability directly from
 *   Open-Meteo whenever /devices/{deviceId}/weather is missing or stale.
 */

#include <Arduino.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EnvironmentalSensors.h"
#include "EventNotificationManager.h"
#include "IrrigationController.h"
#include "LcdManager.h"
#include "Models.h"
#include "ProvisioningPairingManager.h"
#include "RainWeatherManager.h"
#include "RelayController.h"
#include "ReservoirManager.h"
#include "SecretsTemplate.h"
#include "SoilMoistureManager.h"

class FirebaseClientManager {
 public:
  void begin(ProvisioningPairingManager* provisioning);

  // Maintain nonblocking Wi-Fi connection, NTP clock sync, and buzzer confirmation on connect
  void serviceNetworkAndTime(uint32_t nowMs, DiagnosticsMetrics& ioDiag, BuzzerManager& buzzer);

  // Periodic 15s telemetry + status publish (or immediate on state transition)
  bool publishStatusAndTelemetry(uint32_t nowMs,
                                 const RuntimeConfig& cfg,
                                 const EnvironmentReading& env,
                                 const SoilZoneStatus soil[3],
                                 const ReservoirReading& reservoir,
                                 const RainGateStatus& rain,
                                 const RelayController& relays,
                                 const IrrigationController& irrigation,
                                 uint8_t lcdAddr,
                                 DiagnosticsMetrics& ioDiag);

  // Flush queued state-transition events and command execution reports
  void flushPendingEventsAndReports(uint32_t nowMs,
                                    EventNotificationManager& events,
                                    IrrigationController& irrigation,
                                    DiagnosticsMetrics& ioDiag);

  // Fast 1.5s poll for manual watering & emergency stop commands only
  void pollPendingCommandsOnly(uint32_t nowMs,
                               const RuntimeConfig& cfg,
                               IrrigationController& irrigation,
                               BuzzerManager& buzzer);

  // Individual staggered cloud polls (prevents 5-6 stacked HTTPS calls on one tick)
  void pollConfigOnly(uint32_t nowMs,
                      RuntimeConfig& ioConfig,
                      RelayController& relays,
                      BuzzerManager& buzzer);

  void pollMetaOnly(uint32_t nowMs,
                    RuntimeConfig& ioConfig,
                    RelayController& relays,
                    IrrigationController& irrigation,
                    BuzzerManager& buzzer,
                    EventNotificationManager& events,
                    LcdManager& lcd);

  void pollWeatherOnly(uint32_t nowMs, RainWeatherManager& rainWeather);

  // Combined poll helper (kept for compatibility)
  void pollCloudControlAndWeather(uint32_t nowMs,
                                  RuntimeConfig& ioConfig,
                                  RelayController& relays,
                                  IrrigationController& irrigation,
                                  RainWeatherManager& rainWeather,
                                  BuzzerManager& buzzer,
                                  EventNotificationManager& events,
                                  LcdManager& lcd,
                                  DiagnosticsMetrics& ioDiag);

  bool     isWifiConnected() const { return WiFi.status() == WL_CONNECTED; }
  bool     isFirebaseAuthenticated() const { return idTokenValid_ || sparkDirectMode_; }
  uint32_t lastSyncUptimeMs() const { return lastSuccessfulSyncMs_; }
  bool     consumeWeatherRefreshRequested() {
    if (!weatherRefreshRequested_) return false;
    weatherRefreshRequested_ = false;
    return true;
  }
  uint64_t currentEpochMs() const;

 private:
  void   resetRtdbConnection();
  bool   ensureValidAuth(uint32_t nowMs);
  bool   requestCustomTokenAndSignIn(uint32_t nowMs);
  bool   requestAnonymousOrSparkAuth(uint32_t nowMs);
  String buildRtdbUrl(const String& path) const;
  bool   sendResetAckToBackend(uint32_t nowMs);

  void syncMetaNode(uint32_t nowMs,
                    RuntimeConfig& ioConfig,
                    RelayController& relays,
                    IrrigationController& irrigation,
                    BuzzerManager& buzzer,
                    EventNotificationManager& events,
                    LcdManager& lcd);
  void syncWeatherNode(uint32_t nowMs, RainWeatherManager& rainWeather);
  bool fetchOpenMeteoDirectAndCache(uint32_t nowMs, RainWeatherManager& rainWeather);
  void syncConfigNode(RuntimeConfig& ioConfig,
                      RelayController& relays,
                      BuzzerManager& buzzer);
  void syncPendingCommands(uint32_t nowMs,
                           const RuntimeConfig& cfg,
                           IrrigationController& irrigation,
                           BuzzerManager& buzzer);

  bool httpRequestJson(const char* method,
                       const String& url,
                       const String& payload,
                       String& outResponse);

  ProvisioningPairingManager* provisioning_             = nullptr;
  bool                        wifiAttemptActive_        = false;
  uint8_t                     wifiConnectAttemptNum_    = 0;
  uint32_t                    wifiConnectedAtMs_        = 0;
  uint32_t                    wifiDhcpWaitStartMs_      = 0;
  uint32_t                    lastWifiAttemptAtMs_      = 0;
  uint32_t                    wifiBackoffMs_            = 12000UL;
  bool                        ntpConfigured_            = false;
  bool                        firstTelemetryPublished_  = false;

  bool                        idTokenValid_          = false;
  bool                        sparkDirectMode_       = false;
  String                      idToken_;
  uint32_t                    idTokenObtainedAtMs_   = 0;
  uint32_t                    lastAuthAttemptAtMs_   = 0;
  uint32_t                    lastSuccessfulSyncMs_     = 0;
  uint32_t                    lastDirectWeatherAtMs_    = 0;
  uint32_t                    lastDirectWeatherRetryMs_ = 0;
  uint64_t                    lastCloudWeatherFetched_  = 0;
  bool                        ownerLocationLoaded_      = false;
  bool                        weatherRefreshRequested_  = false;
  float                       deviceLat_                = Secrets::DEFAULT_WEATHER_LATITUDE;
  float                       deviceLon_                = Secrets::DEFAULT_WEATHER_LONGITUDE;
  char                        deviceLocationName_[48]   = "";

  // Persistent HTTPS Keep-Alive client dedicated to Firebase RTDB (eliminates 1.5-3s TLS handshakes)
  WiFiClientSecure            rtdbClient_;
  HTTPClient                  rtdbHttp_;
  bool                        rtdbHttpActive_           = false;
};
