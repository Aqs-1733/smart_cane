#include "vibration.h"

#include "config.h"

static unsigned long stopAtMs[3] = {0, 0, 0};

static const uint8_t motorPins[3] = {
  SMARTCANE_VIB_LEFT_PIN,
  SMARTCANE_VIB_RIGHT_PIN,
  SMARTCANE_VIB_CENTER_PIN
};

static void writeMotorPin(uint8_t pin, bool on) {
#if SMARTCANE_VIB_ACTIVE_HIGH
  digitalWrite(pin, on ? HIGH : LOW);
#else
  digitalWrite(pin, on ? LOW : HIGH);
#endif
}

static void setMotor(uint8_t index, uint8_t level) {
  if (index >= 3) return;

  // Teacher-style motor logic: only ON/OFF.
  // level > 0 means output ON, level == 0 means output OFF.
  writeMotorPin(motorPins[index], level > 0);
}

static void vibrateIndex(uint8_t index, uint8_t level, uint16_t durationMs) {
  setMotor(index, level);
  stopAtMs[index] = millis() + durationMs;
}

bool vibrationBegin() {
  for (uint8_t i = 0; i < 3; ++i) {
    pinMode(motorPins[i], OUTPUT);
    stopAtMs[i] = 0;
    setMotor(i, 0);
  }

  Serial.print(F("[VIB] GPIO teacher logic ready pins L/R/C="));
  Serial.print(SMARTCANE_VIB_LEFT_PIN);
  Serial.print(F("/"));
  Serial.print(SMARTCANE_VIB_RIGHT_PIN);
  Serial.print(F("/"));
  Serial.println(SMARTCANE_VIB_CENTER_PIN);
  return true;
}

void vibrationUpdate() {
  unsigned long now = millis();
  for (uint8_t i = 0; i < 3; ++i) {
    if (stopAtMs[i] != 0 && (long)(now - stopAtMs[i]) >= 0) {
      stopAtMs[i] = 0;
      setMotor(i, 0);
    }
  }
}

bool vibrationReady() {
  return true;
}

const char *vibrationModeName() {
  return "gpio-8-9-10-teacher";
}

void vibrateLeft(uint8_t level, uint16_t durationMs) {
  vibrateIndex(0, level, durationMs);
}

void vibrateRight(uint8_t level, uint16_t durationMs) {
  vibrateIndex(1, level, durationMs);
}

void vibrateCenter(uint8_t level, uint16_t durationMs) {
  vibrateIndex(2, level, durationMs);
}

void vibrateAll(uint8_t level, uint16_t durationMs) {
  vibrateLeft(level, durationMs);
  vibrateRight(level, durationMs);
  vibrateCenter(level, durationMs);
}

void vibrationStopAll() {
  for (uint8_t i = 0; i < 3; ++i) {
    stopAtMs[i] = 0;
    setMotor(i, 0);
  }
}

void patternObstacle() {
  vibrateCenter(SMARTCANE_VIB_LEVEL_MEDIUM, 180);
}

void patternGroundDrop() {
  vibrateAll(SMARTCANE_VIB_LEVEL_HIGH, 300);
}

void patternTurnLeft() {
  vibrateLeft(SMARTCANE_VIB_LEVEL_MEDIUM, 220);
}

void patternTurnRight() {
  vibrateRight(SMARTCANE_VIB_LEVEL_MEDIUM, 220);
}

void patternStop() {
  vibrateAll(SMARTCANE_VIB_LEVEL_HIGH, 260);
}

void patternSos() {
  vibrateAll(SMARTCANE_VIB_LEVEL_HIGH, 350);
}
