#include "IrrigationController.h"
#include <stdio.h>
#include <string.h>

void IrrigationController::begin(RelayController* relays,
                                 SoilMoistureManager* soil,
                                 ReservoirManager* reservoir,
                                 RainWeatherManager* rainWeather,
                                 BuzzerManager* buzzer,
                                 EventNotificationManager* events) {
  relays_      = relays;
  soil_        = soil;
  reservoir_   = reservoir;
  rainWeather_ = rainWeather;
  buzzer_      = buzzer;
  events_      = events;

  if (relays_) {
    relays_->emergencyAllOff();
  }
  state_            = IrrigationState::SELF_TEST;
  stateEnteredAtMs_ = millis();
}

void IrrigationController::serviceSafetyInterlocks(uint32_t nowMs,
                                                   const RuntimeConfig& cfg,
                                                   OwnershipState ownershipState) {
  if (!relays_) return;

  // 1. Verify relay state invariants on every 25ms safety pass
  if (!relays_->verifyHardwareConsistency()) {
    faultLockoutLatched_ = true;
    abortActiveCycle(nowMs, "POWER_OR_RELAY_FAULT", "Relay state consistency check failed");
    if (buzzer_) buzzer_->triggerEventTransition(BuzzerEventId::CRITICAL_SYSTEM_FAULT);
    return;
  }

  // 2. If notOwned or resetPending, ensure all outputs are strictly OFF
  if (ownershipState != OwnershipState::OWNED) {
    if (relays_->isPumpOn() || relays_->activeValveZone() != CropZoneId::NONE) {
      abortActiveCycle(nowMs, "IRRIGATION_ABORTED", "Device unclaimed or reset pending");
    }
    return;
  }

  // 3. Emergency stop or fault lockout preempts all states
  if (emergencyStopLatched_ || faultLockoutLatched_) {
    relays_->emergencyAllOff();
    state_ = IrrigationState::FAULT_LOCKOUT;
    return;
  }

  const bool isActivelyWatering =
      (state_ == IrrigationState::OPENING_SELECTED_VALVE ||
       state_ == IrrigationState::PUMP_WATERING ||
       state_ == IrrigationState::PUMP_STOPPING);

  if (!isActivelyWatering) return;

  // 4. Reservoir safety check during active watering
  if (reservoir_ && !reservoir_->isPumpAllowed()) {
    abortActiveCycle(nowMs, "IRRIGATION_ABORTED", "Reservoir critical, empty, or sensor fault");
    return;
  }

  // 5. Outdoor confirmed rain suspension check during active watering
  if (cfg.mode == DeviceMode::OUTDOOR && rainWeather_ &&
      rainWeather_->rainStatus().rainSuspensionLatched) {
    abortActiveCycle(nowMs, "IRRIGATION_ABORTED", "Confirmed rain suspension triggered");
    state_ = IrrigationState::SUSPENDED_RAIN;
    return;
  }

  // 6. Hard maximum pump runtime guard
  if (state_ == IrrigationState::PUMP_WATERING) {
    const uint32_t pumpElapsed = uint32_t(nowMs - pumpStartedAtMs_);
    const uint8_t zIdx = static_cast<uint8_t>(activeZone_);
    const uint32_t maxZoneMs = (zIdx < 3) ? cfg.zones[zIdx].maxPumpOnMs
                                          : Config::DEFAULT_MAX_ZONE_PUMP_ON_MS;

    if (pumpElapsed > Config::GLOBAL_HARD_MAX_PUMP_ON_MS ||
        pumpElapsed > maxZoneMs + 2000) {
      faultLockoutLatched_ = true;
      abortActiveCycle(nowMs, "POWER_OR_RELAY_FAULT", "Maximum pump runtime limit exceeded");
      if (buzzer_) buzzer_->triggerEventTransition(BuzzerEventId::CRITICAL_SYSTEM_FAULT);
      return;
    }
  }
}

