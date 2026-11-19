#pragma once
/**
 * @file EventNotificationManager.h
 * @brief Bounded ring buffer and deduplication manager for ESP32 state-transition events.
 *
 * Ensures that:
 * - Push/event records are created ONLY on verified state transitions, NEVER on routine 15s samples.
 * - Each event carries a deterministic eventId (<deviceId>:<bootId>:<seq>) and dedupeKey.
 * - Critical events are preserved in a bounded RAM queue when Wi-Fi is temporarily offline.
 */

#include <Arduino.h>
#include "Config.h"
#include "Models.h"

class EventNotificationManager {
 public:
  void begin(const char* deviceId, const char* bootId, uint32_t ownershipEpoch);
  void setIdentity(const char* deviceId, const char* bootId, uint32_t ownershipEpoch);

  bool enqueueEvent(const char* type,
                    EventSeverity severity,
                    const char* title,
                    const char* message,
                    const char* crop = nullptr,
                    float primaryValue = NAN);

  bool peekNextPending(DeviceEventRecord& outRecord) const;
  void popOldestPending();
  void clearAllForReset();

  size_t   pendingCount() const { return count_; }
  uint32_t droppedCount() const { return droppedCount_; }

 private:
  DeviceEventRecord queue_[Config::EVENT_QUEUE_CAPACITY];
  size_t            head_           = 0;
  size_t            tail_           = 0;
  size_t            count_          = 0;
  uint32_t          sequence_       = 0;
  uint32_t          droppedCount_   = 0;
  uint32_t          ownershipEpoch_ = 0;
  char              deviceId_[24]   = "spc-unit-0001";
  char              bootId_[24]     = "boot-0";
};
