#include "imu_fall.h"

#include <Wire.h>
#include <math.h>

#include "config.h"

// BMI270 minimal register path for acceleration-only fall demo.
static const uint8_t REG_CHIP_ID = 0x00;
static const uint8_t REG_ACC_X_LSB = 0x0C;
static const uint8_t REG_ACC_CONF = 0x40;
static const uint8_t REG_ACC_RANGE = 0x41;
static const uint8_t REG_PWR_CONF = 0x7C;
static const uint8_t REG_PWR_CTRL = 0x7D;

static ImuFallState state;
static unsigned long lastSampleMs = 0;
static unsigned long impactMs = 0;
static unsigned long lyingSinceMs = 0;
static unsigned long lastFallEventMs = 0;

static bool writeReg(uint8_t reg, uint8_t value) {
  Wire.beginTransmission(state.address);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

static bool readReg(uint8_t reg, uint8_t *buffer, uint8_t len) {
  Wire.beginTransmission(state.address);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) {
    return false;
  }
  uint8_t got = Wire.requestFrom(state.address, len);
  if (got != len) {
    return false;
  }
  for (uint8_t i = 0; i < len; ++i) {
    buffer[i] = Wire.read();
  }
  return true;
}

static uint8_t readChipId(uint8_t address) {
  state.address = address;
  uint8_t id = 0;
  if (!readReg(REG_CHIP_ID, &id, 1)) {
    return 0;
  }
  return id;
}

static bool readAccel() {
  uint8_t bytes[6] = {0};
  if (!readReg(REG_ACC_X_LSB, bytes, sizeof(bytes))) {
    state.available = false;
    state.reason = "read_failed";
    return false;
  }

  state.axRaw = (int16_t)((uint16_t)bytes[1] << 8 | bytes[0]);
  state.ayRaw = (int16_t)((uint16_t)bytes[3] << 8 | bytes[2]);
  state.azRaw = (int16_t)((uint16_t)bytes[5] << 8 | bytes[4]);

  // ACC_RANGE is configured to +-4 g, so 1 g is 8192 LSB.
  state.axG = state.axRaw / 8192.0f;
  state.ayG = state.ayRaw / 8192.0f;
  state.azG = state.azRaw / 8192.0f;
  state.totalG = sqrtf(state.axG * state.axG + state.ayG * state.ayG + state.azG * state.azG);

  float safeTotal = state.totalG < 0.01f ? 0.01f : state.totalG;
  float zRatio = fabsf(state.azG) / safeTotal;
  zRatio = zRatio > 1.0f ? 1.0f : zRatio;
  float tiltDeg = acosf(zRatio) * 57.2957795f;
  state.pitchDeg = atan2f(state.axG, sqrtf(state.ayG * state.ayG + state.azG * state.azG)) * 57.2957795f;
  state.rollDeg = atan2f(state.ayG, state.azG) * 57.2957795f;
  state.updatedAtMs = millis();

  bool impact = state.totalG >= SMARTCANE_FALL_IMPACT_G || state.totalG <= SMARTCANE_FALL_FREEFALL_G;
  bool lying = tiltDeg >= SMARTCANE_FALL_LIE_TILT_DEG && state.totalG >= 0.65f && state.totalG <= 1.45f;

  unsigned long now = millis();
  if (impact) {
    impactMs = now;
    lyingSinceMs = 0;
    state.stage = state.totalG <= SMARTCANE_FALL_FREEFALL_G ? "freefall" : "impact";
    state.reason = "impact_or_freefall";
    state.confidence = 0.45f;
  } else if (impactMs != 0 && now - impactMs <= SMARTCANE_FALL_CONFIRM_WINDOW_MS && lying) {
    if (lyingSinceMs == 0) {
      lyingSinceMs = now;
    }
    state.stage = "lying_candidate";
    state.reason = "impact_then_tilted_still";
    state.confidence = 0.65f;
    if (now - lyingSinceMs >= SMARTCANE_FALL_LIE_MS &&
        now - lastFallEventMs >= SMARTCANE_FALL_UPLOAD_COOLDOWN_MS) {
      state.fallActive = true;
      state.eventPending = true;
      state.stage = "confirmed";
      state.reason = "confirmed_fall";
      state.confidence = 0.92f;
      lastFallEventMs = now;
    }
  } else {
    if (impactMs != 0 && now - impactMs > SMARTCANE_FALL_CONFIRM_WINDOW_MS) {
      impactMs = 0;
      lyingSinceMs = 0;
    }
    if (!state.fallActive) {
      state.stage = "normal";
      state.reason = "normal_motion";
      state.confidence = 0.2f;
    }
  }

  return true;
}