void IrrigationController::advanceStateMachine(uint32_t nowMs,
                                               const RuntimeConfig& cfg,
                                               OwnershipState ownershipState) {
  if (!relays_ || !soil_ || !reservoir_ || !rainWeather_) return;

  if (emergencyStopLatched_ || faultLockoutLatched_) {
    relays_->emergencyAllOff();
    state_ = IrrigationState::FAULT_LOCKOUT;
    return;
  }

  if (ownershipState != OwnershipState::OWNED) {
    relays_->emergencyAllOff();
    state_ = IrrigationState::IDLE_MONITORING;
    return;
  }

  switch (state_) {
    case IrrigationState::BOOT_SAFE:
    case IrrigationState::SELF_TEST: {
      relays_->emergencyAllOff();
      if (uint32_t(nowMs - stateEnteredAtMs_) >= 3500) {
        state_            = IrrigationState::IDLE_MONITORING;
        stateEnteredAtMs_ = nowMs;
        if (events_) {
          events_->enqueueEvent("DEVICE_READY",
                                EventSeverity::INFO,
                                "Smart Plant Care Ready",
                                "Sensor self-test completed; all valves and pump in safe OFF state.");
        }
      }
      break;
    }

    case IrrigationState::SUSPENDED_RAIN: {
      relays_->emergencyAllOff();
      if (cfg.mode == DeviceMode::INDOOR ||
          !rainWeather_->rainStatus().rainSuspensionLatched) {
        state_            = IrrigationState::IDLE_MONITORING;
        stateEnteredAtMs_ = nowMs;
      }
      break;
    }

    case IrrigationState::IDLE_MONITORING: {
      if (cfg.mode == DeviceMode::OUTDOOR &&
          rainWeather_->rainStatus().rainSuspensionLatched) {
        state_            = IrrigationState::SUSPENDED_RAIN;
        stateEnteredAtMs_ = nowMs;
        break;
      }

      char rejectReason[64] = {0};
      if (!isGlobalSafetyOk(cfg, ownershipState, false, rejectReason, sizeof(rejectReason))) {
        break;
      }

      const CropZoneId nextCrop = selectNextEligibleZoneRoundRobin(nowMs, cfg);
      if (nextCrop != CropZoneId::NONE) {
        activeZone_              = nextCrop;
        activeRunIsManual_       = false;
        activeCommandId_[0]      = '\0';
        const uint8_t zIdx       = static_cast<uint8_t>(nextCrop);
        currentTargetDurationMs_ = constrain(cfg.zones[zIdx].pulseDurationMs,
                                             1000UL,
                                             cfg.zones[zIdx].maxPumpOnMs);
        state_                   = IrrigationState::QUEUED;
        stateEnteredAtMs_        = nowMs;
      }
      break;
    }

    case IrrigationState::QUEUED: {
      char rejectReason[64] = {0};
      if (!isGlobalSafetyOk(cfg, ownershipState, activeRunIsManual_,
                            rejectReason, sizeof(rejectReason))) {
        abortActiveCycle(nowMs, "IRRIGATION_ABORTED", rejectReason);
        break;
      }

      // STEP 1: Confirm all relays OFF, then open ONLY the selected crop solenoid valve
      relays_->emergencyAllOff();
      if (!relays_->openSingleValve(activeZone_)) {
        abortActiveCycle(nowMs, "POWER_OR_RELAY_FAULT", "Failed to open selected crop valve");
        break;
      }
      state_            = IrrigationState::OPENING_SELECTED_VALVE;
      stateEnteredAtMs_ = nowMs;
      break;
    }

    case IrrigationState::OPENING_SELECTED_VALVE: {
      // STEP 2: Wait nonblocking valve-opening settling time (400ms) before starting pump
      if (uint32_t(nowMs - stateEnteredAtMs_) < Config::VALVE_OPEN_SETTLE_MS) {
        break;
      }

      char rejectReason[64] = {0};
      if (!isGlobalSafetyOk(cfg, ownershipState, activeRunIsManual_,
                            rejectReason, sizeof(rejectReason))) {
        abortActiveCycle(nowMs, "IRRIGATION_ABORTED", rejectReason);
        break;
      }

      // STEP 3: Activate shared water pump
      if (!relays_->setPump(true)) {
        abortActiveCycle(nowMs, "POWER_OR_RELAY_FAULT", "Pump refused to start safely");
        break;
      }

      pumpStartedAtMs_  = nowMs;
      state_            = IrrigationState::PUMP_WATERING;
      stateEnteredAtMs_ = nowMs;

      if (activeRunIsManual_ && activeCommandId_[0] != '\0') {
        queueCommandReport(activeCommandId_, "RUNNING", "Watering in progress");
      }

      if (events_) {
        char msg[112];
        snprintf(msg, sizeof(msg), "Started %s watering for %s (%lu s pulse).",
                 activeRunIsManual_ ? "manual" : "automatic",
                 cropZoneDisplayName(activeZone_),
                 static_cast<unsigned long>(currentTargetDurationMs_ / 1000));
        events_->enqueueEvent("IRRIGATION_STARTED",
                              EventSeverity::INFO,
                              "Irrigation Started",
                              msg,
                              cropZoneSlug(activeZone_),
                              static_cast<float>(currentTargetDurationMs_ / 1000));
      }
      break;
    }

    case IrrigationState::PUMP_WATERING: {
      const uint32_t elapsedMs = uint32_t(nowMs - pumpStartedAtMs_);
      const uint8_t  zIdx      = static_cast<uint8_t>(activeZone_);

      // Check if soil already reached stop threshold during automatic run
      const SoilZoneStatus& zs = soil_->zoneStatus(activeZone_);
      if (!activeRunIsManual_ && zs.valid &&
          zs.moisturePct >= cfg.zones[zIdx].stopThresholdPct) {
        beginNormalStopSequence(nowMs, "Target soil moisture reached");
        break;
      }

      // Check if pulse duration completed
      if (elapsedMs >= currentTargetDurationMs_) {
        beginNormalStopSequence(nowMs, "Scheduled watering pulse completed");
      }
      break;
    }

    case IrrigationState::PUMP_STOPPING: {
      // STEP 4: Pump is already OFF; wait 250ms pressure run-down before closing valve
      if (uint32_t(nowMs - stateEnteredAtMs_) >= Config::PUMP_STOP_SETTLE_MS) {
        relays_->closeAllValves();
        state_            = IrrigationState::CLOSING_VALVE;
        stateEnteredAtMs_ = nowMs;
      }
      break;
    }

    case IrrigationState::CLOSING_VALVE: {
      // STEP 5: Valve closed -> record completion & enter soak-settle period
      soil_->recordZoneWatered(activeZone_, nowMs);

      if (activeRunIsManual_ && activeCommandId_[0] != '\0') {
        queueCommandReport(activeCommandId_, "DONE", "Manual watering pulse completed");
      }

      if (events_) {
        char msg[112];
        snprintf(msg, sizeof(msg),
                 "Completed watering pulse for %s. Waiting soak settle before rechecking moisture.",
                 cropZoneDisplayName(activeZone_));
        events_->enqueueEvent("IRRIGATION_COMPLETED",
                              EventSeverity::INFO,
                              "Irrigation Completed",
                              msg,
                              cropZoneSlug(activeZone_));
      }

      state_            = IrrigationState::SOAK_SETTLE;
      stateEnteredAtMs_ = nowMs;
      break;
    }

    case IrrigationState::SOAK_SETTLE: {
      const uint8_t  zIdx   = static_cast<uint8_t>(activeZone_);
      const uint32_t soakMs = (zIdx < 3) ? cfg.zones[zIdx].soakSettleMs
                                         : Config::DEFAULT_SOAK_SETTLE_MS;
      if (uint32_t(nowMs - stateEnteredAtMs_) >= soakMs) {
        activeZone_         = CropZoneId::NONE;
        activeRunIsManual_  = false;
        activeCommandId_[0] = '\0';
        state_              = IrrigationState::IDLE_MONITORING;
        stateEnteredAtMs_   = nowMs;
      }
      break;
    }

    case IrrigationState::FAULT_LOCKOUT:
    default:
      relays_->emergencyAllOff();
      break;
  }
}

