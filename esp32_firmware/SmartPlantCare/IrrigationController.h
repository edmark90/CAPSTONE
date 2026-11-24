#pragma once
/**
 * @file IrrigationController.h
 * @brief Complete 3-zone solenoid + 1 shared pump irrigation state machine with
 *        fair round-robin scheduling, manual command validation, and hard safety gates.
 *
 * SAFETY PRECEDENCE (HIGHEST FIRST):
 * 1. Emergency stop / Reset-pending / Setup mode -> Immediate all-outputs OFF
 * 2. Relay hardware consistency fault -> Latch FAULT_LOCKOUT, all-outputs OFF
 * 3. Reservoir CRITICAL / EMPTY / SENSOR_FAULT -> Immediate stop, block manual & auto
 * 4. Global or zone maximum pump runtime exceeded -> Latch FAULT_LOCKOUT, all-outputs OFF
 * 5. Outdoor confirmed rain suspension latch -> Stop active run, block new starts
 * 6. Outdoor weather unavailable/stale or rain sensor fault -> Block new unattended auto starts
 * 7. Zone soil sensor fault, crop disabled, or zone cooldown active -> Block affected crop
 * 8. Eligible dry crop OR validated manual command -> Open 1 valve -> settle -> Pump ON
 *    -> run pulse -> Pump OFF -> settle -> Close valve -> Soak settle -> Recheck soil.
 */

#include <Arduino.h>
#include <Preferences.h>
#include <string.h>
#include "BuzzerManager.h"
#include "Config.h"
#include "EventNotificationManager.h"
#include "Models.h"
#include "RainWeatherManager.h"
#include "RelayController.h"
#include "ReservoirManager.h"
#include "SoilMoistureManager.h"

struct CommandExecutionReport {
  bool     hasReport       = false;
  char     commandId[40]   = {0};
  char     state[16]       = {0};  // ACCEPTED, REJECTED, RUNNING, DONE, ABORTED
  char     reason[64]      = {0};
  uint32_t updatedAtMs     = 0;

  bool isTerminal() const {
    return strcmp(state, "DONE") == 0 ||
           strcmp(state, "REJECTED") == 0 ||
           strcmp(state, "ABORTED") == 0;
  }
};

class IrrigationController {
 public:
  void begin(RelayController* relays,
             SoilMoistureManager* soil,
             ReservoirManager* reservoir,
             RainWeatherManager* rainWeather,
             BuzzerManager* buzzer,
             EventNotificationManager* events);

  // Fast 25ms safety check — preempts everything if reservoir/emergency/runtime fails
  void serviceSafetyInterlocks(uint32_t nowMs,
                               const RuntimeConfig& cfg,
                               OwnershipState ownershipState);

  // 50ms irrigation state machine advancement
  void advanceStateMachine(uint32_t nowMs,
                           const RuntimeConfig& cfg,
                           OwnershipState ownershipState);

  // Validate and process incoming remote command from Firebase /commandRequests
  void handleCommand(const CommandRequest& cmd,
                     uint32_t nowMs,
                     uint64_t currentEpochMs,
                     uint32_t currentOwnershipEpoch,
                     const RuntimeConfig& cfg,
                     OwnershipState ownershipState);

  void triggerEmergencyStop(const char* reason);
  void clearEmergencyStop();

  IrrigationState state() const { return state_; }
  CropZoneId      activeZone() const { return activeZone_; }
  bool            isEmergencyStopped() const { return emergencyStopLatched_; }
  bool            isFaultLocked() const { return faultLockoutLatched_; }
  const char*     lastAbortReason() const { return lastAbortReason_; }
  uint32_t        activePumpElapsedSec(uint32_t nowMs) const;
  uint32_t        activeTargetDurationSec() const { return currentTargetDurationMs_ / 1000; }

  void setNetworkOnline(bool online) { networkOnline_ = online; }
  void setOwnershipEpoch(uint32_t epoch) { ownershipEpoch_ = epoch; }

