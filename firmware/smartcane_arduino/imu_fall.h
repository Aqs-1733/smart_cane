#pragma once

#include <Arduino.h>

struct ImuFallState {
  bool available = false;
  bool mock = false;
  bool fallActive = false;
  bool eventPending = false;
  uint8_t address = 0;
  int16_t axRaw = 0;
  int16_t ayRaw = 0;
  int16_t azRaw = 0;
  float axG = 0.0f;
  float ayG = 0.0f;
  float azG = 1.0f;
  float totalG = 1.0f;
  float pitchDeg = 0.0f;
  float rollDeg = 0.0f;
  float confidence = 0.0f;
  const char *stage = "idle";
  const char *reason = "not_started";
  unsigned long updatedAtMs = 0;
};

bool imuFallBegin();
void imuFallUpdate();
bool imuFallConsumeEvent(ImuFallState &out);
ImuFallState imuFallCurrent();
void imuFallMockTrigger();
void imuFallClear();
void imuFallPrintStatus();
void imuFallPrintRaw();