void IrrigationController::handleCommand(const CommandRequest& cmd,
                                         uint32_t nowMs,
                                         uint64_t currentEpochMs,
                                         uint32_t currentOwnershipEpoch,
                                         const RuntimeConfig& cfg,
                                         OwnershipState ownershipState) {
  if (!cmd.valid || cmd.commandId[0] == '\0') return;

  // Deduplicate replayed commands
  if (isCommandAlreadyProcessed(cmd.commandId)) {
    return;
  }
  recordCommandProcessed(cmd.commandId);

  // Verify ownership epoch matches
  if (ownershipState != OwnershipState::OWNED ||
      cmd.ownershipEpoch != currentOwnershipEpoch) {
    queueCommandReport(cmd.commandId, "REJECTED", "Ownership epoch mismatch or device unclaimed");
    return;
  }

  // Always allow STOP_WATERING with highest priority regardless of clock sync
  if (cmd.type == CommandType::STOP_WATERING) {
    if (state_ == IrrigationState::OPENING_SELECTED_VALVE ||
        state_ == IrrigationState::PUMP_WATERING ||
        state_ == IrrigationState::QUEUED) {
      abortActiveCycle(nowMs, "IRRIGATION_ABORTED", "Stopped by user command");
    } else if (relays_) {
      relays_->emergencyAllOff();
    }
    queueCommandReport(cmd.commandId, "DONE", "Stopped immediately");
    return;
  }

  if (cmd.type == CommandType::CLEAR_EMERGENCY) {
    clearEmergencyStop();
    queueCommandReport(cmd.commandId, "DONE", "Emergency lockout cleared");
    return;
  }

  if (cmd.type == CommandType::MUTE_BUZZER) {
    if (buzzer_) buzzer_->setMuted(true);
    queueCommandReport(cmd.commandId, "DONE", "Buzzer muted");
    return;
  }

  // Validate command expiration so delayed offline commands NEVER start watering on reconnect.
  // Only compare wall-clock (currentEpochMs > cmd.expiresAtMs) after NTP has synced (> 1.7e12 ms).
  const bool ntpSynced = (currentEpochMs > 1700000000000ULL);
  if (cmd.expiresAtMs == 0 ||
      (ntpSynced && currentEpochMs > cmd.expiresAtMs) ||
      (cmd.createdAtMs > 0 && (cmd.expiresAtMs - cmd.createdAtMs) > Config::MANUAL_CMD_MAX_TTL_MS)) {
    queueCommandReport(cmd.commandId, "REJECTED", "Command expired before execution");
    if (events_) {
      events_->enqueueEvent("MANUAL_COMMAND_REJECTED",
                            EventSeverity::WARNING,
                            "Manual Command Expired",
                            "Rejected expired watering request to prevent delayed watering.");
    }
    return;
  }

  if (cmd.type != CommandType::WATER_CROP ||
      (cmd.crop != CropZoneId::TOMATO &&
       cmd.crop != CropZoneId::CHILI &&
       cmd.crop != CropZoneId::EGGPLANT)) {
    queueCommandReport(cmd.commandId, "REJECTED", "Invalid command type or crop zone");
    return;
  }

  // Reject if another zone is currently running
  if (state_ != IrrigationState::IDLE_MONITORING &&
      state_ != IrrigationState::SOAK_SETTLE) {
    queueCommandReport(cmd.commandId, "REJECTED", "BUSY: Another irrigation cycle is active");
    if (events_) {
      events_->enqueueEvent("MANUAL_COMMAND_REJECTED",
                            EventSeverity::INFO,
                            "Manual Watering Busy",
                            "Cannot start manual watering while another zone is active.",
                            cropZoneSlug(cmd.crop));
    }
    return;
  }

  char rejectReason[64] = {0};
  if (!isGlobalSafetyOk(cfg, ownershipState, true, rejectReason, sizeof(rejectReason))) {
    queueCommandReport(cmd.commandId, "REJECTED", rejectReason);
    if (events_) {
      events_->enqueueEvent("MANUAL_COMMAND_REJECTED",
                            EventSeverity::WARNING,
                            "Manual Watering Blocked",
                            rejectReason,
                            cropZoneSlug(cmd.crop));
    }
    return;
  }

  const uint8_t zIdx = static_cast<uint8_t>(cmd.crop);
  activeZone_        = cmd.crop;
  activeRunIsManual_ = true;
  snprintf(activeCommandId_, sizeof(activeCommandId_), "%s", cmd.commandId);
  currentTargetDurationMs_ = constrain(cmd.durationMs, 1000UL, cfg.zones[zIdx].maxPumpOnMs);
  state_                   = IrrigationState::QUEUED;
  stateEnteredAtMs_        = nowMs;

  queueCommandReport(cmd.commandId, "ACCEPTED", "Queued for valve opening");
}

