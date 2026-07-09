#include "vibration_motor.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "i2c_bus.h"
#include "smartcane_config.h"

static const char *TAG = "FEEDBACK";

#define PCA9685_MODE1 0x00
#define PCA9685_PRESCALE 0xFE
#define PCA9685_LED0_ON_L 0x06

typedef struct {
    uint8_t channel;
    bool active;
    int64_t stop_at_ms;
} motor_state_t;

static bool s_ready = false;
static motor_state_t s_motors[3] = {
    {.channel = SMARTCANE_VIB_LEFT_CHANNEL},
    {.channel = SMARTCANE_VIB_RIGHT_CHANNEL},
    {.channel = SMARTCANE_VIB_CENTER_CHANNEL},
};

static int64_t now_ms(void)
{
    return esp_timer_get_time() / 1000;
}

static esp_err_t pca_write8(uint8_t reg, uint8_t value)
{
    uint8_t data[2] = {reg, value};
    return i2c_bus_write(SMARTCANE_PCA9685_ADDR, data, sizeof(data), 50);
}

static esp_err_t pca_set_pwm(uint8_t channel, uint16_t value)
{
    if (channel > 15) {
        return ESP_ERR_INVALID_ARG;
    }
    if (value > SMARTCANE_PCA9685_PWM_MAX) {
        value = SMARTCANE_PCA9685_PWM_MAX;
    }

    uint8_t reg = (uint8_t)(PCA9685_LED0_ON_L + 4 * channel);
    uint8_t data[5] = {
        reg,
        0x00,
        0x00,
        (uint8_t)(value & 0xff),
        (uint8_t)(value >> 8),
    };
    return i2c_bus_write(SMARTCANE_PCA9685_ADDR, data, sizeof(data), 50);
}

static uint16_t level_to_pwm(uint8_t level)
{
    if (level > 100) {
        level = 100;
    }
    return (uint16_t)((uint32_t)level * SMARTCANE_PCA9685_PWM_MAX / 100);
}

static void set_motor(uint8_t index, uint8_t level, uint16_t duration_ms)
{
    if (index >= 3) {
        return;
    }
    if (duration_ms > 300) {
        duration_ms = 300;
    }

    uint16_t pwm = level_to_pwm(level);
    s_motors[index].active = pwm > 0 && duration_ms > 0;
    s_motors[index].stop_at_ms = now_ms() + duration_ms;

    if (s_ready) {
        esp_err_t ret = pca_set_pwm(s_motors[index].channel, pwm);
        if (ret != ESP_OK) {
            ESP_LOGW(TAG, "motor channel %u set failed: %s",
                     s_motors[index].channel,
                     esp_err_to_name(ret));
        }
    } else {
        ESP_LOGI(TAG, "mock motor channel=%u level=%u duration=%u",
                 s_motors[index].channel,
                 level,
                 duration_ms);
    }
}

static void stop_motor(uint8_t index)
{
    if (index >= 3) {
        return;
    }
    s_motors[index].active = false;
    if (s_ready) {
        (void)pca_set_pwm(s_motors[index].channel, 0);
    }
}

esp_err_t vibration_motor_init(void)
{
    if (!i2c_bus_probe(SMARTCANE_PCA9685_ADDR)) {
        s_ready = false;
        ESP_LOGW(TAG, "PCA9685 missing, motor output will be logged");
        return ESP_ERR_NOT_FOUND;
    }

    uint32_t prescale_calc = (25000000UL + (2048UL * SMARTCANE_PCA9685_PWM_FREQ_HZ)) /
                             (4096UL * SMARTCANE_PCA9685_PWM_FREQ_HZ);
    uint8_t prescale = (uint8_t)(prescale_calc > 0 ? prescale_calc - 1 : 0);
    (void)pca_write8(PCA9685_MODE1, 0x10);
    (void)pca_write8(PCA9685_PRESCALE, prescale);
    (void)pca_write8(PCA9685_MODE1, 0x20);

    s_ready = true;
    for (uint8_t i = 0; i < 3; ++i) {
        stop_motor(i);
    }
    ESP_LOGI(TAG, "PCA9685 ready freq=%dHz", SMARTCANE_PCA9685_PWM_FREQ_HZ);
    return ESP_OK;
}

void vibration_motor_update(void)
{
    int64_t now = now_ms();
    for (uint8_t i = 0; i < 3; ++i) {
        if (s_motors[i].active && now >= s_motors[i].stop_at_ms) {
            stop_motor(i);
        }
    }
}

bool vibration_motor_ready(void)
{
    return s_ready;
}

void vibration_motor_left(uint8_t level, uint16_t duration_ms)
{
    set_motor(0, level, duration_ms);
}

void vibration_motor_right(uint8_t level, uint16_t duration_ms)
{
    set_motor(1, level, duration_ms);
}

void vibration_motor_center(uint8_t level, uint16_t duration_ms)
{
    set_motor(2, level, duration_ms);
}

void vibration_motor_all(uint8_t level, uint16_t duration_ms)
{
    vibration_motor_left(level, duration_ms);
    vibration_motor_right(level, duration_ms);
    vibration_motor_center(level, duration_ms);
}

void vibration_motor_pattern_obstacle(void)
{
    vibration_motor_center(SMARTCANE_VIB_LEVEL_MEDIUM, 160);
}

void vibration_motor_pattern_ground_drop(void)
{
    vibration_motor_center(SMARTCANE_VIB_LEVEL_HIGH, 260);
    vibration_motor_left(SMARTCANE_VIB_LEVEL_HIGH, 240);
    vibration_motor_right(SMARTCANE_VIB_LEVEL_HIGH, 240);
}

void vibration_motor_pattern_turn_left(void)
{
    vibration_motor_left(SMARTCANE_VIB_LEVEL_MEDIUM, 180);
}

void vibration_motor_pattern_turn_right(void)
{
    vibration_motor_right(SMARTCANE_VIB_LEVEL_MEDIUM, 180);
}

void vibration_motor_pattern_stop(void)
{
    vibration_motor_all(SMARTCANE_VIB_LEVEL_HIGH, 220);
}

void vibration_motor_pattern_sos(void)
{
    vibration_motor_all(SMARTCANE_VIB_LEVEL_HIGH, 300);
}
