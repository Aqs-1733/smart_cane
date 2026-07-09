#include "touch_input.h"

#include <string.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "i2c_bus.h"
#include "smartcane_config.h"

static const char *TAG = "TOUCH";

#define MPR121_TOUCH_STATUS_L 0x00
#define MPR121_ELE0_TOUCH_THRESHOLD 0x41
#define MPR121_ECR 0x5E

static bool s_mpr121_active = false;
static bool s_gpio_fallback = false;
static bool s_touched[6] = {false};
static bool s_long_sent[6] = {false};
static bool s_pending_tap[6] = {false};
static int64_t s_touch_start_ms[6] = {0};
static int64_t s_pending_tap_ms[6] = {0};

static const int s_touch_gpio[6] = {
    SMARTCANE_TOUCH_E0_GPIO,
    SMARTCANE_TOUCH_E1_GPIO,
    SMARTCANE_TOUCH_E2_GPIO,
    SMARTCANE_TOUCH_E3_GPIO,
    SMARTCANE_TOUCH_E4_GPIO,
    SMARTCANE_TOUCH_E5_GPIO,
};

static int64_t now_ms(void)
{
    return esp_timer_get_time() / 1000;
}

const char *touch_input_event_name(touch_event_type_t type)
{
    switch (type) {
    case TOUCH_EVENT_LONG_PRESS:
        return "long_press";
    case TOUCH_EVENT_DOUBLE_CLICK:
        return "double_click";
    case TOUCH_EVENT_TAP:
    default:
        return "tap";
    }
}

static esp_err_t mpr121_write8(uint8_t reg, uint8_t value)
{
    uint8_t data[2] = {reg, value};
    return i2c_bus_write(SMARTCANE_MPR121_ADDR, data, sizeof(data), 50);
}

