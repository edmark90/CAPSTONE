#include "EventNotificationManager.h"
#include <stdio.h>
#include <string.h>

void EventNotificationManager::begin(const char* deviceId,
                                     const char* bootId,
                                     uint32_t ownershipEpoch) {
  setIdentity(deviceId, bootId, ownershipEpoch);
  head_         = 0;
  tail_         = 0;
  count_        = 0;
  sequence_     = 0;
  droppedCount_ = 0;
}

void EventNotificationManager::setIdentity(const char* deviceId,
                                           const char* bootId,
                                           uint32_t ownershipEpoch) {
  if (deviceId && deviceId[0] != '\0') {
    snprintf(deviceId_, sizeof(deviceId_), "%s", deviceId);
  }
  if (bootId && bootId[0] != '\0') {
    snprintf(bootId_, sizeof(bootId_), "%s", bootId);
  }
  ownershipEpoch_ = ownershipEpoch;
  for (size_t i = 0; i < Config::EVENT_QUEUE_CAPACITY; ++i) {
    if (queue_[i].occupied) {
      queue_[i].ownershipEpoch = ownershipEpoch_;
    }
  }
}

bool EventNotificationManager::enqueueEvent(const char* type,
                                            EventSeverity severity,
                                            const char* title,
                                            const char* message,
                                            const char* crop,
                                            float primaryValue) {
  if (!type || type[0] == '\0') return false;

  // Coalesce if an identical unsent event type + crop is already sitting in the queue
  for (size_t i = 0; i < count_; ++i) {
    const size_t idx = (head_ + i) % Config::EVENT_QUEUE_CAPACITY;
    if ( queue_[idx].occupied &&
         strcmp(queue_[idx].type, type) == 0 &&
         strcmp(queue_[idx].crop, crop ? crop : "") == 0 ) {
      // Update message and value in place rather than duplicating
      snprintf(queue_[idx].message, sizeof(queue_[idx].message), "%s", message ? message : "");
      queue_[idx].primaryValue = primaryValue;
      queue_[idx].createdAtMs  = millis();
      return true;
    }
  }

  // If queue is full, drop oldest non-critical event to reserve space for critical alerts
  if (count_ >= Config::EVENT_QUEUE_CAPACITY) {
    if (severity == EventSeverity::CRITICAL) {
      popOldestPending();
      ++droppedCount_;
    } else {
      ++droppedCount_;
      return false;
    }
  }

  ++sequence_;
  DeviceEventRecord& rec = queue_[tail_];
  rec.occupied       = true;
  rec.severity       = severity;
  rec.primaryValue   = primaryValue;
  rec.createdAtMs    = millis();
  rec.ownershipEpoch = ownershipEpoch_;

  snprintf(rec.eventId, sizeof(rec.eventId), "%s_%s_%lu",
           deviceId_, bootId_, static_cast<unsigned long>(sequence_));
  snprintf(rec.type, sizeof(rec.type), "%s", type);
  snprintf(rec.title, sizeof(rec.title), "%s", title ? title : type);
  snprintf(rec.message, sizeof(rec.message), "%s", message ? message : "");
  snprintf(rec.crop, sizeof(rec.crop), "%s", crop ? crop : "");
  snprintf(rec.dedupeKey, sizeof(rec.dedupeKey), "%s:%lu:%s:%lu",
           deviceId_,
           static_cast<unsigned long>(ownershipEpoch_),
           type,
           static_cast<unsigned long>(sequence_));

  tail_ = (tail_ + 1) % Config::EVENT_QUEUE_CAPACITY;
  ++count_;
  return true;
}

bool EventNotificationManager::peekNextPending(DeviceEventRecord& outRecord) const {
  if (count_ == 0) return false;
  outRecord = queue_[head_];
  return outRecord.occupied;
}

void EventNotificationManager::popOldestPending() {
  if (count_ == 0) return;
  queue_[head_].occupied = false;
  head_ = (head_ + 1) % Config::EVENT_QUEUE_CAPACITY;
  --count_;
}

void EventNotificationManager::clearAllForReset() {
  for (size_t i = 0; i < Config::EVENT_QUEUE_CAPACITY; ++i) {
    queue_[i].occupied = false;
  }
  head_  = 0;
  tail_  = 0;
  count_ = 0;
}