void IrrigationController::triggerEmergencyStop(const char* reason) {
  emergencyStopLatched_ = true;
  if (relays_) {
    relays_->emergencyAllOff();
  }
  state_ = IrrigationState::FAULT_LOCKOUT;
  snprintf(lastAbortReason_, sizeof(lastAbortReason_), "%s",
           reason ? reason : "Emergency Stop");
  if (buzzer_) {
    buzzer_->triggerEventTransition(BuzzerEventId::CRITICAL_SYSTEM_FAULT);
  }
  if (events_) {
    events_->enqueueEvent("EMERGENCY_STOP",
                          EventSeverity::CRITICAL,
                          "Emergency Stop Activated",
                          lastAbortReason_);
  }
}

void IrrigationController::clearEmergencyStop() {
  if (relays_) {
    relays_->emergencyAllOff();
  }
  emergencyStopLatched_ = false;
  faultLockoutLatched_  = false;
  activeZone_           = CropZoneId::NONE;
  state_                = IrrigationState::IDLE_MONITORING;
  stateEnteredAtMs_     = millis();
  if (buzzer_) {
    buzzer_->clearEventLatch(BuzzerEventId::CRITICAL_SYSTEM_FAULT);
  }
}

uint32_t IrrigationController::activePumpElapsedSec(uint32_t nowMs) const {
  if (state_ != IrrigationState::PUMP_WATERING) return 0;
  return uint32_t(nowMs - pumpStartedAtMs_) / 1000;
}

