# ESP32-C5 Smart Cane Arduino Firmware

This is the Arduino IDE / Arduino framework firmware for the ESP32-C5 collaborative smart cane demo.

It is designed for the hardware already tested in the supplied Arduino IDE screenshots:

- ESP32C5 Dev Module at `115200` baud
- I2C on `SDA=GPIO2`, `SCL=GPIO3`
- TCA9548A at `0x70`
- Four VL53L1X sensors through TCA channels `CH2/CH3/CH4/CH5`
- MPR121/HW-017 touch module on TCA `CH7` at `0x5A`
- Active buzzer on `GPIO4`
- Teacher-verified vibration motor wiring on GPIO `8/9/10` using HIGH/LOW output
- Optional ESP-SensairShuttle BMI270 motion sensor for fall detection

All pins, I2C addresses, thresholds, Wi-Fi, backend URL, and device ID are in `config.h`.

## Arduino Libraries

Install these from Arduino IDE Library Manager:

- `Adafruit MPR121`
- `Adafruit PWM Servo Driver Library`
- `VL53L1X` by Pololu
- `ArduinoJson`

`WiFi` and `HTTPClient` come from the ESP32 Arduino board package.

## Wiring

| Hardware | Connection |
| --- | --- |
| TCA9548A SDA/SCL | ESP32-C5 `GPIO2/GPIO3` |
| MPR121 SDA/SCL | TCA `CH7` by current bench wiring, or root I2C if `SMARTCANE_TOUCH_ON_TCA=0` |
| VL53L1X front | TCA `CH2` |
| VL53L1X left | TCA `CH3` |
| VL53L1X right | TCA `CH4` |
| VL53L1X down | TCA `CH5` |
| Left vibration signal | GPIO `8`, teacher HIGH/LOW test logic |
| Right vibration signal | GPIO `9`, teacher HIGH/LOW test logic |
| Center vibration signal | GPIO `10`, teacher HIGH/LOW test logic |
| Buzzer | `GPIO4` |
| SOS button | `GPIO5`, active low with internal pull-up |
| BMI270 | Built-in SensairShuttle sensor; same I2C bus if present |

Keep each three-pin motor plug orientation unchanged when moving it: black/brown to `GND`, red to `V+`, and white/orange/yellow to `PWM`.

If your final wiring returns to the original `CH0/CH1/CH2/CH3` ToF plan, only change these macros in `config.h`:

```cpp
#define SMARTCANE_TCA_CH_TOF_FRONT 0
#define SMARTCANE_TCA_CH_TOF_LEFT 1
#define SMARTCANE_TCA_CH_TOF_RIGHT 2
#define SMARTCANE_TCA_CH_TOF_DOWN 3
```

## Configure

Edit `config.h`:

```cpp
#define SMARTCANE_DEVICE_ID "cane_001"
#define SMARTCANE_WIFI_SSID "your_wifi"
#define SMARTCANE_WIFI_PASSWORD "your_password"
#define SMARTCANE_SERVER_BASE_URL "http://your_pc_lan_ip:8000"
#define SMARTCANE_MOCK_LAT 31.230400
#define SMARTCANE_MOCK_LNG 121.473700
```

Use your PC LAN IP, not `127.0.0.1`, because `127.0.0.1` from the ESP32 means the ESP32 itself.

## Open And Flash

1. Open Arduino IDE.
2. Open `firmware/smartcane_arduino/smartcane_arduino.ino`.
3. Select `ESP32C5 Dev Module`.
4. Select the COM port, for example `COM3`.
5. Install the libraries above.
6. Upload.
7. Open Serial Monitor at `115200 baud`, newline enabled.

## What Runs Locally

Local safety does not depend on Wi-Fi:

- Samples four ToF distances every `3000 ms` for bench testing.
- Detects front warning/danger by distance thresholds.
- Detects ground drops from the down-facing sensor.
- Fuses nearby history when available.
- Drives left/right/center vibration motors.
- Uses the buzzer only for high-risk cases, ground drops, and SOS.
- Debounces the SOS button and triggers after `2 s`.
- Reads MPR121 touch electrodes 0-5.
- Reads BMI270 acceleration and raises `fall_detected` on impact/freefall plus lying posture. Fall alert uses buzzer and backend upload only, no vibration.

## Route And Risk Recording

Because the current purchase list does not include a verified GNSS module, route recording uses mock/mobile-replaceable coordinates from `config.h` by default.

When the location moves into a new small grid cell, the firmware:

- stores it in a local ring buffer,
- uploads it to `POST /api/locations` when network mode is enabled.

Local risk events are event-driven: the same risk type/level/direction in the same location grid is logged, vibrated, and uploaded only once. It is reported again after the risk changes, clears and reappears, or the user moves into another grid cell. User marks are uploaded to `POST /api/risk-events`. Another device ID can then call `GET /api/risks/nearby` and use the historical risk count in local risk fusion.

`SMARTCANE_MOCK_ROUTE_ENABLED` is `0` by default for bench testing. Set it to `1` only when you want the device to simulate walking while it is not physically moving.

Optional UART GNSS parsing is reserved behind `SMARTCANE_GNSS_ENABLED`, but it is disabled by default to match the currently verified hardware.

## Touch Controls

| Electrode | Action |
| --- | --- |
| E0 tap | Print current road/risk status, call backend deep-risk if online |
| E1 long press | Upload `user_mark` risk point |
| E2 tap | Repeat last vibration cue |
| E3 tap | Toggle local/network mode |
| E4 tap | Manual left cue |
| E5 tap | Manual right cue |

The firmware prints every touch event clearly to Serial.

## Serial Demo Commands

Use these in Serial Monitor:

```text
help
status
read
scan
mock auto
mock clear
mock warn
mock danger
mock drop
mock blocked
mock left
mock right
nearby
deep
mark
sos
mode
path
vib status
vib left
vib right
vib center
vib all
vib stop
imu
imuraw
fall
fallclear
t0
t1long
t2
t3
t4
t5
```

These commands allow a full demo even without all touch or ToF hardware attached.

## Demo Flow

1. Start the backend.
2. Flash `cane_001`.
3. Run `status` or `read` once to see the current distances and risk state.
4. Put an obstacle in front: Serial prints one risk event, center motor vibrates, and high danger also beeps.
5. Keep the obstacle still: the same place/same risk is not printed repeatedly.
6. Open left/right side space or move to another grid cell: the left/right motor suggests the safer direction and a new event can be recorded.
7. Lift the down-facing sensor or use `mock drop`: ground drop triggers strong vibration and buzzer once for that place.
8. Run `mark` or long-press touch E1: backend records a user risk point.
9. Run `path`: local walked route/risk ring buffer is printed.
10. Change `SMARTCANE_DEVICE_ID` to `cane_002`, flash again, and run `nearby`: the second cane sees the historical risk area.
11. Hold SOS for 2 seconds or run `sos`: buzzer, vibration, Serial SOS log, and backend upload.
12. Run `imu` and `imuraw` to check BMI270. Run `fall` to simulate a fall: the buzzer alarms, no motor runs, and the backend exposes the alert to both blind and companion app roles.
