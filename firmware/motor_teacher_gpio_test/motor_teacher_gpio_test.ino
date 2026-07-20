/*
 * Teacher-style vibration motor test for ESP32-C5.
 *
 * This sketch intentionally does NOT use PCA9685 and does NOT read ToF sensors.
 * It follows the verified classroom test style:
 *   M1/M2/M3 are plain GPIO outputs.
 *
 * Board: ESP32C5 Dev Module
 * Serial Monitor: 115200 baud, New Line
 */

#include <Arduino.h>

#define M1 1
#define M2 2
#define M3 3

#define MOTOR_ACTIVE_HIGH 1
#define MOTOR_ON_MS 1000
#define MOTOR_GAP_MS 700

static String serialLine;

static void motorWrite(uint8_t pin, bool on) {
#if MOTOR_ACTIVE_HIGH
  digitalWrite(pin, on ? HIGH : LOW);
#else
  digitalWrite(pin, on ? LOW : HIGH);
#endif
}

static void allOff() {
  motorWrite(M1, false);
  motorWrite(M2, false);
  motorWrite(M3, false);
}

static void pulseMotor(uint8_t pin, const char *name) {
  Serial.println(name);
  motorWrite(pin, true);
  delay(MOTOR_ON_MS);
  motorWrite(pin, false);
  delay(MOTOR_GAP_MS);
}

static void pulseAll() {
  Serial.println("MOTOR ALL");
  motorWrite(M1, true);
  motorWrite(M2, true);
  motorWrite(M3, true);
  delay(MOTOR_ON_MS);
  allOff();
  delay(MOTOR_GAP_MS);
}

static void printHelp() {
  Serial.println("Commands:");
  Serial.println("  m1    test motor 1 / left");
  Serial.println("  m2    test motor 2 / right");
  Serial.println("  m3    test motor 3 / center");
  Serial.println("  all   test all motors");
  Serial.println("  stop  all motors off");
}

static void processCommand(String command) {
  command.trim();
  command.toLowerCase();
  if (command == "m1") {
    pulseMotor(M1, "MOTOR 1");
  } else if (command == "m2") {
    pulseMotor(M2, "MOTOR 2");
  } else if (command == "m3") {
    pulseMotor(M3, "MOTOR 3");
  } else if (command == "all") {
    pulseAll();
  } else if (command == "stop") {
    Serial.println("MOTOR STOP");
    allOff();
  } else if (command == "help" || command == "?") {
    printHelp();
  } else if (command.length() > 0) {
    Serial.print("UNKNOWN: ");
    Serial.println(command);
    printHelp();
  }
}

static void handleSerial() {
  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\n' || c == '\r') {
      processCommand(serialLine);
      serialLine = "";
    } else if (serialLine.length() < 40) {
      serialLine += c;
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(1200);

  pinMode(M1, OUTPUT);
  pinMode(M2, OUTPUT);
  pinMode(M3, OUTPUT);
  allOff();

  Serial.println();
  Serial.println("ESP32-C5 TEACHER MOTOR TEST START");
  Serial.print("M1=");
  Serial.print(M1);
  Serial.print(" M2=");
  Serial.print(M2);
  Serial.print(" M3=");
  Serial.println(M3);
  printHelp();
}

void loop() {
  handleSerial();

  pulseMotor(M1, "MOTOR 1");
  handleSerial();
  pulseMotor(M2, "MOTOR 2");
  handleSerial();
  pulseMotor(M3, "MOTOR 3");
  handleSerial();
  pulseAll();
  handleSerial();
}
