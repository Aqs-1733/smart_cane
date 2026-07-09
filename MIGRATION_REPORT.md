# ESP-IDF 迁移报告

## 1. 删除的旧依赖

- 删除旧 `arduino/` 固件目录
- 删除旧 `firmware/smartcane_arduino/` 工程
- 删除 `.ino` 主程序
- 不再使用 `Arduino.h`
- 不再使用 `setup()` / `loop()`
- 不再使用 Arduino 风格 GPIO、I2C、串口、延时、时间和网络 API

## 2. 新增 ESP-IDF 文件

- `CMakeLists.txt`
- `sdkconfig.defaults`
- `partitions.csv`
- `main/main.c`
- `main/app_tasks.c`
- `main/app_tasks.h`
- `main/board_config.h`
- `components/common/include/smartcane_config.h`
- `components/common/include/app_types.h`
- `components/i2c_bus/*`
- `components/tof_sensors/*`
- `components/touch_input/*`
- `components/vibration_motor/*`
- `components/buzzer/*`
- `components/buttons/*`
- `components/gps_location/*`
- `components/risk_logic/*`
- `components/communication/*`

## 3. 模块与 ESP-IDF 驱动对应关系

| 模块 | ESP-IDF 驱动 / 组件 |
| --- | --- |
| I2C / TCA9548A | `driver/i2c_master.h` |
| VL53L1X | 原生 I2C 读写 + TCA9548A 通道选择 |
| MPR121 | 原生 I2C 读写 |
| GPIO 触控兜底 | `driver/gpio.h` |
| SOS 按键 | `driver/gpio.h` |
| 有源蜂鸣器 | `driver/gpio.h` + 非阻塞状态机 |
| PCA9685 震动马达 | 原生 I2C PWM 寄存器控制 |
| GPS/GNSS | `driver/uart.h` + NMEA RMC/GGA 解析 |
| 时间 | `esp_timer_get_time()` |
| 日志 | `esp_log.h` |
| 多任务 | FreeRTOS task |
| Wi-Fi | `esp_wifi.h` / `esp_netif.h` / `esp_event.h` |
| HTTP | `esp_http_client.h` |
| JSON | `cJSON` |
| 本地设备协同 | `esp_now.h` |

## 4. 旧 API 到新 API 的对应关系

| 旧调用类别 | ESP-IDF 替代 |
| --- | --- |
| GPIO 输入输出 | `gpio_config()`、`gpio_get_level()`、`gpio_set_level()` |
| 毫秒周期 | `esp_timer_get_time() / 1000` |
| 非阻塞等待 | `vTaskDelay(pdMS_TO_TICKS(x))` |
| 串口日志 | `ESP_LOGI()`、`ESP_LOGW()`、`ESP_LOGE()` |
| I2C 总线 | `i2c_new_master_bus()`、`i2c_master_transmit_receive()` |
| Wi-Fi | `esp_wifi_*` |
| HTTP | `esp_http_client_*` |
| JSON | `cJSON` |
| 主循环 | FreeRTOS tasks |

## 5. 已可运行功能

- mock ToF 距离演示
- 本地风险计算
- 地面落差检测
- 蜂鸣器报警状态机
- 震动马达状态机，PCA9685 缺失时日志模拟
- SOS 长按触发
- GPS mock 兜底
- 后端 HTTP 接口对接
- ESP-NOW 状态广播与接收
- 周期调试日志

## 6. 需要接真实硬件后验证

- VL53L1X 实测距离寄存器读取稳定性
- MPR121 触摸阈值
- GPS 模块定位质量
- 蜂鸣器电平和实际响度
- PCA9685 频率、MOS 管和马达强度
- ESP-NOW 在目标环境下的收发范围

## 7. 编译结果

ESP-IDF v6.0.2 已编译到生成 `smart_cane_espidf.bin` 阶段。默认 1MB factory 分区过小，已新增 `partitions.csv`，将 factory app 分区调整为 1536K。

在已安装 ESP-IDF v6.0.2 的环境中执行：

```bash
idf.py set-target esp32c5
idf.py fullclean
idf.py build
```

## 8. 烧录测试方法

```bash
idf.py -p COMx flash monitor
```

观察日志：

- 系统启动成功
- 目标芯片输出
- I2C / ToF / 触控 / 震动 / 蜂鸣器 / GPS / 通信初始化结果
- 距离数据
- 风险判断结果
- 反馈状态
- Wi-Fi / HTTP / ESP-NOW 状态

## 9. 后续需要手动确认

- `SMARTCANE_WIFI_SSID` / `SMARTCANE_WIFI_PASSWORD`
- `SMARTCANE_SERVER_BASE_URL`
- ESP32-C5 板卡实际 I2C、UART、蜂鸣器、SOS GPIO 是否与接线一致
- GPS 模块默认波特率
- 是否关闭 `SMARTCANE_MOCK_SENSOR_MODE` 进入真实 ToF 测距
