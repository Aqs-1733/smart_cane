#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

#define SDA_PIN 2
#define SCL_PIN 3
#define PCA_ADDR 0x40

#define CH_LEFT 8
#define CH_RIGHT 9
#define CH_CENTER 10

Adafruit_PWMServoDriver pwm(PCA_ADDR);

static bool probe(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

static void scanBus() {
  Serial.println("[SCAN] root I2C");
  bool foundAny = false;
  for (uint8_t addr = 1; addr < 127; ++addr) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      Serial.print("  found 0x");
      if (addr < 16) Serial.print("0");
      Serial.println(addr, HEX);
      foundAny = true;
      delay(2);
    }
  }
  if (!foundAny) {
    Serial.println("  no device");
  }
}

static void motorOn(uint8_t ch) {
  pwm.setPWM(ch, 0, 4095);
}

static void motorOff(uint8_t ch) {
  pwm.setPWM(ch, 0, 0);
}

static void allOff() {
  motorOff(CH_LEFT);
  motorOff(CH_RIGHT);
  motorOff(CH_CENTER);
}

static void pulseChannel(const char *name, uint8_t ch) {
  Serial.print("[TEST] ");
  Serial.print(name);
  Serial.print(" CH");
  Serial.println(ch);
  motorOn(ch);
  delay(1000);
  motorOff(ch);
  delay(800);
}

void setup() {
  Serial.begin(115200);
  delay(1500);

  Serial.println();
  Serial.println("PCA9685 CH8/CH9/CH10 ON/OFF TEST");
  Serial.println("Wire: SDA=GPIO2 SCL=GPIO3 clock=100kHz");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  delay(100);

  scanBus();

  if (!probe(PCA_ADDR)) {
    Serial.println("[PCA] 0x40 NOT FOUND");
    Serial.println("[PCA] check SDA/SCL/3V-or-VCC/GND/VCC-Select");
    return;
  }

  Serial.println("[PCA] 0x40 FOUND");
  pwm.begin();
  pwm.setPWMFreq(160);
  allOff();
  delay(300);
}

void loop() {
  if (!probe(PCA_ADDR)) {
    Serial.println("[PCA] lost 0x40");
    delay(2000);
    return;
  }

  pulseChannel("LEFT", CH_LEFT);
  pulseChannel("RIGHT", CH_RIGHT);
  pulseChannel("CENTER", CH_CENTER);

  Serial.println("[TEST] ALL CH8/9/10");
  motorOn(CH_LEFT);
  motorOn(CH_RIGHT);
  motorOn(CH_CENTER);
  delay(1200);
  allOff();
  delay(2000);
}