bool IrrigationController::popPendingCommandReport(CommandExecutionReport& outReport) {
  if (!pendingReport_.hasReport) return false;
  outReport                = pendingReport_;
  pendingReport_.hasReport = false;
  return true;
}

void IrrigationController::abortActiveCycle(uint32_t nowMs,
                                            const char* eventType,
                                            const char* reason) {
  // Always stop pump first, then close valves immediately on safety abort
  if (relays_) {
    relays_->emergencyAllOff();
  }

  snprintf(lastAbortReason_, sizeof(lastAbortReason_), "%s",
           reason ? reason : "Safety abort");

  if (activeRunIsManual_ && activeCommandId_[0] != '\0') {
    queueCommandReport(activeCommandId_, "ABORTED", lastAbortReason_);
  }

  if (events_ && activeZone_ != CropZoneId::NONE) {
    char msg[112];
    snprintf(msg, sizeof(msg), "Aborted %s irrigation: %s.",
             cropZoneDisplayName(activeZone_), lastAbortReason_);
    events_->enqueueEvent(eventType ? eventType : "IRRIGATION_ABORTED",
                          EventSeverity::CRITICAL,
                          "Irrigation Aborted",
                          msg,
                          cropZoneSlug(activeZone_));
  }

  activeZone_         = CropZoneId::NONE;
  activeRunIsManual_  = false;
  activeCommandId_[0] = '\0';
  state_              = (emergencyStopLatched_ || faultLockoutLatched_)
                            ? IrrigationState::FAULT_LOCKOUT
                            : IrrigationState::IDLE_MONITORING;
  stateEnteredAtMs_   = nowMs;
}

void IrrigationController::beginNormalStopSequence(uint32_t nowMs,
                                                   const char* completionReason) {
  // Normal shutdown: turn PUMP OFF first, keep valve open briefly for pressure relief
  if (relays_) {
    relays_->setPump(false);
  }
  snprintf(lastAbortReason_, sizeof(lastAbortReason_), "%s",
           completionReason ? completionReason : "Completed");
  state_            = IrrigationState::PUMP_STOPPING;
  stateEnteredAtMs_ = nowMs;
}