static esp_err_t mpr121_read_touch(uint16_t *mask)
{
    if (mask == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    uint8_t reg = MPR121_TOUCH_STATUS_L;
    uint8_t data[2] = {0};
    esp_err_t ret = i2c_bus_write_read(SMARTCANE_MPR121_ADDR, &reg, 1, data, sizeof(data), 50);
    if (ret != ESP_OK) {
        return ret;
    }
    *mask = (uint16_t)((data[1] << 8) | data[0]);
    return ESP_OK;
}

static bool mpr121_configure(void)
{
    if (!i2c_bus_probe(SMARTCANE_MPR121_ADDR)) {
        return false;
    }

    if (mpr121_write8(MPR121_ECR, 0x00) != ESP_OK) {
        return false;
    }

    for (uint8_t electrode = 0; electrode < 12; ++electrode) {
        uint8_t base = (uint8_t)(MPR121_ELE0_TOUCH_THRESHOLD + electrode * 2);
        (void)mpr121_write8(base, 12);
        (void)mpr121_write8(base + 1, 6);
    }

    (void)mpr121_write8(0x2B, 0x01);
    (void)mpr121_write8(0x2C, 0x01);
    (void)mpr121_write8(0x2D, 0x00);
    (void)mpr121_write8(0x2E, 0x00);
    (void)mpr121_write8(0x2F, 0x01);
    (void)mpr121_write8(0x30, 0x01);
    (void)mpr121_write8(0x31, 0xFF);
    (void)mpr121_write8(0x32, 0x02);
    (void)mpr121_write8(0x5D, 0x04);
    return mpr121_write8(MPR121_ECR, 0x8F) == ESP_OK;
}

static void emit_event(touch_event_callback_t callback, void *ctx, uint8_t electrode, touch_event_type_t type)
{
    ESP_LOGI(TAG, "electrode=%u event=%s", electrode, touch_input_event_name(type));
    if (callback != NULL) {
        callback(electrode, type, ctx);
    }
}

static bool read_gpio_touched(uint8_t electrode)
{
    if (electrode >= 6 || s_touch_gpio[electrode] < 0) {
        return false;
    }
    int level = gpio_get_level((gpio_num_t)s_touch_gpio[electrode]);
    return SMARTCANE_TOUCH_ACTIVE_LOW ? (level == 0) : (level != 0);
}

static void process_electrode(uint8_t electrode,
                              bool now_touched,
                              touch_event_callback_t callback,
                              void *ctx)
{
    int64_t now = now_ms();

    if (now_touched && !s_touched[electrode]) {
        s_touched[electrode] = true;
        s_touch_start_ms[electrode] = now;
        s_long_sent[electrode] = false;
    }

    if (now_touched && s_touched[electrode] && !s_long_sent[electrode] &&
        now - s_touch_start_ms[electrode] >= SMARTCANE_TOUCH_LONG_PRESS_MS) {
        s_long_sent[electrode] = true;
        s_pending_tap[electrode] = false;
        emit_event(callback, ctx, electrode, TOUCH_EVENT_LONG_PRESS);
    }

    if (!now_touched && s_touched[electrode]) {
        s_touched[electrode] = false;
        if (!s_long_sent[electrode]) {
            if (s_pending_tap[electrode] &&
                now - s_pending_tap_ms[electrode] <= SMARTCANE_TOUCH_DOUBLE_CLICK_MS) {
                s_pending_tap[electrode] = false;
                emit_event(callback, ctx, electrode, TOUCH_EVENT_DOUBLE_CLICK);
            } else {
                s_pending_tap[electrode] = true;
                s_pending_tap_ms[electrode] = now;
            }
        }
    }

    if (s_pending_tap[electrode] &&
        now - s_pending_tap_ms[electrode] > SMARTCANE_TOUCH_DOUBLE_CLICK_MS) {
        s_pending_tap[electrode] = false;
        emit_event(callback, ctx, electrode, TOUCH_EVENT_TAP);
    }
}

esp_err_t touch_input_init(void)
{
    s_mpr121_active = mpr121_configure();
    if (s_mpr121_active) {
        ESP_LOGI(TAG, "MPR121 ready at addr=0x%02x", SMARTCANE_MPR121_ADDR);
        return ESP_OK;
    }

    uint64_t pin_mask = 0;
    for (int i = 0; i < 6; ++i) {
        if (s_touch_gpio[i] >= 0) {
            pin_mask |= (1ULL << s_touch_gpio[i]);
        }
    }

    if (pin_mask != 0) {
        gpio_config_t cfg = {
            .pin_bit_mask = pin_mask,
            .mode = GPIO_MODE_INPUT,
            .pull_up_en = SMARTCANE_TOUCH_ACTIVE_LOW ? GPIO_PULLUP_ENABLE : GPIO_PULLUP_DISABLE,
            .pull_down_en = SMARTCANE_TOUCH_ACTIVE_LOW ? GPIO_PULLDOWN_DISABLE : GPIO_PULLDOWN_ENABLE,
            .intr_type = GPIO_INTR_DISABLE,
        };
        esp_err_t ret = gpio_config(&cfg);
        if (ret != ESP_OK) {
            ESP_LOGE(TAG, "GPIO touch fallback config failed: %s", esp_err_to_name(ret));
            return ret;
        }
        s_gpio_fallback = true;
        ESP_LOGW(TAG, "MPR121 missing, GPIO touch fallback enabled");
        return ESP_OK;
    }

    ESP_LOGW(TAG, "MPR121 missing and no GPIO touch fallback pins configured");
    return ESP_ERR_NOT_FOUND;
}

void touch_input_update(touch_event_callback_t callback, void *ctx)
{
    uint16_t mask = 0;
    if (s_mpr121_active) {
        if (mpr121_read_touch(&mask) != ESP_OK) {
            ESP_LOGW(TAG, "MPR121 read failed");
            return;
        }
        for (uint8_t electrode = 0; electrode < 6; ++electrode) {
            process_electrode(electrode, (mask & (1U << electrode)) != 0, callback, ctx);
        }
        return;
    }

    if (s_gpio_fallback) {
        for (uint8_t electrode = 0; electrode < 6; ++electrode) {
            process_electrode(electrode, read_gpio_touched(electrode), callback, ctx);
        }
    }
}

bool touch_input_mpr121_active(void)
{
    return s_mpr121_active;
}