  const char* activeCommandId() const { return activeCommandId_; }
  bool hasProcessedCommand(const char* commandId) const { return isCommandAlreadyProcessed(commandId); }
  bool hasPendingCommandReports() const { return reportCount_ > 0; }
  bool peekPendingCommandReport(CommandExecutionReport& outReport) const;
  void popOldestCommandReport();
  bool popPendingCommandReport(CommandExecutionReport& outReport);
  void clearProcessedCommandsForReset();

  bool peekPendingIrrigationHistory(IrrigationHistoryRecord& outRecord) const;
  void popOldestIrrigationHistory();
  void clearIrrigationHistoryQueue();

 private:
  void loadProcessedCommandsFromNvs();
  void persistProcessedCommandSlot(size_t slotIdx, const char* commandId, size_t nextHead);
  void enqueueIrrigationHistory(uint32_t nowMs,
                                CropZoneId zone,
                                bool isManual,
                                bool completed,
                                const char* reason,
                                uint32_t durationMs);
  void abortActiveCycle(uint32_t nowMs, const char* eventType, const char* reason);
  void beginNormalStopSequence(uint32_t nowMs, const char* completionReason);
  bool isGlobalSafetyOk(const RuntimeConfig& cfg,
                        OwnershipState ownershipState,
                        bool isManualRequest,
                        char* outRejectReason,
                        size_t reasonLen) const;
  bool isZoneEligibleForAuto(CropZoneId zone,
                             uint32_t nowMs,
                             const RuntimeConfig& cfg) const;
  CropZoneId selectNextEligibleZoneRoundRobin(uint32_t nowMs, const RuntimeConfig& cfg);
  bool isCommandAlreadyProcessed(const char* commandId) const;
  void recordCommandProcessed(const char* commandId);
  void queueCommandReport(const char* commandId, const char* state, const char* reason);

  RelayController*          relays_                  = nullptr;
  SoilMoistureManager*      soil_                    = nullptr;
  ReservoirManager*         reservoir_               = nullptr;
  RainWeatherManager*       rainWeather_             = nullptr;
  BuzzerManager*            buzzer_                  = nullptr;
  EventNotificationManager* events_                  = nullptr;

  IrrigationState           state_                   = IrrigationState::BOOT_SAFE;
  CropZoneId                activeZone_              = CropZoneId::NONE;
  uint8_t                   roundRobinCursor_        = 0;
  bool                      activeRunIsManual_       = false;
  char                      activeCommandId_[40]     = {0};

  uint32_t                  stateEnteredAtMs_        = 0;
  uint32_t                  pumpStartedAtMs_         = 0;
  uint32_t                  pumpStoppedAtMs_         = 0;
  uint64_t                  pumpStartedEpochMs_      = 0;
  float                     pumpStartMoisturePct_    = NAN;
  bool                      pumpRanThisCycle_        = false;
  uint32_t                  currentTargetDurationMs_ = Config::DEFAULT_WATER_PULSE_MS;
  uint32_t                  zoneCumulativePumpMs_[3] = {0, 0, 0};

  bool                      emergencyStopLatched_    = false;
  bool                      faultLockoutLatched_     = false;
  char                      lastAbortReason_[64]     = "NONE";
  bool                      networkOnline_           = false;
  uint32_t                  ownershipEpoch_          = 0;

  char                      processedCmdIds_[Config::PROCESSED_CMD_RING_SIZE][40] = {{0}};
  size_t                    processedCmdHead_        = 0;
  CommandExecutionReport    reportQueue_[Config::CMD_REPORT_QUEUE_CAPACITY];
  size_t                    reportHead_              = 0;
  size_t                    reportTail_              = 0;
  size_t                    reportCount_             = 0;

  IrrigationHistoryRecord   irrHistoryQueue_[Config::IRRIGATION_HISTORY_QUEUE_CAPACITY];
  size_t                    irrHistoryHead_          = 0;
  size_t                    irrHistoryTail_          = 0;
  size_t                    irrHistoryCount_         = 0;
  uint32_t                  irrHistorySeq_           = 0;
};