bool IrrigationController::isGlobalSafetyOk(const RuntimeConfig& cfg,
                                            OwnershipState ownershipState,
                                            bool isManualRequest,
                                            char* outRejectReason,
                                            size_t reasonLen) const {
  if (ownershipState != OwnershipState::OWNED) {
    snprintf(outRejectReason, reasonLen, "Device not claimed by an active owner");
    return false;
  }
  if (emergencyStopLatched_ || faultLockoutLatched_) {
    snprintf(outRejectReason, reasonLen, "Emergency stop or fault lockout is active");
    return false;
  }
  if (!reservoir_ || !reservoir_->isPumpAllowed()) {
    snprintf(outRejectReason, reasonLen, "Reservoir level critical, empty, or sensor invalid");
    return false;
  }
  if (cfg.mode == DeviceMode::OUTDOOR && rainWeather_) {
    if (rainWeather_->rainStatus().rainSuspensionLatched) {
      snprintf(outRejectReason, reasonLen, "Rain suspension latch is active");
      return false;
    }
    if (!isManualRequest && !rainWeather_->isAutoWateringAllowedByWeather(cfg)) {
      snprintf(outRejectReason, reasonLen, "Outdoor weather forecast unavailable or stale");
      return false;
    }
  }
  return true;
}

bool IrrigationController::isZoneEligibleForAuto(CropZoneId zone,
                                                 uint32_t nowMs,
                                                 const RuntimeConfig& cfg) const {
  const uint8_t idx = static_cast<uint8_t>(zone);
  if (idx >= 3 || !soil_) return false;

  const SoilZoneConfig& zcfg = cfg.zones[idx];
  const SoilZoneStatus& zst  = soil_->zoneStatus(zone);

  if (!zcfg.enabled) return false;
  if (!zst.valid || !isfinite(zst.moisturePct)) return false;
  if (!zst.needsWater || zst.moisturePct > zcfg.startThresholdPct) return false;

  // Enforce per-zone cooldown between watering pulses
  if (zst.lastWateredAtMs != 0 &&
      uint32_t(nowMs - zst.lastWateredAtMs) < zcfg.cooldownMs) {
    return false;
  }
  return true;
}

CropZoneId IrrigationController::selectNextEligibleZoneRoundRobin(
    uint32_t nowMs,
    const RuntimeConfig& cfg) {
  for (uint8_t offset = 0; offset < 3; ++offset) {
    const uint8_t idx = (roundRobinCursor_ + offset) % 3;
    const CropZoneId candidate = static_cast<CropZoneId>(idx);
    if (isZoneEligibleForAuto(candidate, nowMs, cfg)) {
      roundRobinCursor_ = (idx + 1) % 3;
      return candidate;
    }
  }
  return CropZoneId::NONE;
}

bool IrrigationController::isCommandAlreadyProcessed(const char* commandId) const {
  for (size_t i = 0; i < Config::PROCESSED_CMD_RING_SIZE; ++i) {
    if (processedCmdIds_[i][0] != '\0' &&
        strcmp(processedCmdIds_[i], commandId) == 0) {
      return true;
    }
  }
  return false;
}

void IrrigationController::recordCommandProcessed(const char* commandId) {
  snprintf(processedCmdIds_[processedCmdHead_],
           sizeof(processedCmdIds_[0]),
           "%s",
           commandId);
  processedCmdHead_ = (processedCmdHead_ + 1) % Config::PROCESSED_CMD_RING_SIZE;
}

void IrrigationController::queueCommandReport(const char* commandId,
                                              const char* state,
                                              const char* reason) {
  pendingReport_.hasReport   = true;
  pendingReport_.updatedAtMs = millis();
  snprintf(pendingReport_.commandId, sizeof(pendingReport_.commandId), "%s", commandId);
  snprintf(pendingReport_.state, sizeof(pendingReport_.state), "%s", state);
  snprintf(pendingReport_.reason, sizeof(pendingReport_.reason), "%s", reason ? reason : "");
}
