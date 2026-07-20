#include "vibration.h"

#include <Adafruit_PWMServoDriver.h>

#include "config.h"
#include "i2c_bus.h"

static unsigned long stopAtMs[3] = {0, 0, 0};
static bool pcaReady = false;

static Adafruit_PWMServoDriver pca = Adafruit_PWMServoDriver(SMARTCANE_PCA9685_ADDR);

static const uint8_t motorChannels[3] = {
  SMARTCANE_VIB_LEFT_CHANNEL,
  SMARTCANE_VIB_RIGHT_CHANNEL,
  SMARTCANE_VIB_CENTER_CHANNEL
};

static uint16_t levelToPwm(uint8_t level) {
  if (level == 0) {
    return 0;
  }
  uint8_t clamped = level > 100 ? 100 : level;
  return map(clamped,
             1,
             100,
             SMARTCANE_PCA9685_MIN_RUN_PWM,
             SMARTCANE_PCA9685_MAX_PWM);
}

static void writeMotorChannel(uint8_t channel, uint16_t pwmValue) {
  if (!pcaReady) {
    return;
  }
  disableTcaChannels();
  pca.setPWM(channel, 0, pwmValue);
}

static void setMotor(uint8_t index, uint8_t level) {
  if (index >= 3) {
    return;
  }
  writeMotorChannel(motorChannels[index], levelToPwm(level));
}

static void vibrateIndex(uint8_t index, uint8_t level, uint16_t durationMs) {
  if (!pcaReady || index >= 3) {
    return;
  }
  setMotor(index, level);
  stopAtMs[index] = millis() + durationMs;
}

bool vibrationBegin() {
#if !SMARTCANE_VIB_ENABLED
  Serial.println(F("[VIB] disabled"));
  return false;
#else
  disableTcaChannels();
  if (!i2cProbe(SMARTCANE_PCA9685_ADDR)) {
    pcaReady = false;
    Serial.print(F("[VIB] PCA9685 not found at 0x"));
    Serial.println(SMARTCANE_PCA9685_ADDR, HEX);
    return false;
  }

  pca.begin();
  pca.setPWMFreq(SMARTCANE_PCA9685_PWM_FREQ_HZ);
  delay(5);

  pcaReady = true;
  vibrationStopAll();

  Serial.print(F("[VIB] PCA9685 ready addr=0x"));
  Serial.print(SMARTCANE_PCA9685_ADDR, HEX);
  Serial.print(F(" channels L/R/C="));
  Serial.print(SMARTCANE_VIB_LEFT_CHANNEL);
  Serial.print(F("/"));
  Serial.print(SMARTCANE_VIB_RIGHT_CHANNEL);
  Serial.print(F("/"));
  Serial.println(SMARTCANE_VIB_CENTER_CHANNEL);
  return true;
#endif
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
  return pcaReady;
}

const char *vibrationModeName() {
#if !SMARTCANE_VIB_ENABLED
  return "disabled";
#else
  return pcaReady ? "pca9685-ch8-9-10" : "pca9685-not-found";
#endif
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