bool imuFallBegin() {
#if !SMARTCANE_IMU_ENABLED
  state.available = false;
  state.reason = "disabled";
  Serial.println(F("[IMU] disabled"));
  return false;
#else
  uint8_t id = readChipId(SMARTCANE_BMI270_ADDR_PRIMARY);
  if (id != SMARTCANE_BMI270_CHIP_ID) {
    id = readChipId(SMARTCANE_BMI270_ADDR_SECONDARY);
  }

  if (id != SMARTCANE_BMI270_CHIP_ID) {
    state.available = false;
    state.mock = false;
    state.address = 0;
    state.reason = "bmi270_not_found";
    Serial.println(F("[IMU] BMI270 not found; use Serial command 'fall' for demo"));
    return false;
  }

  state.available = true;
  state.mock = false;
  state.reason = "bmi270_ready";
  writeReg(REG_PWR_CONF, 0x00);
  delay(10);
  writeReg(REG_PWR_CTRL, 0x04);
  delay(10);
  writeReg(REG_ACC_CONF, 0xA8);   // accel normal mode, about 100 Hz ODR
  writeReg(REG_ACC_RANGE, 0x01);  // +-4 g
  delay(50);

  Serial.print(F("[IMU] BMI270 OK addr=0x"));
  Serial.println(state.address, HEX);
  readAccel();
  return true;
#endif
}

void imuFallUpdate() {
  unsigned long now = millis();
  if (now - lastSampleMs < SMARTCANE_IMU_SAMPLE_INTERVAL_MS) {
    return;
  }
  lastSampleMs = now;
  if (!state.available || state.mock) {
    return;
  }
  readAccel();
}

bool imuFallConsumeEvent(ImuFallState &out) {
  if (!state.eventPending) {
    return false;
  }
  out = state;
  state.eventPending = false;
  return true;
}

ImuFallState imuFallCurrent() {
  return state;
}

void imuFallMockTrigger() {
  state.available = true;
  state.mock = true;
  state.fallActive = true;
  state.eventPending = true;
  state.totalG = 2.8f;
  state.axG = 0.9f;
  state.ayG = 0.2f;
  state.azG = 0.25f;
  state.pitchDeg = 74.0f;
  state.rollDeg = 66.0f;
  state.confidence = 0.9f;
  state.stage = "mock_confirmed";
  state.reason = "serial_mock_fall";
  state.updatedAtMs = millis();
}

void imuFallClear() {
  state.fallActive = false;
  state.eventPending = false;
  state.stage = state.available ? "normal" : "idle";
  state.reason = state.available ? "manual_clear" : state.reason;
  impactMs = 0;
  lyingSinceMs = 0;
}

void imuFallPrintStatus() {
  Serial.print(F("[IMU] available="));
  Serial.print(state.available ? F("yes") : F("no"));
  Serial.print(F(" mock="));
  Serial.print(state.mock ? F("yes") : F("no"));
  Serial.print(F(" addr=0x"));
  Serial.print(state.address, HEX);
  Serial.print(F(" fall="));
  Serial.print(state.fallActive ? F("yes") : F("no"));
  Serial.print(F(" stage="));
  Serial.print(state.stage);
  Serial.print(F(" total_g="));
  Serial.print(state.totalG, 2);
  Serial.print(F(" pitch="));
  Serial.print(state.pitchDeg, 1);
  Serial.print(F(" roll="));
  Serial.print(state.rollDeg, 1);
  Serial.print(F(" confidence="));
  Serial.print(state.confidence, 2);
  Serial.print(F(" reason="));
  Serial.println(state.reason);
}

void imuFallPrintRaw() {
  if (state.available && !state.mock) {
    readAccel();
  }
  Serial.print(F("[IMU_RAW] ax="));
  Serial.print(state.axRaw);
  Serial.print(F(" ay="));
  Serial.print(state.ayRaw);
  Serial.print(F(" az="));
  Serial.print(state.azRaw);
  Serial.print(F(" ax_g="));
  Serial.print(state.axG, 3);
  Serial.print(F(" ay_g="));
  Serial.print(state.ayG, 3);
  Serial.print(F(" az_g="));
  Serial.print(state.azG, 3);
  Serial.print(F(" total_g="));
  Serial.println(state.totalG, 3);
}
